import type { League } from "./types";

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
  return (await res.json()) as T;
}

export function fetchLeague(): Promise<League> {
  return fetch("/api/league").then(json<League>);
}

/** 「今すぐ更新」。PandaScore から取り込み直してから返る。 */
export function syncNow(): Promise<League> {
  return fetch("/api/sync", { method: "POST" }).then(json<League>);
}
