package jp.oidaira.owcs.web;

import jp.oidaira.owcs.sync.LogoSyncService;
import jp.oidaira.owcs.sync.MatchSyncService;
import jp.oidaira.owcs.sync.StandingsSyncService;
import jp.oidaira.owcs.sync.SyncCoordinator;
import jp.oidaira.owcs.web.LeagueDtos.HeadToHead;
import jp.oidaira.owcs.web.LeagueDtos.League;
import jp.oidaira.owcs.web.LeagueDtos.Rankings;
import jp.oidaira.owcs.web.LeagueDtos.Today;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class LeagueController {

    private final LeagueService service;
    private final MatchSyncService matches;
    private final StandingsSyncService standings;
    private final LogoSyncService logos;
    private final SyncCoordinator coordinator;

    public LeagueController(LeagueService service, MatchSyncService matches,
                            StandingsSyncService standings, LogoSyncService logos,
                            SyncCoordinator coordinator) {
        this.service = service;
        this.matches = matches;
        this.standings = standings;
        this.logos = logos;
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
    public League league(@RequestParam(name = "serie", required = false) Integer serieId) {
        coordinator.refreshIfStale();
        return service.build(serieId);
    }

    /**
     * 今日前後の全試合。大会を選ばずに「今日 OWCS で何があるか」を見るための入口。
     * 大会をまたぐので、どの大会の試合かも一緒に返す。
     */
    @GetMapping("/today")
    public Today today() {
        coordinator.refreshIfStale();
        return service.today();
    }

    /** 対戦相手別の通算成績。大会をまたいで集計する。 */
    @GetMapping("/team/{teamId}/head-to-head")
    public HeadToHead headToHead(@PathVariable int teamId) {
        return service.headToHead(teamId);
    }

    /**
     * 年間の通算ランキング。取り込み済みの全大会を横断する。
     * year を省略すると、試合があった最も新しい年を返す。
     */
    @GetMapping("/rankings")
    public Rankings rankings(@RequestParam(name = "year", required = false) Integer year) {
        return service.rankings(year);
    }

    /** 手動で取り込みを走らせる。画面の「今すぐ更新」用。 */
    @PostMapping("/sync")
    public ResponseEntity<League> syncNow(@RequestParam(name = "serie", required = false) Integer serieId) {
        matches.syncMatches();
        matches.syncGameDetails();
        standings.sync();
        logos.sync();
        return ResponseEntity.ok(service.build(serieId));
    }
}
