import { useState } from "react";
import { api } from "./api";
import {
  CONDITION_LABEL, DEFAULT_RP_DELTA, ROLE_LABEL, ROLES, TIER_LABEL, TIERS,
  clampRank, rankLabelOf, rankValueOf, rpOf,
  type ConditionLevel, type MatchInput, type MatchResult, type RoleType,
  type SessionResponse, type Tier,
} from "./types";

interface Props {
  latest: SessionResponse | null;
  onSaved: (s: SessionResponse) => void;
}

/**
 * 入力速度が最優先。判断のよりどころは参照モック:
 * https://claude.ai/code/artifact/e1d9c45b-7eed-4c6d-9cc3-5fe305972197
 *
 * 勝敗ボタンは1タップで既定の増減が入る。数字が違った試合だけ、あとからチップを叩いて直す。
 * 開始ランクは前回セッションの終了ランクから始まるので、通常は触らなくてよい。
 */
export default function RecordForm({ latest, onSaved }: Props) {
  const start = latest?.rank;
  const [role, setRole] = useState<RoleType>(latest?.roleType ?? "SUPPORT");
  const [tier, setTier] = useState<Tier>(start?.tier ?? "PLATINUM");
  const [division, setDivision] = useState(start?.division ?? 3);
  const [rp, setRp] = useState(start?.rp ?? 0);
  const [condition, setCondition] = useState<ConditionLevel>("NORMAL");
  const [memo, setMemo] = useState("");
  const [memoOpen, setMemoOpen] = useState(false);
  const [rankOpen, setRankOpen] = useState(!start);
  const [matches, setMatches] = useState<MatchInput[]>([]);
  const [editing, setEditing] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState<SessionResponse | null>(null);

  const wins = matches.filter((m) => m.result === "WIN").length;
  const losses = matches.filter((m) => m.result === "LOSS").length;
  const draws = matches.filter((m) => m.result === "DRAW").length;

  const startValue = rankValueOf(tier, division, rp);
  const change = matches.reduce((sum, m) => sum + m.rpDelta, 0);
  const current = clampRank(startValue + change);

  const add = (result: MatchResult) =>
    setMatches([...matches, { result, rpDelta: DEFAULT_RP_DELTA[result] }]);

  const bump = (i: number, d: number) =>
    setMatches(matches.map((m, k) => (k === i ? { ...m, rpDelta: clampDelta(m.rpDelta + d) } : m)));

  const remove = (i: number) => {
    setMatches(matches.filter((_, k) => k !== i));
    setEditing(null);
  };

  async function save() {
    setSaving(true);
    try {
      const res = await api.createSession({
        roleType: role,
        startTier: tier, startDivision: division, startRp: rp,
        conditionLevel: condition,
        memo: memo || undefined,
        matches,
      });
      setSaved(res);
      onSaved(res);
    } catch (e) {
      alert("保存に失敗しました: " + (e as Error).message);
    } finally {
      setSaving(false);
    }
  }

  if (saved) {
    return (
      <div className="done">
        <h2>記録しました</h2>
        <p>
          {saved.startRank.label} → <b>{saved.rank.label} · {saved.rank.rp}RP</b>
          <br />
          {saved.matches.length}試合 {saved.wins}勝{saved.losses}敗（{signed(saved.rpChange)} RP）
        </p>
        <button
          className="again"
          onClick={() => { setSaved(null); setMatches([]); setEditing(null); setRankOpen(false); }}
        >
          続けて記録する
        </button>
      </div>
    );
  }

  return (
    <div className="screen">
      <div className="today">
        <span className="d">{new Date().getMonth() + 1}/{new Date().getDate()}</span>
        <span className="auto">自動</span>
      </div>

      <section className="field">
        <h3>ROLE</h3>
        <div className="seg">
          {ROLES.map((r) => (
            <button key={r} aria-pressed={role === r} onClick={() => setRole(r)}>
              {ROLE_LABEL[r]}
            </button>
          ))}
        </div>
      </section>

      {/* 開始ランクは前回の終了ランクから引き継ぐ。ズレた日だけ開いて直す。 */}
      <section className="field">
        <div className="label">
          <h3>START</h3>
          <button className="undo" onClick={() => setRankOpen(!rankOpen)}>
            {rankOpen ? "閉じる" : "開始ランクを直す"}
          </button>
        </div>
        <div className="startline">
          <b>{rankLabelOf(startValue)}</b>
          <span>{rpOf(startValue)} RP</span>
          {!!matches.length && (
            <em className={change >= 0 ? "up" : "down"}>
              → {rankLabelOf(current)} · {rpOf(current)}RP（{signed(change)}）
            </em>
          )}
        </div>

        {rankOpen && (
          <>
            <div className="tiers">
              {TIERS.map((t) => (
                <button key={t} aria-pressed={tier === t} onClick={() => setTier(t)}>
                  {TIER_LABEL[t]}
                </button>
              ))}
            </div>
            <div className="divrow">
              {[5, 4, 3, 2, 1].map((d) => (
                <button key={d} aria-pressed={division === d} onClick={() => setDivision(d)}>
                  {d}
                </button>
              ))}
            </div>
            <div className="rp">
              {[-10, -1].map((d) => (
                <button key={d} className="step" onClick={() => setRp(clampRp(rp + d))}>{d}</button>
              ))}
              <input
                type="number" inputMode="numeric" aria-label="開始RP" value={rp}
                onChange={(e) => setRp(clampRp(Number(e.target.value)))}
              />
              {[1, 10].map((d) => (
                <button key={d} className="step" onClick={() => setRp(clampRp(rp + d))}>+{d}</button>
              ))}
              <span className="unit">RP</span>
            </div>
          </>
        )}
      </section>

      <section className="field">
        <div className="label">
          <h3>MATCHES</h3>
          <span className="hint">チップを押すと増減を直せます</span>
        </div>

        <div className="tally">
          {matches.length ? (
            <span className="score">
              <b className="w">{wins}勝</b> / <b className="l">{losses}敗</b>
              {draws > 0 && <> / {draws}分</>}
            </span>
          ) : (
            <span className="empty">タップして積み上げ</span>
          )}
        </div>

        {!!matches.length && (
          <div className="mchips">
            {matches.map((m, i) => (
              <button
                key={i}
                className={`mchip ${m.result.toLowerCase()} ${editing === i ? "on" : ""}`}
                onClick={() => setEditing(editing === i ? null : i)}
              >
                <b>{m.result === "WIN" ? "W" : m.result === "LOSS" ? "L" : "D"}</b>
                <span>{signed(m.rpDelta)}</span>
              </button>
            ))}
          </div>
        )}

        {editing !== null && matches[editing] && (
          <div className="editor">
            <span className="elabel">{editing + 1}試合目</span>
            {[-5, -1].map((d) => (
              <button key={d} className="step" onClick={() => bump(editing, d)}>{d}</button>
            ))}
            <input
              type="number" inputMode="numeric" aria-label="RP増減"
              value={matches[editing].rpDelta}
              onChange={(e) =>
                setMatches(matches.map((m, k) =>
                  k === editing ? { ...m, rpDelta: clampDelta(Number(e.target.value)) } : m))
              }
            />
            {[1, 5].map((d) => (
              <button key={d} className="step" onClick={() => bump(editing, d)}>+{d}</button>
            ))}
            <button className="del" onClick={() => remove(editing)}>削除</button>
          </div>
        )}

        <div className="results">
          <button className="rbtn win" onClick={() => add("WIN")}>WIN</button>
          <button className="rbtn loss" onClick={() => add("LOSS")}>LOSE</button>
          <button className="rbtn draw" onClick={() => add("DRAW")}>分け</button>
        </div>
      </section>

      <section className="field">
        <h3>CONDITION</h3>
        <div className="chips">
          {(Object.keys(CONDITION_LABEL) as ConditionLevel[]).map((c) => (
            <button key={c} aria-pressed={condition === c} onClick={() => setCondition(c)}>
              {CONDITION_LABEL[c]}
            </button>
          ))}
        </div>
      </section>

      <section className="field">
        {memoOpen ? (
          <textarea
            value={memo} autoFocus placeholder="気づいたことがあれば"
            onChange={(e) => setMemo(e.target.value)}
          />
        ) : (
          <button className="memo-toggle" onClick={() => setMemoOpen(true)}>
            ＋ メモを追加（任意）
          </button>
        )}
      </section>

      <div className="savebar">
        <button className="save" disabled={!matches.length || saving} onClick={save}>
          {saving ? "保存中…" : "保存する"}
        </button>
      </div>
    </div>
  );
}

const signed = (n: number) => (n > 0 ? `+${n}` : `${n}`);
const clampRp = (n: number) => Math.max(0, Math.min(99, Number.isFinite(n) ? n : 0));
const clampDelta = (n: number) => Math.max(-99, Math.min(99, Number.isFinite(n) ? n : 0));
