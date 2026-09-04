package jp.oidaira.owcs.sync;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import jp.oidaira.owcs.OwcsProperties;
import jp.oidaira.owcs.domain.Match;
import jp.oidaira.owcs.domain.StandingRow;
import jp.oidaira.owcs.domain.SyncState;
import jp.oidaira.owcs.domain.Team;
import jp.oidaira.owcs.domain.Tournament;
import jp.oidaira.owcs.pandascore.PandaScoreClient;
import jp.oidaira.owcs.pandascore.Ps;
import jp.oidaira.owcs.repo.MatchRepository;
import jp.oidaira.owcs.repo.SyncStateRepository;
import jp.oidaira.owcs.repo.TeamRepository;
import jp.oidaira.owcs.repo.TournamentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 順位表の取り込み。
 *
 * 対象トーナメントは設定で固定せず、追っているチームの試合から毎回選び直す。
 * こうすると Stage が変わっても設定を触らずに順位表が切り替わる。
 *
 * ハマりどころ:
 * Midseason Championship の "Group A/B" のように、名前は総当たりでも
 * PandaScore に勝敗レコードが入っていないトーナメントがある。
 * 取得しただけで採用すると勝敗が全部空の表が出るので、
 * 「勝敗が 1 件でも入っているか」を検証し、駄目なら次の候補シリーズへ遡る。
 */
@Service
public class StandingsSyncService {

    private static final Logger log = LoggerFactory.getLogger(StandingsSyncService.class);
    static final String JOB = "standings";

    /** 探索の上限。無料枠に対しては十分小さいが、無駄打ちも避ける。 */
    private static final int MAX_SERIES = 4;
    private static final int MAX_STANDINGS_CALLS = 6;

    private final PandaScoreClient client;
    private final MatchRepository matchRepo;
    private final TournamentRepository tournamentRepo;
    private final TeamRepository teamRepo;
    private final SyncStateRepository syncRepo;
    private final OwcsProperties props;

    public StandingsSyncService(PandaScoreClient client, MatchRepository matchRepo,
                                TournamentRepository tournamentRepo, TeamRepository teamRepo,
                                SyncStateRepository syncRepo, OwcsProperties props) {
        this.client = client;
        this.matchRepo = matchRepo;
        this.tournamentRepo = tournamentRepo;
        this.teamRepo = teamRepo;
        this.syncRepo = syncRepo;
        this.props = props;
    }

    @Transactional
    public void sync() {
        if (!client.isConfigured()) {
            log.warn("PANDASCORE_TOKEN is not set; skipping sync (job={})", JOB);
            return;
        }
        SyncState state = syncRepo.findById(JOB).orElseGet(() -> new SyncState(JOB));
        try {
            int calls = 0;
            for (Integer serieId : candidateSerieIds()) {
                List<Tournament> tournaments = upsertTournaments(serieId);
                for (Tournament t : leagueTableCandidates(tournaments)) {
                    if (calls >= MAX_STANDINGS_CALLS) break;
                    calls++;

                    List<StandingRow> rows = fetchStandings(t.getId());
                    if (hasRecords(rows)) {
                        t.replaceStandings(rows);
                        tournamentRepo.save(t);
                        state.succeeded();
                        syncRepo.save(state);
                        log.info("sync ok: job={} tournament={} ({}) count={}",
                                JOB, t.getId(), t.getName(), rows.size());
                        return;
                    }
                    // 勝敗が入っていない表は画面に出さない。過去に保存していたら消す。
                    if (!t.getStandings().isEmpty()) {
                        t.replaceStandings(List.of());
                        tournamentRepo.save(t);
                    }
                    log.debug("standings without records: tournament={} ({})", t.getId(), t.getName());
                }
            }
            state.succeeded();
            log.info("sync ok: job={} count=0 (no usable standings)", JOB);
        } catch (RuntimeException e) {
            state.failed(e.getMessage());
            log.warn("sync FAILED: job={} : {}", JOB, e.toString());
        }
        syncRepo.save(state);
    }

    /** 追っているチームの試合が属するシリーズを、新しい順に。 */
    private List<Integer> candidateSerieIds() {
        int teamId = props.primaryTeamId();
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        matchRepo.findUpcoming(teamId, PageRequest.of(0, 5)).stream()
                .map(Match::getSerieId).filter(Objects::nonNull).forEach(ids::add);
        matchRepo.findRecentFinished(teamId, PageRequest.of(0, 30)).stream()
                .map(Match::getSerieId).filter(Objects::nonNull).forEach(ids::add);
        return ids.stream().limit(MAX_SERIES).toList();
    }

    /** 総当たり戦らしいものを優先し、無ければ全部を開始の新しい順で試す。 */
    private List<Tournament> leagueTableCandidates(List<Tournament> list) {
        Comparator<Tournament> byBegin = Comparator.comparing(
                Tournament::getBeginAt, Comparator.nullsLast(Comparator.reverseOrder()));
        List<Tournament> league = list.stream()
                .filter(Tournament::looksLikeLeagueTable).sorted(byBegin).toList();
        if (!league.isEmpty()) return league;
        return list.stream().sorted(byBegin).toList();
    }

    private List<StandingRow> fetchStandings(int tournamentId) {
        List<Ps.Standing> src = client.standings(tournamentId);
        List<StandingRow> rows = new ArrayList<>();
        if (src == null) return rows;
        for (Ps.Standing r : src) {
            if (r.team() == null || r.team().id() == null) continue;
            upsertTeam(r.team());
            rows.add(new StandingRow(r.team().id(), r.rank(), r.wins(), r.losses(),
                    r.gameWins(), r.gameLosses()));
        }
        return rows;
    }

    /** 勝敗が 1 件でも入っていれば、順位表として意味がある。 */
    private static boolean hasRecords(List<StandingRow> rows) {
        return rows.stream().anyMatch(r -> r.getWins() != null || r.getLosses() != null);
    }

    private List<Tournament> upsertTournaments(int serieId) {
        List<Ps.Tournament> src = client.tournaments(serieId);
        List<Tournament> out = new ArrayList<>();
        if (src == null) return out;
        for (Ps.Tournament s : src) {
            if (s.id() == null) continue;
            Tournament t = tournamentRepo.findById(s.id()).orElseGet(() -> new Tournament(s.id()));
            String serieName = s.serie() != null
                    ? (s.serie().fullName() != null ? s.serie().fullName() : s.serie().name())
                    : null;
            t.update(s.serieId() != null ? s.serieId() : serieId, serieName, s.name(), s.slug(), s.beginAt());
            out.add(tournamentRepo.save(t));
        }
        return out;
    }

    private void upsertTeam(Ps.Team t) {
        Team team = teamRepo.findById(t.id()).orElseGet(() -> new Team(t.id()));
        team.update(t.name() != null ? t.name() : "TBD", t.acronym(), t.slug(), t.imageUrl());
        teamRepo.save(team);
    }
}
