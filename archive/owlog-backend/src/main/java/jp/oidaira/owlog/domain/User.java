package jp.oidaira.owlog.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "battle_tag", nullable = false, unique = true)
    private String battleTag;

    @Column(nullable = false)
    private String platform = "console";

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public String getBattleTag() { return battleTag; }
    public void setBattleTag(String battleTag) { this.battleTag = battleTag; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
