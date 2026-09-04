import type { TeamView } from "../types";

type Props = { team: TeamView | null | undefined; size?: "lg" | "md" | "sm" };

/**
 * ロゴ画像。PandaScore はチームロゴを持っていないことが多いので、
 * その場合は頭文字 1 字のモノグラムで代替する。
 * 略称をそのまま出すと隣のチーム名と重複して読みにくくなるため。
 */
export default function TeamBadge({ team, size = "md" }: Props) {
  const label = team?.shortName ?? "?";
  return (
    <div className={`badge badge-${size}`} title={team?.name ?? "未定"}>
      {team?.imageUrl ? (
        <img src={team.imageUrl} alt={label} />
      ) : (
        <span className="badge-text">{label.charAt(0)}</span>
      )}
    </div>
  );
}
