package jp.oidaira.owlog.web;

import jakarta.validation.Valid;
import jp.oidaira.owlog.service.SessionService;
import jp.oidaira.owlog.web.dto.RankPoint;
import jp.oidaira.owlog.web.dto.SessionRequest;
import jp.oidaira.owlog.web.dto.SessionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SessionController {

    private final SessionService service;

    public SessionController(SessionService service) {
        this.service = service;
    }

    @PostMapping("/sessions")
    public SessionResponse create(@Valid @RequestBody SessionRequest req) {
        return service.record(req);
    }

    @GetMapping("/sessions")
    public List<SessionResponse> list() {
        return service.list();
    }

    @GetMapping("/sessions/latest")
    public ResponseEntity<SessionResponse> latest() {
        return service.latest().map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/rank-history")
    public List<RankPoint> rankHistory() {
        return service.rankHistory();
    }
}
