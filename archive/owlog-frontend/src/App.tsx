import { useEffect, useState } from "react";
import RecordForm from "./RecordForm";
import TrendView from "./TrendView";
import { api } from "./api";
import type { SessionResponse } from "./types";

type Tab = "log" | "trend";

export default function App() {
  const [latest, setLatest] = useState<SessionResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<Tab>("log");
  /** 保存のたびに増やして推移タブに再取得させる。 */
  const [version, setVersion] = useState(0);

  useEffect(() => {
    api
      .latestSession()
      .then(setLatest)
      .catch(() => setError("サーバーに繋がりません。backend を起動してください。"))
      .finally(() => setLoading(false));
  }, []);

  function onSaved(s: SessionResponse) {
    setLatest(s);
    setVersion((v) => v + 1);
  }

  return (
    <div className="phone">
      <header className="appbar">
        <span className="dot" />
        <span className="wordmark">OW セッションログ</span>
      </header>

      <nav className="tabs" role="tablist">
        <button role="tab" aria-selected={tab === "log"} onClick={() => setTab("log")}>
          記録
        </button>
        <button role="tab" aria-selected={tab === "trend"} onClick={() => setTab("trend")}>
          推移
        </button>
      </nav>

      {loading && <p className="status">読み込み中…</p>}
      {error && <p className="status error">{error}</p>}
      {!loading && !error && tab === "log" && <RecordForm latest={latest} onSaved={onSaved} />}
      {!loading && !error && tab === "trend" && <TrendView version={version} />}
    </div>
  );
}
