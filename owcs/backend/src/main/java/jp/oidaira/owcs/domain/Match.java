package jp.oidaira.owcs.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * PandaScore から取り込んだ 1 試合。
 * 画面は必ずこのテーブルを読む（リクエストのたびに PandaScore を叩かない）。
 */
@Entity
@Table(name = "matches")
public class Match {

    public static final String NOT_STARTED = "not_started";
    public static final String RUNNING = "running";
    public static final String FINISHED = "finished";

    /** PandaScore の match id をそのまま主キーにする。 */
    @Id
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String status;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "begin_at")
    private OffsetDateTime beginAt;

    @Column(name = "end_at")
    private OffsetDateTime endAt;

    @Column(name = "league_id")
    private Integer leagueId;

    @Column(name = "serie_id")
    private Integer serieId;

    @Column(name = "tournament_id")
    private Integer tournamentId;

    @Column(name = "serie_name")
    private String serieName;

    @Column(name = "tournament_name")
    private String tournamentName;

    @Column(name = "match_type")
    private String matchType;

    @Column(name = "number_of_games")
    private Short numberOfGames;

    @Column(name = "winner_id")
    private Integer winnerId;

    @Column(name = "team_a_id")
    private Integer teamAId;

    @Column(name = "team_b_id")
    private Integer teamBId;

    @Column(name = "score_a")
    private Short scoreA;

    @Column(name = "score_b")
    private Short scoreB;

    /** PandaScore 側の更新時刻。これが変わっていなければ再取り込みを省ける。 */
    @Column(name = "modified_at")
    private OffsetDateTime modifiedAt;

    @Column(name = "synced_at", nullable = false)
    private OffsetDateTime syncedAt = OffsetDateTime.now();

    @Column(name = "games_synced", nullable = false)
    private boolean gamesSynced = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "games", joinColumns = @JoinColumn(name = "match_id"))
    @OrderBy("gameNo asc")
    private List<GameResult> games = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "streams", joinColumns = @JoinColumn(name = "match_id"))
    @OrderBy("seq asc")
    private List<StreamLink> streams = new ArrayList<>();

    protected Match() {
    }

    public Match(Integer id) {
        this.id = id;
    }

    // ---- 更新 ----------------------------------------------------------

    public void updateHeader(String name, String status, OffsetDateTime scheduledAt,
                             OffsetDateTime beginAt, OffsetDateTime endAt, Integer leagueId,
                             Integer serieId, String serieName,
                             Integer tournamentId, String tournamentName, String matchType,
                             Short numberOfGames, Integer winnerId, OffsetDateTime modifiedAt) {
        this.name = name;
        this.status = status;
        this.scheduledAt = scheduledAt;
        this.beginAt = beginAt;
        this.endAt = endAt;
        this.leagueId = leagueId;
        this.serieId = serieId;
        this.serieName = serieName;
        this.tournamentId = tournamentId;
        this.tournamentName = tournamentName;
        this.matchType = matchType;
        this.numberOfGames = numberOfGames;
        this.winnerId = winnerId;
        this.modifiedAt = modifiedAt;
        this.syncedAt = OffsetDateTime.now();
    }

    public void updateOpponents(Integer teamAId, Integer teamBId, Short scoreA, Short scoreB) {
        this.teamAId = teamAId;
        this.teamBId = teamBId;
        this.scoreA = scoreA;
        this.scoreB = scoreB;
    }

    public void replaceGames(List<GameResult> next) {
        this.games.clear();
        this.games.addAll(next);
        this.gamesSynced = !next.isEmpty();
    }

    public void replaceStreams(List<StreamLink> next) {
        this.streams.clear();
        this.streams.addAll(next);
    }

    /** PandaScore 側が更新されているか。未取得なら常に true。 */
    public boolean isStaleAgainst(OffsetDateTime remoteModifiedAt) {
        if (this.modifiedAt == null || remoteModifiedAt == null) return true;
        return remoteModifiedAt.isAfter(this.modifiedAt);
    }

    // ---- 参照 ----------------------------------------------------------

    /** 指定チームから見た相手チーム id。 */
    public Optional<Integer> opponentOf(int teamId) {
        if (teamAId != null && teamAId == teamId) return Optional.ofNullable(teamBId);
        if (teamBId != null && teamBId == teamId) return Optional.ofNullable(teamAId);
        return Optional.empty();
    }

    /** 指定チームのスコア。 */
    public Short scoreOf(int teamId) {
        if (teamAId != null && teamAId == teamId) return scoreA;
        if (teamBId != null && teamBId == teamId) return scoreB;
        return null;
    }

    public Short scoreAgainst(int teamId) {
        if (teamAId != null && teamAId == teamId) return scoreB;
        if (teamBId != null && teamBId == teamId) return scoreA;
        return null;
    }

    /** 勝敗。未確定なら empty。 */
    public Optional<Boolean> wonBy(int teamId) {
        if (!FINISHED.equals(status) || winnerId == null) return Optional.empty();
        return Optional.of(winnerId == teamId);
    }

    /** 画面に出す配信リンク。日本語 > 公式 > メイン の順。 */
    public Optional<StreamLink> preferredStream() {
        return streams.stream().min(Comparator.comparingInt(StreamLink::priority));
    }

    /** 実際に使う開始時刻。begin_at が無ければ scheduled_at。 */
    public OffsetDateTime startsAt() {
        return beginAt != null ? beginAt : scheduledAt;
    }

    public Integer getId() { return id; }
    public String getName() { return name; }
    public String getStatus() { return status; }
    public OffsetDateTime getScheduledAt() { return scheduledAt; }
    public OffsetDateTime getBeginAt() { return beginAt; }
    public OffsetDateTime getEndAt() { return endAt; }
    public Integer getLeagueId() { return leagueId; }
    public Integer getSerieId() { return serieId; }
    public Integer getTournamentId() { return tournamentId; }
    public String getSerieName() { return serieName; }
    public String getTournamentName() { return tournamentName; }
    public String getMatchType() { return matchType; }
    public Short getNumberOfGames() { return numberOfGames; }
    public Integer getWinnerId() { return winnerId; }
    public Integer getTeamAId() { return teamAId; }
    public Integer getTeamBId() { return teamBId; }
    public Short getScoreA() { return scoreA; }
    public Short getScoreB() { return scoreB; }
    public boolean isGamesSynced() { return gamesSynced; }
    public List<GameResult> getGames() { return games; }
    public List<StreamLink> getStreams() { return streams; }
}
