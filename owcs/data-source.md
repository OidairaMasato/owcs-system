# データソース検討メモ（OWCS 観戦ダッシュボード）

## 結論

**PandaScore の無料プランを第一候補とする。** Liquipedia は要件が合わないため見送り。

## 比較

| | Liquipedia | PandaScore |
|---|---|---|
| 申請 | フォーム申請＋審査 | セルフサービス登録、**カード不要** |
| 条件 | **コードのオープンソース化が必須**、無料枠は期限付きが原則 | 無料プランは恒久 |
| レート制限 | LiquipediaDB API は **60リクエスト/時** | **1,000リクエスト/時** |
| 日程 | あり | あり（過去・未来のカレンダー） |
| 結果 | あり | あり |
| チーム・選手・大会 | あり（ただしコミュニティ編集で鮮度にムラ） | あり（pre-match data） |
| 試合後の詳細統計 | 限定的 | **有料（€400/月〜）** |
| 帰属表示 | CC BY-SA 3.0 で必須 | 規約要確認 |

## 未確認（最優先で潰すこと）

**PandaScore が OWCS Korea をカバーしているか。** 公式ドキュメントには対応リーグの一覧がなく、
OWCS への言及もない。ここが空振りだと、この案は成立しない。

登録後、以下で確認する。

```powershell
$token = "取得したトークン"
$h = @{ Authorization = "Bearer $token" }

# 1. Overwatch のリーグ一覧に OWCS があるか
Invoke-RestMethod -Uri "https://api.pandascore.co/overwatch/leagues?per_page=50" -Headers $h |
  Select-Object id, name, slug

# 2. 今後の試合が取れるか
Invoke-RestMethod -Uri "https://api.pandascore.co/overwatch/matches/upcoming?per_page=20" -Headers $h |
  Select-Object begin_at, name, @{n='league';e={$_.league.name}}

# 3. ZETA DIVISION が存在するか
Invoke-RestMethod -Uri "https://api.pandascore.co/overwatch/teams?search[name]=ZETA" -Headers $h |
  Select-Object id, name, acronym

# 4. 選手が取れるか（Viol2t）
Invoke-RestMethod -Uri "https://api.pandascore.co/overwatch/players?search[name]=Viol2t" -Headers $h |
  Select-Object id, name, first_name, last_name, role
```

## 収益化を前提にした場合の論点（2026-09-03 追記）

将来的に広告モデルで収益化したい意向があるため、「非営利限定」の無料枠は使えない可能性がある。

- **Liquipedia の無料枠は非営利が条件**。将来の広告掲載と矛盾するため見送り。
- **PandaScore の無料プランが商用利用可かは公開情報にない。** 登録時に必ず問い合わせること。
  不可なら有料は月 €400〜（約6.5万円／月）で、広告収入で賄うには相当のPVが必要になる。
- **原価が先に立つモデルは個人の広告サイトとして最も厳しい。** 収益より先に固定費が来るため。
  データ原価ゼロで始められるかどうかが、この題材の成否を分ける。

## わかっている制約

- **試合後の詳細統計は無料枠に入らない。** つまり選手ごとの成績やヒーロー構成の分析
  （フェーズ3で作りたかったメタ系）は、無料の範囲では届かない可能性が高い。
  日程・結果・チーム・選手（フェーズ1・2）は無料枠で足りる見込み。
- API キーは秘密情報。リポジトリに入れず、環境変数で渡すこと。

## 空振りだった場合の次の候補

- OWCS 公式サイトの日程・順位ページ（自動取得の可否は要確認）
- ZETA DIVISION 公式サイトの試合日程（フェーズ1がZETA限定なら十分な可能性）
- Twitch / YouTube の配信スケジュール（VOD 側から攻める）

---

## 検証結果（2026-09-04・PandaScore 無料プランの実トークンで確認）

結論: **4つの関門すべて通過。PandaScore で OWCS ZETA ダッシュボードは成立する。**

| 確認項目 | 結果 | 値 |
|---|---|---|
| Overwatch の対応 | ○ | videogame id=14 / **slug = `ow`** |
| OWCS リーグの存在 | ○ | league id=**5223** / name `OCS` / slug `ow-overwatch-champions-series` |
| Korea シリーズの存在と鮮度 | ○ | 2024〜2026 まで継続。Korea Stage 2 2026 = series id **10681** |
| ZETA DIVISION | ○ | team id=**135271** / slug `zeta-division-ow` / acronym `ZETA` |
| 試合データ | ○ | `filter[opponent_id]=135271` でスコア付きで取得可（例: 2026-08-02 Grand final ZETA 4-2 TM） |

### ハマりどころ

- ルートの prefix は **`/ow/`**。`/overwatch/...` は `{"error":"Route not found"}` を返す（認証エラーではない）。
- リーグ名は `OWCS` ではなく **`OCS`**。名前での検索では見つからないので **id 5223 直指定**が確実。
- シリーズは地域×ステージで別レコード（`Korea Stage 2 2026` など）。`filter[league_id]=5223` で一覧し、`full_name` に `Korea` を含むものを拾う。

### 商用利用について

趣味プロジェクトとして作るため、当面は確認不要。
将来 広告・アフィリエイトを載せる段階になったら、**無料プランでの商用利用可否を PandaScore サポートに問い合わせる**こと（規約に明記がない）。

### セキュリティ注意

アクセストークンはチャットや Git に絶対に貼らない。`.env` / 環境変数に置き、`.gitignore` に入れる。
