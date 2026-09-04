package jp.oidaira.owcs.web;

import java.time.OffsetDateTime;
import java.util.List;

/** 画面が必要とする形。時刻は UTC で返し、JST への変換はフロントで行う（KST = JST）。 */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record TeamView(int id, String name, String shortName, String imageUrl) {
    }

    public record GameView(int gameNo, boolean won, Integer lengthSec) {
    }

    public record MatchView(
            int id,
            String name,
            String status,
            OffsetDateTime startsAt,
            String serieName,
            String tournamentName,
            TeamView opponent,
            Integer scoreUs,
            Integer scoreThem,
            Boolean won,
            Integer bestOf,
            String streamUrl,
            List<GameView> games) {
    }

    public record StandingRowView(
            Integer rankNo,
            TeamView team,
            Integer wins,
            Integer losses,
            Integer gameWins,
            Integer gameLosses,
            boolean me) {
    }

    public record StandingsView(
            int tournamentId,
            String serieName,
            String tournamentName,
            List<StandingRowView> rows) {
    }

    public record Dashboard(
            TeamView team,
            MatchView live,
            MatchView next,
            List<MatchView> upcoming,
            List<MatchView> recent,
            StandingsView standings,
            OffsetDateTime lastSyncedAt,
            OffsetDateTime serverTime,
            String notice) {
    }
}
