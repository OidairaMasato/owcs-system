import { useEffect, useMemo, useState } from "react";
import RankChart from "./RankChart";
import { api } from "./api";
import { ROLE_LABEL, type RankPoint, type RoleType } from "./types";

interface Props {
  /** 保存のたびに増える。再取得のトリガー。 */
  version: number;
}

export default function TrendView({ version }: Props) {
  const [points, setPoints] = useState<RankPoint[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [role, setRole] = useState<RoleType | null>(null);

  useEffect(() => {
    setError(null);
    api
      .rankHistory()
      .then(setPoints)
      .catch(() => setError("推移を取得できませんでした。"));
  }, [version]);

  /** 記録数がいちばん多いロールを初期表示にする。 */
  const roles = useMemo(() => {
    if (!points) return [];
    const count = new Map<RoleType, number>();
    points.forEach((p) => count.set(p.roleType, (count.get(p.roleType) ?? 0) + 1));
    return [...count.entries()].sort((a, b) => b[1] - a[1]).map(([r]) => r);
  }, [points]);

  useEffect(() => {
    if (!role && roles.length) setRole(roles[0]);
  }, [roles, role]);

  const series = useMemo(
    () => (points && role ? points.filter((p) => p.roleType === role) : []),
    [points, role],
  );

  if (error) return <p className="status error">{error}</p>;
  if (!points) return <p className="status">読み込み中…</p>;
  if (points.length === 0) {
    return <p className="status">まだ記録がありません。1件目を保存すると、ここに推移が出ます。</p>;
  }

  const latest = series[series.length - 1];
  const delta = series.length > 1 ? latest.rankValue - series[0].rankValue : null;

  return (
    <div className="screen">
      {roles.length > 1 && (
        <div className="seg filter">
          {roles.map((r) => (
            <button key={r} aria-pressed={role === r} onClick={() => setRole(r)}>
              {ROLE_LABEL[r]}
            </button>
          ))}
        </div>
      )}

      {latest && (
        <div className="hero">
          <div>
            <div className="now">現在ランク</div>
            <div className="val">
              {latest.rankLabel}
              <i>{latest.rp} RP</i>
            </div>
          </div>
          {delta !== null && (
            <div className={`delta ${delta >= 0 ? "up" : "down"}`}>
              <b>{delta >= 0 ? "+" : ""}{delta} RP</b>
              <small>この期間</small>
            </div>
          )}
        </div>
      )}

      <figure>
        <figcaption>
          ランク推移 — {role ? ROLE_LABEL[role] : ""}・{series.length}セッション
        </figcaption>
        {series.length >= 2 ? (
          <RankChart points={series} />
        ) : (
          <p className="status">記録が2件たまるとグラフになります。</p>
        )}
      </figure>

      <div className="label">
        <h3>SESSIONS</h3>
        <span className="hint">新しい順</span>
      </div>
      <ul className="slist">
        {[...series].reverse().slice(0, 12).map((p) => (
          <li key={p.sessionId}>
            <span className="date">{md(p.playedOn)}</span>
            <span className="wl">
              <b className="w">{p.wins}</b>勝<b className="l">{p.losses}</b>敗
              <i className={p.rpChange >= 0 ? "up" : "down"}>
                {p.rpChange >= 0 ? "+" : ""}{p.rpChange}
              </i>
            </span>
            <span className="rk">
              <b>{p.rankLabel}</b> {p.rp}RP
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

const md = (iso: string) => {
  const [, m, d] = iso.split("-");
  return `${Number(m)}/${d}`;
};
