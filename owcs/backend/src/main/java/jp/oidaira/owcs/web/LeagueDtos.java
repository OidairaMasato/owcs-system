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
            String serieName,
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

    /**
     * @param placementOnly 勝敗が無く、最終順位だけの表か。
     *                      ブラケット戦（Playoffs など）はこちらになる。
     */
    public record StandingsView(
            int tournamentId,
            String serieName,
            String tournamentName,
            boolean placementOnly,
            List<StandingRowView> rows) {
    }

    /** 大会セレクトの 1 項目。 */
    public record SerieRef(int id, String name) {
    }

    /** 「今日の試合」タブ。大会をまたぐので serieName を見せる。 */
    public record Today(
            List<TeamView> teams,
            List<MatchRow> matches,
            OffsetDateTime lastSyncedAt,
            OffsetDateTime serverTime) {
    }

    /** 対戦相手別の通算成績。 */
    public record HeadToHeadRow(
            TeamView opponent,
            int wins,
            int losses,
            int mapWins,
            int mapLosses,
            OffsetDateTime lastPlayedAt) {
    }

    public record HeadToHead(
            TeamView team,
            int wins,
            int losses,
            List<HeadToHeadRow> rows) {
    }

    /**
     * 通算ランキングの 1 行。
     *
     * @param rating      Elo レーティング。初期値 1500。
     * @param provisional 試合数が少なく、レーティングがまだ当てにならない
     * @param played      その年の試合数（レーティングは前年までを引き継ぐ）
     */
    public record RankingRow(
            TeamView team,
            int rating,
            boolean provisional,
            int played,
            int wins,
            int losses,
            int mapWins,
            int mapLosses) {
    }

    /**
     * 年間ランキング。強さの指標は Elo レーティング。
     *
     * 勝率で並べると、対戦相手の強さが無視されるため
     * 「弱い地域で勝ち続けたチーム」が上位に来てしまう。
     * Elo なら強い相手に勝つほど大きく上がるので、
     * 国際大会を経由して地域をまたいだ比較ができる。
     *
     * @param years 集計できる年の一覧（新しい順）。画面の切り替えに使う。
     */
    public record Rankings(int year, List<Integer> years, List<RankingRow> rows) {
    }

    public record League(
            Integer serieId,
            String serieName,
            List<SerieRef> series,
            List<TeamView> teams,
            List<MatchRow> matches,
            StandingsView standings,
            OffsetDateTime lastSyncedAt,
            OffsetDateTime serverTime,
            String notice) {
    }
}
