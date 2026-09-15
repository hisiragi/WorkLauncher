# WorkLauncher

仕事道具をホーム画面にまとめた、Android 向けのホームアプリ（ランチャー）です。
アプリを起動するだけの入り口ではなく、勤怠の打刻・タスク・集中タイマー・会議の予定・
経費メモまでを 1 画面から扱えます。データはすべて端末内に保存され、外部には送信しません。

*A home-screen launcher for working professionals: time clock, tasks, Pomodoro focus,
calendar agenda, notes, quick contacts, expenses and screen-time insight — all offline.*

---

## 主な機能

### ホーム画面
- 大きな時計・日付・バッテリー残量と、勤務中／休憩中／勤務外のステータス表示
- ワンタップの **出勤・退勤・休憩** ボタン
- 今日のサマリー（実働時間・出勤時刻・集中時間・未完了タスク数）
- 今日の会議一覧（進行中の会議をハイライト）
- 今日のタスク（タップで完了切り替え）
- よく使うアプリと、お気に入りを並べたドック
- 上スワイプ、または検索バーからアプリドロワーへ

### アプリドロワー
- インクリメンタル検索（該当がなければそのままウェブ検索へ）
- **仕事／プライベート／ツール／未分類** のカテゴリ絞り込み
- 名前順・使用回数順・最近使った順の並び替え
- 長押しメニュー：ドックに追加、非表示、名前変更、集中の妨げに設定、アプリ情報、アンインストール

### 勤怠（Time clock）
- 日単位の打刻（出勤・退勤・休憩の開始／終了）
- 勤務場所（出社・在宅・客先・出張）と備考
- 週次／月次の集計：合計・残業・勤務日数・平均
- 過去の記録を 15 分単位で修正、休憩時間の手入力
- **CSV 書き出し**（共有シート経由、Excel 対応の BOM 付き）

### タスク
- 優先度 4 段階、期限（日付＋時刻）、案件タグ、見積もり時間
- 今日／今週／すべて／完了 のフィルタと案件別の絞り込み
- 未完了・期限切れ・今日完了のカウンタ
- タスクを指定して集中セッションを開始すると、集中した時間がそのタスクに積算されます

### 集中モード（ポモドーロ）
- 円形カウントダウン、集中／小休憩／長休憩の自動サイクル
- フォアグラウンドサービスで他アプリ使用中も継続、通知から一時停止・停止
- 集中中は「妨げになる」と設定したアプリを確認ダイアログでいったん止める
- 終了時のバイブレーションと次セッションの案内、今日の集中実績

### 予定
- 端末のカレンダーから会議を読み取り（読み取り専用）
- 1〜30 日先まで、日付ごとにグルーピング
- 今日の会議合計時間＝「会議の負荷」を可視化
- 場所欄が URL ならその会議リンクを、住所なら地図アプリを開きます

### メモ / クイック連絡先 / 経費メモ / 利用時間
- **メモ**：色分け・ピン留め・全文検索のスタガードグリッド
- **クイック連絡先**：電話帳から取り込み、または手入力。発信・SMS・メールをワンタップ
- **経費メモ**：科目別の月次集計、未精算額、CSV 書き出し
- **利用時間**：アプリ別のスクリーンタイムと、仕事／プライベートの内訳

### 設定
既定のホームアプリ設定への導線、テーマ（システム／ライト／ダーク）と Material You、
グリッド列数、アプリ名表示、24 時間表示、勤務時間・勤務日・所定労働時間、
ポモドーロの各長さ、検索エンジン、通貨記号、ドック／非表示／妨げアプリの一覧管理。

---

## 動作環境

| | |
|---|---|
| 最小 SDK | 26 (Android 8.0) |
| ターゲット SDK | 34 (Android 14) |
| 言語 | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |

## 技術構成

- **UI**：Jetpack Compose、Material 3（Dynamic Color 対応）、Navigation Compose
- **状態管理**：ViewModel + Kotlin Flow（`StateFlow` を画面ごとに 1 つ）
- **永続化**：Room（タスク・メモ・勤怠・集中セッション・連絡先・アプリ設定・経費）、
  設定は DataStore Preferences
- **DI**：`AppContainer` による手書きの依存グラフ（単一プロセス・単一 Activity のため
  DI フレームワークは使っていません）
- **集中タイマー**：`FocusController` が状態機械を持ち、`FocusTimerService`
  （`specialUse` フォアグラウンドサービス）が通知を鏡写しにします

