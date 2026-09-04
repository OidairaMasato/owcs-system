package jp.oidaira.owcs.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** 取り込みジョブの最終実行結果。画面のフッターに「最終更新」を出すのに使う。 */
@Entity
@Table(name = "sync_state")
public class SyncState {

    @Id
    @Column(name = "job_key")
    private String jobKey;

    @Column(name = "last_success_at")
    private OffsetDateTime lastSuccessAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected SyncState() {
    }

    public SyncState(String jobKey) {
        this.jobKey = jobKey;
    }

    public void succeeded() {
        this.lastSuccessAt = OffsetDateTime.now();
        this.lastError = null;
        this.updatedAt = this.lastSuccessAt;
    }

    public void failed(String message) {
        this.lastError = message != null && message.length() > 500 ? message.substring(0, 500) : message;
        this.updatedAt = OffsetDateTime.now();
    }

    public String getJobKey() { return jobKey; }
    public OffsetDateTime getLastSuccessAt() { return lastSuccessAt; }
    public String getLastError() { return lastError; }
}
