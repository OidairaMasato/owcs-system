import type { MatchView, TeamView } from "../types";
import { countdownParts, formatDateTime, streamLabel } from "../format";
import TeamBadge from "./TeamBadge";

type Props = {
  match: MatchView | null;
  me: TeamView;
  now: number;
  live: boolean;
  notice: string | null;
};

/**
 * ダッシュボードの主役。
 * 「いつ・誰と・どこで見るか」に答えることだけを担当する。
 * この画面で一番知りたいのは残り時間なので、そこを最大の文字にしている。
 */
export default function NextMatchCard({ match, me, now, live, notice }: Props) {
  if (!match) {
    return (
      <section className="next next-empty">
        <p className="empty-title">NO UPCOMING MATCH</p>
        <p className="empty-sub">{notice ?? "日程が発表されると自動で表示されます"}</p>
      </section>
    );
  }

  const parts = countdownParts(match.startsAt, now);
  const meta = [match.serieName, match.tournamentName, match.bestOf ? `BO${match.bestOf}` : null]
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
        <p className="countdown-live">試合中</p>
      ) : (
        <p className="countdown">
          {parts.length === 0 ? (
            <span className="cd-soon">まもなく開始</span>
          ) : (
            parts.map((p) => (
              <span key={p.u}>
                {p.v}
                <small>{p.u}</small>
              </span>
            ))
          )}
        </p>
      )}

      <p className="next-when">{formatDateTime(match.startsAt)}</p>
      <p className="next-meta">{meta}</p>

      <div className="next-teams">
        <div className="side">
          <TeamBadge team={me} size="md" />
          <b>{me.shortName}</b>
        </div>
        <span className="vsx">VS</span>
        <div className="side right">
          <b>{match.opponent?.shortName ?? "TBD"}</b>
          <TeamBadge team={match.opponent} size="md" />
        </div>
      </div>

      {match.streamUrl ? (
        <a className="watch" href={match.streamUrl} target="_blank" rel="noreferrer">
          {streamLabel(match.streamUrl)}
        </a>
      ) : (
        <p className="no-stream">配信リンクは試合が近づくと表示されます</p>
      )}
    </section>
  );
}
