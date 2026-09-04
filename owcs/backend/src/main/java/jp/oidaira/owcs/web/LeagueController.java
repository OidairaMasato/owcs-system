package jp.oidaira.owcs.web;

import jp.oidaira.owcs.sync.MatchSyncService;
import jp.oidaira.owcs.sync.StandingsSyncService;
import jp.oidaira.owcs.sync.SyncCoordinator;
import jp.oidaira.owcs.web.LeagueDtos.League;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class LeagueController {

    private final LeagueService service;
    private final MatchSyncService matches;
    private final StandingsSyncService standings;
    private final SyncCoordinator coordinator;

    public LeagueController(LeagueService service, MatchSyncService matches,
                            StandingsSyncService standings, SyncCoordinator coordinator) {
        this.service = service;
        this.matches = matches;
        this.standings = standings;
        this.coordinator = coordinator;
    }

    /**
     * 画面が必要とするデータを 1 リクエストで返す。
     * リーグ表示もチーム表示も、この 1 本から組み立てる。
     *
     * 無料ホスティングではアプリがスリープして定期取り込みが止まるので、
     * ここで鮮度を見て必要なら取り込んでから返す（十分新しければ何もしない）。
     */
    @GetMapping("/league")
    public League league() {
        coordinator.refreshIfStale();
        return service.build();
    }

    /** 手動で取り込みを走らせる。画面の「今すぐ更新」用。 */
    @PostMapping("/sync")
    public ResponseEntity<League> syncNow() {
        matches.syncMatches();
        matches.syncGameDetails();
        standings.sync();
        return ResponseEntity.ok(service.build());
    }
}
