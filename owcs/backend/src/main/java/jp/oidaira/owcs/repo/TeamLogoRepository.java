package jp.oidaira.owcs.repo;

import java.util.List;
import jp.oidaira.owcs.domain.TeamLogo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TeamLogoRepository extends JpaRepository<TeamLogo, Integer> {

    /** 保持しているロゴのチーム id。画面に出す URL を組み立てるのに使う。 */
    @Query("select l.teamId from TeamLogo l")
    List<Integer> findAllTeamIds();
}
