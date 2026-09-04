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
 * 大会 1 つにつき順位表は 1 つだけ採用する。選び方は 2 段階。
 *
 * 1. 勝敗が入っている表があればそれ（総当たり戦。Group Stage / Regular season）
 * 2. 無ければ、順位だけの表のうち一番遅く始まったトーナメント
 *    （ブラケット戦の最終結果。Midseason Championship の Playoffs など）
 *
 * ハマりどころ:
 * - "Group A/B" のように名前は総当たりでも勝敗レコードが無い大会がある。
 *   名前だけで決めず、中身に勝敗が入っているかを見る。
 * - 名前が総当たりらしい候補だけに絞ると Playoffs が候補から消え、
 *   国際大会で最終結果を出せなくなる。総当たりらしいものを優先しつつ、残りも候補に残す。
 *
 * 再評価の仕組み:
 * 取得済み判定は sync_state の {@link #STANDINGS_KEY_PREFIX} で行う。
 * 選び方のロジックを変えたらこの接頭辞の版を上げる。
 * 全大会の判定が消えて、次の同期で選び直される。
 */
@Service
public class StandingsSyncService {

    private static final Logger log = LoggerFactory.getLogger(StandingsSyncService.class);
    static final String JOB = "standings";

    /** 版を上げると全大会の順位表が選び直される。 */
    private static final String STANDINGS_KEY_PREFIX = "standings:v2:";

    /** 1 回の同期で叩く上限。初回は数回に分かれて埋まる。 */
    private static final int MAX_STANDINGS_CALLS = 20;

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
            List<SerieResolver.SerieInfo> hot = series.hotSeries();
            int calls = 0;
            int found = 0;

            for (SerieResolver.SerieInfo s : series.targetSeries()) {
                if (calls >= MAX_STANDINGS_CALLS) break;
                // 開催中でなく、すでに評価済みの大会はやり直さない
                if (!hot.contains(s) && isEvaluated(s.id())) continue;

                List<Tournament> tournaments = upsertTournaments(s.id());

                Tournament adopted = null;
                List<StandingRow> adoptedRows = null;
                Tournament placementT = null;
                List<StandingRow> placementRows = null;

                for (Tournament t : candidates(tournaments)) {
                    if (calls >= MAX_STANDINGS_CALLS) break;
                    calls++;

                    List<StandingRow> rows = fetchStandings(t.getId());
                    if (rows.isEmpty()) continue;

                    if (hasRecords(rows)) {
                        adopted = t;
                        adoptedRows = rows;
                        break; // 勝敗入りが最優先。これ以上探さない
                    }
                    // 順位だけの表は、一番遅く始まったもの（＝最終結果）を控える
                    if (placementT == null || isLater(t, placementT)) {
                        placementT = t;
                        placementRows = rows;
                    }
                }

                if (adopted == null && placementT != null) {
                    adopted = placementT;
                    adoptedRows = placementRows;
                }

                if (adopted != null) {
                    saveOnly(tournaments, adopted, adoptedRows);
                    found++;
                    log.info("standings adopted: serie={} tournament={} ({}) rows={}",
                            s.name(), adopted.getId(), adopted.getName(), adoptedRows.size());
                }
                markEvaluated(s.id());
            }

            state.succeeded();
            log.info("sync ok: job={} standings={} calls={}", JOB, found, calls);
        } catch (RuntimeException e) {
            state.failed(e.getMessage());
            log.warn("sync FAILED: job={} : {}", JOB, e.toString());
        }
        syncRepo.save(state);
    }

    /**
     * 採用したトーナメントにだけ順位表を残し、同じ大会の他は消す。
     * 選び直しで別のトーナメントが採用されたとき、古い表が残らないようにする。
     */
    private void saveOnly(List<Tournament> all, Tournament adopted, List<StandingRow> rows) {
        for (Tournament t : all) {
            if (t.getId().equals(adopted.getId())) {
                t.replaceStandings(rows);
                tournamentRepo.save(t);
            } else if (!t.getStandings().isEmpty()) {
                t.replaceStandings(List.of());
                tournamentRepo.save(t);
            }
        }
    }

    /** 総当たりらしい名前を優先しつつ、残りも候補に残す。 */
    private List<Tournament> candidates(List<Tournament> list) {
        Comparator<Tournament> byBegin = Comparator.comparing(
                Tournament::getBeginAt, Comparator.nullsLast(Comparator.reverseOrder()));

        List<Tournament> out = new ArrayList<>(
                list.stream().filter(Tournament::looksLikeLeagueTable).sorted(byBegin).toList());
        for (Tournament t : list.stream().sorted(byBegin).toList()) {
            if (out.stream().noneMatch(x -> x.getId().equals(t.getId()))) out.add(t);
        }
        return out;
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

    /** 勝敗が 1 件でも入っていれば、総当たりの順位表として扱える。 */
    private static boolean hasRecords(List<StandingRow> rows) {
        return rows.stream().anyMatch(r -> r.getWins() != null || r.getLosses() != null);
    }

    /** a の方が後に始まったか。最終結果を選ぶのに使う。 */
    private static boolean isLater(Tournament a, Tournament b) {
        if (a.getBeginAt() == null) return false;
        if (b.getBeginAt() == null) return true;
        return a.getBeginAt().isAfter(b.getBeginAt());
    }

    private boolean isEvaluated(int serieId) {
        return syncRepo.findById(STANDINGS_KEY_PREFIX + serieId)
                .map(st -> st.getLastSuccessAt() != null)
                .orElse(false);
    }

    private void markEvaluated(int serieId) {
        String key = STANDINGS_KEY_PREFIX + serieId;
        SyncState st = syncRepo.findById(key).orElseGet(() -> new SyncState(key));
        st.succeeded();
        syncRepo.save(st);
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
