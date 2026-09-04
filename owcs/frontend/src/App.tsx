import { useCallback, useEffect, useMemo, useState } from "react";
import { fetchLeague, syncNow } from "./api";
import type { League } from "./types";
import { teamMap } from "./derive";
import { relativePast } from "./format";
import LeagueTab from "./components/LeagueTab";
import TeamTab from "./components/TeamTab";

type Tab = "league" | "team";

const TAB_KEY = "owcs.tab";
const TEAM_KEY = "owcs.teamId";

/** localStorage は環境によって例外を投げるので、必ず包んで使う。 */
function load(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function save(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    /* 保存できなくても動作に影響はない */
  }
}

export default function App() {
  const [data, setData] = useState<League | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [syncing, setSyncing] = useState(false);
  const [now, setNow] = useState(() => Date.now());

  const [tab, setTab] = useState<Tab>(() => (load(TAB_KEY) === "league" ? "league" : "team"));
  const [teamId, setTeamId] = useState<number | null>(() => {
    const v = load(TEAM_KEY);
    return v ? Number(v) : null;
  });

  // カウントダウン用に 1 秒ごとに時刻を進める
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);

  const load_ = useCallback(() => {
    fetchLeague()
      .then((d) => {
        setData(d);
        setError(null);
      })
      .catch((e: Error) => setError(e.message));
  }, []);

  useEffect(() => {
    load_();
    // 開きっぱなしでも 5 分ごとにキャッシュを読み直す（PandaScore は叩かない）
    const t = setInterval(load_, 5 * 60 * 1000);
    return () => clearInterval(t);
  }, [load_]);

  // 選択チームが未設定、またはこのステージに居ないチームなら先頭に寄せる
  useEffect(() => {
    if (!data || data.teams.length === 0) return;
    if (teamId != null && data.teams.some((t) => t.id === teamId)) return;
    setTeamId(data.teams[0].id);
  }, [data, teamId]);

  const teams = useMemo(() => teamMap(data?.teams ?? []), [data]);

  const onSelectTeam = (id: number) => {
    setTeamId(id);
    save(TEAM_KEY, String(id));
  };

  const onTab = (t: Tab) => {
    setTab(t);
    save(TAB_KEY, t);
  };

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
        <button className="ghost" onClick={load_}>
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

  return (
    <main className="app">
      <header className="app-head">
        <h1>OWCS {data.serieName?.split(" ")[0] ?? "Korea"}</h1>
        <nav className="tabs">
          <button className={tab === "league" ? "on" : ""} onClick={() => onTab("league")}>
            リーグ
          </button>
          <button className={tab === "team" ? "on" : ""} onClick={() => onTab("team")}>
            チーム
          </button>
        </nav>
      </header>

      {tab === "league" ? (
        <LeagueTab league={data} teams={teams} highlightTeamId={teamId} now={now} />
      ) : (
        <TeamTab
          league={data}
          teams={teams}
          selectedId={teamId}
          onSelect={onSelectTeam}
          now={now}
        />
      )}

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
