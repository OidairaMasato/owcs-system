import { useEffect, useMemo, useRef } from "react";
import type { League, TeamView } from "../types";
import { groupByDay, todayIndex } from "../derive";
import { formatDay } from "../format";
import MatchLine from "./MatchLine";
import StandingsTable from "./StandingsTable";

type Props = {
  league: League;
  teams: Map<number, TeamView>;
  highlightTeamId: number | null;
  now: number;
};

/** ステージ全体を日付順に見渡すタブ。 */
export default function LeagueTab({ league, teams, highlightTeamId, now }: Props) {
  const days = useMemo(() => groupByDay(league.matches), [league.matches]);
  const focus = useMemo(() => todayIndex(days, now), [days, now]);
  const ref = useRef<HTMLDivElement>(null);

  // 開いたときに「今日」あたりが見えている状態にする
  useEffect(() => {
    const el = ref.current?.querySelectorAll<HTMLElement>(".day")[focus];
    el?.scrollIntoView({ block: "start", behavior: "auto" });
  }, [focus]);

  if (league.matches.length === 0) {
    return (
      <section className="next next-empty">
        <p className="empty-title">NO SCHEDULE YET</p>
        <p className="empty-sub">{league.notice ?? "日程が発表されると自動で表示されます"}</p>
      </section>
    );
  }

  return (
    <div ref={ref}>
      <section className="block">
        <h2 className="block-title">
          SCHEDULE
          <span className="block-sub">{league.serieName}</span>
        </h2>

        {days.map((d) => (
          <div className="day" key={d.key}>
            <h3 className="day-head">{formatDay(d.date)}</h3>
            <ul className="mlist">
              {d.matches.map((m) => (
                <MatchLine
                  key={m.id}
                  match={m}
                  teams={teams}
                  highlightTeamId={highlightTeamId}
                />
              ))}
            </ul>
          </div>
        ))}
      </section>

      <StandingsTable
        standings={league.standings}
        teams={teams}
        highlightTeamId={highlightTeamId}
      />
    </div>
  );
}
