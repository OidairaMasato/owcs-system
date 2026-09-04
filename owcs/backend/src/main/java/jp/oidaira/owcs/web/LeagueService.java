package jp.oidaira.owcs.web;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import jp.oidaira.owcs.OwcsProperties;
import jp.oidaira.owcs.domain.GameResult;
import jp.oidaira.owcs.domain.Match;
import jp.oidaira.owcs.domain.StandingRow;
import jp.oidaira.owcs.domain.StreamLink;
import jp.oidaira.owcs.domain.SyncState;
import jp.oidaira.owcs.domain.Team;
import jp.oidaira.owcs.domain.Tournament;
import jp.oidaira.owcs.repo.MatchRepository;
import jp.oidaira.owcs.repo.SyncStateRepository;
import jp.oidaira.owcs.repo.TeamRepository;
import jp.oidaira.owcs.repo.TournamentRepository;
import jp.oidaira.owcs.web.LeagueDtos.GameRow;
import jp.oidaira.owcs.web.LeagueDtos.League;
import jp.oidaira.owcs.web.LeagueDtos.MatchRow;
import jp.oidaira.owcs.web.LeagueDtos.StandingRowView;
import jp.oidaira.owcs.web.LeagueDtos.StandingsView;
import jp.oidaira.owcs.web.LeagueDtos.TeamView;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeagueService {

    private final MatchRepository matchRepo;
    private final TeamRepository teamRepo;
    private final TournamentRepository tournamentRepo;
    private final SyncStateRepository syncRepo;
    private final OwcsProperties props;

    public LeagueService(MatchRepository matchRepo, TeamRepository teamRepo,
                         TournamentRepository tournamentRepo, SyncStateRepository syncRepo,
                         OwcsProperties props) {
        this.matchRepo = matchRepo;
        this.teamRepo = teamRepo;
        this.tournamentRepo = tournamentRepo;
        this.syncRepo = syncRepo;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public League build() {
        Integer serieId = currentSerieId();
        if (serieId == null) {
            return new League(null, List.of(), List.of(), null, lastSynced(),
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

        List<TeamView> teams = teamRepo.findAllById(teamIds).stream()
                .map(this::toView)
                .sorted((a, b) -> a.shortName().compareToIgnoreCase(b.shortName()))
                .toList();

        String serieName = matches.stream()
                .map(Match::getSerieName)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(standings != null ? standings.serieName() : null);

        String notice = matches.isEmpty() ? "このステージの日程はまだ発表されていません" : null;

        return new League(serieName, teams, matches.stream().map(this::toRow).toList(),
                standings, lastSynced(), OffsetDateTime.now(), notice);
    }

    /**
     * 表示するシリーズ。
     * これから試合があるならそのシリーズ、無ければ直近に試合があったシリーズ。
     * ステージが切り替わると自動で追随する。
     */
    private Integer currentSerieId() {
        String keyword = props.regionKeyword();
        List<Integer> upcoming = matchRepo.findUpcomingSerieIds(keyword, PageRequest.of(0, 1));
        if (!upcoming.isEmpty()) return upcoming.get(0);
        List<Integer> recent = matchRepo.findRecentSerieIds(keyword, PageRequest.of(0, 1));
        return recent.isEmpty() ? null : recent.get(0);
    }

    private StandingsView standingsFor(Integer serieId) {
        List<Tournament> found = tournamentRepo.findWithStandings(serieId);
        if (found.isEmpty()) {
            found = tournamentRepo.findLatestWithStandings(PageRequest.of(0, 1));
        }
        if (found.isEmpty()) return null;

        Tournament t = found.get(0);
        List<StandingRowView> rows = new ArrayList<>();
        for (StandingRow r : t.getStandings()) {
            rows.add(new StandingRowView(
                    toInt(r.getRankNo()), r.getTeamId(),
                    toInt(r.getWins()), toInt(r.getLosses()),
                    toInt(r.getGameWins()), toInt(r.getGameLosses())));
        }
        return new StandingsView(t.getId(), t.getSerieName(), t.getName(), rows);
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

    private TeamView toView(Team t) {
        return new TeamView(t.getId(), t.getName(), t.shortName(), t.getImageUrl());
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
