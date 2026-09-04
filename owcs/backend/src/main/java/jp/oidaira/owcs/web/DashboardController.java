package jp.oidaira.owcs.web;

import jp.oidaira.owcs.sync.MatchSyncService;
import jp.oidaira.owcs.sync.StandingsSyncService;
import jp.oidaira.owcs.sync.SyncCoordinator;
import jp.oidaira.owcs.web.DashboardDtos.Dashboard;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DashboardService service;
    private final MatchSyncService sync;
    private final StandingsSyncService standings;
    private final SyncCoordinator coordinator;

    public DashboardController(DashboardService service, MatchSyncService sync,
                               StandingsSyncService standings, SyncCoordinator coordinator) {
        this.service = service;
        this.sync = sync;
        this.standings = standings;
        this.coordinator = coordinator;
    }

    /**
     * 画面が必要とするデータを 1 リクエストで返す。
     *
     * 無料ホスティングではアプリがスリープして定期取り込みが止まるので、
     * ここで鮮度を見て必要なら取り込んでから返す（十分新しければ何もしない）。
     */
    @GetMapping("/dashboard")
    public Dashboard dashboard() {
        coordinator.refreshIfStale();
        return service.build();
    }

    /** 手動で取り込みを走らせる。開発時とスマホで「今すぐ更新」したいとき用。 */
    @PostMapping("/sync")
    public ResponseEntity<Dashboard> syncNow() {
        sync.syncResults();
        sync.syncSchedule();
        sync.syncGameDetails();
        standings.sync();
        return ResponseEntity.ok(service.build());
    }
}
