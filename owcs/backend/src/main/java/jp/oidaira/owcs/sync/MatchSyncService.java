package jp.oidaira.owcs.sync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * - シリーズ単位で引く。チーム単位だと 10 チームで 10 倍のリクエストになるが、
 *   シリーズ単位なら 1 リクエストで全チーム分（50 件程度）が揃う。
 * - 失敗しても画面は前回のキャッシュで動く。エラーは sync_state に残す。
 */
@Service
public class MatchSyncService {

    private static final Logger log = LoggerFactory.getLogger(MatchSyncService.class);

    static final String JOB_MATCHES = "matches";
    static final String JOB_GAMES = "games";

    /**
     * 大会ごとの「一括取得済み」印。sync_state に serie:<id> という行を残す。
     *
     * 件数（0 件かどうか）で判断してはいけない。
     * チーム軸で取り込んでいた頃の部分的なデータが残っていると、
     * 「もう取り込み済み」と誤判定して大会の大半を取りこぼす。
     * 実際 Midseason Championship が 5 試合しか入らない事故が起きた。
     */
    private static String serieKey(int serieId) {
        return SERIE_KEY_PREFIX + serieId;
    }

    /** 版を上げると全大会の試合が取り直される。 */
    private static final String SERIE_KEY_PREFIX = "serie:v2:";

    /** 1 シリーズあたりの取得上限。Korea Stage は 50 件強なので十分。 */
    private static final int PER_PAGE = 100;

    private final PandaScoreClient client;
    private final SerieResolver series;
    private final MatchRepository matchRepo;
    private final TeamRepository teamRepo;
    private final SyncStateRepository syncRepo;

    public MatchSyncService(PandaScoreClient client, SerieResolver series, MatchRepository matchRepo,
                            TeamRepository teamRepo, SyncStateRepository syncRepo) {
        this.client = client;
        this.series = series;
        this.matchRepo = matchRepo;
        this.teamRepo = teamRepo;
        this.syncRepo = syncRepo;
    }

    // ---- ジョブ本体 -----------------------------------------------------

    /**
     * 対象シリーズの全試合を取り込む。予定も結果もこれ 1 本で入る。
     *
     * 毎回引くのは開催中・直近の大会だけ。
     * 終わった大会は結果が変わらないので、一括取得の記録が無いとき（初回）だけ引く。
     * これで対象が 12 大会あってもリクエストは数本で済む。
     */
    @Transactional
    public void syncMatches() {
        run(JOB_MATCHES, () -> {
            List<SerieResolver.SerieInfo> hot = series.hotSeries();
            int n = 0;
            for (SerieResolver.SerieInfo s : series.targetSeries()) {
                boolean isHot = hot.contains(s);
                if (!isHot && isFetched(s.id())) continue;

                List<Ps.Match> src = client.matchesInSerie(s.id(), PER_PAGE);
                if (src == null) continue;
                for (Ps.Match m : src) {
                    upsert(m);
                    n++;
                }
                markFetched(s.id());
            }
            return n;
        });
    }

    private boolean isFetched(int serieId) {
        return syncRepo.findById(serieKey(serieId))
                .map(st -> st.getLastSuccessAt() != null)
                .orElse(false);
    }

    private void markFetched(int serieId) {
        SyncState st = syncRepo.findById(serieKey(serieId))
                .orElseGet(() -> new SyncState(serieKey(serieId)));
        st.succeeded();
        syncRepo.save(st);
    }

    /**
     * マップ単位の結果が欠けている終了試合を、1 回あたり数件ずつ詳細取得で埋める。
     * 一覧レスポンスに games が含まれていれば syncMatches で埋まるので、
     * ここに残るのは取りこぼしだけ。
     */
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
                src.serie() != null ? src.serie().label() : null,
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
}
