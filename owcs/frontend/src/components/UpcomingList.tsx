import type { Perspective } from "../derive";
import type { TeamView } from "../types";
import { formatDateTime } from "../format";

type Props = { items: Perspective[]; teams: Map<number, TeamView> };

/** 次の試合カードに出していない、それ以降の予定。 */
export default function UpcomingList({ items, teams }: Props) {
  if (items.length === 0) return null;

  return (
    <section className="block">
      <h2 className="block-title">SCHEDULE</h2>
      <ul className="upcoming-list">
        {items.map((p) => (
          <li key={p.match.id}>
            <span className="up-when">{formatDateTime(p.match.startsAt)}</span>
            <span className="up-opp">
              vs {p.opponentId != null ? teams.get(p.opponentId)?.shortName ?? "TBD" : "TBD"}
            </span>
            <span className="up-serie">{p.match.tournamentName ?? ""}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}
