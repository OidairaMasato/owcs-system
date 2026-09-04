-- 1日に複数セッションを許し、RP を1試合ごとに持つ。
--
-- 変更点:
--   * sessions_unique_day を撤廃（朝と夜で別セッションになる実態に合わせる）
--   * sessions は「開始ランク」と「終了ランク」を持つ。tier/division/rp は rank_value から導出できるので落とす
--   * session_matches が rp_delta と、その試合を終えた時点の rank_value_after を持つ

alter table sessions drop constraint sessions_unique_day;
alter table sessions drop constraint sessions_division_range;
alter table sessions drop constraint sessions_rp_range;

alter table sessions add column started_at timestamptz not null default now();
alter table sessions add column start_rank_value integer;
update sessions set start_rank_value = rank_value;
alter table sessions alter column start_rank_value set not null;

alter table sessions drop column tier;
alter table sessions drop column division;
alter table sessions drop column rp;

create index idx_sessions_user_started on sessions (user_id, started_at desc);

alter table session_matches add column rp_delta smallint not null default 0;
alter table session_matches add column rank_value_after integer;
update session_matches m set rank_value_after = s.rank_value from sessions s where s.id = m.session_id;
alter table session_matches alter column rank_value_after set not null;
alter table session_matches alter column rp_delta drop default;
