package jp.oidaira.owcs.web;

import java.time.Duration;
import jp.oidaira.owcs.domain.TeamLogo;
import jp.oidaira.owcs.repo.TeamLogoRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自前で保持しているチームロゴを配信する。
 * PandaScore の画像を画面から直リンクしないための入口。
 */
@RestController
@RequestMapping("/api")
public class LogoController {

    private final TeamLogoRepository logoRepo;

    public LogoController(TeamLogoRepository logoRepo) {
        this.logoRepo = logoRepo;
    }

    @GetMapping("/logo/{teamId}")
    public ResponseEntity<byte[]> logo(@PathVariable int teamId) {
        TeamLogo logo = logoRepo.findById(teamId).orElse(null);
        if (logo == null) return ResponseEntity.notFound().build();

        MediaType type;
        try {
            type = MediaType.parseMediaType(logo.getContentType());
        } catch (RuntimeException e) {
            type = MediaType.IMAGE_PNG;
        }

        // ロゴはまず変わらないので長くキャッシュさせる
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(logo.getData());
    }
}
