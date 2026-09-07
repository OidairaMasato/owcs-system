package jp.oidaira.owcs;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 設定値。トークンは環境変数 PANDASCORE_TOKEN から入る。
 * 値をソースや application.yml に直書きしないこと。
 *
 * 取り込みの単位は「シリーズ（大会）」。
 * チーム単位だと 10 チームで 10 倍のリクエストになるが、
 * シリーズ単位なら 1 リクエストで全チーム分が揃う。
 *
 * 対象シリーズは地域では絞らない。地域名で絞ると
 * Midseason Championship や World Finals のような国際大会が漏れるため、期間で絞る。
 */
@ConfigurationProperties(prefix = "owcs")
public record OwcsProperties(
        PandaScore pandascore,
        Integer leagueId,
        Series series,
        Sync sync) {

    public record PandaScore(String baseUrl, String token) {
        public boolean configured() {
            return token != null && !token.isBlank();
        }
    }

    /** どの大会を追うかの範囲。 */
    public record Series(Integer pastDays, Integer futureDays, Integer max, Integer hotMarginDays) {
    }

    /**
     * 取り込みのタイミング設定。
     *
     * 無料ホスティングではアプリが無操作でスリープし、@Scheduled が止まる。
     * そのため画面の要求時にも鮮度を見て取り込む（maxAge）。
     * 失敗しているときに毎リクエスト叩かないよう retryInterval で間隔を空ける。
     *
     * 試合中は結果がすぐ変わるので liveMaxAge の短い間隔に切り替える。
     */
    public record Sync(String resultsCron, String scheduleCron, Duration maxAge,
                       Duration retryInterval, Duration liveMaxAge) {
    }
}
