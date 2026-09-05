import { useEffect, useState } from "react";
import type { HeadToHead } from "../types";
import { fetchHeadToHead } from "../api";

type Props = { teamId: number | null };

/**
 * 対戦相手別の通算成績。大会をまたいで集計している。
 * 1 大会の順位表では分からない「この相手には勝てていない」が見える。
 */
export default function HeadToHeadTable({ teamId }: Props) {
  const [data, setData] = useState<HeadToHead | null>(null);

  useEffect(() => {
    if (teamId == null) return;
    setData(null);
    fetchHeadToHead(teamId)
      .then(setData)
      .catch(() => setData(null));
  }, [teamId]);

  if (!data || data.rows.length === 0) return null;

  return (
    <section className="block">
      <h2 className="block-title">
        HEAD TO HEAD
        <span className="block-sub">
          通算 {data.wins}勝 {data.losses}敗 · 取り込み済みの全大会
        </span>
      </h2>

      <table className="standings h2h">
        <thead>
          <tr>
            <th className="col-team">OPPONENT</th>
            <th className="col-num">W</th>
            <th className="col-num">L</th>
            <th className="col-maps">MAPS</th>
          </tr>
        </thead>
        <tbody>
          {data.rows.map((r) => (
            <tr key={r.opponent.id} className={r.losses > r.wins ? "bad" : undefined}>
              <td className="col-team">{r.opponent.shortName}</td>
              <td className="col-num">{r.wins}</td>
              <td className="col-num">{r.losses}</td>
              <td className="col-maps">
                {r.mapWins}-{r.mapLosses}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
