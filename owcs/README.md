# OWCS Watch — 観戦ダッシュボード

OWCS の試合を見逃さないための個人用ダッシュボード。全地域と国際大会に対応。
**入力欄はひとつも無い。** データは PandaScore から自動で溜まる。

3 つのタブで見る。

- **今日** — 大会を選ばずに「いま OWCS で何があるか」。地域をまたいで今日前後の試合を並べる
- **リーグ** — 選んだ大会の日程と順位表
- **チーム** — 1 チームを追う。次の試合・連勝の帯・直近結果・対戦相手別の通算成績

上部の大会セレクトで OWCS の全地域と国際大会を切り替えられる。
チームは全て対等に扱い、最後に見たチームだけ端末に記憶する。

チームの日程は **iCalendar で購読**できる。カレンダーアプリが日程の変更を自動で取り直し、
通知も OS のカレンダーに任せられるので、Push を自前で実装しなくても見逃さずに済む。

- 要件: [REQUIREMENTS.md](REQUIREMENTS.md)
- データソースの検証記録: [data-source.md](data-source.md)

## 構成

```
owcs/
├─ backend/    Java 21 / Spring Boot 3.3.4 / Flyway / PostgreSQL 16   → :8081
└─ frontend/   React 19 / TypeScript / Vite 6                          → :5173
```

## 起動手順（Windows / PowerShell）

### 1. DB

```powershell
docker run -d --name owcs-db -e POSTGRES_DB=owcs -e POSTGRES_USER=owcs -e POSTGRES_PASSWORD=owcs -p 5433:5432 postgres:16
```

2回目以降は `docker start owcs-db`。
ポート 5433 は、旧 owlog 用の `owlog-db`（5432）と衝突させないため。

### 2. バックエンド

```powershell
cd owcs\backend
$env:PANDASCORE_TOKEN = "自分のトークン"
mvn spring-boot:run
```

トークンは **絶対にソースやコミットに含めない**。環境変数だけで渡す。

起動すると Flyway が V1 を流し、そのまま PandaScore から初回取り込みを行う。
ログの `sync ok: job=results count=15` のような行が出れば成功。

### 3. フロントエンド

```powershell
cd owcs\frontend
npm install
npm run dev -- --host
```

`--host` を付けるとスマホから `http://<PCのIP>:5173` で開ける。

### 4. PWA の確認

Service Worker は **本番ビルドでしか登録しない**（開発中に登録すると古いキャッシュで消耗するため）。

```powershell
cd owcs\frontend
npm run build
npm run preview
```

`http://localhost:4173` を開くと Service Worker が動く。
DevTools の Application タブで登録状況とキャッシュを確認できる。

**重要な制約**: Service Worker とインストールは **HTTPS か localhost でしか動かない**。
スマホから `http://192.168.x.x:5173` で開いた場合は通常の Web ページとして動作する
（iOS の「ホーム画面に追加」はアイコン付きで可能だが、オフライン動作はしない）。
フル機能にするには HTTPS でのホスティングが必要。

## 本番環境

| 役割 | サービス | プラン | 備考 |
|---|---|---|---|
| アプリ | Render Web Service (Singapore) | Free | Docker。15分の無操作でスリープする |
| DB | Neon PostgreSQL 16 (Singapore) | Free | 0.5GB / 100 CU時間。5分でゼロにスケール |
| ソース | GitHub `OidairaMasato/owcs-system` | - | `main` への push で自動デプロイ |

デプロイはリポジトリ直下の `Dockerfile` を使う。
フロントエンドのビルド成果物を Spring Boot の `static` に入れて**同一オリジンで配信**するため、
無料枠の「サービス1つ」という制約に収まり、CORS も不要になる。

### Render の環境変数

| Key | 内容 |
|---|---|
| `PANDASCORE_TOKEN` | PandaScore のアクセストークン |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<Neonのホスト>/owcsdb?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | Neon のロール名（`neondb_owner` など） |
| `SPRING_DATASOURCE_PASSWORD` | Neon のパスワード |

Neon の接続文字列は `postgresql://ユーザー:パスワード@ホスト/owcsdb?...` の形。
末尾に `&channel_binding=require` が付くが、**JDBC ドライバが解釈できないので削る**。

### スリープへの対処

