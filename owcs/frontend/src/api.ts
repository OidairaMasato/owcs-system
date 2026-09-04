import type { Dashboard } from "./types";

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
  return (await res.json()) as T;
}

export function fetchDashboard(): Promise<Dashboard> {
  return fetch("/api/dashboard").then(json<Dashboard>);
}

/** 「今すぐ更新」。PandaScore から取り込み直してから返る。 */
export function syncNow(): Promise<Dashboard> {
  return fetch("/api/sync", { method: "POST" }).then(json<Dashboard>);
}
