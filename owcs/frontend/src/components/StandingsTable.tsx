import type { StandingsView, TeamView } from "../types";

type Props = {
  standings: StandingsView | null;
  teams: Map<number, TeamView>;
  highlightTeamId: number | null;
  /** 選択チームから離れた順位を沈めるか。リーグ全体を見る画面では沈めない。 */
  dimFar?: boolean;
};

/**
 * 順位表。PandaScore の rank は同率があるので連番ではない（1, 1, 3, 3, 5 ...）。
 * 選択中のチームの行だけ強調し、そこから離れた順位は沈める。
 * 「推し」を特別扱いするコードは無く、選択されたチームがそう見えるだけ。
 */
export default function StandingsTable({
  standings,
  teams,
  highlightTeamId,
  dimFar = false,
}: Props) {
  if (!standings || standings.rows.length === 0) return null;

  const myIndex = standings.rows.findIndex((r) => r.teamId === highlightTeamId);

  return (
    <section className="block">
      <h2 className="block-title">
        STANDINGS
        <span className="block-sub">
          {standings.serieName}
          {standings.tournamentName ? ` · ${standings.tournamentName}` : ""}
        </span>
      </h2>

      <table className="standings">
        <thead>
          <tr>
            <th className="col-rank">#</th>
            <th className="col-team">TEAM</th>
            <th className="col-num">W</th>
            <th className="col-num">L</th>
            <th className="col-maps">MAPS</th>
          </tr>
        </thead>
        <tbody>
          {standings.rows.map((r, i) => {
            const me = r.teamId === highlightTeamId;
            const far = dimFar && myIndex >= 0 && Math.abs(i - myIndex) > 2;
            const team = teams.get(r.teamId);
            return (
              <tr key={r.teamId} className={`${me ? "me" : ""}${far ? " dim" : ""}`}>
                <td className="col-rank">{r.rankNo ?? "-"}</td>
                <td className="col-team">{team?.shortName ?? r.teamId}</td>
                <td className="col-num">{r.wins ?? "-"}</td>
                <td className="col-num">{r.losses ?? "-"}</td>
                <td className="col-maps">
                  {r.gameWins != null && r.gameLosses != null
                    ? `${r.gameWins}-${r.gameLosses}`
                    : "-"}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </section>
  );
}
