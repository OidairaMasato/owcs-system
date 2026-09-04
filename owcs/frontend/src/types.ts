export type TeamView = {
  id: number;
  name: string;
  shortName: string;
  imageUrl: string | null;
};

export type GameView = {
  gameNo: number;
  won: boolean;
  lengthSec: number | null;
};

export type MatchView = {
  id: number;
  name: string;
  status: "not_started" | "running" | "finished" | string;
  startsAt: string | null;
  serieName: string | null;
  tournamentName: string | null;
  opponent: TeamView | null;
  scoreUs: number | null;
  scoreThem: number | null;
  won: boolean | null;
  bestOf: number | null;
  streamUrl: string | null;
  games: GameView[];
};

export type StandingRowView = {
  rankNo: number | null;
  team: TeamView;
  wins: number | null;
  losses: number | null;
  gameWins: number | null;
  gameLosses: number | null;
  me: boolean;
};

export type StandingsView = {
  tournamentId: number;
  serieName: string | null;
  tournamentName: string | null;
  rows: StandingRowView[];
};

export type Dashboard = {
  team: TeamView;
  live: MatchView | null;
  next: MatchView | null;
  upcoming: MatchView[];
  recent: MatchView[];
  standings: StandingsView | null;
  lastSyncedAt: string | null;
  serverTime: string;
  notice: string | null;
};
