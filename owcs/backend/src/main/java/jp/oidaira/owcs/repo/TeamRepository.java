package jp.oidaira.owcs.repo;

import java.util.List;
import jp.oidaira.owcs.domain.Team;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TeamRepository extends JpaRepository<Team, Integer> {

    /** ロゴ画像の URL を持っているが、まだ自前で保持していないチーム。 */
    @Query("""
            select t from Team t
            where t.imageUrl is not null
              and t.id not in (select l.teamId from TeamLogo l)
            """)
    List<Team> findMissingLogos(Pageable pageable);
}
