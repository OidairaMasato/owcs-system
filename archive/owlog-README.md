# OW Session Log

Overwatch 2 のランクセッションを記録して、推移を眺めて、AI に振り返らせる個人用アプリ。

## 必要なもの

- **JDK 21 以上**（Spring Boot 3 の要件。`java -version` で確認）
- **Node.js 20 以上**
- **PostgreSQL 16**（Docker でも、インストール版でも可）
- Maven（IntelliJ / VS Code の Maven 統合でも可）

## 1. データベースを用意する

### Docker がある場合

```powershell
docker run -d --name owlog-db `
  -e POSTGRES_DB=owlog -e POSTGRES_USER=owlog -e POSTGRES_PASSWORD=owlog `
  -p 5432:5432 postgres:16
```

2回目以降は起動するだけ。

```powershell
docker start owlog-db
```

### インストール版 PostgreSQL を使う場合

```powershell
psql -U postgres -c "CREATE USER owlog WITH PASSWORD 'owlog';"
psql -U postgres -c "CREATE DATABASE owlog OWNER owlog;"
```

スキーマは作らなくてよい。backend の起動時に Flyway が作り、初期ユーザーも1件入れる。

## 2. backend を起動する

```powershell
cd C:\Users\masat\04_Claude\hobby-system\backend
mvn spring-boot:run
```

`http://localhost:8080` で起動する。

接続先を変えたいときは環境変数で上書きする。

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/owlog"
$env:DB_USER="owlog"
$env:DB_PASSWORD="owlog"
```

## 3. frontend を起動する

別のターミナルで。

```powershell
cd C:\Users\masat\04_Claude\hobby-system\frontend
npm install
npm run dev
```

`http://localhost:5173` を開く。`/api` は backend にプロキシされる。

同じ Wi-Fi のスマホから触りたいときは、ホストを開放して起動する。

```powershell
npm run dev -- --host
```

表示された `Network:` の URL をスマホで開く。

## 4. 動くか確かめる

記録するのは **開始ランク** と **各試合の RP 増減**。終了ランクはサーバー側が積み上げて返す。

```powershell
$body = @{
  roleType    = "SUPPORT"
  startTier   = "PLATINUM"; startDivision = 3; startRp = 20
  conditionLevel = "NORMAL"
  matches = @(
    @{ result = "WIN";  rpDelta =  22 },
    @{ result = "WIN";  rpDelta =  21 },
    @{ result = "LOSS"; rpDelta = -18 },
    @{ result = "WIN";  rpDelta =  23 }
  )
} | ConvertTo-Json -Depth 3

Invoke-RestMethod -Uri http://localhost:8080/api/sessions -Method Post `
  -ContentType "application/json" -Body $body
```

`ConvertTo-Json` の **`-Depth 3` を忘れないこと**。既定の深さでは matches の中身が文字列に潰れて 400 になる。

`rank.label` が `プラチナ 2`、`rpChange` が `48` で返ってくれば、RP の積み上げとディビジョンの繰り上がりまで通っている
（20 + 22 + 21 − 18 + 23 = 68 → 100 を超えた分がディビジョンに繰り上がる）。

**同じ日に何回でも投げられる。** 1日に複数セッションを持てるようにしてあるので、上のコマンドをもう一度実行すれば2件目が入る。
推移グラフは2件以上で描画される。

一覧と推移を確認する。

```powershell
Invoke-RestMethod http://localhost:8080/api/sessions | ConvertTo-Json -Depth 6
Invoke-RestMethod http://localhost:8080/api/rank-history | ConvertTo-Json -Depth 5
```

## API

| メソッド | パス | 用途 |
|---|---|---|
| POST | `/api/sessions` | セッションを記録する |
| GET | `/api/sessions` | セッション一覧（新しい順） |
| GET | `/api/sessions/latest` | 直近のセッション（入力初期値の取得用） |
| GET | `/api/rank-history` | ランク推移グラフ用の時系列 |

## つまずいたら

| 症状 | 原因 |
|---|---|
| `release version 21 not supported` | JDK が 21 未満。`JAVA_HOME` を JDK 21 に向ける |
| `Connection to localhost:5432 refused` | DB が起動していない。`docker start owlog-db` |
| 画面に「サーバーに繋がりません」 | backend が落ちている。8080 が空いているか確認する |
| POST が 400 | PowerShell の `ConvertTo-Json` に `-Depth 3` を付け忘れている |
| `column "tier" does not exist` | Flyway の V3 が未適用。backend を再起動する |
