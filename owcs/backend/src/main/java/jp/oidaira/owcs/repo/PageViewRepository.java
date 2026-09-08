package jp.oidaira.owcs.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * アクセス数の記録と読み出し。
 *
 * JPA のエンティティにしていないのは、主キーが 3 列の複合キーで、
 * かつ「1 行を読んで書き戻す」のではなく UPSERT で数えたいため。
 * ここは素の SQL の方が短く、意図もそのまま読める。
 */
@Repository
public class PageViewRepository {

    private final JdbcTemplate jdbc;

    public PageViewRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 1 回ぶん数える。同じ日・同じ流入元・同じ端末なら既存の行に足す。 */
    public void record(LocalDate day, String referrerHost, String device) {
        jdbc.update("""
                insert into page_views (day, referrer_host, device, views)
                values (?, ?, ?, 1)
                on conflict (day, referrer_host, device)
                do update set views = page_views.views + 1
                """, day, referrerHost, device);
    }

    /** 指定日以降の明細。 */
    public List<Map<String, Object>> since(LocalDate from) {
        return jdbc.queryForList("""
                select day, referrer_host, device, views
                from page_views
                where day >= ?
                order by day desc, views desc
                """, from);
    }
}
