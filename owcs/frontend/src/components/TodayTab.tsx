import { useCallback, useEffect, useState } from "react";
import type { Today } from "../types";
import { teamMap } from "../derive";
import { formatDay, formatTime, streamLabel } from "../format";
import { fetchToday } from "../api";
import { hasLiveMatch, useLiveRefresh } from "../live";

/**
 * 大会を選ばずに「いま OWCS で何があるか」を見るタブ。
 * リーグタブは 1 大会の中しか見えないので、地域をまたいだ今日の全体像はここでしか分からない。
 */
export default function TodayTab() {
  const [data, setData] = useState<Today | null>(null);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(() => {
    fetchToday()
      .then((d) => {
        setData(d);
        setError(null);
      })
      .catch((e: Error) => setError(e.message));
  }, []);

  useEffect(() => {
    reload();
  }, [reload]);

  // 試合中は 60 秒ごとに読み直す。地域をまたぐので、ここが一番 LIVE に当たりやすい
  useLiveRefresh(hasLiveMatch(data?.matches ?? [], Date.now()), reload);

  if (error) return <p className="error">読み込めませんでした: {error}</p>;
  if (!data) return <p className="loading">読み込み中…</p>;

  if (data.matches.length === 0) {
    return (
      <section className="next next-empty">
        <p className="empty-title">NO MATCHES</p>
        <p className="empty-sub">この前後に予定されている試合はありません</p>
      </section>
    );
  }

  const teams = teamMap(data.teams);

  // 日付ごとにまとめる（大会をまたぐので、大会名は各行に出す）
  const days = new Map<string, typeof data.matches>();
  for (const m of data.matches) {
    const d = m.startsAt ? new Date(m.startsAt) : null;
    const key = d ? `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}` : "tbd";
    const list = days.get(key) ?? [];
    list.push(m);
    days.set(key, list);
  }

  return (
    <div>
      {[...days.entries()].map(([key, list]) => {
        const first = list[0].startsAt ? new Date(list[0].startsAt) : null;
        return (
          <section className="block" key={key}>
            <h2 className="block-title">{formatDay(first)}</h2>
            <ul className="today-list">
              {list.map((m) => {
                const a = m.teamAId != null ? teams.get(m.teamAId) : undefined;
                const b = m.teamBId != null ? teams.get(m.teamBId) : undefined;
                const live = m.status === "running";
                const finished = m.status === "finished";
                const winA = finished && m.winnerId != null && m.winnerId === m.teamAId;
                const winB = finished && m.winnerId != null && m.winnerId === m.teamBId;
                return (
                  <li key={m.id} className={live ? "live" : undefined}>
                    <div className="t-head">
                      <span className="t-time">{live ? "LIVE" : formatTime(m.startsAt)}</span>
                      <span className="t-serie">{m.serieName}</span>
                    </div>
                    <div className="t-body">
                      <span className={`mteam a${winA ? " win" : ""}`}>{a?.shortName ?? "TBD"}</span>
                      <span className="mscore">
                        {finished || live ? `${m.scoreA ?? 0}–${m.scoreB ?? 0}` : "vs"}
                      </span>
                      <span className={`mteam b${winB ? " win" : ""}`}>{b?.shortName ?? "TBD"}</span>
                      {!finished && m.streamUrl ? (
                        <a
                          className="mwatch"
                          href={m.streamUrl}
                          target="_blank"
                          rel="noreferrer"
                          title={streamLabel(m.streamUrl)}
                        >
                          ▶
                        </a>
                      ) : (
                        <span className="mwatch empty" />
                      )}
                    </div>
                  </li>
                );
              })}
            </ul>
          </section>
        );
      })}
    </div>
  );
}
