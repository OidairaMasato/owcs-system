package jp.oidaira.owcs.sync;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 取り込みジョブの起動タイミングだけを持つ。
 *
 * 常時起動しているとき（ローカル・有料プラン）はこの定期実行が効く。
 * 無料プランではスリープ中に止まるので、実際には
 * 画面要求時の {@link SyncCoordinator#refreshIfStale()} が主役になる。
 *
 * 1 時間あたりのリクエスト数の見積り（常時起動時・シリーズ 2 本）:
 *   試合同期  6 回/h x 2 req = 12
 *   詳細同期  4 回/h x 最大 5 req = 20
 *   順位表    2 回/h x 最大 4 req = 8
 *   シリーズ一覧 6 時間に 1 回
 *   合計 40 req/h 程度（PandaScore 無料枠は 1000 req/h）
 */
@Component
public class SyncScheduler implements ApplicationRunner {

    private final MatchSyncService matches;
    private final StandingsSyncService standings;
    private final LogoSyncService logos;
    private final SyncCoordinator coordinator;

    public SyncScheduler(MatchSyncService matches, StandingsSyncService standings,
                         LogoSyncService logos, SyncCoordinator coordinator) {
        this.matches = matches;
        this.standings = standings;
        this.logos = logos;
        this.coordinator = coordinator;
    }

    /** 起動直後に一度だけ取り込む。空の画面を見せないため。 */
    @Override
    public void run(ApplicationArguments args) {
        coordinator.refreshOnStartup();
    }

    @Scheduled(cron = "${owcs.sync.results-cron}")
    public void syncMatches() {
        matches.syncMatches();
    }

    @Scheduled(cron = "0 15/15 * * * *")
    public void gameDetails() {
        matches.syncGameDetails();
    }

    @Scheduled(cron = "0 20/30 * * * *")
    public void syncStandings() {
        standings.sync();
    }

    @Scheduled(cron = "0 25/10 * * * *")
    public void syncLogos() {
        logos.sync();
    }
}
