import { useCallback, useEffect, useMemo, useState } from "react";
import { fetchLeague, syncNow } from "./api";
import type { League } from "./types";
import { teamMap } from "./derive";
import { relativePast } from "./format";
import LeagueTab from "./components/LeagueTab";
import TeamTab from "./components/TeamTab";
import TodayTab from "./components/TodayTab";

type Tab = "today" | "league" | "team";

const TAB_KEY = "owcs.tab";
const TEAM_KEY = "owcs.teamId";
const SERIE_KEY = "owcs.serieId";

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

function loadNumber(key: string): number | null {
  const v = load(key);
  const n = v != null ? Number(v) : NaN;
  return Number.isFinite(n) ? n : null;
}

export default function App() {
  const [data, setData] = useState<League | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [syncing, setSyncing] = useState(false);
  const [now, setNow] = useState(() => Date.now());

  const [tab, setTab] = useState<Tab>(() => {
    const v = load(TAB_KEY);
    return v === "league" || v === "today" ? v : "team";
  });
  const [serieId, setSerieId] = useState<number | null>(() => loadNumber(SERIE_KEY));

  /**
   * 「見たいチーム」の希望。大会を切り替えると出場チームが変わるので、
   * 希望はそのまま保持し、その大会に居ないときだけ先頭チームで代替する。
   * こうしないと一度他地域を見ただけで推しチームの記憶が上書きされてしまう。
   */
  const [preferredTeamId, setPreferredTeamId] = useState<number | null>(() => loadNumber(TEAM_KEY));

  // カウントダウン用に 1 秒ごとに時刻を進める
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);

  const reload = useCallback(
    (serie: number | null) => {
      fetchLeague(serie)
        .then((d) => {
          setData(d);
          setError(null);
          // サーバーが既定の大会を選んだ場合は、それを記憶しておく
          if (d.serieId != null && d.serieId !== serie) {
            setSerieId(d.serieId);
            save(SERIE_KEY, String(d.serieId));
          }
        })
        .catch((e: Error) => setError(e.message));
    },
    [],
  );

  useEffect(() => {
    reload(serieId);
    // 開きっぱなしでも 5 分ごとにキャッシュを読み直す（PandaScore は叩かない）
    const t = setInterval(() => reload(serieId), 5 * 60 * 1000);
    return () => clearInterval(t);
  }, [reload, serieId]);

  const teams = useMemo(() => teamMap(data?.teams ?? []), [data]);

  const teamId = useMemo(() => {
    const list = data?.teams ?? [];
    if (list.length === 0) return null;
    if (preferredTeamId != null && list.some((t) => t.id === preferredTeamId)) {
      return preferredTeamId;
    }
    return list[0].id;
  }, [data, preferredTeamId]);

  const onSelectTeam = (id: number) => {
    setPreferredTeamId(id);
    save(TEAM_KEY, String(id));
  };

  const onSelectSerie = (id: number) => {
    setSerieId(id);
    save(SERIE_KEY, String(id));
  };

  const onTab = (t: Tab) => {
    setTab(t);
    save(TAB_KEY, t);
  };

  const onSync = () => {
    setSyncing(true);
    syncNow(serieId)
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
        <button className="ghost" onClick={() => reload(serieId)}>
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
        {/* 「今日」タブは大会に依存しないので、セレクトの代わりにアプリ名を出す */}
        {tab === "today" ? (
          <span className="app-title">OWCS</span>
        ) : (
          <select
            className="serie-select"
            value={data.serieId ?? ""}
            onChange={(e) => onSelectSerie(Number(e.target.value))}
            aria-label="大会を選ぶ"
          >
            {data.series.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        )}

        <nav className="tabs">
          <button className={tab === "today" ? "on" : ""} onClick={() => onTab("today")}>
            今日
          </button>
          <button className={tab === "league" ? "on" : ""} onClick={() => onTab("league")}>
            リーグ
          </button>
          <button className={tab === "team" ? "on" : ""} onClick={() => onTab("team")}>
            チーム
          </button>
        </nav>
      </header>

      {tab === "today" && <TodayTab />}
      {tab === "league" && (
        <LeagueTab league={data} teams={teams} highlightTeamId={teamId} now={now} />
      )}
      {tab === "team" && (
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

      {/*
        出典表示は PandaScore の規約で必須。
        非公式である旨も、大会主催者と無関係であることを示すために出す。
      */}
      <p className="credit">
        Source:{" "}
        <a href="https://www.pandascore.co/" target="_blank" rel="noreferrer noopener">
          PandaScore
        </a>
        <br />
        本サイトは個人が趣味で運営する非公式のものです。
        Overwatch および OWCS は Blizzard Entertainment, Inc. の商標です。
        大会主催者・各チームとは一切関係ありません。
      </p>
    </main>
  );
}
