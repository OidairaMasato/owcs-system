import { useEffect } from "react";
import type { MatchRow } from "./types";

/**
 * 試合中だけ更新間隔を詰めるための判定と仕掛け。
 *
 * 普段は 5 分ごとで十分だが、試合中はマップを 1 本取るたびにスコアが動く。
 * そこだけ 60 秒間隔にする。サーバー側も同じ考えで、
 * 試合の時間帯にあたるときだけ PandaScore への取り込み間隔を短くしている。
 */

/** 開始予定の何分前から「そろそろ始まる」とみなすか。 */
const START_MARGIN_MS = 10 * 60 * 1000;

/** 開始から何時間までを「まだやっているかもしれない」とみなすか。 */
const PLAY_LENGTH_MS = 4 * 60 * 60 * 1000;

export const LIVE_INTERVAL_MS = 60 * 1000;

/**
 * 試合中の可能性がある試合を含むか。
 *
 * status が running かどうかだけでは足りない。
 * 取り込みが止まっている間、実際には始まっている試合も
 * not_started のままなので、開始予定時刻の窓でも見る。
 */
export function hasLiveMatch(matches: MatchRow[], now: number): boolean {
  return matches.some((m) => {
    if (m.status === "running") return true;
    if (m.status !== "not_started" || !m.startsAt) return false;
    const t = new Date(m.startsAt).getTime();
    if (Number.isNaN(t)) return false;
    return now >= t - START_MARGIN_MS && now <= t + PLAY_LENGTH_MS;
  });
}

/**
 * live のあいだ 60 秒ごとに reload を呼ぶ。
 *
 * 裏に回っているタブで叩き続けても意味がないので止め、
 * 戻ってきた瞬間に 1 回だけ取り直す（そのとき一番古くなっているため）。
 *
 * reload は呼ぶたびに同一である必要がある（useCallback で包むこと）。
 */
export function useLiveRefresh(live: boolean, reload: () => void): void {
  useEffect(() => {
    if (!live) return;

    const tick = () => {
      if (!document.hidden) reload();
    };
    const onVisibility = () => {
      if (!document.hidden) reload();
    };

    const timer = setInterval(tick, LIVE_INTERVAL_MS);
    document.addEventListener("visibilitychange", onVisibility);
    return () => {
      clearInterval(timer);
      document.removeEventListener("visibilitychange", onVisibility);
    };
  }, [live, reload]);
}