無料プランは無操作でアプリが停止し、**その間 `@Scheduled` の定期取り込みが動かない**。
そこで `SyncCoordinator` を用意し、ダッシュボード要求時に前回同期から
`owcs.sync.max-age`（既定10分）以上経っていればその場で取り込む。

起き上がりに50秒ほどかかるが、Service Worker が前回のデータを即座に表示するので
体感の待ち時間にはならない。裏で起きたバックエンドが新しいデータを返し、次に開いたときに反映される。

## API

| メソッド | パス | 用途 |
|---|---|---|
| GET | `/api/league?serie={id}` | 画面が必要とするデータを 1 リクエストで返す。`serie` 省略時は開催中・直近の大会 |
| GET | `/api/today` | 今日前後の全試合（大会をまたぐ）。「今日」タブ用 |
| GET | `/api/team/{id}/head-to-head` | 対戦相手別の通算成績（大会をまたぐ） |
| GET | `/api/rankings?year={y}` | 年間ランキング（Elo）。`year` 省略時は最新の年 |
| GET | `/api/logo/{teamId}` | 自前で保持しているチームロゴ |
| GET | `/calendar/team-{id}.ics` | 試合日程の iCalendar。カレンダーアプリの購読用 |
| POST | `/api/sync?serie={id}` | PandaScore から取り込み直してから返す（画面の「今すぐ更新」） |

`/calendar/` を `/api/` 配下に置いていないのは、カレンダーアプリが定期取得するため
レート制限の対象から外す必要があるため。

## データ利用の条件

一般公開するにあたり、2026-09-04 に PandaScore へ次の 3 点を問い合わせ、
2026-09-07 に Customer Success Manager から回答を得ている。

| 確認したこと | 回答 |
|---|---|
| 無料プランで個人が非営利の公開サイトを運営してよいか | 規約の事業者向け条項は主に有料プラン向けのもので、この用途は歓迎 |
| `Source: PandaScore` の表記で出典表示として十分か | 出典表示自体が必須ではない |
| データを自前 DB に保存し自分の API から配信する方式 | ロゴを自前に持つ方式はむしろ推奨（`preferred for us!`） |

出典表示は必須ではないと言われたが、画面の `credit` にはそのまま残している。
どこから来たデータなのかを見た人が分かる方が誠実であり、外す理由が無いため。
非公式である旨と商標の注記は、PandaScore とは無関係に必要なので当然残す。

## 検索エンジンとSNS

一般公開してから足したもの。

| ファイル | 役割 |
|---|---|
| `frontend/public/og.png` | SNS のカード画像（1200×630）。これが無いと X では素の URL のまま出る |
| `frontend/public/robots.txt` | クロールを許可。`/api/` と `/calendar/` は中身が JSON なので除外 |
| `frontend/public/sitemap.xml` | 画面は 1 ページだけ（タブは URL を持たない）のでトップのみ |

`index.html` の `<body>` に `site-about` セクションを置いている。
画面本体は React が描くので、JS を実行しないクローラーには何も残らない。
`#root` の外に置くことで React に消されずに HTML へ残る。
**隠さず表示している**のは、隠すとクローキング（不正な最適化）になるため。

`og:image` は絶対 URL でなければ読まれない。ドメインを変えるときは
`index.html` の `og:` / `twitter:` / `canonical` と `robots.txt` の Sitemap 行を直すこと。

Render の無料枠は 15 分でスリープし、起き上がりに 50 秒かかる。
クローラーが来たときに寝ていると取得に失敗する。
検索流入を本気で狙うなら有料プランが要る。

## PWA の構成

ビルドプラグインは使わず手書き。

| ファイル | 役割 |
|---|---|
| `frontend/public/manifest.webmanifest` | アプリ名・アイコン・表示モード（standalone） |
| `frontend/public/sw.js` | Service Worker。キャッシュ戦略はファイル冒頭のコメント参照 |
| `frontend/public/icon-*.png` | アイコン。`maskable` は Android の切り抜き用に別途用意 |
| `frontend/src/main.tsx` | 本番ビルドのときだけ Service Worker を登録 |
| `frontend/index.html` | iOS はマニフェストを見ないので apple-touch-icon 等を個別指定 |

`/api/dashboard` は「ネットワーク優先・失敗したら前回のレスポンス」なので、
圏外や PC のバックエンドが落ちていても最後に取れた内容が見られる。
`sw.js` の `VERSION` を上げると古いキャッシュが破棄される。

