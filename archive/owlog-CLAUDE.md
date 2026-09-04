# OW Session Log

Overwatch 2 のランク戦績を記録・分析する個人用 Web アプリ。

詳しい背景と決定事項は `REQUIREMENTS.md` を読むこと。

## 最優先の設計原則

**入力が数タップで終わること。** Overwatch には試合履歴 API がないため、データは必ず手入力になる。
入力が面倒になった瞬間にこのシステムは死ぬ。機能追加の提案は常に「入力が遅くならないか」で判断する。

目標値: 1セッションの記録を **30秒以内・15タップ以内**。

## 構成

```
backend/   Java 21 / Spring Boot 3 / PostgreSQL / Flyway
frontend/  React 19 / TypeScript / Vite（PWA 化予定）
```

## 規約

- パッケージルートは `jp.oidaira.owlog`
- DTO は record を使う。エンティティを API に直接出さない
- DB のカラム名は snake_case。`role` `condition` は予約語を避けて `role_type` `condition_level`
- マイグレーションは Flyway。既存の migration ファイルは絶対に書き換えず、新しい V番号 を足す
- 全テーブルに `user_id` を持たせる（将来のマルチユーザー対応。v1 は seed した 1 ユーザーのみ）

## ランクの数値化

ティア × ディビジョン × RP を一本の整数軸 `rank_value` に落として推移グラフを描く。

```
rank_value = tierIndex * 500 + (5 - division) * 100 + rp
tierIndex: ブロンズ0 シルバー1 ゴールド2 プラチナ3 ダイヤ4 マスター5 GM6 チャンピオン7
```

`RankValue.java` が唯一の実装。フロントで再実装しないこと（API が返す値を使う）。

## 実装の進め方

1. **v1-a** セッション記録の CRUD と一覧 — 実装済み
2. **v1-b** ランク推移グラフ — 実装済み（`TrendView.tsx` / `RankChart.tsx`）← いまここ
3. **v1-c** PaaS デプロイ、認証、PWA 化
4. **v1-d** OverFast API 連携（ランク自動取得）
5. **v2** 試合詳細、マップ／ヒーロー別勝率、週次 AI レポート

`session_matches` には v2 用の `map_name` `hero_names` `replay_code` `note` を nullable で先に用意してある。
v2 でマイグレーションが要らないようにするための措置。

## セッションと試合の関係（V3 で変更）

- **1日に複数セッションを持てる**（朝と夜は別セッション）。日付ではなく `started_at` で並べる
- `sessions` が持つランクは **開始値 `start_rank_value` と終了値 `rank_value`** の2つ。
  `tier` / `division` / `rp` のカラムは持たない（`rank_value` から導出できるため V3 で落とした）
- **RP は1試合ごとに `session_matches.rp_delta` へ持つ。** リザルト画面の数字（+22 / −18）をそのまま入れる
- `rank_value_after` はその試合を終えた時点のランク。将来「何試合目で崩れたか」を見るために持っている
- 終了ランクはクライアントから受け取らない。`Session.addMatch()` が積み上げて決める

## 入力UIの参照モック

https://claude.ai/code/artifact/e1d9c45b-7eed-4c6d-9cc3-5fe305972197

このモックの操作感が正。画面を作るときはこれに寄せる。

## グラフの実装方針

チャートライブラリは入れず、`RankChart.tsx` で SVG を直接組む。バンドルを膨らませないため。

- ランクはロール別に独立しているので、**ロールを跨いで1本の線にしない**。`TrendView` で絞り込んでから渡す
- 単一系列なので凡例は置かない。終点だけドットで示す
- 背景の横線はティアの境目（500刻み）。飾りの目盛りは置かない
- **SVG のプレゼンテーション属性に `var()` を書かない**（ブラウザによって効かない）。色はすべて CSS クラス経由で当てる
- スマホ前提なので `onPointerDown` も拾い、タップでツールチップが出るようにする
