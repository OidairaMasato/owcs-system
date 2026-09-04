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

| フェーズ | 範囲 |
|---|---|
| Phase 1 | ZETA DIVISION のみ（team id 135271） |
| Phase 2 | OWCS Korea の全チーム |

構成・メタ分析は **対象外**（PandaScore にヒーロー/選手統計データが無いため。`detailed_stats: false`）。

## 3. データソース

PandaScore 無料プラン（検証済み・詳細は `data-source.md`）

- route prefix: `/ow/`（`/overwatch/` は Route not found）
- OWCS league id: **5223**（名前は `OCS`）
- ZETA team id: **135271**
- 単体取得は videogame 配下ではなく共通ルート: `/matches/{id}`
- レート制限: 1000 req/h

## 4. 画面（Phase 1）

単一ページ。上から順に：

### 4-1. 次の試合カード（主役）

- 相手チーム名 + acronym
- 日時（**KST = JST なので変換不要**。`scheduled_at` は UTC なので +9h）
- 残り時間カウントダウン（あと 2日 5時間）
- 大会名 / ラウンド名（例: Korea Stage 3 2026 / Quarterfinal 3）
- **配信ボタン**（`streams_list` から。日本語配信があれば優先、無ければ official → main の順）
- 試合が近い/進行中なら見た目を変える（LIVE バッジ）

次の試合が無い期間（オフシーズン）は「次の試合は未定」と直近結果だけ出す。

### 4-2. 直近の結果ストリップ

直近5試合を `W 4-2 TM` のような横並びチップで。タップでマップ別スコアを展開。

### 4-3. 今後の予定リスト

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
| 予定同期 | 30分 | `/ow/matches?filter[opponent_id]=135271&filter[status]=not_started` |
| 結果同期 | 10分 | `/ow/matches?filter[opponent_id]=135271&sort=-begin_at&per_page=10` |
| 詳細同期 | 結果同期で status 変化を検知した試合のみ | `/matches/{id}` で games を取得 |

30分+10分でも 1時間あたり 8 req 程度。上限 1000 に対して余裕。

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
