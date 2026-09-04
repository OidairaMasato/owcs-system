import type { RankPoint, SessionRequest, SessionResponse } from "./types";

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    throw new Error(`${res.status} ${res.statusText}`);
  }
  return res.json() as Promise<T>;
}

export const api = {
  async createSession(body: SessionRequest): Promise<SessionResponse> {
    return json(
      await fetch("/api/sessions", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      }),
    );
  },

  async listSessions(): Promise<SessionResponse[]> {
    return json(await fetch("/api/sessions"));
  },

  /** 入力初期値に使う。まだ1件も無ければ null。 */
  async latestSession(): Promise<SessionResponse | null> {
    const res = await fetch("/api/sessions/latest");
    if (res.status === 204) return null;
    return json(res);
  },

  async rankHistory(): Promise<RankPoint[]> {
    return json(await fetch("/api/rank-history"));
  },
};
