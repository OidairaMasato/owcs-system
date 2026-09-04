package jp.oidaira.owcs.sync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jp.oidaira.owcs.OwcsProperties;
import jp.oidaira.owcs.domain.GameResult;
import jp.oidaira.owcs.domain.Match;
import jp.oidaira.owcs.domain.StreamLink;
import jp.oidaira.owcs.domain.SyncState;
import jp.oidaira.owcs.domain.Team;
import jp.oidaira.owcs.pandascore.PandaScoreClient;
import jp.oidaira.owcs.pandascore.Ps;
import jp.oidaira.owcs.repo.MatchRepository;
import jp.oidaira.owcs.repo.SyncStateRepository;
import jp.oidaira.owcs.repo.TeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PandaScore → 自前 DB への取り込み。
 *
 * 設計の要:
 * - 画面リクエスト時に PandaScore を叩かない。取り込みはここだけ。
 * - 失敗しても画面は前回のキャッシュで動く。エラーは sync_state に残す。
 */
@Service
public class MatchSyncService {

    private static final Logger log = LoggerFactory.getLogger(MatchSyncService.class);

    static final String JOB_SCHEDULE = "schedule";
    static final String JOB_RESULTS = "results";
    static final String JOB_GAMES = "games";

    private final PandaScoreClient client;
    private final MatchRepository matchRepo;
    private final TeamRepository teamRepo;
    private final SyncStateRepository syncRepo;
    private final OwcsProperties props;

    public MatchSyncService(PandaScoreClient client, MatchRepository matchRepo, TeamRepository teamRepo,
                            SyncStateRepository syncRepo, OwcsProperties props) {
        this.client = client;
        this.matchRepo = matchRepo;
        this.teamRepo = teamRepo;
        this.syncRepo = syncRepo;
        this.props = props;
    }

    // ---- ジョブ本体 -----------------------------------------------------

    /** 予定同期。まだ始まっていない試合を取り込む。 */
    @Transactional
    public void syncSchedule() {
        run(JOB_SCHEDULE, () -> {
            int n = 0;
            for (int teamId : props.teamIds()) {
                for (Ps.Match src : safe(client.upcoming(teamId, 25))) {
                    upsert(src);
                    n++;
                }
            }
            return n;
        });
    }

    /** 結果同期。進行中と直近の終了試合を取り込む。 */
    @Transactional
    public void syncResults() {
        run(JOB_RESULTS, () -> {
            int n = 0;
            for (int teamId : props.teamIds()) {
                for (Ps.Match src : safe(client.running(teamId, 5))) {
                    upsert(src);
                    n++;
                }
                for (Ps.Match src : safe(client.past(teamId, 15))) {
                    upsert(src);
                    n++;
                }
            }
            return n;
        });
    }

    /** マップ単位の結果が欠けている終了試合を、1 回あたり数件ずつ詳細取得で埋める。 */
    @Transactional
    public void syncGameDetails() {
        run(JOB_GAMES, () -> {
            List<Match> targets = matchRepo.findNeedingGameDetail(PageRequest.of(0, 5));
            for (Match m : targets) {
                Ps.Match src = client.match(m.getId());
                if (src != null) {
                    applyGames(m, src);
                    matchRepo.save(m);
                }
            }
            return targets.size();
        });
    }

    // ---- 取り込み -------------------------------------------------------

    void upsert(Ps.Match src) {
        if (src == null || src.id() == null) return;

        Match m = matchRepo.findById(src.id()).orElseGet(() -> new Match(src.id()));

        Integer teamA = null;
        Integer teamB = null;
        if (src.opponents() != null) {
            for (Ps.Opponent o : src.opponents()) {
                Ps.Team t = o.opponent();
                if (t == null || t.id() == null) continue;
                upsertTeam(t);
                if (teamA == null) teamA = t.id();
                else if (teamB == null) teamB = t.id();
            }
        }

        Map<Integer, Short> scores = new HashMap<>();
        if (src.results() != null) {
            for (Ps.Result r : src.results()) {
                if (r.teamId() != null && r.score() != null) {
                    scores.put(r.teamId(), r.score().shortValue());
                }
            }
        }

        m.updateHeader(
                src.name() != null ? src.name() : "TBD",
                src.status() != null ? src.status() : Match.NOT_STARTED,
                src.scheduledAt(), src.beginAt(), src.endAt(),
                src.leagueId(),
                src.serie() != null ? src.serie().id() : null,
                src.serie() != null ? firstNonBlank(src.serie().fullName(), src.serie().name()) : null,
                src.tournament() != null ? src.tournament().id() : null,
                src.tournament() != null ? src.tournament().name() : null,
                src.matchType(),
                src.numberOfGames() != null ? src.numberOfGames().shortValue() : null,
                src.winnerId(),
                src.modifiedAt());

        m.updateOpponents(teamA, teamB,
                teamA != null ? scores.get(teamA) : null,
                teamB != null ? scores.get(teamB) : null);

        applyStreams(m, src);
        applyGames(m, src);

        matchRepo.save(m);
    }

    private void upsertTeam(Ps.Team t) {
        Team team = teamRepo.findById(t.id()).orElseGet(() -> new Team(t.id()));
        team.update(t.name() != null ? t.name() : "TBD", t.acronym(), t.slug(), t.imageUrl());
        teamRepo.save(team);
    }

    private void applyStreams(Match m, Ps.Match src) {
        if (src.streamsList() == null) return;
        List<StreamLink> links = new ArrayList<>();
        int seq = 1;
        for (Ps.Stream s : src.streamsList()) {
            if (s.rawUrl() == null || s.rawUrl().isBlank()) continue;
            links.add(new StreamLink(seq++, s.rawUrl(), s.language(),
                    Boolean.TRUE.equals(s.main()), Boolean.TRUE.equals(s.official())));
        }
        m.replaceStreams(links);
    }

    /**
     * マップ結果を反映する。
     * 未開始試合の games は空箱なので取り込まない（取り込むと gamesSynced が誤って立つ）。
     */
    private void applyGames(Match m, Ps.Match src) {
        if (!Match.FINISHED.equals(src.status()) || src.games() == null) return;
        List<GameResult> games = new ArrayList<>();
        for (Ps.Game g : src.games()) {
            if (g.position() == null) continue;
            Integer winnerId = g.winner() != null ? g.winner().id() : null;
            if (winnerId == null) continue; // 未消化のマップは持たない
            games.add(new GameResult(g.position(), g.status() != null ? g.status() : "finished",
                    winnerId, g.length()));
        }
        if (!games.isEmpty()) {
            m.replaceGames(games);
        }
    }

    // ---- 補助 -----------------------------------------------------------

    private interface Job {
        int call();
    }

    private void run(String key, Job job) {
        if (!client.isConfigured()) {
            log.warn("PANDASCORE_TOKEN is not set; skipping sync (job={})", key);
            return;
        }
        SyncState state = syncRepo.findById(key).orElseGet(() -> new SyncState(key));
        try {
            int n = job.call();
            state.succeeded();
            log.info("sync ok: job={} count={}", key, n);
        } catch (RuntimeException e) {
            state.failed(e.getMessage());
            log.warn("sync FAILED: job={} : {}", key, e.toString());
        }
        syncRepo.save(state);
    }

    private static <T> List<T> safe(List<T> list) {
        return list != null ? list : List.of();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return b;
    }
}
