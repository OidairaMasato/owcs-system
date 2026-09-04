package jp.oidaira.owcs.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** シリーズ配下の 1 トーナメント（Group Stage / Playoffs など）と、その順位表。 */
@Entity
@Table(name = "tournaments")
public class Tournament {

    @Id
    private Integer id;

    @Column(name = "serie_id")
    private Integer serieId;

    @Column(name = "serie_name")
    private String serieName;

    private String name;
    private String slug;

    @Column(name = "begin_at")
    private OffsetDateTime beginAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "standings", joinColumns = @JoinColumn(name = "tournament_id"))
    @OrderBy("rankNo asc")
    private List<StandingRow> standings = new ArrayList<>();

    protected Tournament() {
    }

    public Tournament(Integer id) {
        this.id = id;
    }

    public void update(Integer serieId, String serieName, String name, String slug, OffsetDateTime beginAt) {
        this.serieId = serieId;
        this.serieName = serieName;
        this.name = name;
        this.slug = slug;
        this.beginAt = beginAt;
        this.updatedAt = OffsetDateTime.now();
    }

    public void replaceStandings(List<StandingRow> rows) {
        this.standings.clear();
        this.standings.addAll(rows);
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 総当たりの順位表を持つトーナメントか。
     * Group Stage / Regular season がそれにあたる。Playoffs はブラケットなので順位表にならない。
     */
    public boolean looksLikeLeagueTable() {
        if (name == null) return false;
        String n = name.toLowerCase();
        return n.contains("group") || n.contains("regular") || n.contains("league");
    }

    public Integer getId() { return id; }
    public Integer getSerieId() { return serieId; }
    public String getSerieName() { return serieName; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public OffsetDateTime getBeginAt() { return beginAt; }
    public List<StandingRow> getStandings() { return standings; }
}
