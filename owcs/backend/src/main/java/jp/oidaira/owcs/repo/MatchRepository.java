package jp.oidaira.owcs.repo;

import java.util.List;
import jp.oidaira.owcs.domain.Match;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchRepository extends JpaRepository<Match, Integer> {

    /** 予定（開始時刻の昇順）。 */
    @Query("""
            select m from Match m
            where (m.teamAId = :teamId or m.teamBId = :teamId)
              and m.status = 'not_started'
            order by coalesce(m.beginAt, m.scheduledAt) asc
            """)
    List<Match> findUpcoming(@Param("teamId") int teamId, Pageable pageable);

    /** 進行中。あればダッシュボードの主役を差し替える。 */
    @Query("""
            select m from Match m
            where (m.teamAId = :teamId or m.teamBId = :teamId)
              and m.status = 'running'
            order by coalesce(m.beginAt, m.scheduledAt) asc
            """)
    List<Match> findRunning(@Param("teamId") int teamId);

    /** 直近の結果（開始時刻の降順）。 */
    @Query("""
            select m from Match m
            where (m.teamAId = :teamId or m.teamBId = :teamId)
              and m.status = 'finished'
            order by coalesce(m.beginAt, m.scheduledAt) desc
            """)
    List<Match> findRecentFinished(@Param("teamId") int teamId, Pageable pageable);

    /** マップ単位の結果が未取得の終了試合。詳細同期の対象。 */
    @Query("""
            select m from Match m
            where m.status = 'finished' and m.gamesSynced = false
            order by coalesce(m.beginAt, m.scheduledAt) desc
            """)
    List<Match> findNeedingGameDetail(Pageable pageable);
}
