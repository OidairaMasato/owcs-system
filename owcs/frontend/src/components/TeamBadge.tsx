import type { TeamView } from "../types";

type Props = { team: TeamView | null; size?: "lg" | "md" | "sm" };

/** ロゴ画像。無い場合は acronym の文字バッジで代替する。 */
export default function TeamBadge({ team, size = "md" }: Props) {
  const label = team?.shortName ?? "TBD";
  return (
    <div className={`badge badge-${size}`} title={team?.name ?? "未定"}>
      {team?.imageUrl ? (
        <img src={team.imageUrl} alt={label} />
      ) : (
        <span className="badge-text">{label.slice(0, 4)}</span>
      )}
    </div>
  );
}
