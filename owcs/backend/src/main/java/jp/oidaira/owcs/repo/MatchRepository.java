package jp.oidaira.owcs.repo;

import java.time.OffsetDateTime;
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

    /** 期間内の全試合（大会をまたぐ）。「今日の試合」に使う。 */
    @Query("""
            select m from Match m
            where coalesce(m.beginAt, m.scheduledAt) >= :from
              and coalesce(m.beginAt, m.scheduledAt) < :to
            order by coalesce(m.beginAt, m.scheduledAt) asc
            """)
    List<Match> findBetween(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    /** 指定チームの終了した全試合（大会をまたぐ）。対戦相手別の通算成績に使う。 */
    @Query("""
            select m from Match m
            where m.status = 'finished'
              and (m.teamAId = :teamId or m.teamBId = :teamId)
            order by coalesce(m.beginAt, m.scheduledAt) asc
            """)
    List<Match> findFinishedForTeam(@Param("teamId") Integer teamId);

    /** 指定チームの全試合（状態を問わない・大会をまたぐ）。カレンダー配信に使う。 */
    @Query("""
            select m from Match m
            where (m.teamAId = :teamId or m.teamBId = :teamId)
              and coalesce(m.beginAt, m.scheduledAt) >= :since
            order by coalesce(m.beginAt, m.scheduledAt) asc
            """)
    List<Match> findForTeamSince(@Param("teamId") Integer teamId,
                                 @Param("since") OffsetDateTime since);

    /**
     * 「いま試合中かもしれない」時間帯にある試合の数。
     * 0 でなければ取り込み間隔を短くする（LIVE スコア用）。
     *
     * status だけで判定してはいけない。取り込みが止まっている間は
     * 実際には始まっている試合も not_started のままだからである。
     * 開始予定時刻の窓で見る。
     */
    @Query("""
            select count(m) from Match m
            where m.status in ('not_started', 'running')
              and coalesce(m.beginAt, m.scheduledAt) >= :from
              and coalesce(m.beginAt, m.scheduledAt) <= :to
            """)
    long countInPlayWindow(@Param("from") OffsetDateTime from,
                           @Param("to") OffsetDateTime to);

    /** 終了した全試合（大会をまたぐ）。通算ランキングの集計に使う。 */
    @Query("""
            select m from Match m
            where m.status = 'finished'
            order by coalesce(m.beginAt, m.scheduledAt) asc
            """)
    List<Match> findAllFinished();

    /** マップ単位の結果が未取得の終了試合。詳細同期の対象。 */
    @Query("""
            select m from Match m
            where m.status = 'finished' and m.gamesSynced = false
            order by coalesce(m.beginAt, m.scheduledAt) desc
            """)
    List<Match> findNeedingGameDetail(Pageable pageable);
}
