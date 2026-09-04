package jp.oidaira.owlog.domain;

/**
 * ランクのティア。宣言順がそのまま強さの順序で、rank_value の計算に使う。
 * 並び替えたり途中に挿入したりしないこと。
 */
public enum Tier {
    BRONZE("ブロンズ"),
    SILVER("シルバー"),
    GOLD("ゴールド"),
    PLATINUM("プラチナ"),
    DIAMOND("ダイヤ"),
    MASTER("マスター"),
    GRANDMASTER("グランドマスター"),
    CHAMPION("チャンピオン");

    private final String label;

    Tier(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
