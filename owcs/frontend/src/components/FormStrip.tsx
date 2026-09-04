import type { MatchView } from "../types";

type Props = { matches: MatchView[] };

/**
 * 直近の勝敗を W / L の帯で並べる。
 * 「5連勝している」ことを数字の羅列ではなく形で見せるのが目的。
 * 渡ってくる配列は新しい順なので、左が古くなるように反転する。
 */
export default function FormStrip({ matches }: Props) {
  if (matches.length === 0) return null;

  const ordered = [...matches].reverse();

  return (
    <div className="form-strip">
      {ordered.map((m) => (
        <i key={m.id} className={m.won ? "w" : "l"} title={`${m.opponent?.shortName ?? ""} ${m.scoreUs ?? "-"}-${m.scoreThem ?? "-"}`}>
          {m.won ? "W" : "L"}
        </i>
      ))}
    </div>
  );
}
