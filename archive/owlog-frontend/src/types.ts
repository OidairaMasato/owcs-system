export const ROLES = ["SUPPORT", "DAMAGE", "TANK"] as const;
export type RoleType = (typeof ROLES)[number];

export const TIERS = [
  "BRONZE", "SILVER", "GOLD", "PLATINUM",
  "DIAMOND", "MASTER", "GRANDMASTER", "CHAMPION",
] as const;
export type Tier = (typeof TIERS)[number];

export type MatchResult = "WIN" | "LOSS" | "DRAW";
export type ConditionLevel = "GREAT" | "NORMAL" | "POOR";

export const ROLE_LABEL: Record<RoleType, string> = {
  SUPPORT: "サポート", DAMAGE: "ダメージ", TANK: "タンク",
};

export const TIER_LABEL: Record<Tier, string> = {
  BRONZE: "ブロンズ", SILVER: "シルバー", GOLD: "ゴールド", PLATINUM: "プラチナ",
  DIAMOND: "ダイヤ", MASTER: "マスター", GRANDMASTER: "GM", CHAMPION: "チャンピオン",
};

/** グラフのティア帯ラベル用。表示幅が狭いので短縮形を別に持つ。 */
export const TIER_SHORT: Record<Tier, string> = {
  BRONZE: "BRZ", SILVER: "SLV", GOLD: "GLD", PLATINUM: "PLT",
  DIAMOND: "DIA", MASTER: "MST", GRANDMASTER: "GM", CHAMPION: "CHP",
};

export const CONDITION_LABEL: Record<ConditionLevel, string> = {
  GREAT: "絶好調", NORMAL: "普通", POOR: "イマイチ",
};

/** 勝敗ボタンを押したときに入る既定の増減。あとから1件ずつ直せる。 */
export const DEFAULT_RP_DELTA: Record<MatchResult, number> = {
  WIN: 22, LOSS: -18, DRAW: 0,
};

export const PER_TIER = 500;
export const PER_DIVISION = 100;
export const RANK_MAX = TIERS.length * PER_TIER - 1;

export interface Rank {
  value: number; tier: Tier; division: number; rp: number; label: string;
}

/* --------------------------------------------------------------------------
 * ランク計算のクライアント側実装。
 * 正は backend の RankValue.java。ここにあるのは「入力中のプレビュー」専用で、
 * 保存後はサーバーが返した Rank をそのまま表示すること。
 * ------------------------------------------------------------------------ */
export const clampRank = (v: number) => Math.max(0, Math.min(RANK_MAX, v));
export const rankValueOf = (tier: Tier, division: number, rp: number) =>
  TIERS.indexOf(tier) * PER_TIER + (5 - division) * PER_DIVISION + rp;
export const tierOf = (v: number): Tier => TIERS[Math.floor(clampRank(v) / PER_TIER)];
export const divisionOf = (v: number) => 5 - Math.floor((clampRank(v) % PER_TIER) / PER_DIVISION);
export const rpOf = (v: number) => clampRank(v) % PER_DIVISION;
export const rankLabelOf = (v: number) => `${TIER_LABEL[tierOf(v)]} ${divisionOf(v)}`;

export interface MatchInput { result: MatchResult; rpDelta: number; }

export interface SessionRequest {
  playedOn?: string;
  roleType: RoleType;
  startTier: Tier;
  startDivision: number;
  startRp: number;
  conditionLevel?: ConditionLevel;
  memo?: string;
  matches: MatchInput[];
}

export interface MatchOut { seq: number; result: MatchResult; rpDelta: number; rank: Rank; }

export interface SessionResponse {
  id: number;
  playedOn: string;
  startedAt: string;
  roleType: RoleType;
  startRank: Rank;
  rank: Rank;
  rpChange: number;
  conditionLevel: ConditionLevel | null;
  memo: string | null;
  wins: number; losses: number; draws: number;
  matches: MatchOut[];
}

export interface RankPoint {
  sessionId: number;
  playedOn: string;
  startedAt: string;
  roleType: RoleType;
  rankValue: number;
  rankLabel: string;
  rp: number;
  rpChange: number;
  wins: number;
  losses: number;
}
