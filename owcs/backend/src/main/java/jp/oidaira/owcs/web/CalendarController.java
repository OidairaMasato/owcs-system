package jp.oidaira.owcs.web;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import jp.oidaira.owcs.domain.Match;
import jp.oidaira.owcs.domain.Team;
import jp.oidaira.owcs.repo.MatchRepository;
import jp.oidaira.owcs.repo.TeamRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * チームの試合日程を iCalendar (.ics) で配信する。
 *
 * カレンダーアプリに「購読」として登録してもらうと、
 * 日程が追加・変更されたときにアプリ側が自動で取り直す。
 * 通知は OS のカレンダーに任せられるので、こちらで Push を実装しなくても
 * 「見逃さない」という目的を満たせる。
 *
 * /api/ 配下に置いていないのは、カレンダーアプリが定期的に取得するため
 * レート制限の対象から外すため。
 */
@RestController
public class CalendarController {

    /** 試合の所要時間の目安。BO5 で 2 時間、BO7 で 2.5 時間程度。 */
    private static final Duration DEFAULT_LENGTH = Duration.ofHours(2);

    /** 過去はこの日数ぶんだけ載せる。カレンダーを過去で埋め尽くさない。
     *  オフシーズンでも直近シーズンの結果が残るよう、1シーズン分は確保する。 */
    private static final int PAST_DAYS = 90;

    private static final DateTimeFormatter UTC =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final MatchRepository matchRepo;
    private final TeamRepository teamRepo;

    public CalendarController(MatchRepository matchRepo, TeamRepository teamRepo) {
        this.matchRepo = matchRepo;
        this.teamRepo = teamRepo;
    }

    @GetMapping(value = "/calendar/team-{teamId}.ics", produces = "text/calendar;charset=UTF-8")
    @Transactional(readOnly = true)
    public ResponseEntity<String> teamCalendar(@PathVariable int teamId) {
        Team team = teamRepo.findById(teamId).orElse(null);
        if (team == null) return ResponseEntity.notFound().build();

        OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusDays(PAST_DAYS);
        List<Match> matches = matchRepo.findForTeamSince(teamId, since).stream()
                .filter(m -> m.startsAt() != null)
                .toList();

        Map<Integer, Team> teams = new java.util.HashMap<>();
        teamRepo.findAll().forEach(t -> teams.put(t.getId(), t));

        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n")
                .append("VERSION:2.0\r\n")
                .append("PRODID:-//owcs-korea//dashboard//JA\r\n")
                .append("CALSCALE:GREGORIAN\r\n")
                .append("METHOD:PUBLISH\r\n")
                .append("X-WR-CALNAME:").append(escape(team.shortName() + " — OWCS")).append("\r\n")
                .append("X-WR-TIMEZONE:Asia/Tokyo\r\n")
                // 購読側にどれくらいの間隔で取り直すかを伝える
                .append("REFRESH-INTERVAL;VALUE=DURATION:PT1H\r\n")
                .append("X-PUBLISHED-TTL:PT1H\r\n");

        String stamp = OffsetDateTime.now(ZoneOffset.UTC).format(UTC);
        for (Match m : matches) {
            Integer oppId = m.opponentOf(teamId).orElse(null);
            Team opp = oppId != null ? teams.get(oppId) : null;
            String oppName = opp != null ? opp.shortName() : "TBD";

            OffsetDateTime start = m.startsAt().withOffsetSameInstant(ZoneOffset.UTC);
            OffsetDateTime end = m.getEndAt() != null
                    ? m.getEndAt().withOffsetSameInstant(ZoneOffset.UTC)
                    : start.plus(DEFAULT_LENGTH);

            String summary = team.shortName() + " vs " + oppName;
            if (Match.FINISHED.equals(m.getStatus())) {
                Short us = m.scoreOf(teamId);
                Short them = m.scoreAgainst(teamId);
                if (us != null && them != null) {
                    summary += "  " + us + "-" + them;
                }
            }

            sb.append("BEGIN:VEVENT\r\n")
                    .append("UID:owcs-match-").append(m.getId()).append("@owcs-korea\r\n")
                    .append("DTSTAMP:").append(stamp).append("\r\n")
                    .append("DTSTART:").append(start.format(UTC)).append("\r\n")
                    .append("DTEND:").append(end.format(UTC)).append("\r\n")
                    .append("SUMMARY:").append(escape(summary)).append("\r\n")
                    .append("DESCRIPTION:").append(escape(description(m))).append("\r\n");

            m.preferredStream().ifPresent(s ->
                    sb.append("URL:").append(escape(s.getRawUrl())).append("\r\n"));

            // 終わった試合に通知は不要
            if (!Match.FINISHED.equals(m.getStatus())) {
                sb.append("BEGIN:VALARM\r\n")
                        .append("TRIGGER:-PT15M\r\n")
                        .append("ACTION:DISPLAY\r\n")
                        .append("DESCRIPTION:").append(escape(summary)).append("\r\n")
                        .append("END:VALARM\r\n");
            }
            sb.append("END:VEVENT\r\n");
        }
        sb.append("END:VCALENDAR\r\n");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(30)).cachePublic())
                .body(sb.toString());
    }

    private static String description(Match m) {
        StringBuilder d = new StringBuilder();
        if (m.getSerieName() != null) d.append(m.getSerieName());
        if (m.getTournamentName() != null) d.append(" / ").append(m.getTournamentName());
        if (m.getNumberOfGames() != null) d.append(" / BO").append(m.getNumberOfGames());
        m.preferredStream().ifPresent(s -> d.append("\n").append(s.getRawUrl()));
        return d.toString();
    }

    /** iCalendar のテキスト値のエスケープ。 */
    private static String escape(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n");
    }
}
