package jp.oidaira.owcs.web;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import jp.oidaira.owcs.domain.GameResult;
import jp.oidaira.owcs.domain.Match;
import jp.oidaira.owcs.domain.StandingRow;
import jp.oidaira.owcs.domain.StreamLink;
import jp.oidaira.owcs.domain.SyncState;
import jp.oidaira.owcs.domain.Team;
import jp.oidaira.owcs.domain.Tournament;
import jp.oidaira.owcs.repo.MatchRepository;
import jp.oidaira.owcs.repo.SyncStateRepository;
import jp.oidaira.owcs.repo.TeamLogoRepository;
import jp.oidaira.owcs.repo.TeamRepository;
import jp.oidaira.owcs.repo.TournamentRepository;
import jp.oidaira.owcs.web.LeagueDtos.GameRow;
import jp.oidaira.owcs.web.LeagueDtos.HeadToHead;
import jp.oidaira.owcs.web.LeagueDtos.HeadToHeadRow;
import jp.oidaira.owcs.web.LeagueDtos.League;
import jp.oidaira.owcs.web.LeagueDtos.MatchRow;
import jp.oidaira.owcs.web.LeagueDtos.RankingRow;
import jp.oidaira.owcs.web.LeagueDtos.Rankings;
import jp.oidaira.owcs.web.LeagueDtos.StandingRowView;
import jp.oidaira.owcs.web.LeagueDtos.SerieRef;
import jp.oidaira.owcs.web.LeagueDtos.StandingsView;
import jp.oidaira.owcs.web.LeagueDtos.TeamView;
import jp.oidaira.owcs.web.LeagueDtos.Today;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeagueService {

    private final MatchRepository matchRepo;
    private final TeamRepository teamRepo;
    private final TournamentRepository tournamentRepo;
    private final SyncStateRepository syncRepo;
    private final TeamLogoRepository logoRepo;

    public LeagueService(MatchRepository matchRepo, TeamRepository teamRepo,
                         TournamentRepository tournamentRepo, SyncStateRepository syncRepo,
                         TeamLogoRepository logoRepo) {
        this.matchRepo = matchRepo;
        this.teamRepo = teamRepo;
        this.tournamentRepo = tournamentRepo;
        this.syncRepo = syncRepo;
        this.logoRepo = logoRepo;
    }

    /**
     * @param requestedSerieId 画面で選ばれた大会。null なら既定（開催中・直近）を選ぶ。
     */
    @Transactional(readOnly = true)
    public League build(Integer requestedSerieId) {
        List<SerieRef> series = listSeries();

        Integer serieId = requestedSerieId != null
                && series.stream().anyMatch(r -> r.id() == requestedSerieId.intValue())
                ? requestedSerieId
                : defaultSerieId();

        if (serieId == null) {
            return new League(null, null, series, List.of(), List.of(), null, lastSynced(),
                    OffsetDateTime.now(), "まだ試合データがありません");
        }

        List<Match> matches = matchRepo.findBySerie(serieId);
        StandingsView standings = standingsFor(serieId);

        // 画面に出てくるチームを集める（試合の対戦相手 + 順位表）
        Set<Integer> teamIds = new LinkedHashSet<>();
        for (Match m : matches) {
            if (m.getTeamAId() != null) teamIds.add(m.getTeamAId());
            if (m.getTeamBId() != null) teamIds.add(m.getTeamBId());
        }
        if (standings != null) {
            standings.rows().forEach(r -> teamIds.add(r.teamId()));
        }

        Set<Integer> withLogo = new java.util.HashSet<>(logoRepo.findAllTeamIds());
        List<TeamView> teams = teamRepo.findAllById(teamIds).stream()
                .map(t -> toView(t, withLogo))
                .sorted((a, b) -> a.shortName().compareToIgnoreCase(b.shortName()))
                .toList();

        String serieName = matches.stream()
                .map(Match::getSerieName)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(standings != null ? standings.serieName() : null);

        String notice = matches.isEmpty() ? "この大会の日程はまだ発表されていません" : null;

        return new League(serieId, serieName, series, teams,
                matches.stream().map(this::toRow).toList(),
                standings, lastSynced(), OffsetDateTime.now(), notice);
    }

    /**
     * 今日前後の全試合。大会を選ばずに「今日 OWCS で何があるか」を見るための入口。
     * 昨日の朝から明後日の朝までを JST で切る。
     */
    @Transactional(readOnly = true)
    public Today today() {
        ZoneId jst = ZoneId.of("Asia/Tokyo");
        ZonedDateTime startOfToday = ZonedDateTime.now(jst).truncatedTo(ChronoUnit.DAYS);
        OffsetDateTime from = startOfToday.minusDays(1).toOffsetDateTime();
        OffsetDateTime to = startOfToday.plusDays(2).toOffsetDateTime();

        List<Match> matches = matchRepo.findBetween(from, to);

        Set<Integer> teamIds = new LinkedHashSet<>();
        for (Match m : matches) {
            if (m.getTeamAId() != null) teamIds.add(m.getTeamAId());
            if (m.getTeamBId() != null) teamIds.add(m.getTeamBId());
        }
        Set<Integer> withLogo = new java.util.HashSet<>(logoRepo.findAllTeamIds());
        List<TeamView> teams = teamRepo.findAllById(teamIds).stream()
                .map(t -> toView(t, withLogo))
                .toList();

        return new Today(teams, matches.stream().map(this::toRow).toList(),
                lastSynced(), OffsetDateTime.now());
    }

    /**
     * 対戦相手別の通算成績。大会をまたいで集計する。
     * 「ZETA は CR に通算何勝何敗か」という、1 大会だけでは分からない見方を出す。
     */
    @Transactional(readOnly = true)
    public HeadToHead headToHead(int teamId) {
        List<Match> matches = matchRepo.findFinishedForTeam(teamId);

        Map<Integer, int[]> tally = new LinkedHashMap<>();   // [勝, 敗, マップ勝, マップ敗]
        Map<Integer, OffsetDateTime> lastPlayed = new HashMap<>();
        int wins = 0;
        int losses = 0;

        for (Match m : matches) {
            Integer opponentId = m.opponentOf(teamId).orElse(null);
            if (opponentId == null || m.getWinnerId() == null) continue;

            boolean won = m.getWinnerId() == teamId;
            if (won) wins++; else losses++;

            int[] t = tally.computeIfAbsent(opponentId, k -> new int[4]);
            if (won) t[0]++; else t[1]++;

            Short us = m.scoreOf(teamId);
            Short them = m.scoreAgainst(teamId);
            if (us != null) t[2] += us;
            if (them != null) t[3] += them;

            OffsetDateTime at = m.startsAt();
            if (at != null) {
                OffsetDateTime cur = lastPlayed.get(opponentId);
                if (cur == null || at.isAfter(cur)) lastPlayed.put(opponentId, at);
            }
        }

        Set<Integer> withLogo = new java.util.HashSet<>(logoRepo.findAllTeamIds());
        Map<Integer, Team> teamById = new HashMap<>();
        teamRepo.findAllById(tally.keySet()).forEach(t -> teamById.put(t.getId(), t));

        List<HeadToHeadRow> rows = new ArrayList<>();
        for (Map.Entry<Integer, int[]> e : tally.entrySet()) {
            Team opp = teamById.get(e.getKey());
            if (opp == null) continue;
            int[] t = e.getValue();
            rows.add(new HeadToHeadRow(toView(opp, withLogo), t[0], t[1], t[2], t[3],
                    lastPlayed.get(e.getKey())));
        }
        // 対戦数の多い順、次に直近に当たった順
        rows.sort(Comparator
                .comparingInt((HeadToHeadRow r) -> -(r.wins() + r.losses()))
                .thenComparing(HeadToHeadRow::lastPlayedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())));

        Team me = teamRepo.findById(teamId).orElse(null);
        TeamView meView = me != null ? toView(me, withLogo)
                : new TeamView(teamId, "TBD", "TBD", null);

        return new HeadToHead(meView, wins, losses, rows);
    }

    /** Elo の初期値。全チームここから始める。 */
    private static final double ELO_START = 1500;

    /** Elo の変動幅。1 試合でどれだけ動かすか。 */
    private static final double ELO_K = 32;

    /** これ未満の試合数だと、その年のレーティングはまだ当てにならない。 */
    private static final int PROVISIONAL_MATCHES = 5;

    /**
     * 年間ランキング。強さの指標は Elo レーティング。
     *
     * 勝率で並べると対戦相手の強さが無視されるので、
     * 弱い地域で勝ち続けたチームが上位に来てしまい「強さ順」にならない。
     * Elo は強い相手に勝つほど大きく上がるので、
     * 国際大会を経由して地域をまたいだ比較ができる。
     *
     * レーティングは年で区切らず、取り込み済みの全試合を古い順に通して計算し、
     * 対象年の末時点（当年なら現在）の値を出す。前年の実績を捨てないため。
     * 表に出す勝敗はその年のぶんだけを数える。
     *
     * @param year null なら、試合があった最も新しい年
     */
    @Transactional(readOnly = true)
    public Rankings rankings(Integer year) {
        ZoneId jst = ZoneId.of("Asia/Tokyo");
        List<Match> all = matchRepo.findAllFinished();   // 開始時刻の昇順

        Set<Integer> yearSet = new java.util.TreeSet<>(Comparator.reverseOrder());
        for (Match m : all) {
            Integer y = yearOf(m, jst);
            if (y != null) yearSet.add(y);
        }
        List<Integer> years = new ArrayList<>(yearSet);
        if (years.isEmpty()) return new Rankings(0, years, List.of());
        int target = year != null ? year : years.get(0);

        Map<Integer, Double> rating = new HashMap<>();
        Map<Integer, int[]> tally = new HashMap<>();   // その年だけ [勝, 敗, マップ勝, マップ敗]

        for (Match m : all) {
            Integer y = yearOf(m, jst);
            // 対象年より後の試合は「その年末時点の強さ」に含めない
            if (y == null || y > target) continue;

            Integer a = m.getTeamAId();
            Integer b = m.getTeamBId();
            Integer winner = m.getWinnerId();
            if (a == null || b == null || winner == null) continue;
            if (a.intValue() == b.intValue()) continue;

            double ra = rating.getOrDefault(a, ELO_START);
            double rb = rating.getOrDefault(b, ELO_START);
            // a から見た期待勝率。差が 400 なら約 90% になる
            double expectedA = 1.0 / (1.0 + Math.pow(10, (rb - ra) / 400.0));
            double actualA = winner.intValue() == a.intValue() ? 1.0 : 0.0;

            rating.put(a, ra + ELO_K * (actualA - expectedA));
            rating.put(b, rb + ELO_K * ((1.0 - actualA) - (1.0 - expectedA)));

            if (y == target) {
                accumulate(tally, m, a);
                accumulate(tally, m, b);
            }
        }

        Set<Integer> withLogo = new java.util.HashSet<>(logoRepo.findAllTeamIds());
        Map<Integer, Team> teamById = new HashMap<>();
        teamRepo.findAllById(tally.keySet()).forEach(t -> teamById.put(t.getId(), t));

        List<RankingRow> rows = new ArrayList<>();
        for (Map.Entry<Integer, int[]> e : tally.entrySet()) {
            Team t = teamById.get(e.getKey());
            if (t == null) continue;
            int[] v = e.getValue();
            int played = v[0] + v[1];
            rows.add(new RankingRow(
                    toView(t, withLogo),
                    (int) Math.round(rating.getOrDefault(e.getKey(), ELO_START)),
                    played < PROVISIONAL_MATCHES,
                    played, v[0], v[1], v[2], v[3]));
        }
        // レーティングの高い順。同値なら試合数の多い方（標本が多い方）を上に
        rows.sort(Comparator
                .comparingInt((RankingRow r) -> -r.rating())
                .thenComparingInt(r -> -r.played()));

        return new Rankings(target, years, rows);
    }

    /** その試合が「何年の試合か」。日付は JST で判定する。 */
    private static Integer yearOf(Match m, ZoneId jst) {
        OffsetDateTime at = m.startsAt();
        return at == null ? null : at.atZoneSameInstant(jst).getYear();
    }

    /** 1 試合ぶんを片方のチームの集計に足し込む。 */
    private static void accumulate(Map<Integer, int[]> tally, Match m, int teamId) {
        int[] v = tally.computeIfAbsent(teamId, k -> new int[4]);
        if (m.getWinnerId() != null && m.getWinnerId() == teamId) v[0]++; else v[1]++;
        Short us = m.scoreOf(teamId);
        Short them = m.scoreAgainst(teamId);
        if (us != null) v[2] += us;
        if (them != null) v[3] += them;
    }

    /** 取り込み済みの大会一覧。新しい順。 */
    private List<SerieRef> listSeries() {
        List<SerieRef> out = new ArrayList<>();
        for (Object[] row : matchRepo.listSeries()) {
            Integer id = (Integer) row[0];
            String name = (String) row[1];
            if (id == null) continue;
            out.add(new SerieRef(id, name != null ? name : "大会 " + id));
        }
        return out;
    }

    /**
     * 既定で表示する大会。
     * これから試合があるならその大会、無ければ直近に試合があった大会。
     * ステージが切り替わると自動で追随する。
     */
    private Integer defaultSerieId() {
        List<Integer> upcoming = matchRepo.findUpcomingSerieIds(PageRequest.of(0, 1));
        if (!upcoming.isEmpty()) return upcoming.get(0);
        List<Integer> recent = matchRepo.findRecentSerieIds(PageRequest.of(0, 1));
        return recent.isEmpty() ? null : recent.get(0);
    }

    /**
     * 選んだ大会の順位表。
     *
     * 他の大会の順位表で代替してはいけない。
     * 大会を切り替えられるようにしたことで、
     * 「Japan Stage を見ているのに Korea の順位表が出る」という混入が起きた。
     * その大会に順位表が無ければ、何も出さないのが正しい。
     */
    private StandingsView standingsFor(Integer serieId) {
        List<Tournament> found = tournamentRepo.findWithStandings(serieId);
        if (found.isEmpty()) return null;

        Tournament t = found.get(0);
        List<StandingRowView> rows = new ArrayList<>();
        for (StandingRow r : t.getStandings()) {
            rows.add(new StandingRowView(
                    toInt(r.getRankNo()), r.getTeamId(),
                    toInt(r.getWins()), toInt(r.getLosses()),
                    toInt(r.getGameWins()), toInt(r.getGameLosses())));
        }
        boolean placementOnly = rows.stream()
                .allMatch(r -> r.wins() == null && r.losses() == null);
        return new StandingsView(t.getId(), t.getSerieName(), t.getName(), placementOnly, rows);
    }

    private MatchRow toRow(Match m) {
        List<GameRow> games = new ArrayList<>();
        for (GameResult g : m.getGames()) {
            games.add(new GameRow(g.getGameNo(), g.getWinnerId(), g.getLengthSec()));
        }
        return new MatchRow(
                m.getId(),
                m.getName(),
                m.getStatus(),
                m.startsAt(),
                m.getSerieName(),
                m.getTournamentName(),
                m.getTeamAId(),
                m.getTeamBId(),
                toInt(m.getScoreA()),
                toInt(m.getScoreB()),
                m.getWinnerId(),
                toInt(m.getNumberOfGames()),
                m.preferredStream().map(StreamLink::getRawUrl).orElse(null),
                games);
    }

    /**
     * 画像はこちらで保持しているものだけを指す。
     * PandaScore の URL をそのまま返すと画面から直リンクすることになり、規約に触れる。
     */
    private TeamView toView(Team t, Set<Integer> withLogo) {
        String logo = withLogo.contains(t.getId()) ? "/api/logo/" + t.getId() : null;
        return new TeamView(t.getId(), t.getName(), t.shortName(), logo);
    }

    private OffsetDateTime lastSynced() {
        return syncRepo.findAll().stream()
                .map(SyncState::getLastSuccessAt)
                .filter(Objects::nonNull)
                .max(OffsetDateTime::compareTo)
                .orElse(null);
    }

    private static Integer toInt(Short v) {
        return v == null ? null : v.intValue();
    }
}
