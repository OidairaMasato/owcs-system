package jp.oidaira.owcs.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jp.oidaira.owcs.repo.PageViewRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * アクセス数の記録と閲覧。
 *
 * 外部の解析サービスを使わないのは、
 * 訪問者のデータを第三者に渡さずに済ませたいのと、
 * 第三者スクリプトを入れるとプライバシーポリシーの設置が要るため。
 *
 * 個人は追跡しない。IP もセッション識別子も保存しない。
 * 保存するのは「日付 × 流入元ホスト × 端末種別」の回数だけ。
 * 「X の告知で何回見られたか」を知るにはこれで足りる。
 *
 * /api/stats は認証を掛けていない。集計値だけで個人情報を含まないため。
 * 見せたくなくなったら、環境変数でキーを要求するように変えればよい。
 */
@RestController
@RequestMapping("/api")
public class StatsController {

    /** 流入元ホストの最大長。長すぎるものは捨てる（悪意ある値を貯めないため）。 */
    private static final int MAX_HOST = 100;

    /** /api/stats が返す期間。 */
    private static final int STATS_DAYS = 90;

    private final PageViewRepository repo;

    public StatsController(PageViewRepository repo) {
        this.repo = repo;
    }

    /** 画面が読み込まれたときに 1 回だけ呼ばれる。 */
    @PostMapping("/hit")
    public ResponseEntity<Void> hit(@RequestBody(required = false) Hit body,
                                    HttpServletRequest request) {
        String host = referrerHost(body != null ? body.ref() : null, request.getServerName());
        String device = device(request.getHeader("User-Agent"));
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Tokyo"));
        try {
            repo.record(today, host, device);
        } catch (RuntimeException e) {
            // 数えられなくても画面は動くべきなので、握りつぶす
        }
        return ResponseEntity.noContent().build();
    }

    /** 直近 90 日のアクセス数。 */
    @GetMapping("/stats")
    public Stats stats() {
        LocalDate from = LocalDate.now(ZoneId.of("Asia/Tokyo")).minusDays(STATS_DAYS);
        List<Map<String, Object>> raw = repo.since(from);

        int total = 0;
        Map<String, Integer> byDay = new LinkedHashMap<>();
        Map<String, Integer> byReferrer = new LinkedHashMap<>();
        Map<String, Integer> byDevice = new LinkedHashMap<>();

        for (Map<String, Object> r : raw) {
            int v = ((Number) r.get("views")).intValue();
            total += v;
            byDay.merge(String.valueOf(r.get("day")), v, Integer::sum);
            String ref = String.valueOf(r.get("referrer_host"));
            // PowerShell (CP932) で読むと日本語が化けるので、キーは ASCII にする
            byReferrer.merge(ref.isBlank() ? "(direct)" : ref, v, Integer::sum);
            byDevice.merge(String.valueOf(r.get("device")), v, Integer::sum);
        }

        return new Stats(total, toRows(byDay), toRows(byReferrer), toRows(byDevice));
    }

    /** 受け取るのは document.referrer だけ。サーバー側の Referer は自サイトになるので使えない。 */
    public record Hit(String ref) {
    }

    public record Row(String key, int views) {
    }

    public record Stats(int total, List<Row> byDay, List<Row> byReferrer, List<Row> byDevice) {
    }

    private static List<Row> toRows(Map<String, Integer> m) {
        List<Row> out = new ArrayList<>();
        m.forEach((k, v) -> out.add(new Row(k, v)));
        out.sort((a, b) -> b.views() - a.views());
        return out;
    }

    /**
     * 流入元のホスト名。自サイト内の遷移と、判別できないものは空文字（＝直接）とする。
     * 任意の文字列が入ってくる場所なので、形を検査してから通す。
     */
    private static String referrerHost(String ref, String ownHost) {
        if (ref == null || ref.isBlank()) return "";
        String host;
        try {
            host = java.net.URI.create(ref.trim()).getHost();
        } catch (RuntimeException e) {
            return "";
        }
        if (host == null) return "";
        host = host.toLowerCase();
        if (host.startsWith("www.")) host = host.substring(4);
        if (host.equals(ownHost) || host.equals("localhost")) return "";
        if (host.length() > MAX_HOST) return "";
        if (!host.matches("[a-z0-9.-]+")) return "";
        return host;
    }

    private static String device(String userAgent) {
        return userAgent != null && userAgent.contains("Mobi") ? "mobile" : "desktop";
    }
}
