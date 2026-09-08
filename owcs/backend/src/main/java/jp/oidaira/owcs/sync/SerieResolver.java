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
 * 「いま追うべき大会（シリーズ）」を決める。
 *
 * OWCS のシリーズは 地域 × ステージ（"Korea Stage 3 2026"）と
 * 国際大会（"Midseason Championship 2026", "World Finals 2025"）が混在する。
 * 地域名で絞ると国際大会が漏れるので、開催期間で絞る。
 *
 * 「hot」は開催中と、その前後わずかの大会。ここだけ毎回同期する。
 * 終わった大会は結果が変わらないので、初回に取り込んだあとは放置してよい。
 *
 * シリーズ一覧はめったに変わらないので、しばらくメモリに持つ。
 */
@Component
public class SerieResolver {

    private static final Logger log = LoggerFactory.getLogger(SerieResolver.class);
    private static final Duration TTL = Duration.ofHours(6);

    /** 1 件のシリーズ。 */
    public record SerieInfo(int id, String name, OffsetDateTime beginAt, OffsetDateTime endAt) {

        /**
         * まだ始まっていない大会か。
         *
         * PandaScore は大会の枠を先に作り、試合を後から入れてくることがある。
         * 開始前の大会は「一度取ったから終わり」にしてはいけない。
         */
        boolean isUpcoming(Instant now) {
            return beginAt == null || beginAt.toInstant().isAfter(now);
        }

        /** 開催中か、その前後 margin 日以内か。 */
        boolean isHot(Instant now, int marginDays) {
            Duration margin = Duration.ofDays(marginDays);
            Instant from = beginAt != null ? beginAt.toInstant().minus(margin) : Instant.MIN;
            Instant to = endAt != null ? endAt.toInstant().plus(margin) : Instant.MAX;
            return !now.isBefore(from) && !now.isAfter(to);
        }
    }

    private final PandaScoreClient client;
    private final OwcsProperties props;

    private volatile List<SerieInfo> cached = List.of();
    private volatile Instant fetchedAt = Instant.EPOCH;

    public SerieResolver(PandaScoreClient client, OwcsProperties props) {
        this.client = client;
        this.props = props;
    }

    /** 取り込み対象のシリーズ。新しい順。 */
    public List<SerieInfo> targetSeries() {
        if (!cached.isEmpty() && Instant.now().isBefore(fetchedAt.plus(TTL))) {
            return cached;
        }
        List<Ps.Serie> all = client.series(props.leagueId(), 100);
        if (all == null) return cached;

        OwcsProperties.Series cfg = props.series();
        Instant now = Instant.now();
        Instant oldest = now.minus(Duration.ofDays(cfg.pastDays()));
        Instant newest = now.plus(Duration.ofDays(cfg.futureDays()));

        List<SerieInfo> picked = all.stream()
                .filter(s -> s.id() != null)
                .map(s -> new SerieInfo(s.id(), s.label(), s.beginAt(), s.endAt()))
                .filter(s -> inWindow(s, oldest, newest))
                .sorted(Comparator.comparing(SerieInfo::beginAt,
                        Comparator.nullsLast(Comparator.<OffsetDateTime>reverseOrder())))
                .limit(cfg.max())
                .toList();

        if (!picked.isEmpty()) {
            cached = picked;
            fetchedAt = Instant.now();
            log.info("target series resolved: {}", picked.stream().map(SerieInfo::name).toList());
        }
        return cached;
    }

    /** 開催中・直近の大会だけ。毎回の同期対象。 */
    public List<SerieInfo> hotSeries() {
        Instant now = Instant.now();
        int margin = props.series().hotMarginDays();
        return targetSeries().stream().filter(s -> s.isHot(now, margin)).toList();
    }

    private static boolean inWindow(SerieInfo s, Instant oldest, Instant newest) {
        // 終了済みでも oldest より新しければ残す。開始が newest より先なら除く。
        if (s.endAt() != null && s.endAt().toInstant().isBefore(oldest)) return false;
        if (s.beginAt() != null && s.beginAt().toInstant().isAfter(newest)) return false;
        return true;
    }

    /** シリーズ構成が変わったときに手動で捨てるため。 */
    public void invalidate() {
        fetchedAt = Instant.EPOCH;
    }
}
