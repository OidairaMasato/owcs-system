package jp.oidaira.owlog.web.dto;

import jp.oidaira.owlog.domain.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record SessionResponse(
        Long id,
        LocalDate playedOn,
        OffsetDateTime startedAt,
        RoleType roleType,
        Rank startRank,
        Rank rank,
        int rpChange,
        ConditionLevel conditionLevel,
        String memo,
        long wins,
        long losses,
        long draws,
        List<MatchOut> matches
) {
    public record MatchOut(int seq, MatchResult result, int rpDelta, Rank rank) {
    }

    public static SessionResponse from(Session s) {
        return new SessionResponse(
                s.getId(),
                s.getPlayedOn(),
                s.getStartedAt(),
                s.getRoleType(),
                Rank.of(s.getStartRankValue()),
                Rank.of(s.getRankValue()),
                s.rpChange(),
                s.getConditionLevel(),
                s.getMemo(),
                s.countOf(MatchResult.WIN),
                s.countOf(MatchResult.LOSS),
                s.countOf(MatchResult.DRAW),
                s.getMatches().stream()
                        .map(m -> new MatchOut(m.getSeq(), m.getResult(), m.getRpDelta(),
                                Rank.of(m.getRankValueAfter())))
                        .toList()
        );
    }
}
