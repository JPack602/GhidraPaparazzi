# GitHub 管理方法

このアドオンは、**小さい独立リポジトリとして管理する方法**と、**bethington/ghidra-mcp の fork に組み込む方法**の 2 通りがあります。

## A. アドオン単体を GitHub に置く（推奨）

本 ZIP を展開した `ghidra-mcp-decompiler-capture` ディレクトリ内で作業します。

```powershell
Set-Location C:\ghidra-work\ghidra-mcp-decompiler-capture

git init
git add .
git status
git commit -m 'Initial decompiler capture addon'
```

GitHub Web で空のリポジトリを作り、表示された URL を指定します。

```powershell
git branch -M main
git remote add origin https://github.com/<your-user>/ghidra-mcp-decompiler-capture.git
git push -u origin main
```

GitHub CLI (`gh`) がある場合、プライベートリポジトリとして作るなら：

```powershell
gh auth login
gh repo create ghidra-mcp-decompiler-capture --private --source . --remote origin --push
```

**公開する場合は `--public` を明示的に選択してください。** バイナリ、スクリーンショット、API キー、秘密情報が混入していないか `git status` と差分を確認してから公開します。

## B. bethington/ghidra-mcp の fork に全部まとめる

GhidraMCP 本体まで一つのリポジトリで管理したい場合は、GitHub 上で [bethington/ghidra-mcp](https://github.com/bethington/ghidra-mcp) を fork します。

すでに動作確認した `C:\ghidra-work\ghidra-mcp` にパッチが入っているなら、次を確認します。

```powershell
Set-Location C:\ghidra-work\ghidra-mcp

git describe --tags --always
git status --short
git diff -- src/main/java/com/xebyte/GhidraMCPPlugin.java
```

fork 側を `origin` とは別名のリモートとして登録し、自分のブランチへ変更を push する例：

```powershell
git switch -c feature/decompiler-capture

git add src/main/java/com/xebyte/GhidraMCPPlugin.java `
  src/main/java/com/xebyte/core/DecompilerCaptureService.java

git commit -m 'Add MCP decompiler screenshot tools'
git remote add myfork https://github.com/<your-user>/ghidra-mcp.git
git push -u myfork feature/decompiler-capture
```

- **注意：** `git switch -c` が失敗する場合はブランチ名が既存かを確認してください。
- `git add` は本当に変更した上記2ファイルのみに絞っています。既存の実験設定・解析対象などは追加しないでください。
- fork には上流コードも含まれるため、[上流ライセンス](https://github.com/bethington/ghidra-mcp/blob/v7.0.0/LICENSE) と NOTICE 等を維持してください。
- アドオン単体方式では GhidraMCP の全ファイルをコピーする必要がなく、更新追従が楽です。

## 更新時の注意

このアドオンの `apply_addon.py` は **bethington v7.0.0 の Java 構成**に合わせた文字列ベースのパッチャーです。上流更新で `GhidraMCPPlugin.java` の構造や `GuiToolService` の宣言が変わると適用できなくなる可能性があります。新しいタグへ更新するときは、新規 checkout でビルド・実機撮影を確認してください。
