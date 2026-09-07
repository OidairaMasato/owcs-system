package jp.oidaira.owcs.sync;

import java.util.List;
import jp.oidaira.owcs.domain.SyncState;
import jp.oidaira.owcs.domain.Team;
import jp.oidaira.owcs.domain.TeamLogo;
import jp.oidaira.owcs.repo.SyncStateRepository;
import jp.oidaira.owcs.repo.TeamLogoRepository;
import jp.oidaira.owcs.repo.TeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/**
 * チームロゴを自前で保持する。
 *
 * PandaScore の規約は「PandaScore のインターフェースへの直リンクは禁止。
 * データは自分のサイトに再掲載せよ」としている。
 * 画像 URL をそのまま img タグに書くと直リンクになるので、
 * ここで取得して DB に保存し、{@code /api/logo/{teamId}} から配信する。
 *
 * 1 回の実行で少数ずつ埋める。ロゴは変わらないので一度取れば終わり。
 */
@Service
public class LogoSyncService {

    private static final Logger log = LoggerFactory.getLogger(LogoSyncService.class);
    static final String JOB = "logos";

    /** 1 回あたりの取得数。急がないので少しずつ。 */
    private static final int BATCH = 8;

    /** 想定外に大きい画像は保存しない。 */
    private static final int MAX_BYTES = 512 * 1024;

    private final TeamRepository teamRepo;
    private final TeamLogoRepository logoRepo;
    private final SyncStateRepository syncRepo;
    /**
     * タイムアウトは必ず入れる。既定の HTTP クライアントは無制限に待つので、
     * CDN に到達できないと同期がそのまま固まる。
     */
    private final RestClient rest = RestClient.builder()
            .requestFactory(timeoutFactory(Duration.ofSeconds(5), Duration.ofSeconds(10)))
            .build();

    static ClientHttpRequestFactory timeoutFactory(Duration connect, Duration read) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(connect);
        f.setReadTimeout(read);
        return f;
    }

    public LogoSyncService(TeamRepository teamRepo, TeamLogoRepository logoRepo,
                           SyncStateRepository syncRepo) {
        this.teamRepo = teamRepo;
        this.logoRepo = logoRepo;
        this.syncRepo = syncRepo;
    }

    @Transactional
    public void sync() {
        SyncState state = syncRepo.findById(JOB).orElseGet(() -> new SyncState(JOB));
        int saved = 0;
        try {
            List<Team> targets = teamRepo.findMissingLogos(PageRequest.of(0, BATCH));
            for (Team t : targets) {
                if (fetchAndStore(t)) saved++;
            }
            state.succeeded();
            log.info("sync ok: job={} saved={} remaining_batch={}", JOB, saved, targets.size());
        } catch (RuntimeException e) {
            state.failed(e.getMessage());
            log.warn("sync FAILED: job={} : {}", JOB, e.toString());
        }
        syncRepo.save(state);
    }

    private boolean fetchAndStore(Team team) {
        String url = team.getImageUrl();
        if (url == null || url.isBlank()) return false;
        try {
            // URI.create で渡すこと。uri(String) は URI テンプレートとして解釈され、
            // 既にエンコード済みの URL を二重エンコードすることがある。
            ResponseEntity<byte[]> res = rest.get()
                    .uri(java.net.URI.create(url))
                    .header("User-Agent", "owcs-watch/1.0")
                    .retrieve()
                    .toEntity(byte[].class);

            byte[] body = res.getBody();
            if (body == null || body.length == 0) {
                log.warn("logo empty team={} url={}", team.getId(), url);
                return false;
            }
            if (body.length > MAX_BYTES) {
                log.warn("logo too large team={} bytes={}", team.getId(), body.length);
                return false;
            }

            MediaType type = res.getHeaders().getContentType();
            String contentType = type != null ? type.toString() : MediaType.IMAGE_PNG_VALUE;
            if (!contentType.startsWith("image/")) {
                log.warn("logo not an image team={} contentType={}", team.getId(), contentType);
                return false;
            }

            logoRepo.save(new TeamLogo(team.getId(), url, contentType, body));
            return true;
        } catch (RuntimeException e) {
            // 1 チーム取れなくても他は続ける。次回また対象になる。
            log.warn("logo fetch failed team={} url={} : {}", team.getId(), url, e.toString());
            return false;
        }
    }
}
