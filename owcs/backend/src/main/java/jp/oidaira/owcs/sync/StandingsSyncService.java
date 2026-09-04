package jp.oidaira.owcs.sync;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import jp.oidaira.owcs.domain.StandingRow;
import jp.oidaira.owcs.domain.SyncState;
import jp.oidaira.owcs.domain.Team;
import jp.oidaira.owcs.domain.Tournament;
import jp.oidaira.owcs.pandascore.PandaScoreClient;
import jp.oidaira.owcs.pandascore.Ps;
import jp.oidaira.owcs.repo.SyncStateRepository;
import jp.oidaira.owcs.repo.TeamRepository;
import jp.oidaira.owcs.repo.TournamentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 順位表の取り込み。
 *
 * 対象トーナメントは設定で固定せず、{@link SerieResolver} が返す
 * 「いま追っているシリーズ」から毎回選び直す。
 * こうすると Stage が変わっても設定を触らずに順位表が切り替わる。
 *
 * ハマりどころ:
 * Midseason Championship の "Group A/B" のように、名前は総当たりでも
 * PandaScore に勝敗レコードが入っていないトーナメントがある。
 * 取得しただけで採用すると勝敗が全部空の表が出るので、
 * 「勝敗が 1 件でも入っているか」を検証し、駄目なら次の候補へ移る。
 */
@Service
public class StandingsSyncService {

    private static final Logger log = LoggerFactory.getLogger(StandingsSyncService.class);
    static final String JOB = "standings";

    private static final int MAX_STANDINGS_CALLS = 6;

    private final PandaScoreClient client;
    private final SerieResolver series;
    private final TournamentRepository tournamentRepo;
    private final TeamRepository teamRepo;
    private final SyncStateRepository syncRepo;

    public StandingsSyncService(PandaScoreClient client, SerieResolver series,
                                TournamentRepository tournamentRepo, TeamRepository teamRepo,
                                SyncStateRepository syncRepo) {
        this.client = client;
        this.series = series;
        this.tournamentRepo = tournamentRepo;
        this.teamRepo = teamRepo;
        this.syncRepo = syncRepo;
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
            for (Integer serieId : series.targetSerieIds()) {
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
            String serieName = s.serie() != null ? s.serie().label() : null;
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
