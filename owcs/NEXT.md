# 次にやること

最終更新: 2026-09-04（順位表・UIスタイル確定・PWA化まで完了）

## 現在地

**Phase 1（ZETA 版）の中核が動作**。手入力ゼロで、開くだけで情報が揃う状態。

動作確認済み:
- Flyway V1 適用 → Hibernate validate 通過
- 起動時に PandaScore から 15 試合を自動取り込み
- 「直近の結果」に Midseason Championship 2026 の 5 連勝が並ぶ
- 行をタップしてマップ別の勝敗と所要時間が展開される
- UTC → JST の変換が正しい（8/2 15:37 UTC → 8/3(月) 00:37）
- 順位表（Korea Stage 2 2026 · Group Stage）が出て、ZETA の行が強調される
- PWA: Service Worker が登録され、**オフラインでも全画面が表示される**
  （`npm run preview` の本番ビルドで、初回ロード → 即オフラインの手順で検証済み）

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
| 試合前の通知 | 見逃さないという目的に直結。PWA の Push が必要 | 中 |
| Phase 2: Korea 全チーム | 当初からの計画。`owcs.team-ids` を増やすだけでは足りず、画面の作り直しが要る | 重 |
| ホスティング（低コスト PaaS） | PC を起動していなくても見られる。**PWA をスマホでフル機能にするにも必須**（HTTPS が要る）。通知をやるなら当然必須 | 中 |

## 節目

- **2026-10-04** OWCS 2026 Stage 3 開幕 ← ここまでに「次の試合」が実データで出る状態にする
- 2026-12-03〜07 World Finals

## 旧プロジェクト

`../backend` `../frontend` は OW ランク記録アプリ（中止）。動くが使わない。
再利用するものは無いので、整理するなら削除してよい。
