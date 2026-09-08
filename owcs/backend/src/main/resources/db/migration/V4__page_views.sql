-- アクセス数の集計。
--
-- 1 訪問 1 行ではなく、日ごとの合計だけを持つ。
-- 個人を追跡しないので、IP もセッション識別子も一切保存しない。
-- 知りたいのは「何回見られたか」と「どこから来たか」だけである。
-- 行数は 1 日あたり数行にしかならないので、無料枠の容量も気にしなくてよい。
create table page_views (
    day           date    not null,
    referrer_host text    not null,   -- 空文字 = 直接アクセス（ブックマーク等）
    device        text    not null,   -- mobile / desktop
    views         integer not null default 0,
    primary key (day, referrer_host, device)
);
