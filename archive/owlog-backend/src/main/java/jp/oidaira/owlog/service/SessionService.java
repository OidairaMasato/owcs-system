package jp.oidaira.owlog.service;

import jp.oidaira.owlog.config.OwlogProperties;
import jp.oidaira.owlog.domain.*;
import jp.oidaira.owlog.repository.SessionRepository;
import jp.oidaira.owlog.repository.UserRepository;
import jp.oidaira.owlog.web.dto.RankPoint;
import jp.oidaira.owlog.web.dto.SessionRequest;
import jp.oidaira.owlog.web.dto.SessionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class SessionService {

    private final SessionRepository sessions;
    private final UserRepository users;
    private final OwlogProperties props;

    public SessionService(SessionRepository sessions, UserRepository users, OwlogProperties props) {
        this.sessions = sessions;
        this.users = users;
        this.props = props;
    }

    /** v1 は単一ユーザー。認証を入れたらここを置き換える。 */
    private Long currentUserId() {
        return users.findByBattleTag(props.defaultBattleTag())
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "ユーザーが見つかりません: " + props.defaultBattleTag()));
    }

    @Transactional
    public SessionResponse record(SessionRequest req) {
        Session s = new Session();
        s.setUserId(currentUserId());
        s.setPlayedOn(req.playedOn() != null ? req.playedOn() : LocalDate.now());
        s.setStartedAt(OffsetDateTime.now());
        s.setRoleType(req.roleType());
        s.setConditionLevel(req.conditionLevel());
        s.setMemo(req.memo());
        s.startAt(RankValue.of(req.startTier(), req.startDivision(), req.startRp()));
        req.matches().forEach(m -> s.addMatch(m.result(), m.rpDelta()));
        return SessionResponse.from(sessions.save(s));
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> list() {
        return sessions.findByUserIdOrderByStartedAtDescIdDesc(currentUserId())
                .stream().map(SessionResponse::from).toList();
    }

    /** 入力画面の初期値に使う。前回の「終了ランク」が次回の「開始ランク」になる。 */
    @Transactional(readOnly = true)
    public Optional<SessionResponse> latest() {
        return sessions.findFirstByUserIdOrderByStartedAtDescIdDesc(currentUserId())
                .map(SessionResponse::from);
    }

    @Transactional(readOnly = true)
    public List<RankPoint> rankHistory() {
        return sessions.findByUserIdOrderByStartedAtAscIdAsc(currentUserId()).stream()
                .map(s -> new RankPoint(
                        s.getId(),
                        s.getPlayedOn(),
                        s.getStartedAt(),
                        s.getRoleType(),
                        s.getRankValue(),
                        RankValue.label(s.getRankValue()),
                        RankValue.rpOf(s.getRankValue()),
                        s.rpChange(),
                        s.countOf(MatchResult.WIN),
                        s.countOf(MatchResult.LOSS)))
                .toList();
    }
}
