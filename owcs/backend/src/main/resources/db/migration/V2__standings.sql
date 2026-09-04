-- 順位表。試合が属するシリーズの Group Stage を自動で選んで取り込む。

alter table matches add column serie_id integer;
alter table matches add column tournament_id integer;

create index idx_matches_serie on matches (serie_id);

create table tournaments (
    id         integer primary key,      -- PandaScore の tournament id
    serie_id   integer,
    serie_name text,
    name       text,                     -- 例: "Group Stage" / "Playoffs"
    slug       text,
    begin_at   timestamptz,
    updated_at timestamptz not null default now()
);

create index idx_tournaments_serie on tournaments (serie_id, begin_at);

create table standings (
    tournament_id integer  not null references tournaments (id) on delete cascade,
    team_id       integer  not null references teams (id),
    rank_no       smallint,                -- PandaScore の rank。同率があるので連番ではない
    wins          smallint,
    losses        smallint,
    game_wins     smallint,
    game_losses   smallint,
    primary key (tournament_id, team_id)
);

create index idx_standings_rank on standings (tournament_id, rank_no);
