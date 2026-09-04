import { useCallback, useEffect, useState } from "react";
import { fetchDashboard, syncNow } from "./api";
import type { Dashboard } from "./types";
import { relativePast } from "./format";
import NextMatchCard from "./components/NextMatchCard";
import RecentStrip from "./components/RecentStrip";
import StandingsTable from "./components/StandingsTable";
import UpcomingList from "./components/UpcomingList";

export default function App() {
  const [data, setData] = useState<Dashboard | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [syncing, setSyncing] = useState(false);
  const [now, setNow] = useState(() => Date.now());

  // カウントダウン用に 1 秒ごとに時刻を進める
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);

  const load = useCallback(() => {
    fetchDashboard()
      .then((d) => {
        setData(d);
        setError(null);
      })
      .catch((e: Error) => setError(e.message));
  }, []);

  useEffect(() => {
    load();
    // 画面を開きっぱなしでも 5 分ごとにキャッシュを読み直す（PandaScore は叩かない）
    const t = setInterval(load, 5 * 60 * 1000);
    return () => clearInterval(t);
  }, [load]);

  const onSync = () => {
    setSyncing(true);
    syncNow()
      .then((d) => {
        setData(d);
        setError(null);
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setSyncing(false));
  };

  if (error && !data) {
    return (
      <main className="app">
        <p className="error">読み込めませんでした: {error}</p>
        <button className="ghost" onClick={load}>
          再試行
        </button>
      </main>
    );
  }

  if (!data) {
    return (
      <main className="app">
        <p className="loading">読み込み中…</p>
      </main>
    );
  }

  const featured = data.live ?? data.next;

  return (
    <main className="app">
      <header className="app-head">
        <h1>{data.team.shortName} 番</h1>
        <span className="league">OWCS</span>
      </header>

      <NextMatchCard
        match={featured}
        me={data.team}
        now={now}
        live={data.live != null}
        notice={data.notice}
      />

      <RecentStrip matches={data.recent} />
      <StandingsTable standings={data.standings} />
      <UpcomingList matches={data.upcoming} />

      <footer className="app-foot">
        <span>最終更新 {relativePast(data.lastSyncedAt, now)}</span>
        <button className="ghost" onClick={onSync} disabled={syncing}>
          {syncing ? "更新中…" : "今すぐ更新"}
        </button>
      </footer>
      {error && <p className="error-inline">更新に失敗しました: {error}</p>}
    </main>
  );
}
