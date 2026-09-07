package jp.oidaira.owcs.sync;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import jp.oidaira.owcs.OwcsProperties;
import jp.oidaira.owcs.domain.SyncState;
import jp.oidaira.owcs.repo.MatchRepository;
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
    private final LogoSyncService logos;
    private final SyncStateRepository syncRepo;
    private final MatchRepository matchRepo;
    private final OwcsProperties props;

    /**
     * 「試合中かもしれない」とみなす窓。
     * 開始予定の 15 分前から、始まって 4 時間後まで。
     * BO7 でも 3 時間あれば終わるので、4 時間あれば取りこぼさない。
     */
    private static final Duration PLAY_WINDOW_BEFORE = Duration.ofMinutes(15);
    private static final Duration PLAY_WINDOW_AFTER = Duration.ofHours(4);

    /** 同時に走らせない。2 本目以降はそのまま素通りさせる（待たせない）。 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** 成否にかかわらず最後に試みた時刻。失敗時の叩きすぎを防ぐ。 */
    private volatile Instant lastAttempt = Instant.EPOCH;

    public SyncCoordinator(MatchSyncService matches, StandingsSyncService standings,
                           LogoSyncService logos, SyncStateRepository syncRepo,
                           MatchRepository matchRepo, OwcsProperties props) {
        this.matches = matches;
        this.standings = standings;
        this.logos = logos;
        this.syncRepo = syncRepo;
        this.matchRepo = matchRepo;
        this.props = props;
    }

    /**
     * 前回成功から maxAge 以上経っていれば取り込む。そうでなければ何もしない。
     *
     * 試合中は maxAge を短くして、スコアが画面で動くようにする。
     * ただしその短い間隔で取りに行くのは試合結果だけにする。
     * 順位表とロゴまで毎分叩くと API の回数を無駄に使うし、
     * 順位表は試合が終わるまで動かないので急ぐ理由が無い。
     */
    public void refreshIfStale() {
        Duration normal = props.sync().maxAge();
        Duration retry = props.sync().retryInterval();

        if (Instant.now().isBefore(lastAttempt.plus(retry))) return;

        boolean inPlay = inPlayWindow();
        Duration maxAge = inPlay ? props.sync().liveMaxAge() : normal;

        if (isFresh(maxAge)) return;
        if (!running.compareAndSet(false, true)) return;

        // 通常の鮮度でも古いときだけ、順位表とロゴまで含めて取り込む
        boolean full = !isFresh(normal);

        try {
            log.info("sync on demand: inPlay={} full={}", inPlay, full);
            matches.syncMatches();
            matches.syncGameDetails();
            if (full) {
                standings.sync();
                logos.sync();
            }
        } catch (RuntimeException e) {
            // 各サービスが内部で握るので基本ここには来ないが、画面は落とさない
            log.warn("on-demand sync failed: {}", e.toString());
        } finally {
            lastAttempt = Instant.now();
            running.set(false);
        }
    }

    /** いま試合が行われている可能性がある時間帯か。 */
    private boolean inPlayWindow() {
        OffsetDateTime now = OffsetDateTime.now();
        return matchRepo.countInPlayWindow(
                now.minus(PLAY_WINDOW_AFTER), now.plus(PLAY_WINDOW_BEFORE)) > 0;
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