### 踏んだ罠（どちらもオフライン時に画面が真っ白になる）

1. **ハッシュ付きアセットをプリキャッシュできない**
   Vite の成果物は `/assets/index-<hash>.js` とファイル名が毎回変わるので固定リストに書けない。
   さらに初回ロード時点では SW がまだ制御下に入っておらず、JS/CSS はネットワークから
   直接読まれるためキャッシュに残らない。
   → install 時に `index.html` を読んで `/assets/...` を正規表現で抽出し、明示的にキャッシュする。

2. **`Vary: Origin` でキャッシュがヒットしない**
   Vite の preview / 多くの静的サーバーは `Vary: Origin` を返す。
   Vite の成果物は `<script type="module" crossorigin>` で読まれるためページからの
   リクエストには `Origin` が付くが、SW が `cache.addAll` したときには付かない。
   この差で Vary 判定に落ち、キャッシュに入っているのに miss する。
   → `cache.match` には必ず `{ ignoreVary: true }` を渡す。

### 動作確認の手順（順番を間違えると必ず失敗する）

Unregister した直後にオフラインにすると、キャッシュが空なので当然失敗する。

1. Offline のチェックを外す
2. `Unregister`
3. **オンラインのまま**再読み込み → `activated and is running` を待つ
4. Cache storage に `shell-<VERSION>` ができたことを確認
5. Offline にチェック → 再読み込み

## 覚えておくこと

- **画面リクエストで PandaScore を叩かない。** 取り込みは `MatchSyncService` だけ。
- PandaScore の Overwatch ルート prefix は `/ow`。`/overwatch` は Route not found。
- 単体取得は `/matches/{id}`（videogame 配下ではない）。
- OWCS のリーグ名は `OCS`、id は 5223。ZETA の team id は 135271。
- **大会は地域名で絞らないこと。** `Midseason Championship` や `World Finals` は
  シリーズ名に地域を含まないため、地域で絞ると国際大会が丸ごと漏れる。開催期間で絞る。
- ネストしたルート `/ow/series/{id}/matches` は存在しない（Route not found）。
  `filter[serie_id]` を使う。
- **「取り込み済みか」を件数で判断しないこと。** 終わった大会を毎回引き直さないための判定に
  「その大会の試合が DB に 1 件でもあるか」を使ったところ、
  チーム軸で取り込んでいた頃の部分的なデータを取り込み済みと誤判定し、
  Midseason Championship が 28 試合中 5 試合しか入らない事故が起きた。
  いまは `sync_state` に `serie:<id>` の行を残し、**一括取得の記録**で判断している。
- 1 大会あたりの取得上限は 100 試合（`PER_PAGE`）。
  現状の最大は Korea Stage の 51 試合だが、超える大会が出たら分割取得が要る。
- 順位表は選んだ大会のものだけを出す。**他大会の順位表で代替しない。**
  大会を切り替えられるようにした際、Japan Stage を見ているのに Korea の順位表が出る混入が起きた。
- 順位表には 2 種類ある。勝敗入り（総当たり戦）と、**順位だけ**（ブラケット戦）。
  Midseason Championship や World Finals は後者で、`placementOnly` を立てて
  W / L / MAPS の列を出さずに「FINAL STANDINGS」として見せる。
- 候補を「名前が総当たりらしいもの」だけに絞ってはいけない。
  Playoffs が候補から消え、国際大会の最終結果を出せなくなる。優先はするが、残りも候補に残す。
- 取得済み判定は `sync_state` のキーで行い、**接頭辞に版番号を持たせている**
  （`serie:v2:` / `standings:v2:`）。選び方のロジックを変えたら版を上げるだけで全大会が作り直される。

### 既知の限界

- **複数グループ制の大会は片方のグループしか出ない**（例: Asia Stage 1 の Group A / B）。
  1 大会につき順位表を 1 つしか持たない設計のため。並べて出すには構造の変更が要る。
- 1 大会あたりの取得は 100 試合まで。現状の最大は 51 試合。
- OWCS の主要地域は KST / JST 開催で **KST = JST**。時差変換は不要。
- ログメッセージは ASCII。日本語だと PowerShell(CP932) で文字化けする。
- PowerShell 5.1 の `Invoke-RestMethod` は charset 無し JSON を ISO-8859-1 と誤認するため
  日本語が化けて見えるが、ブラウザでは正常。
