# 次にやること

最終更新: 2026-09-05（今日タブ・カレンダー購読・対戦相手別成績を追加）

## 現在地

**Phase 3（全地域＋国際大会）まで完了**。手入力ゼロで、開くだけで情報が揃う状態。

取り込みは**大会（シリーズ）軸**。`filter[serie_id]` で 1 リクエスト = 1 大会の全試合。
対象は地域ではなく**開催期間**で絞る（地域名で絞ると国際大会が漏れるため）。
チームは全て対等に扱い、最後に見たチームだけ端末に記憶する。

動作確認済み:
- Flyway V1 適用 → Hibernate validate 通過
- 起動時に PandaScore から 15 試合を自動取り込み
- 「直近の結果」に Midseason Championship 2026 の 5 連勝が並ぶ
- 行をタップしてマップ別の勝敗と所要時間が展開される
- UTC → JST の変換が正しい（8/2 15:37 UTC → 8/3(月) 00:37）
- 順位表（Korea Stage 2 2026 · Group Stage）が出て、ZETA の行が強調される
- PWA: Service Worker が登録され、**オフラインでも全画面が表示される**
  （`npm run preview` の本番ビルドで、初回ロード → 即オフラインの手順で検証済み）
- **本番稼働**: Render (Singapore, Free) + Neon (Singapore, Free)。
  GitHub `OidairaMasato/owcs-system` の `main` への push で自動デプロイ。
  遅延同期（`SyncCoordinator`）がスリープ明けに発火することをログで確認済み。

## UI の決定事項

- スタイルは **Broadcast**（中継グラフィック風）。数字は斜体・太字・等幅数字、
  見出しは英字大文字＋広いトラッキング、アクセントは赤 1 色に絞る。
- ベースカラーは **Midnight Navy**（`--bg: #070c18`）。
  黒に青みを入れることで、アクセントの赤が浮かずに馴染む。
  ZETA のチームカラー（黒・白・赤）と競合しないのも理由。
- 色はすべて CSS 変数なので、ライトテーマを後から足すのは容易。
- 検討した案は `design-variants.html`（スタイル3案）と
  `design-colors.html`（ベースカラー5案）に残してある。

未確認:
- 「次の試合」カードの本番表示。Stage 3 の日程が PandaScore に載るまで待ち
- 配信ボタン（`streamUrl`）の本番表示。同上
- LIVE バッジ。試合中にしか出ない

## ハマった点（同じ轍を踏まないために）

- **順位表のトーナメント選びは名前だけでは決められない。**
  Midseason Championship の "Group A/B" は名前は総当たりでも
  PandaScore に勝敗レコードが入っておらず、勝敗が全部空の表が出た。
  いまは「勝敗が 1 件でも入っているか」を検証し、駄目なら次の候補シリーズへ遡る。
- **トークンは環境変数に永続化する。**
  `[Environment]::SetEnvironmentVariable("PANDASCORE_TOKEN", "...", "User")`
  設定後に開いたウィンドウにしか反映されない。
  コマンド例にトークン代入行を含めると、ログを貼るときに一緒に漏れる。
- **Service Worker のキャッシュは 2 箇所で外れる。** 詳細は README の「踏んだ罠」。
  (1) Vite のハッシュ付きアセットは固定リストにできない → install 時に index.html から抽出
  (2) `Vary: Origin` で cache.match が miss する → `{ ignoreVary: true }` が必須
- `sw.js` は `public/` にあるので、**`npm run build` しないと `dist/` に反映されない。**
- ポートは backend 8081 / frontend 5173 / preview 4173 / DB 5433。旧 owlog（8080 / 5432）と別。
- 二重起動すると `Port 8081 was already in use`。
  `Get-NetTCPConnection -LocalPort 8081 -State Listen` で確認する（「見つかりません」＝空き）。

## 候補（優先度は未決）

| やること | 効果 | 重さ |
|---|---|---|
| 試合前の通知（Web Push） | カレンダー購読で当面は代替できている。自前 Push が要るのは「試合開始5分前に確実に鳴らす」までやりたくなったとき | 中 |
| スマホのホーム画面に追加 | 実際に使うための最後の一歩 | 軽 |
| 選手情報 | PandaScore の players で名前・ロールは取れる。ただし統計は無い | 中 |

## 確認できていること

- 取り込み済みの大会（2026-09-04 時点、16件・全大会に順位表あり）:
  Midseason Championship 2026 / Champions Clash 2026 / World Finals 2025 /
  Korea・Japan・Pacific・China・North America・EMEA の Stage 1 と Stage 2 / Asia Stage 1
- 国際大会（Midseason / Champions Clash / World Finals）は順位のみの表として表示される。
- 既知の限界: 複数グループ制の大会（Asia Stage 1）は片方のグループしか順位表に出ない。
- 2026-09-05 追加: 「今日」タブ（地域横断）、iCalendar 購読、対戦相手別の通算成績。
- Stage 3 と World Finals 2026 は PandaScore にまだ未登録。
  登録されれば 6 時間以内に大会一覧へ自動で入り、予定ができた時点で既定の表示もそちらへ移る。

## 節目

- **2026-10-04** OWCS 2026 Stage 3 開幕 ← ここまでに「次の試合」が実データで出る状態にする
- 2026-12-03〜07 World Finals

## 旧プロジェクト

`../backend` `../frontend` は OW ランク記録アプリ（中止）。動くが使わない。
再利用するものは無いので、整理するなら削除してよい。
