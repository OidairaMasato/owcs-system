package jp.oidaira.owcs;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 設定値。トークンは環境変数 PANDASCORE_TOKEN から入る。
 * 値をソースや application.yml に直書きしないこと。
 */
@ConfigurationProperties(prefix = "owcs")
public record OwcsProperties(
        PandaScore pandascore,
        List<Integer> teamIds,
        Integer leagueId,
        Sync sync) {

    public record PandaScore(String baseUrl, String token) {
        public boolean configured() {
            return token != null && !token.isBlank();
        }
    }

    /**
     * 取り込みのタイミング設定。
     *
     * 無料ホスティングではアプリが無操作でスリープし、@Scheduled が止まる。
     * そのためダッシュボード要求時にも鮮度を見て取り込む（maxAge）。
     * 失敗しているときに毎リクエスト叩かないよう retryInterval で間隔を空ける。
     */
    public record Sync(String resultsCron, String scheduleCron, Duration maxAge, Duration retryInterval) {
    }

    /** Phase 1 は 1 チームだけ。Phase 2 で複数になる。 */
    public int primaryTeamId() {
        return teamIds.get(0);
    }
}
