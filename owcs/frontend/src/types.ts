export type TeamView = {
  id: number;
  name: string;
  shortName: string;
  imageUrl: string | null;
};

export type GameRow = {
  gameNo: number;
  winnerId: number | null;
  lengthSec: number | null;
};

export type MatchRow = {
  id: number;
  name: string;
  status: "not_started" | "running" | "finished" | string;
  startsAt: string | null;
  tournamentName: string | null;
  teamAId: number | null;
  teamBId: number | null;
  scoreA: number | null;
  scoreB: number | null;
  winnerId: number | null;
  bestOf: number | null;
  streamUrl: string | null;
  games: GameRow[];
};

export type StandingRowView = {
  rankNo: number | null;
  teamId: number;
  wins: number | null;
  losses: number | null;
  gameWins: number | null;
  gameLosses: number | null;
};

export type StandingsView = {
  tournamentId: number;
  serieName: string | null;
  tournamentName: string | null;
  /** 勝敗が無く、最終順位だけの表か。ブラケット戦はこちらになる。 */
  placementOnly: boolean;
  rows: StandingRowView[];
};

export type SerieRef = {
  id: number;
  name: string;
};

export type League = {
  serieId: number | null;
  serieName: string | null;
  series: SerieRef[];
  teams: TeamView[];
  matches: MatchRow[];
  standings: StandingsView | null;
  lastSyncedAt: string | null;
  serverTime: string;
  notice: string | null;
};
