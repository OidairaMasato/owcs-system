package jp.oidaira.owlog.repository;

import jp.oidaira.owlog.domain.Session;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {

    /** 1日に複数セッションがあるので、日付ではなく開始時刻で並べる。 */
    @EntityGraph(attributePaths = "matches")
    List<Session> findByUserIdOrderByStartedAtDescIdDesc(Long userId);

    Optional<Session> findFirstByUserIdOrderByStartedAtDescIdDesc(Long userId);

    @EntityGraph(attributePaths = "matches")
    List<Session> findByUserIdOrderByStartedAtAscIdAsc(Long userId);
}
