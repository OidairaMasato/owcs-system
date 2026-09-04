package jp.oidaira.owlog.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jp.oidaira.owlog.domain.ConditionLevel;
import jp.oidaira.owlog.domain.MatchResult;
import jp.oidaira.owlog.domain.RoleType;
import jp.oidaira.owlog.domain.Tier;

import java.time.LocalDate;
import java.util.List;

/**
 * 記録するのは「開始ランク」と「各試合の RP 増減」。
 * 終了ランクはサーバー側で積み上げて算出するので、クライアントは送らない。
 */
public record SessionRequest(
        LocalDate playedOn,
        @NotNull RoleType roleType,
        @NotNull Tier startTier,
        @Min(1) @Max(5) int startDivision,
        @Min(0) @Max(99) int startRp,
        ConditionLevel conditionLevel,
        @Size(max = 2000) String memo,
        @NotEmpty @Valid List<MatchInput> matches
) {
    /** rpDelta はリザルト画面の数字そのまま。負けは負の値。 */
    public record MatchInput(@NotNull MatchResult result, @Min(-99) @Max(99) int rpDelta) {
    }
}
