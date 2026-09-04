package jp.oidaira.owlog.repository;

import jp.oidaira.owlog.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByBattleTag(String battleTag);
}
