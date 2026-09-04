import type { Perspective } from "../derive";

type Props = { results: Perspective[] };

/**
 * 直近の勝敗を W / L の帯で並べる。
 * 「何連勝しているか」を数字の羅列ではなく形で見せるのが目的。
 * 渡ってくる配列は新しい順なので、左が古くなるように反転する。
 */
export default function FormStrip({ results }: Props) {
  if (results.length === 0) return null;

  return (
    <div className="form-strip">
      {[...results].reverse().map((p) => (
        <i key={p.match.id} className={p.won ? "w" : "l"}>
          {p.won ? "W" : "L"}
        </i>
      ))}
    </div>
  );
}
