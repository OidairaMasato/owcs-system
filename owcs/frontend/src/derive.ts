import type { MatchRow, TeamView } from "./types";

/**
 * API は「シリーズの全試合」を 1 本で返す。
 * 日付ごとの並びも、チーム視点の抽出も、ここで組み立てる。
 * サーバーを増やさずに 2 つのタブを賄うための設計。
 */

export function teamMap(teams: TeamView[]): Map<number, TeamView> {
  return new Map(teams.map((t) => [t.id, t]));
}

/** その日のローカル日付キー（YYYY-MM-DD）。JST で切る。 */
function dayKey(iso: string | null): string {
  if (!iso) return "tbd";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "tbd";
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

export type Day = { key: string; date: Date | null; matches: MatchRow[] };

/** 日付ごとにまとめる。順序は渡された順（＝開始時刻の昇順）を保つ。 */
export function groupByDay(matches: MatchRow[]): Day[] {
  const days = new Map<string, Day>();
  for (const m of matches) {
    const key = dayKey(m.startsAt);
    let d = days.get(key);
    if (!d) {
      d = { key, date: m.startsAt ? new Date(m.startsAt) : null, matches: [] };
      days.set(key, d);
    }
    d.matches.push(m);
  }
  return [...days.values()];
}

/** 今日以降で最初の日のインデックス。開いたときにそこへスクロールする。 */
export function todayIndex(days: Day[], now: number): number {
  const idx = days.findIndex((d) => d.date != null && d.date.getTime() >= startOfDay(now));
  return idx < 0 ? Math.max(0, days.length - 1) : idx;
}

function startOfDay(ms: number): number {
  const d = new Date(ms);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

/** 指定チームから見た 1 試合。 */
export type Perspective = {
  match: MatchRow;
  opponentId: number | null;
  scoreUs: number | null;
  scoreThem: number | null;
  won: boolean | null;
  games: { gameNo: number; won: boolean; lengthSec: number | null }[];
};

export function perspective(m: MatchRow, teamId: number): Perspective {
  const isA = m.teamAId === teamId;
  const opponentId = isA ? m.teamBId : m.teamAId;
  const scoreUs = isA ? m.scoreA : m.scoreB;
  const scoreThem = isA ? m.scoreB : m.scoreA;
  const won =
    m.status === "finished" && m.winnerId != null ? m.winnerId === teamId : null;
  return {
    match: m,
    opponentId,
    scoreUs,
    scoreThem,
    won,
    games: m.games.map((g) => ({
      gameNo: g.gameNo,
      won: g.winnerId === teamId,
      lengthSec: g.lengthSec,
    })),
  };
}

export type TeamSlice = {
  live: Perspective | null;
  next: Perspective | null;
  upcoming: Perspective[];
  recent: Perspective[];
};

/** チームタブに出す内容を、全試合から切り出す。 */
export function sliceForTeam(matches: MatchRow[], teamId: number): TeamSlice {
  const mine = matches
    .filter((m) => m.teamAId === teamId || m.teamBId === teamId)
    .map((m) => perspective(m, teamId));

  const live = mine.find((p) => p.match.status === "running") ?? null;
  const notStarted = mine.filter((p) => p.match.status === "not_started");
  const finished = mine.filter((p) => p.match.status === "finished").reverse();

  return {
    live,
    next: notStarted[0] ?? null,
    upcoming: notStarted.slice(1, 6),
    recent: finished.slice(0, 5),
  };
}
