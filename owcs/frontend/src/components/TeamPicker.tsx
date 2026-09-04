import { useEffect, useRef } from "react";
import type { TeamView } from "../types";
import TeamBadge from "./TeamBadge";

type Props = {
  teams: TeamView[];
  selectedId: number | null;
  onSelect: (id: number) => void;
};

/**
 * チームの横スクロール選択。
 * 全チームを対等に扱うが、選んだチームは端末に記憶されるので
 * 次に開いたときは同じチームが出る（App 側で localStorage に保存）。
 */
export default function TeamPicker({ teams, selectedId, onSelect }: Props) {
  const ref = useRef<HTMLDivElement>(null);

  // 選択中のチップが画面外にあれば見える位置へ寄せる
  useEffect(() => {
    const el = ref.current?.querySelector<HTMLElement>(".chip.on");
    el?.scrollIntoView({ inline: "center", block: "nearest" });
  }, [selectedId]);

  return (
    <div className="team-picker" ref={ref}>
      {teams.map((t) => (
        <button
          key={t.id}
          className={`chip${t.id === selectedId ? " on" : ""}`}
          onClick={() => onSelect(t.id)}
          title={t.name}
        >
          <TeamBadge team={t} size="sm" />
          <span>{t.shortName}</span>
        </button>
      ))}
    </div>
  );
}
