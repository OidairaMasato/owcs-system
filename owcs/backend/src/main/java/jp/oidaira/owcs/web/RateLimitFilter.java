package jp.oidaira.owcs.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * API の簡易レート制限。
 *
 * 公開する以上、画面ではなく API を直接叩かれる。
 * PandaScore の規約は生データをそのまま第三者に渡すことを禁じているので、
 * 誰かが機械的に取得して再配布する使い方は抑えておく。
 *
 * 単一インスタンス前提のメモリ内カウンタ。無料プランは 1 インスタンスなのでこれで足りる。
 * 複数インスタンスに増やすときは共有ストアが要る。
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    /** 1 分あたりの上限。人が画面を操作する分には十分な余裕がある。 */
    private static final int LIMIT_PER_MINUTE = 60;

    /** 取り込みを起こす操作はさらに絞る。 */
    private static final int SYNC_LIMIT_PER_MINUTE = 5;

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private record Counter(Instant windowStart, AtomicInteger count) {
    }

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String path = req.getRequestURI();
        // ロゴは実質静的で、1 画面に何枚も出るため対象外にする
        return !path.startsWith("/api/") || path.startsWith("/api/logo/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        int limit = req.getRequestURI().startsWith("/api/sync") ? SYNC_LIMIT_PER_MINUTE : LIMIT_PER_MINUTE;
        String key = clientKey(req) + "|" + (limit == SYNC_LIMIT_PER_MINUTE ? "sync" : "read");

        if (!allow(key, limit)) {
            res.setStatus(429);
            res.setHeader("Retry-After", "60");
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"error\":\"too many requests\"}");
            return;
        }
        chain.doFilter(req, res);
    }

    private boolean allow(String key, int limit) {
        Instant now = Instant.now();
        Counter c = counters.compute(key, (k, cur) -> {
            if (cur == null || now.isAfter(cur.windowStart().plus(WINDOW))) {
                return new Counter(now, new AtomicInteger(0));
            }
            return cur;
        });
        // 古い記録が溜まり続けないよう、たまに掃除する
        if (counters.size() > 5000) {
            counters.entrySet().removeIf(e -> now.isAfter(e.getValue().windowStart().plus(WINDOW)));
        }
        return c.count().incrementAndGet() <= limit;
    }

    /** Render はプロキシ越しなので X-Forwarded-For を優先する。 */
    private static String clientKey(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return req.getRemoteAddr();
    }
}
