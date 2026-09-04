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
     * 画面の大会セレクトに出す一覧。
     * [0]=serieId(Integer), [1]=serieName(String), [2]=最終試合日時(OffsetDateTime)。
     * 別テーブルを作らず、取り込んだ試合から組み立てる。
     */
    @Query("""
            select m.serieId, m.serieName, max(coalesce(m.beginAt, m.scheduledAt))
            from Match m
            where m.serieId is not null
            group by m.serieId, m.serieName
            order by max(coalesce(m.beginAt, m.scheduledAt)) desc
            """)
    List<Object[]> listSeries();

    /** これから始まる試合のシリーズ。既定で表示する大会を決めるのに使う。 */
    @Query("""
            select m.serieId from Match m
            where m.status = 'not_started' and m.serieId is not null
            order by coalesce(m.beginAt, m.scheduledAt) asc
            """)
    List<Integer> findUpcomingSerieIds(Pageable pageable);

    /** 直近に行われた試合のシリーズ。予定が無いときの代替。 */
    @Query("""
            select m.serieId from Match m
            where m.serieId is not null
            order by coalesce(m.beginAt, m.scheduledAt) desc
            """)
    List<Integer> findRecentSerieIds(Pageable pageable);

    /** マップ単位の結果が未取得の終了試合。詳細同期の対象。 */
    @Query("""
            select m from Match m
            where m.status = 'finished' and m.gamesSynced = false
            order by coalesce(m.beginAt, m.scheduledAt) desc
            """)
    List<Match> findNeedingGameDetail(Pageable pageable);
}
