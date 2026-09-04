# OWCS Korea — 観戦ダッシュボード

OWCS Korea の試合を見逃さないための個人用ダッシュボード。
**入力欄はひとつも無い。** データは PandaScore から自動で溜まる。

「リーグ」タブでステージ全体の日程と順位表、「チーム」タブで 1 チームを追う。
チームは全て対等に扱い、最後に見たチームだけ端末に記憶する。

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
| GET | `/api/dashboard` | 画面が必要とするデータを 1 リクエストで返す |
| POST | `/api/sync` | PandaScore から取り込み直してから返す（画面の「今すぐ更新」） |

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
- OWCS Korea は KST 開催で **KST = JST**。時差変換は不要。
- ログメッセージは ASCII。日本語だと PowerShell(CP932) で文字化けする。
- PowerShell 5.1 の `Invoke-RestMethod` は charset 無し JSON を ISO-8859-1 と誤認するため
  日本語が化けて見えるが、ブラウザでは正常。
