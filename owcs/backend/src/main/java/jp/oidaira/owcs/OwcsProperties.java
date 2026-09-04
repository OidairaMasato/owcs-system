package jp.oidaira.owcs;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 設定値。トークンは環境変数 PANDASCORE_TOKEN から入る。
 * 値をソースや application.yml に直書きしないこと。
 *
 * Phase 2 で「チーム軸」から「シリーズ軸」に変えた。
 * チーム単位で試合を引くと 10 チームで 10 倍のリクエストになるが、
 * シリーズ単位なら 1 リクエストで全チーム分が揃う。
 */
@ConfigurationProperties(prefix = "owcs")
public record OwcsProperties(
        PandaScore pandascore,
        Integer leagueId,
        String regionKeyword,
        Integer serieCount,
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
     * そのため画面の要求時にも鮮度を見て取り込む（maxAge）。
     * 失敗しているときに毎リクエスト叩かないよう retryInterval で間隔を空ける。
     */
    public record Sync(String resultsCron, String scheduleCron, Duration maxAge, Duration retryInterval) {
    }
}
