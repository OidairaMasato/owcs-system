package jp.oidaira.owcs.repo;

import jp.oidaira.owcs.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Integer> {
}
