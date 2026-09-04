import type { MatchRow, TeamView } from "../types";
import { formatTime, streamLabel } from "../format";

type Props = {
  match: MatchRow;
  teams: Map<number, TeamView>;
  highlightTeamId: number | null;
};

/** リーグタブの 1 行。どちらのチームが勝ったかまで分かる形。 */
export default function MatchLine({ match, teams, highlightTeamId }: Props) {
  const a = match.teamAId != null ? teams.get(match.teamAId) : undefined;
  const b = match.teamBId != null ? teams.get(match.teamBId) : undefined;

  const finished = match.status === "finished";
  const live = match.status === "running";
  const mine =
    highlightTeamId != null &&
    (match.teamAId === highlightTeamId || match.teamBId === highlightTeamId);

  const winA = finished && match.winnerId != null && match.winnerId === match.teamAId;
  const winB = finished && match.winnerId != null && match.winnerId === match.teamBId;

  return (
    <li className={`mline${mine ? " mine" : ""}${live ? " live" : ""}`}>
      <span className="mtime">{live ? "LIVE" : formatTime(match.startsAt)}</span>

      <span className={`mteam a${winA ? " win" : ""}`}>{a?.shortName ?? "TBD"}</span>
      <span className="mscore">
        {finished || live ? `${match.scoreA ?? 0}–${match.scoreB ?? 0}` : "vs"}
      </span>
      <span className={`mteam b${winB ? " win" : ""}`}>{b?.shortName ?? "TBD"}</span>

      {!finished && match.streamUrl ? (
        <a
          className="mwatch"
          href={match.streamUrl}
          target="_blank"
          rel="noreferrer"
          title={streamLabel(match.streamUrl)}
        >
          ▶
        </a>
      ) : (
        <span className="mwatch empty" />
      )}
    </li>
  );
}
