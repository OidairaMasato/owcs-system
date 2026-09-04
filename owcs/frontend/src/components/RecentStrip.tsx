import { useState } from "react";
import type { MatchView } from "../types";
import { formatDateTime, formatLength } from "../format";
import FormStrip from "./FormStrip";

type Props = { matches: MatchView[] };

/** 直近の結果。タップでマップ別の内訳を開く。 */
export default function RecentStrip({ matches }: Props) {
  const [openId, setOpenId] = useState<number | null>(null);

  if (matches.length === 0) return null;

  return (
    <section className="block">
      <h2 className="block-title">RECENT FORM</h2>
      <FormStrip matches={matches} />

      <ul className="recent-list">
        {matches.map((m) => {
          const open = openId === m.id;
          return (
            <li key={m.id}>
              <button
                className={`recent-row${m.won ? " win" : " lose"}`}
                onClick={() => setOpenId(open ? null : m.id)}
                aria-expanded={open}
              >
                <span className="wl">{m.won ? "W" : "L"}</span>
                <span className="score">
                  {m.scoreUs ?? "-"}–{m.scoreThem ?? "-"}
                </span>
                <span className="opp">{m.opponent?.shortName ?? "TBD"}</span>
                <span className="when">{formatDateTime(m.startsAt)}</span>
                <span className="chevron">{open ? "－" : "＋"}</span>
              </button>

              {open && (
                <div className="maps">
                  <p className="maps-serie">
                    {m.serieName}
                    {m.tournamentName ? ` · ${m.tournamentName}` : ""}
                  </p>
                  {m.games.length === 0 ? (
                    <p className="maps-empty">マップ別の記録がありません</p>
                  ) : (
                    <ol className="maps-list">
                      {m.games.map((g) => (
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
