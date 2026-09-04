package jp.oidaira.owlog.domain;

import jakarta.persistence.*;

/**
 * セッション内の1試合。
 * rpDelta はリザルト画面に出る増減（+22 / -18 など）。
 * rankValueAfter はその試合を終えた時点のランク（開始ランク + それまでの増減の累計）。
 * mapName 以降は v2 で使う列で、v1 では常に null。
 */
@Entity
@Table(name = "session_matches")
public class SessionMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id")
    private Session session;

    @Column(nullable = false)
    private short seq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private MatchResult result;

    @Column(name = "rp_delta", nullable = false)
    private short rpDelta;

    @Column(name = "rank_value_after", nullable = false)
    private int rankValueAfter;

    @Column(name = "map_name")
    private String mapName;

    @Column(name = "hero_names")
    private String heroNames;

    @Column(name = "replay_code")
    private String replayCode;

    @Column
    private String note;

    public Long getId() { return id; }
    public Session getSession() { return session; }
    public void setSession(Session session) { this.session = session; }
    public short getSeq() { return seq; }
    public void setSeq(short seq) { this.seq = seq; }
    public MatchResult getResult() { return result; }
    public void setResult(MatchResult result) { this.result = result; }
    public short getRpDelta() { return rpDelta; }
    public void setRpDelta(short rpDelta) { this.rpDelta = rpDelta; }
    public int getRankValueAfter() { return rankValueAfter; }
    public void setRankValueAfter(int rankValueAfter) { this.rankValueAfter = rankValueAfter; }
    public String getMapName() { return mapName; }
    public void setMapName(String mapName) { this.mapName = mapName; }
    public String getHeroNames() { return heroNames; }
    public void setHeroNames(String heroNames) { this.heroNames = heroNames; }
    public String getReplayCode() { return replayCode; }
    public void setReplayCode(String replayCode) { this.replayCode = replayCode; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
