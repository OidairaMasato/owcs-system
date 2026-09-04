package jp.oidaira.owlog.service;

import jp.oidaira.owlog.domain.Tier;

/**
 * ティア × ディビジョン × RP を一本の整数軸に落とす。
 *
 * <pre>
 * rank_value = tierIndex * 500 + (5 - division) * 100 + rp
 * </pre>
 *
 * この形にしておくと <b>RP の増減がそのまま加算になる</b>。
 * +22 なら rank_value に 22 足すだけで、ディビジョンやティアの繰り上がりは自動で表現される。
 *
 * ここが唯一の実装。フロントで再実装せず、API が返した値を使うこと。
 */
public final class RankValue {

    public static final int PER_TIER = 500;
    public static final int PER_DIVISION = 100;
    public static final int MIN = 0;
    public static final int MAX = Tier.values().length * PER_TIER - 1;

    private RankValue() {
    }

    public static int of(Tier tier, int division, int rp) {
        if (division < 1 || division > 5) {
            throw new IllegalArgumentException("division は 1〜5: " + division);
        }
        if (rp < 0 || rp > 99) {
            throw new IllegalArgumentException("rp は 0〜99: " + rp);
        }
        return tier.ordinal() * PER_TIER + (5 - division) * PER_DIVISION + rp;
    }

    /** ブロンズ5の下限とチャンピオン1の上限で頭打ちにする。 */
    public static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }

    public static Tier tierOf(int value) {
        return Tier.values()[clamp(value) / PER_TIER];
    }

    public static int divisionOf(int value) {
        return 5 - (clamp(value) % PER_TIER) / PER_DIVISION;
    }

    public static int rpOf(int value) {
        return clamp(value) % PER_DIVISION;
    }

    /** 表示用。例: "プラチナ 3" */
    public static String label(int value) {
        return tierOf(value).label() + " " + divisionOf(value);
    }
}
