package jp.oidaira.owcs.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * チームロゴの実体。
 *
 * PandaScore の画像 URL を画面から直接参照すると、規約の
 * 「PandaScore のインターフェースへの直リンク禁止・自サイトに再掲載せよ」に触れる。
 * そこで取り込み時に画像を落として保存し、自分の API から配信する。
 */
@Entity
@Table(name = "team_logos")
public class TeamLogo {

    @Id
    @Column(name = "team_id")
    private Integer teamId;

    @Column(name = "source_url", nullable = false)
    private String sourceUrl;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    /** 画像そのもの。@Lob を付けると PostgreSQL では oid 型になるので付けない（bytea に対応させる）。 */
    @Column(name = "data", nullable = false)
    private byte[] data;

    @Column(name = "fetched_at", nullable = false)
    private OffsetDateTime fetchedAt = OffsetDateTime.now();

    protected TeamLogo() {
    }

    public TeamLogo(Integer teamId, String sourceUrl, String contentType, byte[] data) {
        this.teamId = teamId;
        this.sourceUrl = sourceUrl;
        this.contentType = contentType;
        this.data = data;
        this.fetchedAt = OffsetDateTime.now();
    }

    public Integer getTeamId() { return teamId; }
    public String getSourceUrl() { return sourceUrl; }
    public String getContentType() { return contentType; }
    public byte[] getData() { return data; }
    public OffsetDateTime getFetchedAt() { return fetchedAt; }
}
