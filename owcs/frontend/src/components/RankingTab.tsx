import { useEffect, useState } from "react";
import type { Rankings } from "../types";
import { fetchRankings } from "../api";
import TeamBadge from "./TeamBadge";

/**
 * 年間ランキング。強さの指標は Elo レーティング。
 *
 * 勝率で並べると対戦相手の強さが無視され、
 * 弱い地域で勝ち続けたチームが上位に来てしまう。
 * Elo なら強い相手に勝つほど大きく上がるので、
 * 国際大会を経由して地域をまたいだ比較ができる。
 */
export default function RankingTab() {
  const [data, setData] = useState<Rankings | null>(null);
  const [year, setYear] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchRankings(year)
      .then((d) => {
        setData(d);
        setError(null);
      })
      .catch((e: Error) => setError(e.message));
  }, [year]);

  if (error) return <p className="error">読み込めませんでした: {error}</p>;
  if (!data) return <p className="loading">読み込み中…</p>;

  if (data.rows.length === 0) {
    return (
      <section className="next next-empty">
        <p className="empty-title">NO DATA</p>
        <p className="empty-sub">集計できる試合がありません</p>
      </section>
    );
  }

  const matches = Math.round(data.rows.reduce((n, r) => n + r.played, 0) / 2);
  const hasProvisional = data.rows.some((r) => r.provisional);

  return (
    <div>
      {data.years.length > 1 && (
        <nav className="year-picker">
          {data.years.map((y) => (
            <button key={y} className={y === data.year ? "on" : ""} onClick={() => setYear(y)}>
              {y}
            </button>
          ))}
        </nav>
      )}

      <section className="block">
        <h2 className="block-title">
          POWER RANKING {data.year}
          <span className="block-sub">{matches} 試合 · 取り込み済みの全大会</span>
        </h2>

        <table className="standings ranking">
          <thead>
            <tr>
              <th className="col-rank">#</th>
              <th className="col-team">TEAM</th>
              <th className="col-rating">RATING</th>
              <th className="col-num">W</th>
              <th className="col-num">L</th>
            </tr>
          </thead>
          <tbody>
            {data.rows.map((r, i) => (
              <tr key={r.team.id} className={r.provisional ? "dim" : undefined}>
                <td className="col-rank">{i + 1}</td>
                <td className="col-team">
                  <TeamBadge team={r.team} size="sm" />
                  <span className="rk-name">{r.team.shortName}</span>
                </td>
                <td className="col-rating">
                  {r.rating}
                  {r.provisional ? "*" : ""}
                </td>
                <td className="col-num">{r.wins}</td>
                <td className="col-num">{r.losses}</td>
              </tr>
            ))}
          </tbody>
        </table>

        <p className="rk-note">
          レーティングは Elo。全チーム 1500 から始め、強い相手に勝つほど大きく上がる。
          前年までの結果を引き継ぐので、地域をまたいだ比較ができる。
          {hasProvisional && "　* はその年の試合数が 5 未満で、まだ当てにならない。"}
        </p>
      </section>
    </div>
  );
}
