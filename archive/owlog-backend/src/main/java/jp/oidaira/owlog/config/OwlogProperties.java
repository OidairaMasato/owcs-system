package jp.oidaira.owlog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * v1 は単一ユーザー運用のため、対象ユーザーを設定値で持つ。
 * 認証を入れた時点でこのクラスは役目を終える。
 */
@ConfigurationProperties(prefix = "owlog")
public record OwlogProperties(String defaultBattleTag, String corsOrigins) {
}
