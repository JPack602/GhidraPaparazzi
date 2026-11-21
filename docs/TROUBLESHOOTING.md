# トラブルシューティング（Windows）

最初に `Ghidra MCP` と `Ghidra` の**版数とパス**を確認してください。本アドオンの基準は **MCP v7.0.0 + Ghidra 12.1.4 + Java 21** です。

## 1. 大量の `compileJava` / `cannot find symbol` エラー

次のようなエラーがまとめて出る場合：

```text
FunctionService.java: ReturnCommitOption not found
DebuggerService.java: DebuggerTargetService not found
DebuggerService.java: TraceRmiLauncherService not found
ProjectProgramProvider.java: LoadResults API mismatch
```

**原因候補：** ビルドが Ghidra 10.3.2 など、v7.0.0 非対応版を参照している可能性が高いです。実際に、旧 Ghidra 10.3.2 を指定したところ同様の大量エラーが発生しました。

```powershell
Set-Location C:\ghidra-work\ghidra-mcp

git describe --tags --always
Select-String -Path .\pom.xml -Pattern '<ghidra.version>'

$GhidraDir = 'D:\Program Files\ghidra_12.1.4_PUBLIC'
Get-Content "$GhidraDir\Ghidra\application.properties" |
    Select-String '^application.version='
java -version
```

期待値：`v7.0.0` / `12.1.4` / `application.version=12.1.4` / Java 21。変数 `$GhidraDir` が以前の `D:\Program Files\Ghidra_10.3.2_Public` を指していないか注意します。

```powershell
.\gradlew.bat clean buildExtension "-PGHIDRA_INSTALL_DIR=$GhidraDir"
```

Ghidra 10.3.2 を削除する必要はありません。Ghidra 12.1.4 を**別ディレクトリ**に展開してください。

## 2. `java` が見つからない／Java版数不一致

```powershell
java -version
$env:JAVA_HOME
Get-Command java
```

JDK 21 が優先されているか確認します。新しい Ghidra に必要な Java はバージョンに依存するので、本アドオンの基準と異なる版へ勝手に切り替えないでください。

## 3. `GHIDRA_INSTALL_DIR` が不正／`application.properties` がない

`-PGHIDRA_INSTALL_DIR` に指定するのは ZIP 本体や `Ghidra` サブフォルダではなく、`ghidraRun.bat` が置かれた**展開先のルートフォルダ**です。

```powershell
Test-Path "$GhidraDir\ghidraRun.bat"
Test-Path "$GhidraDir\Ghidra\application.properties"
```

パスに空白があるときは、Gradle に渡す引数全体を `"-PGHIDRA_INSTALL_DIR=$GhidraDir"` で囲みます。

## 4. `BUILD SUCCESSFUL` なのに追加ツールが見えない

```powershell
Invoke-RestMethod 'http://127.0.0.1:8089/check_connection'
$schema = Invoke-RestMethod 'http://127.0.0.1:8089/mcp/schema'
$schema | ConvertTo-Json -Depth 30 | Select-String 'capture_decompiler_png|capture_evidence'
```

- 上流 clone に `DecompilerCaptureService.java` があるか確認
- `GhidraMCPPlugin.java` に `captureService` が追加されているか確認
- **新しく生成した ZIP** を **Ghidra 12.1.4** にインストールしたか確認
- Ghidra と MCP ブリッジの**両方**を再起動
- ブリッジの lazy loading 時は `search_tools` / `load_tool_group`（`utility`）を使用

```powershell
Test-Path 'C:\ghidra-work\ghidra-mcp\src\main\java\com\xebyte\core\DecompilerCaptureService.java'
Select-String -Path 'C:\ghidra-work\ghidra-mcp\src\main\java\com\xebyte\GhidraMCPPlugin.java' -Pattern 'captureService'
```

## 5. `Connection refused`／`404 Not Found`

- Ghidra の CodeBrowser ではなく、**GhidraMCPPlugin が起動している**ことを確認
- プラグインが listen するポートを確認（ここでは 8089）
- PowerShell の HTTP アドレスが `127.0.0.1` と正しいパスか確認
- `/mcp/schema` にツールがあるのに個別 HTTP 呼び出しが 404 の場合、別の Ghidra プロセス/別ポートへ接続していないかを調べる

```powershell
Get-NetTCPConnection -LocalPort 8089 -ErrorAction SilentlyContinue
```

## 6. `Decompiler pane missing`／`No program open`

Ghidra 12.1.4 の **CodeBrowser** で対象プログラムを開き、**Window → Decompiler** を表示します。ペインを閉じたり、表示領域が 0×0 だったり、別の CodeBrowser へ切り替えたときには撮影できません。

## 7. `Timed out`／`Decompiler changed during capture`

- `expected_address` / `address` が現在読み込んだプログラムの**関数内**を指すかを確認
- Decompiler が処理中ではないか確認
- 大きな関数なら `timeout_ms` を大きくする（最大 20000）
- AI とユーザーが同時に別関数へ移動していないか確認
- CodeBrowser を 1 つだけ起動して撮影を再試行

## 8. `File exists`／`output_path` 拒否

上書き禁止です。別名の `example_002.png` などを指定します。

デフォルトの出力先は `%USERPROFILE%\GhidraMCP-Captures`。外部ディレクトリへ直接保存するのではなく、Ghidra 起動前に保存ルートを指定します。

```powershell
$env:GHIDRA_MCP_CAPTURE_DIR = 'C:\ghidra-work\screenshots'
& 'D:\Program Files\ghidra_12.1.4_PUBLIC\ghidraRun.bat'
```

`output_path` に `..` を含むディレクトリ脱出、シンボリックリンク経由の脱出、`.png` 以外を指定すると拒否されます。Windows のファイルシステム権限も確認してください。

## 9. PowerShell に出る日本語が文字化けする

Windows のコンソール文字コード・JDK の診断メッセージ設定によってはエラー文が文字化けします。まずは**英語の Java クラス名・行番号**に着目してください。必要に応じて UTF-8 を有効化して再実行します。

```powershell
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
$env:JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8'
```

環境変数は現在の PowerShell にのみ適用されます。

## 10. エラーの解析に必要なログ

```powershell
Set-Location C:\ghidra-work\ghidra-mcp
.\gradlew.bat clean buildExtension "-PGHIDRA_INSTALL_DIR=$GhidraDir" --stacktrace *> build-error.log
```

`build-error.log` の最初の `error:` / `cannot find symbol` と、その直前の Ghidra 版数・Java 版数をセットで確認してください。公開 Issue にログを貼る際は、ユーザーパスや機密バイナリ情報を除去してください。
