-- OW Session Log 初期スキーマ
-- 注意: 適用済みのマイグレーションは書き換えない。変更は新しい V番号 を足す。

create table users (
    id           bigserial primary key,
    battle_tag   varchar(64)  not null unique,
    platform     varchar(16)  not null default 'console',
    display_name varchar(64),
    created_at   timestamptz  not null default now()
);

create table seasons (
    id         bigserial primary key,
    name       varchar(32) not null unique,
    started_on date        not null,
    ended_on   date
);

-- 1日1ロール分のプレイセッション
create table sessions (
    id              bigserial primary key,
    user_id         bigint      not null references users (id),
    season_id       bigint      references seasons (id),
    played_on       date        not null,
    role_type       varchar(16) not null,               -- SUPPORT / DAMAGE / TANK
    tier            varchar(16) not null,               -- BRONZE .. CHAMPION
    division        smallint    not null,
    rp              smallint    not null,
    rank_value      integer     not null,               -- tier*500 + (5-division)*100 + rp
    condition_level varchar(16),                        -- GREAT / NORMAL / POOR
    memo            text,
    created_at      timestamptz not null default now(),
    constraint sessions_division_range check (division between 1 and 5),
    constraint sessions_rp_range       check (rp between 0 and 99),
    constraint sessions_unique_day     unique (user_id, played_on, role_type)
);

create index idx_sessions_user_played on sessions (user_id, played_on desc);

-- セッション内の1試合。v2 で使う列は nullable で先に用意してある
create table session_matches (
    id          bigserial primary key,
    session_id  bigint      not null references sessions (id) on delete cascade,
    seq         smallint    not null,
    result      varchar(8)  not null,                   -- WIN / LOSS / DRAW
    map_name    varchar(48),
    hero_names  varchar(128),
    replay_code varchar(16),
    note        text,
    constraint session_matches_unique_seq unique (session_id, seq)
);

-- OverFast API から取得したランクのスナップショット
create table rank_snapshots (
    id         bigserial primary key,
    user_id    bigint      not null references users (id),
    fetched_at timestamptz not null default now(),
    role_type  varchar(16) not null,
    tier       varchar(16) not null,
    division   smallint    not null,
    rank_value integer     not null,
    source     varchar(16) not null default 'OVERFAST'
);

create index idx_rank_snapshots_user on rank_snapshots (user_id, fetched_at desc);

-- 週次 AI レポート
create table weekly_reports (
    id           bigserial primary key,
    user_id      bigint      not null references users (id),
    week_start   date        not null,
    summary_md   text        not null,
    generated_at timestamptz not null default now(),
    constraint weekly_reports_unique_week unique (user_id, week_start)
);
