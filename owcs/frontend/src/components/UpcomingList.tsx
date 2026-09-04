import type { MatchView } from "../types";
import { formatDateTime } from "../format";

type Props = { matches: MatchView[] };

/** 次の試合カードに出していない、それ以降の予定。 */
export default function UpcomingList({ matches }: Props) {
  if (matches.length === 0) return null;

  return (
    <section className="block">
      <h2 className="block-title">SCHEDULE</h2>
      <ul className="upcoming-list">
        {matches.map((m) => (
          <li key={m.id}>
            <span className="up-when">{formatDateTime(m.startsAt)}</span>
            <span className="up-opp">vs {m.opponent?.shortName ?? "TBD"}</span>
            <span className="up-serie">{m.serieName ?? ""}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}
