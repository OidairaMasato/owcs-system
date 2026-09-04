package jp.oidaira.owlog.web.dto;

import jp.oidaira.owlog.domain.RoleType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 推移グラフ1点分＝1セッション。1日に複数あるので sessionId で一意にする。
 */
public record RankPoint(
        Long sessionId,
        LocalDate playedOn,
        OffsetDateTime startedAt,
        RoleType roleType,
        int rankValue,
        String rankLabel,
        int rp,
        int rpChange,
        long wins,
        long losses
) {
}
