import type { Perspective } from "../derive";
import type { TeamView } from "../types";
import { countdownParts, formatDateTime, streamLabel } from "../format";
import TeamBadge from "./TeamBadge";

type Props = {
  slice: { live: Perspective | null; next: Perspective | null };
  me: TeamView | undefined;
  teams: Map<number, TeamView>;
  now: number;
  serieName: string | null;
  notice: string | null;
};

/**
 * チームタブの主役。
 * 「いつ・誰と・どこで見るか」に答えることだけを担当する。
 * この画面で一番知りたいのは残り時間なので、そこを最大の文字にしている。
 */
export default function NextMatchCard({ slice, me, teams, now, serieName, notice }: Props) {
  const live = slice.live != null;
  const p = slice.live ?? slice.next;

  if (!p) {
    return (
      <section className="next next-empty">
        <p className="empty-title">NO UPCOMING MATCH</p>
        <p className="empty-sub">{notice ?? "日程が発表されると自動で表示されます"}</p>
      </section>
    );
  }

  const m = p.match;
  const opponent = p.opponentId != null ? teams.get(p.opponentId) : undefined;
  const parts = countdownParts(m.startsAt, now);
  const meta = [serieName, m.tournamentName, m.bestOf ? `BO${m.bestOf}` : null]
    .filter(Boolean)
    .join(" · ");

  return (
    <section className={`next${live ? " is-live" : ""}`}>
      {live ? (
        <div className="next-label live">
          <span className="live-dot" />
          LIVE NOW
        </div>
      ) : (
        <div className="next-label">NEXT MATCH IN</div>
      )}

      {live ? (
        <p className="countdown-live">
          {p.scoreUs ?? 0} – {p.scoreThem ?? 0}
        </p>
      ) : (
        <p className="countdown">
          {parts.length === 0 ? (
            <span className="cd-soon">まもなく開始</span>
          ) : (
            parts.map((x) => (
              <span key={x.u}>
                {x.v}
                <small>{x.u}</small>
              </span>
            ))
          )}
        </p>
      )}

      <p className="next-when">{formatDateTime(m.startsAt)}</p>
      <p className="next-meta">{meta}</p>

      <div className="next-teams">
        <div className="side">
          <TeamBadge team={me ?? null} size="md" />
          <b>{me?.shortName ?? "-"}</b>
        </div>
        <span className="vsx">VS</span>
        <div className="side right">
          <b>{opponent?.shortName ?? "TBD"}</b>
          <TeamBadge team={opponent ?? null} size="md" />
        </div>
      </div>

      {m.streamUrl ? (
        <a className="watch" href={m.streamUrl} target="_blank" rel="noreferrer">
          {streamLabel(m.streamUrl)}
        </a>
      ) : (
        <p className="no-stream">配信リンクは試合が近づくと表示されます</p>
      )}
    </section>
  );
}
