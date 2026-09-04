package jp.oidaira.owcs.pandascore;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * PandaScore のレスポンス DTO。必要なフィールドだけ定義する。
 * 未知のフィールドは Spring Boot の既定設定で無視される。
 */
public final class Ps {

    private Ps() {
    }

    public record Match(
            Integer id,
            String name,
            String status,
            @JsonProperty("scheduled_at") OffsetDateTime scheduledAt,
            @JsonProperty("begin_at") OffsetDateTime beginAt,
            @JsonProperty("end_at") OffsetDateTime endAt,
            @JsonProperty("league_id") Integer leagueId,
            @JsonProperty("match_type") String matchType,
            @JsonProperty("number_of_games") Integer numberOfGames,
            @JsonProperty("winner_id") Integer winnerId,
            @JsonProperty("modified_at") OffsetDateTime modifiedAt,
            Serie serie,
            Tournament tournament,
            List<Opponent> opponents,
            List<Result> results,
            List<Game> games,
            @JsonProperty("streams_list") List<Stream> streamsList) {
    }

    public record Serie(
            Integer id,
            @JsonProperty("full_name") String fullName,
            String name,
            Integer year,
            String slug,
            @JsonProperty("begin_at") OffsetDateTime beginAt,
            @JsonProperty("end_at") OffsetDateTime endAt) {

        /** 表示に使う名前。full_name があればそちら。 */
        public String label() {
            return fullName != null && !fullName.isBlank() ? fullName : name;
        }
    }

    public record Tournament(
            Integer id,
            String name,
            String slug,
            @JsonProperty("serie_id") Integer serieId,
            @JsonProperty("begin_at") OffsetDateTime beginAt,
            Serie serie) {
    }

    /** GET /tournaments/{id}/standings の 1 行。 */
    public record Standing(
            Integer rank,
            Integer wins,
            Integer losses,
            Integer total,
            @JsonProperty("game_wins") Integer gameWins,
            @JsonProperty("game_losses") Integer gameLosses,
            Team team) {
    }

    public record Opponent(String type, Team opponent) {
    }

    public record Team(
            Integer id,
            String name,
            String acronym,
            String slug,
            @JsonProperty("image_url") String imageUrl) {
    }

    public record Result(@JsonProperty("team_id") Integer teamId, Integer score) {
    }

    public record Game(
            Integer id,
            Integer position,
            String status,
            Integer length,
            Winner winner) {
    }

    public record Winner(Integer id, String type) {
    }

    public record Stream(
            Boolean main,
            String language,
            Boolean official,
            @JsonProperty("raw_url") String rawUrl,
            @JsonProperty("embed_url") String embedUrl) {
    }
}
