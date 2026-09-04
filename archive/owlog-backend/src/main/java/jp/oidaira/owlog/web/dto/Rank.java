package jp.oidaira.owlog.web.dto;

import jp.oidaira.owlog.domain.Tier;
import jp.oidaira.owlog.service.RankValue;

/**
 * ランクの表示用ひとまとまり。DB が持つのは rank_value だけで、残りはすべて導出値。
 */
public record Rank(int value, Tier tier, int division, int rp, String label) {

    public static Rank of(int value) {
        int v = RankValue.clamp(value);
        return new Rank(v, RankValue.tierOf(v), RankValue.divisionOf(v), RankValue.rpOf(v), RankValue.label(v));
    }
}
