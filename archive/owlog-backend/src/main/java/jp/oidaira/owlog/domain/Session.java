package jp.oidaira.owlog.domain;

import jakarta.persistence.*;
import jp.oidaira.owlog.service.RankValue;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 1回のプレイセッション。1日に何回あってもよい（朝と夜は別セッション）。
 * ランクは開始値だけを持ち、終了値は各試合の RP 増減を積み上げた結果。
 */
@Entity
@Table(name = "sessions")
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "season_id")
    private Long seasonId;

    @Column(name = "played_on", nullable = false)
    private LocalDate playedOn;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt = OffsetDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "role_type", nullable = false, length = 16)
    private RoleType roleType;

    @Column(name = "start_rank_value", nullable = false)
    private int startRankValue;

    /** 終了時のランク。addMatch() が更新する。手で設定しないこと。 */
    @Column(name = "rank_value", nullable = false)
    private int rankValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_level", length = 16)
    private ConditionLevel conditionLevel;

    @Column
    private String memo;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq asc")
    private List<SessionMatch> matches = new ArrayList<>();

    /** 開始ランクを決める。試合を積む前に一度だけ呼ぶ。 */
    public void startAt(int startRankValue) {
        this.startRankValue = RankValue.clamp(startRankValue);
        this.rankValue = this.startRankValue;
    }

    /** 1試合積む。ランクは rank_value への加算だけで繰り上がり・繰り下がりまで表現できる。 */
    public void addMatch(MatchResult result, int rpDelta) {
        rankValue = RankValue.clamp(rankValue + rpDelta);

        SessionMatch m = new SessionMatch();
        m.setSession(this);
        m.setSeq((short) (matches.size() + 1));
        m.setResult(result);
        m.setRpDelta((short) rpDelta);
        m.setRankValueAfter(rankValue);
        matches.add(m);
    }

    public long countOf(MatchResult result) {
        return matches.stream().filter(m -> m.getResult() == result).count();
    }

    /** セッションを通しての増減。 */
    public int rpChange() {
        return rankValue - startRankValue;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getSeasonId() { return seasonId; }
    public void setSeasonId(Long seasonId) { this.seasonId = seasonId; }
    public LocalDate getPlayedOn() { return playedOn; }
    public void setPlayedOn(LocalDate playedOn) { this.playedOn = playedOn; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public RoleType getRoleType() { return roleType; }
    public void setRoleType(RoleType roleType) { this.roleType = roleType; }
    public int getStartRankValue() { return startRankValue; }
    public int getRankValue() { return rankValue; }
    public ConditionLevel getConditionLevel() { return conditionLevel; }
    public void setConditionLevel(ConditionLevel conditionLevel) { this.conditionLevel = conditionLevel; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public List<SessionMatch> getMatches() { return matches; }
}
