package jp.oidaira.owcs.web;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 画面に返す形。
 *
 * Phase 2 では「リーグ」タブと「チーム」タブの両方を持つが、
 * API は 1 本にしてシリーズの全試合をまとめて返し、
 * 日付ごとの並びやチーム別の抽出はフロント側で行う。
 * 試合数は 1 ステージ 50 件程度なので、この方が往復も実装も少なくて済む。
 *
 * 時刻は UTC で返し、JST への変換はフロントで行う（KST = JST）。
 */
public final class LeagueDtos {

    private LeagueDtos() {
    }

    public record TeamView(int id, String name, String shortName, String imageUrl) {
    }

    public record GameRow(int gameNo, Integer winnerId, Integer lengthSec) {
    }

    public record MatchRow(
            int id,
            String name,
            String status,
            OffsetDateTime startsAt,
            String tournamentName,
            Integer teamAId,
            Integer teamBId,
            Integer scoreA,
            Integer scoreB,
            Integer winnerId,
            Integer bestOf,
            String streamUrl,
            List<GameRow> games) {
    }

    public record StandingRowView(
            Integer rankNo,
            int teamId,
            Integer wins,
            Integer losses,
            Integer gameWins,
            Integer gameLosses) {
    }

    public record StandingsView(
            int tournamentId,
            String serieName,
            String tournamentName,
            List<StandingRowView> rows) {
    }

    public record League(
            String serieName,
            List<TeamView> teams,
            List<MatchRow> matches,
            StandingsView standings,
            OffsetDateTime lastSyncedAt,
            OffsetDateTime serverTime,
            String notice) {
    }
}
