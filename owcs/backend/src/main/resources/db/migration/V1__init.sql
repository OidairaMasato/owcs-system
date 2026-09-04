-- OWCS 観戦ダッシュボード 初期スキーマ
-- PandaScore から取得したデータのキャッシュ。画面は必ずこちらを読む。

create table teams (
    id          integer primary key,          -- PandaScore の team id をそのまま使う
    name        text        not null,
    acronym     text,
    slug        text,
    image_url   text,
    updated_at  timestamptz not null default now()
);

create table matches (
    id              integer primary key,      -- PandaScore の match id
    name            text        not null,     -- 例: "Grand final: ZETA vs TM"
    status          text        not null,     -- not_started / running / finished / canceled / postponed
    scheduled_at    timestamptz,
    begin_at        timestamptz,
    end_at          timestamptz,
    league_id       integer,
    serie_name      text,                     -- 例: "Korea Stage 3 2026"
    tournament_name text,                     -- 例: "Playoffs"
    match_type      text,                     -- best_of / all_games_played など
    number_of_games smallint,
    winner_id       integer,
    team_a_id       integer references teams (id),
    team_b_id       integer references teams (id),
    score_a         smallint,
    score_b         smallint,
    modified_at     timestamptz,              -- PandaScore 側の更新時刻。差分判定に使う
    synced_at       timestamptz not null default now(),
    games_synced    boolean     not null default false
);

create index idx_matches_scheduled on matches (scheduled_at);
create index idx_matches_status_sched on matches (status, scheduled_at);

-- マップ単位の結果。PandaScore にマップ名・ヒーロー情報は無く、勝者と所要時間のみ。
create table games (
    match_id   integer  not null references matches (id) on delete cascade,
    game_no    smallint not null,
    status     text     not null,
    winner_id  integer,
    length_sec integer,
    primary key (match_id, game_no)
);

create table streams (
    match_id    integer  not null references matches (id) on delete cascade,
    seq         smallint not null,
    raw_url     text     not null,
    lang        text,
    is_main     boolean  not null default false,
    is_official boolean  not null default false,
    primary key (match_id, seq)
);

create table sync_state (
    job_key         text primary key,
    last_success_at timestamptz,
    last_error      text,
    updated_at      timestamptz not null default now()
);
