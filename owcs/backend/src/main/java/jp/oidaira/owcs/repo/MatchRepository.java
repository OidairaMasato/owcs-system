package jp.oidaira.owcs.repo;

import java.util.List;
import jp.oidaira.owcs.domain.Match;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchRepository extends JpaRepository<Match, Integer> {

    /** 指定シリーズの全試合。開始時刻の昇順。 */
    @Query("""
            select m from Match m
            where m.serieId = :serieId
            order by coalesce(m.beginAt, m.scheduledAt) asc
            """)
    List<Match> findBySerie(@Param("serieId") Integer serieId);

    /**
     * これから始まる試合のシリーズ。開催中／直近のステージを特定するのに使う。
     *
     * 地域で絞るのは、取り込み方式を変える前のデータ（Midseason Championship など）が
     * DB に残っていても、画面が対象地域のステージだけを見るようにするため。
     */
    @Query("""
            select m.serieId from Match m
            where m.status = 'not_started'
              and m.serieId is not null
              and lower(m.serieName) like lower(concat('%', :keyword, '%'))
            order by coalesce(m.beginAt, m.scheduledAt) asc
            """)
    List<Integer> findUpcomingSerieIds(@Param("keyword") String keyword, Pageable pageable);

    /** 直近に行われた試合のシリーズ。予定が無いときの代替。 */
    @Query("""
            select m.serieId from Match m
            where m.serieId is not null
              and lower(m.serieName) like lower(concat('%', :keyword, '%'))
            order by coalesce(m.beginAt, m.scheduledAt) desc
            """)
    List<Integer> findRecentSerieIds(@Param("keyword") String keyword, Pageable pageable);

    /** マップ単位の結果が未取得の終了試合。詳細同期の対象。 */
    @Query("""
            select m from Match m
            where m.status = 'finished' and m.gamesSynced = false
            order by coalesce(m.beginAt, m.scheduledAt) desc
            """)
    List<Match> findNeedingGameDetail(Pageable pageable);
}
