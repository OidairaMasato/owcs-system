import type { StandingsView } from "../types";

type Props = { standings: StandingsView | null };

/**
 * いま参加しているシリーズの順位表。
 * PandaScore の rank は同率があるので連番ではない（1, 1, 3, 3, 5 ...）。
 * 自分の 2 つ隣までを通常の濃さで出し、それより遠い行は沈める。
 */
export default function StandingsTable({ standings }: Props) {
  if (!standings || standings.rows.length === 0) return null;

  const myIndex = standings.rows.findIndex((r) => r.me);

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
            const far = myIndex >= 0 && Math.abs(i - myIndex) > 2;
            return (
              <tr key={r.team.id} className={`${r.me ? "me" : ""}${far ? " dim" : ""}`}>
                <td className="col-rank">{r.rankNo ?? "-"}</td>
                <td className="col-team">{r.team.shortName}</td>
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
