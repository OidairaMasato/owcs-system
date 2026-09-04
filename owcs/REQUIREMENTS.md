# OWCS ZETA ダッシュボード 要件メモ

作成: 2026-09-04
目標納期: **2026-10-04（OWCS 2026 Stage 3 開幕）に間に合わせる**

## 1. 何のためのシステムか

ZETA DIVISION の試合を **見逃さない** ための個人用ダッシュボード。
開いた瞬間に「次はいつ・誰と・どこで見るか」が分かることが唯一の価値。

### 前プロジェクト（OWランク記録）の失敗から得た原則

> **手入力が必要なシステムは使われなくなる。データが自動で溜まるものだけ作る。**

したがって本システムは **入力欄をひとつも作らない**。
ユーザーがやることは「開く」だけ。

## 2. スコープ

| フェーズ | 範囲 | 状態 |
|---|---|---|
| Phase 1 | ZETA DIVISION のみ（team id 135271） | 完了 |
| Phase 2 | OWCS Korea の全チーム | 完了 |

### Phase 2 での方針転換

**取り込みを「チーム軸」から「シリーズ軸」に変えた。**
チーム単位（`filter[opponent_id]`）で引くと 10 チームで 10 倍のリクエストになるが、
シリーズ単位（`filter[serie_id]`）なら **1 リクエストで 51 試合＝全チーム分**が揃う。
対象シリーズは設定で固定せず、リーグ配下のシリーズ一覧から地域名で絞り、新しい順に 2 本を追う。
ステージが変わっても設定を触る必要がない。

**チームは全て対等に扱う。** 推しチームを特別扱いするコードは書かない。
代わりに **最後に見たチームを端末に記憶**する（localStorage）。
これで自分の端末では常に ZETA が開き、他の人が使えばその人の選んだチームが開く。
Phase 1 の「開いた瞬間に答えが出る」という体験を、特別扱い無しで保つための設計。

構成・メタ分析は **対象外**（PandaScore にヒーロー/選手統計データが無いため。`detailed_stats: false`）。

## 3. データソース

PandaScore 無料プラン（検証済み・詳細は `data-source.md`）

- route prefix: `/ow/`（`/overwatch/` は Route not found）
- OWCS league id: **5223**（名前は `OCS`）
- ZETA team id: **135271**
- 単体取得は videogame 配下ではなく共通ルート: `/matches/{id}`
- レート制限: 1000 req/h

## 4. 画面

**リーグ**と**チーム**の 2 タブ。選んだタブも端末に記憶する。

### リーグタブ

- ステージの全試合を日付ごとに区切って表示。開いたときは「今日」あたりが見えている
- 進行中は LIVE 表示、終了分は勝者を強調、これからの試合には配信ボタン
- 選択中のチームが絡む試合には左に色帯を出す
- 下に全チームの順位表

### チームタブ（Phase 1 の画面）

上部でチームを選ぶ。以下は選んだチーム視点。

#### 次の試合カード（主役）

- 相手チーム名 + acronym
- 日時（**KST = JST なので変換不要**。`scheduled_at` は UTC なので +9h）
- 残り時間カウントダウン（あと 2日 5時間）
- 大会名 / ラウンド名（例: Korea Stage 3 2026 / Quarterfinal 3）
- **配信ボタン**（`streams_list` から。日本語配信があれば優先、無ければ official → main の順）
- 試合が近い/進行中なら見た目を変える（LIVE バッジ）

次の試合が無い期間（オフシーズン）は「次の試合は未定」と直近結果だけ出す。

#### 直近の結果

直近5試合を `W 4-2 TM` のような横並びチップで。タップでマップ別スコアを展開。

#### 今後の予定

`status = not_started` の試合を日付順に。

## 5. アーキテクチャ

```
PandaScore API
     │  定期取得（Spring @Scheduled）
     ▼
PostgreSQL（自前キャッシュ）
     │  REST
     ▼
React（PWA / スマホで開く前提）
```

**画面リクエスト時に PandaScore を叩かない。** 必ずキャッシュ経由。
理由: レート制限・API障害時も画面が死なない・トークンをフロントに出さない。

### 取得ジョブ

| ジョブ | 間隔 | 内容 |
|---|---|---|
| 試合同期 | 10分 | `/ow/matches?filter[serie_id]=<対象シリーズ>` を対象シリーズ数ぶん |
| 詳細同期 | 15分 | games が欠けている終了試合を `/matches/{id}` で最大5件 |
| 順位表 | 30分 | 対象シリーズの総当たり戦の standings |
| シリーズ一覧 | 6時間 | どのシリーズを追うかの決定（メモリにキャッシュ） |

合計 40 req/h 程度。上限 1000 に対して余裕。

無料ホスティングではスリープ中に定期実行が止まるため、
画面が要求された時点で前回同期から10分以上経っていればその場で取り込む（`SyncCoordinator`）。

## 6. テーブル設計（案）

```
teams      (id PK, name, acronym, slug, image_url, updated_at)
matches    (id PK, name, status, scheduled_at, begin_at, end_at,
            league_id, serie_name, tournament_name, match_type,
            number_of_games, winner_id,
            team_a_id, team_b_id, score_a, score_b,
            modified_at, synced_at, games_synced)
games      (match_id, game_no PK, status, winner_id, length_sec)
streams    (match_id, seq PK, raw_url, lang, is_main, is_official)
sync_state (job_key PK, last_success_at, last_error, updated_at)
```

OWCS の試合は常に 1 対 1 なので、対戦相手は別テーブルにせず matches の列に持つ（team_a / team_b）。
`game_no` は `position` を避けた名前（PostgreSQL の予約語まわりを踏まない）。

`modified_at` を PandaScore から持ち込み、変化した試合だけ更新する。

## 7. 技術スタック

前プロジェクトと同じ（資産流用のため）
- Java 21 / Spring Boot 3.3.4 / Spring Data JPA / Flyway / PostgreSQL 16
- React 19 / TypeScript / Vite 6
- package root: `jp.oidaira.owcs`

## 8. 秘密情報

PandaScore のアクセストークンは `.env` / 環境変数（`PANDASCORE_TOKEN`）。
**コード・チャット・Git に絶対に含めない。** `.gitignore` に `.env` を入れる。

## 9. 未決

- ホスティング（低コストPaaS）。Phase 1 はローカル起動で十分。
- 通知（試合前に Push）。PWA なので後から足せる。Phase 1 では作らない。
