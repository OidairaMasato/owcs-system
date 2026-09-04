package jp.oidaira.owcs.web;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import jp.oidaira.owcs.OwcsProperties;
import jp.oidaira.owcs.domain.GameResult;
import jp.oidaira.owcs.domain.Match;
import jp.oidaira.owcs.domain.StreamLink;
import jp.oidaira.owcs.domain.SyncState;
import jp.oidaira.owcs.domain.StandingRow;
import jp.oidaira.owcs.domain.Team;
import jp.oidaira.owcs.domain.Tournament;
import jp.oidaira.owcs.repo.MatchRepository;
import jp.oidaira.owcs.repo.SyncStateRepository;
import jp.oidaira.owcs.repo.TeamRepository;
import jp.oidaira.owcs.repo.TournamentRepository;
import jp.oidaira.owcs.web.DashboardDtos.Dashboard;
import jp.oidaira.owcs.web.DashboardDtos.GameView;
import jp.oidaira.owcs.web.DashboardDtos.MatchView;
import jp.oidaira.owcs.web.DashboardDtos.StandingRowView;
import jp.oidaira.owcs.web.DashboardDtos.StandingsView;
import jp.oidaira.owcs.web.DashboardDtos.TeamView;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final MatchRepository matchRepo;
    private final TeamRepository teamRepo;
    private final SyncStateRepository syncRepo;
    private final TournamentRepository tournamentRepo;
    private final OwcsProperties props;

    public DashboardService(MatchRepository matchRepo, TeamRepository teamRepo,
                            SyncStateRepository syncRepo, TournamentRepository tournamentRepo,
                            OwcsProperties props) {
        this.matchRepo = matchRepo;
        this.teamRepo = teamRepo;
        this.syncRepo = syncRepo;
        this.tournamentRepo = tournamentRepo;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public Dashboard build() {
        int teamId = props.primaryTeamId();

        List<Match> running = matchRepo.findRunning(teamId);
        List<Match> upcoming = matchRepo.findUpcoming(teamId, PageRequest.of(0, 10));
        List<Match> recent = matchRepo.findRecentFinished(teamId, PageRequest.of(0, 5));

        MatchView live = running.isEmpty() ? null : toView(running.get(0), teamId);
        MatchView next = upcoming.isEmpty() ? null : toView(upcoming.get(0), teamId);

        List<MatchView> rest = new ArrayList<>();
        for (int i = 1; i < upcoming.size(); i++) {
            rest.add(toView(upcoming.get(i), teamId));
        }

        List<MatchView> recentViews = recent.stream().map(m -> toView(m, teamId)).toList();

        OffsetDateTime lastSynced = syncRepo.findAll().stream()
                .map(SyncState::getLastSuccessAt)
                .filter(java.util.Objects::nonNull)
                .max(OffsetDateTime::compareTo)
                .orElse(null);

        String notice = null;
        if (live == null && next == null) {
            notice = "日程が発表されると自動で表示されます";
        }

        return new Dashboard(teamView(teamId), live, next, rest, recentViews,
                standingsView(teamId), lastSynced, OffsetDateTime.now(), notice);
    }

    private MatchView toView(Match m, int teamId) {
        Integer opponentId = m.opponentOf(teamId).orElse(null);
        TeamView opponent = opponentId != null ? teamView(opponentId) : null;

        Short us = m.scoreOf(teamId);
        Short them = m.scoreAgainst(teamId);

        List<GameView> games = new ArrayList<>();
        for (GameResult g : m.getGames()) {
            games.add(new GameView(g.getGameNo(),
                    g.getWinnerId() != null && g.getWinnerId() == teamId,
                    g.getLengthSec()));
        }

        String streamUrl = m.preferredStream().map(StreamLink::getRawUrl).orElse(null);

        return new MatchView(
                m.getId(),
                m.getName(),
                m.getStatus(),
                m.startsAt(),
                m.getSerieName(),
                m.getTournamentName(),
                opponent,
                us != null ? us.intValue() : null,
                them != null ? them.intValue() : null,
                m.wonBy(teamId).orElse(null),
                m.getNumberOfGames() != null ? m.getNumberOfGames().intValue() : null,
                streamUrl,
                games);
    }

    /** 画面に出す順位表。いま参加しているシリーズの総当たり戦 1 件だけ。 */
    private StandingsView standingsView(int teamId) {
        List<Tournament> found = tournamentRepo.findLatestWithStandings(PageRequest.of(0, 1));
        if (found.isEmpty()) return null;

        Tournament t = found.get(0);
        List<StandingRowView> rows = new ArrayList<>();
        for (StandingRow r : t.getStandings()) {
            rows.add(new StandingRowView(
                    r.getRankNo() != null ? r.getRankNo().intValue() : null,
                    teamView(r.getTeamId()),
                    r.getWins() != null ? r.getWins().intValue() : null,
                    r.getLosses() != null ? r.getLosses().intValue() : null,
                    r.getGameWins() != null ? r.getGameWins().intValue() : null,
                    r.getGameLosses() != null ? r.getGameLosses().intValue() : null,
                    r.getTeamId() == teamId));
        }
        return new StandingsView(t.getId(), t.getSerieName(), t.getName(), rows);
    }

    private TeamView teamView(int id) {
        Optional<Team> t = teamRepo.findById(id);
        return t.map(x -> new TeamView(x.getId(), x.getName(), x.shortName(), x.getImageUrl()))
                .orElseGet(() -> new TeamView(id, "TBD", "TBD", null));
    }
}
