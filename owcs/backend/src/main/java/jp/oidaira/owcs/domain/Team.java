package jp.oidaira.owcs.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "teams")
public class Team {

    /** PandaScore の team id をそのまま主キーにする（採番しない）。 */
    @Id
    private Integer id;

    @Column(nullable = false)
    private String name;

    private String acronym;
    private String slug;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected Team() {
    }

    public Team(Integer id) {
        this.id = id;
    }

    public void update(String name, String acronym, String slug, String imageUrl) {
        this.name = name;
        this.acronym = acronym;
        this.slug = slug;
        this.imageUrl = imageUrl;
        this.updatedAt = OffsetDateTime.now();
    }

    public Integer getId() { return id; }
    public String getName() { return name; }
    public String getAcronym() { return acronym; }
    public String getSlug() { return slug; }
    public String getImageUrl() { return imageUrl; }

    /** 表示用の短い名前。acronym があればそれを使う。 */
    public String shortName() {
        return acronym != null && !acronym.isBlank() ? acronym : name;
    }
}
