package jp.oidaira.owcs.repo;

import java.util.List;
import jp.oidaira.owcs.domain.Tournament;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TournamentRepository extends JpaRepository<Tournament, Integer> {

    List<Tournament> findBySerieIdOrderByBeginAtAsc(Integer serieId);

    /** 指定シリーズで順位表を持つトーナメント。 */
    @Query("""
            select t from Tournament t
            where t.serieId = :serieId and size(t.standings) > 0
            order by t.beginAt desc
            """)
    List<Tournament> findWithStandings(@Param("serieId") Integer serieId);
}
