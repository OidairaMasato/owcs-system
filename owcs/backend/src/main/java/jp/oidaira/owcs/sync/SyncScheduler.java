package jp.oidaira.owcs.sync;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 取り込みジョブの起動タイミングだけを持つ。処理本体は {@link MatchSyncService} と
 * {@link StandingsSyncService}、鮮度判定は {@link SyncCoordinator}。
 *
 * 常時起動しているとき（ローカル・有料プラン）はこの定期実行が効く。
 * 無料プランではスリープ中に止まるので、実際には
 * ダッシュボード要求時の {@link SyncCoordinator#refreshIfStale()} が主役になる。
 *
 * 1 時間あたりのリクエスト数の見積り（常時起動時）:
 *   結果同期  6 回/h x 2 req = 12
 *   予定同期  2 回/h x 1 req = 2
 *   詳細同期  4 回/h x 最大 5 req = 20
 *   順位表    2 回/h x 2 req = 4
 *   合計 38 req/h（PandaScore 無料枠は 1000 req/h）
 */
@Component
public class SyncScheduler implements ApplicationRunner {

    private final MatchSyncService sync;
    private final StandingsSyncService standings;
    private final SyncCoordinator coordinator;

    public SyncScheduler(MatchSyncService sync, StandingsSyncService standings,
                         SyncCoordinator coordinator) {
        this.sync = sync;
        this.standings = standings;
        this.coordinator = coordinator;
    }

    /** 起動直後に一度だけ取り込む。空の画面を見せないため。 */
    @Override
    public void run(ApplicationArguments args) {
        coordinator.refreshOnStartup();
    }

    @Scheduled(cron = "${owcs.sync.results-cron}")
    public void results() {
        sync.syncResults();
    }

    @Scheduled(cron = "${owcs.sync.schedule-cron}")
    public void schedule() {
        sync.syncSchedule();
    }

    @Scheduled(cron = "0 15/15 * * * *")
    public void gameDetails() {
        sync.syncGameDetails();
    }

    @Scheduled(cron = "0 20/30 * * * *")
    public void standings() {
        standings.sync();
    }
}