```
app/src/main/java/jp/hisiragi/worklauncher/
├── core/          AppContainer, AppLauncher, ViewModelFactory
├── data/
│   ├── db/        Room のエンティティ・DAO・データベース
│   ├── repo/      アプリ一覧・カレンダー・利用時間・タスク・勤怠ほか
│   └── settings/  DataStore
├── domain/        ドメインモデルと列挙型
├── service/       集中タイマーの状態機械・通知・サービス
├── ui/            画面ごとのパッケージ（home, drawer, tasks, ...）
└── util/          時刻整形、Intent ヘルパー、CSV 書き出し
```

## ビルド

```bash
# Android SDK の場所を指定
echo "sdk.dir=/path/to/android-sdk" > local.properties

./gradlew :app:assembleDebug        # デバッグ APK
./gradlew :app:testDebugUnitTest    # ユニットテスト
./gradlew :app:assembleRelease      # R8 で最小化したリリース APK
```

インストール後、**設定 → アプリ → 既定のアプリ → ホームアプリ** で WorkLauncher を選ぶか、
アプリ内の 設定 → ランチャー → 既定のホームアプリ から切り替えてください。

### ローカルLLM（任意）

未設定なら関連機能は表示されず、アプリは通常どおり動作します。設定 → ローカルLLM から
次のいずれかを選ぶと、領収書の読み取り・通知のまとめ・チャットが有効になります。

**端末内モデル** — MediaPipe LLM Inference が動かす `.task` ファイルを端末に置き、
そのパスを設定します。完全オフラインで動作します。

```
/sdcard/Download/gemma3-1b-it-int4.task
```

**外部エンドポイント** — PC や Termux で動かす Ollama / llama.cpp server に接続します。
OpenAI 互換の `/v1/chat/completions` を使うため、URL とモデル名だけで繋がります。

```
URL:     http://192.168.1.10:11434
モデル名: gemma3:4b
```

領収書の読み取りは、まず端末内で OCR を行い、その文字列をモデルに渡して
金額・店名・日付・カテゴリを抽出します。そのため画像対応モデルは不要です。

通知のまとめには「通知へのアクセス」の許可が必要です（任意）。許可しない限り
通知は一切読み取られません。読み取った通知はメモリ上のみで保持し、保存しません。

> APK には端末内推論用のネイティブライブラリが含まれるため、ABI ごとに分割しても
> 30〜41MB 程度になります。外部エンドポイントのみを使う場合でもサイズは同じです。

### リリース署名

署名情報が無い場合は署名なし APK が出力されるだけで、ビルド自体は成功します。

ローカルで署名する場合は、リポジトリ直下に `keystore.properties`（git 管理外）を置きます。

```properties
storeFile=release.jks
storePassword=****
keyAlias=****
keyPassword=****
```

キーストアは次のように作成します。

```bash
keytool -genkeypair -v -keystore release.jks -alias worklauncher \
  -keyalg RSA -keysize 2048 -validity 10000
```

GitHub Actions で署名する場合は、リポジトリの Secrets に以下を登録します。
`SIGNING_KEYSTORE_BASE64` は `base64 -w0 release.jks` の出力です。

| Secret | 内容 |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | キーストアを base64 エンコードしたもの |
| `SIGNING_STORE_PASSWORD` | キーストアのパスワード |
| `SIGNING_KEY_ALIAS` | 鍵のエイリアス |
| `SIGNING_KEY_PASSWORD` | 鍵のパスワード |

**キーストアと `keystore.properties` は絶対にコミットしないでください**（`.gitignore` 済み）。

## 権限について

すべて任意です。許可しない機能は、その画面だけが空になります。

| 権限 | 用途 |
|---|---|
| `READ_CALENDAR` | 予定の表示（読み取りのみ） |
| `READ_CONTACTS` | 電話帳からクイック連絡先を取り込む時のみ |
| `POST_NOTIFICATIONS` | 集中タイマーの通知 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | 他アプリ使用中もタイマーを継続する |
| `PACKAGE_USAGE_STATS` | 利用時間の集計（システム設定から手動で許可） |
| `VIBRATE` | セッション終了の合図 |

アプリ一覧の取得には `QUERY_ALL_PACKAGES` ではなく、マニフェストの `<queries>` で
ランチャー起動可能なアクティビティのみを宣言しています。

## ライセンス

未設定です。
