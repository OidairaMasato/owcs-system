package jp.oidaira.owcs.sync;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import jp.oidaira.owcs.OwcsProperties;
import jp.oidaira.owcs.pandascore.PandaScoreClient;
import jp.oidaira.owcs.pandascore.Ps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 「いま追うべきシリーズ」を決める。
 *
 * OWCS のシリーズは 地域 × ステージ で分かれている（例: "Korea Stage 3 2026"）。
 * 設定で id を固定するとステージが変わるたびに手で直すことになるので、
 * リーグ配下のシリーズ一覧から地域名で絞り、新しい順に数件を対象にする。
 * 2 件にしているのは、ステージの切り替わり時期に
 * 「終わったばかりの前ステージ」と「始まる次ステージ」の両方を拾うため。
 *
 * シリーズ一覧はめったに変わらないので、しばらくメモリに持つ。
 */
@Component
public class SerieResolver {

    private static final Logger log = LoggerFactory.getLogger(SerieResolver.class);
    private static final Duration TTL = Duration.ofHours(6);

    private final PandaScoreClient client;
    private final OwcsProperties props;

    private volatile List<Integer> cached = List.of();
    private volatile Instant fetchedAt = Instant.EPOCH;

    public SerieResolver(PandaScoreClient client, OwcsProperties props) {
        this.client = client;
        this.props = props;
    }

    public List<Integer> targetSerieIds() {
        if (!cached.isEmpty() && Instant.now().isBefore(fetchedAt.plus(TTL))) {
            return cached;
        }
        List<Ps.Serie> all = client.series(props.leagueId(), 50);
        if (all == null) return cached;

        String keyword = props.regionKeyword().toLowerCase();
        List<Integer> ids = all.stream()
                .filter(s -> s.id() != null)
                .filter(s -> s.label() != null && s.label().toLowerCase().contains(keyword))
                .sorted(Comparator.comparing(Ps.Serie::beginAt,
                        Comparator.nullsLast(Comparator.<OffsetDateTime>reverseOrder())))
                .limit(props.serieCount())
                .map(Ps.Serie::id)
                .toList();

        if (!ids.isEmpty()) {
            cached = ids;
            fetchedAt = Instant.now();
            log.info("target series resolved: {}", ids);
        }
        return cached;
    }

    /** シリーズ構成が変わったときに手動で捨てるため。 */
    public void invalidate() {
        fetchedAt = Instant.EPOCH;
    }
}
