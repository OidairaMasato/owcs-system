-- チームロゴを自前で保持する。
-- PandaScore の規約は「PandaScore のインターフェースへの直リンク禁止、自サイトに再掲載せよ」
-- としているため、画像を取得して自分の API から配信する。
-- 1 チームあたり数十 KB、100 チームでも数 MB に収まる。

create table team_logos (
    team_id      integer primary key references teams (id) on delete cascade,
    source_url   text        not null,
    content_type text        not null,
    data         bytea       not null,
    fetched_at   timestamptz not null default now()
);
