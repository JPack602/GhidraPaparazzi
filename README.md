# Ghidra MCP Decompiler Capture Add-on

[日本語](README.md) | [Windows セットアップ](#windows-でのインストール) | [使用方法](docs/USAGE.md) | [トラブルシューティング](docs/TROUBLESHOOTING.md)

[bethington/ghidra-mcp](https://github.com/bethington/ghidra-mcp) に、**Ghidra の Decompiler ペインの PNG 撮影**と、**ラベル・赤枠付き証拠画像の保存**を追加する非公式アドオンです。既存の Ghidra MCP の GUI プラグインへ Java のサービスを組み込み、追加 MCP ツールを利用します。

> **対象バージョン（検証に使用した組み合わせ）**：bethington/ghidra-mcp **v7.0.0**、Ghidra **12.1.4**、Java **21**、Windows PowerShell。Ghidra 10.3.2 や他の MCP 実装にはこのまま適用できません。アドオンの元の設計は試作版ですが、ユーザー環境で拡張 ZIP のビルド成功と動作成功の報告を受けています。再現環境を備えた自動 GUI テストは未整備です。

## 機能

| MCP ツール | 動作 |
| --- | --- |
| `capture_decompiler_png` | **今見えている Decompiler ペイン**を PNG に保存。`tool_goto_address` で先に対象関数へ移動可能。`expected_address` によって表示対象を検証。 |
| `capture_evidence` | アドレスへ移動し、対象関数のデコンパイル完了を待って撮影。画像上部にラベル・関数情報を付け、赤枠で全体を囲む。 |

- デスクトップ全体ではなく Decompiler の**表示領域**を撮影します。スクロール外のコードを含む「関数全体キャプチャ」には未対応です。
- `capture_evidence` の赤枠は**画像全体**であり、個々のコード行やトークンを強調するものではありません。
- 出力は **ローカル PNG ファイルと保存先パスを含む応答**です。AI クライアントへの画像データ（MCP `ImageContent`）の直接返却は未実装です。
- Ghidra **GUI 必須**です。headless モード、MechaGhidra 単体、LaurieWired 版などには対応しません。

## ディレクトリ構成

```text
ghidra-mcp-decompiler-capture/
├─ README.md                       # このファイル
├─ apply_addon.py                  # bethington版のソースにパッチを適用
├─ scripts/
│  └─ Build-Windows.ps1            # 版数チェック + パッチ適用 + ビルド
├─ src/main/java/com/xebyte/core/
│  └─ DecompilerCaptureService.java
├─ docs/
│  ├─ USAGE.md                     # MCP / HTTP 使用例
│  ├─ TROUBLESHOOTING.md           # よくある失敗と対処
│  └─ GITHUB.md                    # GitHub 管理方法（アドオン単体／fork）
├─ tests/
│  └─ test_apply_addon.py          # 適用スクリプトの単体テスト
├─ .gitignore
└─ LICENSE
```

**重要：** このリポジトリには上流 `ghidra-mcp` 全体と Ghidra 本体のバイナリは含めていません。上流 **v7.0.0** をクローンし、このアドオンを適用して拡張 ZIP を再生成します。実際にビルド済みの `GhidraMCP-7.0.0.zip` は各環境で生成してください。

## Windows でのインストール

### 1. 前提条件

- Windows 10 / 11、PowerShell
- **Ghidra 12.1.4**（[公式 GitHub リリース](https://github.com/NationalSecurityAgency/ghidra/releases/tag/Ghidra_12.1.4_build)）
- **Java JDK 21**（`java -version` で確認）
- Git、Python 3（`python --version` または `py -3 --version` で確認）
- Gradle の別途インストールは不要（上流の `gradlew.bat` を使用）

**重要：Ghidra 10.3.2 をビルド対象に指定しないでください。** v7.0.0 の `pom.xml` は Ghidra 12.1.4 を要求します。実際に 10.3.2 を指定すると `ReturnCommitOption` / `DebuggerTargetService` / `TraceRmiLauncherService` などで大量の `compileJava` エラーが発生しました。旧 Ghidra はアンインストールせず並行して保持できます。

### 2. 配置

例えば次のように隣接させます。

```text
C:\ghidra-work\
├─ ghidra-mcp\                    # bethington版 v7.0.0
└─ ghidra-mcp-decompiler-capture\ # 本リポジトリ
```

```powershell
New-Item -ItemType Directory -Force C:\ghidra-work | Out-Null
Set-Location C:\ghidra-work

git clone --branch v7.0.0 https://github.com/bethington/ghidra-mcp.git
# 次の行は、ご自身の GitHub に置いた本アドオンの URL に置き換えてください
# git clone https://github.com/<owner>/ghidra-mcp-decompiler-capture.git
```

このリポジトリの ZIP をダウンロードした場合は `C:\ghidra-work\ghidra-mcp-decompiler-capture` に展開しても構いません。

### 3. Ghidra の場所を指定してビルド（推奨・1コマンド）

```powershell
Set-Location C:\ghidra-work\ghidra-mcp-decompiler-capture

.\scripts\Build-Windows.ps1 `
  -GhidraDir 'D:\Program Files\ghidra_12.1.4_PUBLIC' `
  -GhidraMcpDir 'C:\ghidra-work\ghidra-mcp'
```

`-GhidraDir` は**実際に Ghidra 12.1.4 を展開したフォルダ**に変更します（上記の `D:\...` は例です）。このスクリプトは版数をチェックし、`apply_addon.py` を実行した後、Gradle で `clean buildExtension` します。上流リポジトリの checkout が変更済みの場合は、不用意に上書きしないよう既存ファイルをチェックしてください。

**PowerShell の実行ポリシーによりブロックされたとき**は、現在のセッション限定で次のように実行できます。

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Build-Windows.ps1 `
  -GhidraDir 'D:\Program Files\ghidra_12.1.4_PUBLIC' `
  -GhidraMcpDir 'C:\ghidra-work\ghidra-mcp'
```

### 4. 手動ビルド（代替）

```powershell
Set-Location C:\ghidra-work
python .\ghidra-mcp-decompiler-capture\apply_addon.py .\ghidra-mcp

Set-Location .\ghidra-mcp
$GhidraDir = 'D:\Program Files\ghidra_12.1.4_PUBLIC'

Get-Content "$GhidraDir\Ghidra\application.properties" |
    Select-String '^application.version='
# application.version=12.1.4 を確認

.\gradlew.bat clean buildExtension "-PGHIDRA_INSTALL_DIR=$GhidraDir"
Get-ChildItem .\build\distributions\ -Filter 'GhidraMCP-*.zip'
```

成功時の成果物は通常、次の場所です。

```text
C:\ghidra-work\ghidra-mcp\build\distributions\GhidraMCP-7.0.0.zip
```

### 5. Ghidra に拡張をインストール

1. **Ghidra 12.1.4** のプロジェクトウィンドウを起動する。
2. **File → Install Extensions** を開き、旧 GhidraMCP 拡張との重複を避ける。
3. `+` から上記 `GhidraMCP-7.0.0.zip` を選択する。
4. Ghidra を再起動し、**File → Configure → Utility** で `GhidraMCPPlugin` を有効化する。
5. CodeBrowser で解析対象の Program を開き、**Window → Decompiler** を表示する。
6. MCP クライアント側のブリッジも再起動する（新ツールを再検出させる）。

既存の Ghidra プロジェクトを新しい Ghidra で開く前にはバックアップしてください。

### 6. 接続・ツール登録を確認

```powershell
Invoke-RestMethod 'http://127.0.0.1:8089/check_connection'

$schema = Invoke-RestMethod 'http://127.0.0.1:8089/mcp/schema'
$schema | ConvertTo-Json -Depth 30 |
    Select-String 'capture_decompiler_png|capture_evidence'
```

HTTP ポート `8089` は標準例です。設定を変更した場合は実際のポートに読み替えてください。ブリッジが遅延読み込みの場合、MCP クライアントで `search_tools` → `load_tool_group`（`utility`）を行ってください。

### 7. 実際に撮影する

PowerShell の例（Decompiler ペインを表示した状態で実行）：

```powershell
$body = @{
  output_path = 'decompiler_test.png'
  timeout_ms = 10000
} | ConvertTo-Json

Invoke-RestMethod -Uri 'http://127.0.0.1:8089/capture_decompiler_png' `
  -Method Post -ContentType 'application/json' -Body $body
```

証拠画像の例：

```powershell
$body = @{
  address = '0x00401234'               # 対象に合わせて変更
  output_path = 'evidence_00401234.png'
  label = 'Validation logic'
  timeout_ms = 10000
} | ConvertTo-Json

Invoke-RestMethod -Uri 'http://127.0.0.1:8089/capture_evidence' `
  -Method Post -ContentType 'application/json' -Body $body
```

デフォルト保存先は `%USERPROFILE%\GhidraMCP-Captures` です。既存ファイルは**上書きしません**。アドレスが関数の内部を指していても、その関数を捕捉します。追加例は [docs/USAGE.md](docs/USAGE.md) を参照してください。

## 保存先を変更する

```powershell
$env:GHIDRA_MCP_CAPTURE_DIR = 'C:\ghidra-work\screenshots'
& 'D:\Program Files\ghidra_12.1.4_PUBLIC\ghidraRun.bat'
```

環境変数は**Ghidra を起動する前**に設定する必要があります。`output_path` はこのフォルダからの相対パス、または**このフォルダ内**の絶対パスのみ指定できます。親ディレクトリやシンボリックリンクを通じたフォルダ外への保存は拒否します。

## トラブルシューティング

- `compileJava` で多数の `cannot find symbol` / `package ... does not exist` → **Ghidra 10.3.2 を参照していないか**。12.1.4 の `application.properties` を確認。
- `Connection refused` → Ghidra 12.1.4 で GUI プラグインが有効か、ポートが `8089` か確認。
- MCP 上で新ツールが見えない → `/mcp/schema` と MCP ブリッジ再起動、lazy tool group を確認。
- `Decompiler pane missing` → CodeBrowser の **Window → Decompiler** を表示。
- `Timed out` → 大きな関数の解析時間、アドレス、UI 表示、`timeout_ms`（1～20000）を確認。
- `File exists` → 別名に変更する（意図的に上書き禁止）。

詳しくは **[docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md)** を参照してください。

## 開発・GitHub での管理

- Python テスト：`python -m unittest discover -s tests -v`
- 変更を取り消す場合は、上流 clone 内の未コミット変更を確認し、`GhidraMCPPlugin.java` の差分だけ元に戻すか、ファイルを復元してください。`git restore` は他の変更も消すため注意。
- このアドオン単体を GitHub 管理する方法、上流プロジェクトを fork して**全ソースを管理**する方法は [docs/GITHUB.md](docs/GITHUB.md) に記載しています。
- `.gitignore` は逆解析対象のバイナリ、解析プロジェクト、スクリーンショットや環境設定を誤ってコミットしないための一例です。`git status` を確認してから push してください。

## セキュリティと制約

- GhidraMCP の HTTP サーバーは原則ローカルホストのみに公開し、信頼できないネットワークへ直接公開しないでください。
- スクリーンショットには解析対象のコード・関数名が写ります。公開リポジトリに画像や機密バイナリを含めないでください。
- GUI の表示中の領域のみ撮影。複数 CodeBrowser、ユーザーと AI の同時ナビゲーション、実行中の Decompiler 更新は撮影対象に影響します。
- `capture_evidence` は PNG を出力しますが、関数全体の画像化や画像データの MCP 直接返却には対応しません。

## 出典とライセンス

このリポジトリは非公式アドオンで、Ghidra や bethington/ghidra-mcp の公式機能ではありません。ベースの [bethington/ghidra-mcp は Apache-2.0](https://github.com/bethington/ghidra-mcp/blob/v7.0.0/LICENSE) で提供されています。本リポジトリ独自のアドオン用ソースは同梱 [MIT ライセンス](LICENSE) を想定しています。公開前に権利表記・ライセンス選択を確認してください。
