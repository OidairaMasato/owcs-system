package jp.oidaira.owcs.sync;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import jp.oidaira.owcs.OwcsProperties;
import jp.oidaira.owcs.domain.SyncState;
import jp.oidaira.owcs.repo.SyncStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 「必要なときだけ取り込む」入口。
 *
 * 無料ホスティングでは無操作でアプリがスリープし、その間 @Scheduled は動かない。
 * 起きた直後のデータは古いので、ダッシュボードが要求された時点で鮮度を見て取り込む。
 *
 * 画面側は Service Worker のキャッシュを即表示するので、
 * ここで数秒かかってもユーザーの待ち時間にはならない。
 */
@Component
public class SyncCoordinator {

    private static final Logger log = LoggerFactory.getLogger(SyncCoordinator.class);

    private final MatchSyncService matches;
    private final StandingsSyncService standings;
    private final SyncStateRepository syncRepo;
    private final OwcsProperties props;

    /** 同時に走らせない。2 本目以降はそのまま素通りさせる（待たせない）。 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** 成否にかかわらず最後に試みた時刻。失敗時の叩きすぎを防ぐ。 */
    private volatile Instant lastAttempt = Instant.EPOCH;

    public SyncCoordinator(MatchSyncService matches, StandingsSyncService standings,
                           SyncStateRepository syncRepo, OwcsProperties props) {
        this.matches = matches;
        this.standings = standings;
        this.syncRepo = syncRepo;
        this.props = props;
    }

    /** 前回成功から maxAge 以上経っていれば取り込む。そうでなければ何もしない。 */
    public void refreshIfStale() {
        Duration maxAge = props.sync().maxAge();
        Duration retry = props.sync().retryInterval();

        if (Instant.now().isBefore(lastAttempt.plus(retry))) return;
        if (isFresh(maxAge)) return;
        if (!running.compareAndSet(false, true)) return;

        try {
            log.info("data is stale; syncing on demand");
            matches.syncResults();
            matches.syncSchedule();
            matches.syncGameDetails();
            standings.sync();
        } catch (RuntimeException e) {
            // 各サービスが内部で握るので基本ここには来ないが、画面は落とさない
            log.warn("on-demand sync failed: {}", e.toString());
        } finally {
            lastAttempt = Instant.now();
            running.set(false);
        }
    }

    /** 起動直後の 1 回。スリープから起きた直後もここを通る。 */
    public void refreshOnStartup() {
        lastAttempt = Instant.EPOCH;
        refreshIfStale();
    }

    private boolean isFresh(Duration maxAge) {
        OffsetDateTime newest = syncRepo.findAll().stream()
                .map(SyncState::getLastSuccessAt)
                .filter(java.util.Objects::nonNull)
                .max(OffsetDateTime::compareTo)
                .orElse(null);
        return newest != null && newest.toInstant().isAfter(Instant.now().minus(maxAge));
    }
}
