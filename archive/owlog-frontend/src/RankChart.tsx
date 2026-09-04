import { useState } from "react";
import { PER_TIER, TIER_SHORT, tierOf, type RankPoint } from "./types";

const W = 340;
const H = 190;
const PAD = { l: 8, r: 34, t: 10, b: 20 };

interface Props {
  points: RankPoint[];
}

/**
 * ランク推移。単一系列なので凡例は置かず、終点だけを直接ラベルする。
 * 背景の横線はティアの境目（500 刻み）で、目盛りそのものが意味を持つようにしてある。
 */
export default function RankChart({ points }: Props) {
  const [hover, setHover] = useState<number | null>(null);

  const values = points.map((p) => p.rankValue);
  const lo = Math.min(...values) - 70;
  const hi = Math.max(...values) + 70;
  const span = hi - lo || 1;

  const x = (i: number) =>
    PAD.l + ((W - PAD.l - PAD.r) * i) / Math.max(1, points.length - 1);
  const y = (v: number) => PAD.t + (H - PAD.t - PAD.b) * (1 - (v - lo) / span);

  const coords = points.map((p, i) => [x(i), y(p.rankValue)] as const);
  const line = coords.map((c, i) => `${i ? "L" : "M"}${c[0].toFixed(1)} ${c[1].toFixed(1)}`).join(" ");
  const area =
    `${line} L${coords[coords.length - 1][0].toFixed(1)} ${H - PAD.b}` +
    ` L${coords[0][0].toFixed(1)} ${H - PAD.b} Z`;

  const bands: number[] = [];
  for (let b = Math.ceil(lo / PER_TIER) * PER_TIER; b < hi; b += PER_TIER) bands.push(b);

  const last = coords[coords.length - 1];
  const hp = hover !== null ? points[hover] : null;
  const hc = hover !== null ? coords[hover] : null;

  function onMove(e: React.PointerEvent<HTMLDivElement>) {
    const svg = e.currentTarget.querySelector("svg");
    if (!svg) return;
    const rect = svg.getBoundingClientRect();
    const px = ((e.clientX - rect.left) / rect.width) * W;
    let best = Infinity;
    let idx = 0;
    coords.forEach((c, i) => {
      const d = Math.abs(c[0] - px);
      if (d < best) {
        best = d;
        idx = i;
      }
    });
    setHover(idx);
  }

  return (
    <div
      className="chart"
      onPointerDown={onMove}
      onPointerMove={onMove}
      onPointerLeave={() => setHover(null)}
    >
      <svg
        viewBox={`0 0 ${W} ${H}`}
        role="img"
        aria-label={`ランク推移。${points[0].rankLabel} から ${points[points.length - 1].rankLabel} まで。`}
      >
        <defs>
          <linearGradient id="rankFill" x1="0" y1="0" x2="0" y2="1">
            <stop className="s0" offset="0" />
            <stop className="s1" offset="1" />
          </linearGradient>
        </defs>

        {bands.map((b) => (
          <g key={b}>
            <line className="band-line" x1={PAD.l} y1={y(b)} x2={W - PAD.r} y2={y(b)} strokeWidth="1" />
            <text className="band" x={W - PAD.r + 6} y={y(b) - 4}>
              {TIER_SHORT[tierOf(b)]}
            </text>
          </g>
        ))}

        <path d={area} fill="url(#rankFill)" />
        <path
          className="line"
          d={line}
          fill="none"
          strokeWidth="2"
          strokeLinejoin="round"
          strokeLinecap="round"
        />

        {hc && (
          <>
            <line
              className="cross"
              x1={hc[0]} y1={PAD.t} x2={hc[0]} y2={H - PAD.b}
              strokeWidth="1" strokeDasharray="3 3"
            />
            <circle className="dot" cx={hc[0]} cy={hc[1]} r="4.5" strokeWidth="2" />
          </>
        )}
        <circle className="dot" cx={last[0]} cy={last[1]} r="4.5" strokeWidth="2" />

        <text className="axis" x={PAD.l} y={H - 5}>{md(points[0].playedOn)}</text>
        <text className="axis" x={W - PAD.r} y={H - 5} textAnchor="end">
          {md(points[points.length - 1].playedOn)}
        </text>
      </svg>

      {hp && hc && (
        <div
          className="tip"
          style={{ left: `${(hc[0] / W) * 100}%`, top: `${(hc[1] / H) * 100}%` }}
        >
          <b>{hp.rankLabel} · {hp.rp}RP</b>
          <small>{md(hp.playedOn)} — {hp.wins}勝{hp.losses}敗</small>
        </div>
      )}
    </div>
  );
}

const md = (iso: string) => {
  const [, m, d] = iso.split("-");
  return `${Number(m)}/${d}`;
};
