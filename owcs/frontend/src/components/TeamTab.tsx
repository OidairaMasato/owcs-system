import { useMemo } from "react";
import type { League, TeamView } from "../types";
import { sliceForTeam } from "../derive";
import TeamPicker from "./TeamPicker";
import NextMatchCard from "./NextMatchCard";
import ResultList from "./ResultList";
import UpcomingList from "./UpcomingList";
import StandingsTable from "./StandingsTable";

type Props = {
  league: League;
  teams: Map<number, TeamView>;
  selectedId: number | null;
  onSelect: (id: number) => void;
  now: number;
};

/** 1 チームを追うタブ。Phase 1 の画面がそのまま入っている。 */
export default function TeamTab({ league, teams, selectedId, onSelect, now }: Props) {
  const slice = useMemo(
    () => (selectedId != null ? sliceForTeam(league.matches, selectedId) : null),
    [league.matches, selectedId],
  );

  const me = selectedId != null ? teams.get(selectedId) : undefined;

  return (
    <div>
      <TeamPicker teams={league.teams} selectedId={selectedId} onSelect={onSelect} />

      {slice && (
        <>
          <NextMatchCard
            slice={slice}
            me={me}
            teams={teams}
            now={now}
            serieName={league.serieName}
            notice={league.notice}
          />
          <ResultList results={slice.recent} teams={teams} serieName={league.serieName} />
          <UpcomingList items={slice.upcoming} teams={teams} />
          <StandingsTable
            standings={league.standings}
            teams={teams}
            highlightTeamId={selectedId}
            dimFar
          />
        </>
      )}
    </div>
  );
}
