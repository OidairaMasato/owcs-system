import { useState } from "react";
import type { Perspective } from "../derive";
import type { TeamView } from "../types";
import { formatDateTime, formatLength } from "../format";
import FormStrip from "./FormStrip";

type Props = {
  results: Perspective[];
  teams: Map<number, TeamView>;
  serieName: string | null;
};

/** 直近の結果。タップでマップ別の内訳を開く。 */
export default function ResultList({ results, teams, serieName }: Props) {
  const [openId, setOpenId] = useState<number | null>(null);

  if (results.length === 0) return null;

  return (
    <section className="block">
      <h2 className="block-title">RECENT FORM</h2>
      <FormStrip results={results} />

      <ul className="recent-list">
        {results.map((p) => {
          const m = p.match;
          const open = openId === m.id;
          const opponent = p.opponentId != null ? teams.get(p.opponentId) : undefined;
          return (
            <li key={m.id}>
              <button
                className={`recent-row${p.won ? " win" : " lose"}`}
                onClick={() => setOpenId(open ? null : m.id)}
                aria-expanded={open}
              >
                <span className="wl">{p.won ? "W" : "L"}</span>
                <span className="score">
                  {p.scoreUs ?? "-"}–{p.scoreThem ?? "-"}
                </span>
                <span className="opp">{opponent?.shortName ?? "TBD"}</span>
                <span className="when">{formatDateTime(m.startsAt)}</span>
                <span className="chevron">{open ? "－" : "＋"}</span>
              </button>

              {open && (
                <div className="maps">
                  <p className="maps-serie">
                    {serieName}
                    {m.tournamentName ? ` · ${m.tournamentName}` : ""}
                  </p>
                  {p.games.length === 0 ? (
                    <p className="maps-empty">マップ別の記録がありません</p>
                  ) : (
                    <ol className="maps-list">
                      {p.games.map((g) => (
                        <li key={g.gameNo} className={g.won ? "map win" : "map lose"}>
                          <span className="map-no">MAP {g.gameNo}</span>
                          <span className="map-wl">{g.won ? "WIN" : "LOSE"}</span>
                          <span className="map-len">{formatLength(g.lengthSec)}</span>
                        </li>
                      ))}
                    </ol>
                  )}
                </div>
              )}
            </li>
          );
        })}
      </ul>
    </section>
  );
}
