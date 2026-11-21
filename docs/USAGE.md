# 使い方（MCP / HTTP）

## 動作前提

- Ghidra 12.1.4 の GUI が起動している
- CodeBrowser に解析対象 Program が開いている
- Decompiler ペインが**実際に表示されている**
- GhidraMCPPlugin と MCP ブリッジが起動している

## 1. 通常の撮影：`capture_decompiler_png`

MCP クライアントから次の順番で実行します。

1. `tool_goto_address` で `0x00401234` に移動
2. `capture_decompiler_png` を呼び出す

```json
{
  "output_path": "decompiler_00401234.png",
  "expected_address": "0x00401234",
  "timeout_ms": 10000
}
```

`expected_address` を指定すると、そのアドレスが属する関数のデコンパイル完了を確認してから撮影します。指定しない場合は、現在表示している関数が完了した時点で撮影します。

PowerShell から直接 HTTP エンドポイントを呼ぶ例：

```powershell
$body = @{
  output_path = 'decompiler_00401234.png'
  expected_address = '0x00401234'
  timeout_ms = 10000
} | ConvertTo-Json

Invoke-RestMethod `
  -Uri 'http://127.0.0.1:8089/capture_decompiler_png' `
  -Method Post -ContentType 'application/json' -Body $body
```

> `tool_goto_address` は MCP ツール名です。画面のアドレス移動を事前に呼ぶ必要があります。アドオンのキャプチャツールを呼ぶ前に AI クライアントから移動させるか、Ghidra GUI で手動で対象関数を開いてください。

## 2. 証拠画像：`capture_evidence`

こちらは対象アドレスへの移動を内部で行います。

```json
{
  "address": "0x00401234",
  "output_path": "evidence_00401234.png",
  "label": "Validation logic",
  "timeout_ms": 10000
}
```

```powershell
$body = @{
  address = '0x00401234'
  output_path = 'evidence_00401234.png'
  label = 'Validation logic'
  timeout_ms = 10000
} | ConvertTo-Json

Invoke-RestMethod `
  -Uri 'http://127.0.0.1:8089/capture_evidence' `
  -Method Post -ContentType 'application/json' -Body $body
```

画像の上には任意ラベル、Program 名、関数名、エントリアドレスが書き込まれ、画像全体を赤枠で囲みます。特定行の赤枠ではありません。

## 戻り値（成功時）

MCP／HTTP の応答ラッパー内に、次のような情報が返ります（値は例）。

```json
{
  "saved": true,
  "path": "C:\\Users\\user\\GhidraMCP-Captures\\decompiler_00401234.png",
  "bytes": 12345,
  "program": "sample.exe",
  "function": "FUN_00401234",
  "entry": "00401234",
  "width": 950,
  "height": 720
}
```

PNG 自体はディスク保存であり、MCP `ImageContent` としての直接返却ではありません。

## AI への指示例

```text
Ghidra MCP の tool_goto_address で 0x00401234 に移動し、
capture_decompiler_png で Decompiler ペインを保存してください。
ファイル名は decompiler_00401234.png とし、
expected_address に 0x00401234 を指定してください。
成功したら保存先パスと関数名を報告してください。
```

```text
Ghidra で 0x00401234 のデコンパイル結果を capture_evidence で撮影し、
label を Validation logic、output_path を evidence_00401234.png にしてください。
```

## 出力パス・制限

- 相対パスは `%USERPROFILE%\GhidraMCP-Captures` を基準にします。
- `GHIDRA_MCP_CAPTURE_DIR` は Ghidra の**起動前**に設定します。
- 絶対パスはこの保存ルートの内部だけ許可されます。
- `.png` 拡張子必須。上書きしません。
- `timeout_ms` は 1～20000（ミリ秒）。
- Decompiler の表示位置・ウィンドウサイズはそのまま反映されます。関数全行のキャプチャではありません。
