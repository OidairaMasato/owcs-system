import type { HeadToHead, League, Rankings, Today } from "./types";

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
  return (await res.json()) as T;
}

function q(serieId: number | null): string {
  return serieId != null ? `?serie=${serieId}` : "";
}

export function fetchLeague(serieId: number | null): Promise<League> {
  return fetch(`/api/league${q(serieId)}`).then(json<League>);
}

/** 今日前後の全試合。大会をまたぐ。 */
export function fetchToday(): Promise<Today> {
  return fetch("/api/today").then(json<Today>);
}

/** 対戦相手別の通算成績。 */
export function fetchHeadToHead(teamId: number): Promise<HeadToHead> {
  return fetch(`/api/team/${teamId}/head-to-head`).then(json<HeadToHead>);
}

/** 年間の通算ランキング。year 省略で最新の年。 */
export function fetchRankings(year: number | null): Promise<Rankings> {
  const suffix = year != null ? `?year=${year}` : "";
  return fetch(`/api/rankings${suffix}`).then(json<Rankings>);
}

/**
 * アクセスを 1 回数える。
 *
 * document.referrer はクライアントしか知らないので、こちらから送る。
 * サーバー側の Referer ヘッダーは自サイトの URL になってしまい使えない。
 * 失敗しても画面には影響しないので、握りつぶす。
 */
export function sendHit(): void {
  try {
    void fetch("/api/hit", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ref: document.referrer }),
      keepalive: true,
    }).catch(() => undefined);
  } catch {
    /* 数えられなくても構わない */
  }
}

/** 「今すぐ更新」。PandaScore から取り込み直してから返る。 */
export function syncNow(serieId: number | null): Promise<League> {
  return fetch(`/api/sync${q(serieId)}`, { method: "POST" }).then(json<League>);
}
