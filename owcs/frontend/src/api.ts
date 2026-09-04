import type { League } from "./types";

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

/** 「今すぐ更新」。PandaScore から取り込み直してから返る。 */
export function syncNow(serieId: number | null): Promise<League> {
  return fetch(`/api/sync${q(serieId)}`, { method: "POST" }).then(json<League>);
}
