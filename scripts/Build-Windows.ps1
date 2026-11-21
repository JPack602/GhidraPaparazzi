#requires -Version 5.1
<#
.SYNOPSIS
  Validate Ghidra 12.1.4 and bethington/ghidra-mcp v7.0.0, apply addon, build extension.
.EXAMPLE
  .\scripts\Build-Windows.ps1 -GhidraDir 'D:\Program Files\ghidra_12.1.4_PUBLIC' -GhidraMcpDir 'C:\ghidra-work\ghidra-mcp'
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$GhidraDir,

    [string]$GhidraMcpDir = ''
)
$ErrorActionPreference = 'Stop'
$addonDir = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
if (-not $GhidraMcpDir) {
    $GhidraMcpDir = Join-Path (Split-Path -Parent $addonDir) 'ghidra-mcp'
}
$GhidraDir = (Resolve-Path -LiteralPath $GhidraDir).Path
$GhidraMcpDir = (Resolve-Path -LiteralPath $GhidraMcpDir).Path

$propFile = Join-Path $GhidraDir 'Ghidra\application.properties'
if (-not (Test-Path -LiteralPath $propFile)) {
    throw "Missing Ghidra/application.properties: $propFile"
}
$versionLine = Get-Content -LiteralPath $propFile | Where-Object { $_ -match '^application\.version=' } | Select-Object -First 1
if ($null -eq $versionLine) { throw 'Cannot read Ghidra application.version' }
$version = ($versionLine -split '=', 2)[1].Trim()
if ($version -ne '12.1.4') {
    throw "Expected Ghidra 12.1.4 but found $version at '$GhidraDir'. Refusing to build."
}

$pluginFile = Join-Path $GhidraMcpDir 'src\main\java\com\xebyte\GhidraMCPPlugin.java'
if (-not (Test-Path -LiteralPath $pluginFile)) {
    throw "Not a recognized bethington/ghidra-mcp checkout: $GhidraMcpDir"
}
$gitVersion = (& git -C $GhidraMcpDir describe --tags --always 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0) { throw "Cannot determine upstream tag: $gitVersion" }
if ($gitVersion -ne 'v7.0.0') {
    throw "This add-on expects git describe = v7.0.0, but got '$gitVersion'. Use the v7.0.0 tag."
}

$javaText = (& java --version | Out-String)
if ($LASTEXITCODE -ne 0) { throw "Java is unavailable: $javaText" }
if ($javaText -notmatch '(?m)^(openjdk|java)\s+21([.\s]|$)') {
    throw "Expected Java 21; java --version returned:`n$javaText"
}
Write-Host 'Ghidra  : 12.1.4'
Write-Host "MCP tag : $gitVersion"
Write-Host 'Java    : 21'

$patcher = Join-Path $addonDir 'apply_addon.py'
$pyCmd = Get-Command py -ErrorAction SilentlyContinue
if ($pyCmd) {
    & py -3 $patcher $GhidraMcpDir
} else {
    $pythonCmd = Get-Command python -ErrorAction SilentlyContinue
    if (-not $pythonCmd) { throw 'Python 3 is required (python or py launcher).' }
    & python $patcher $GhidraMcpDir
}
if ($LASTEXITCODE -ne 0) { throw 'apply_addon.py failed; no build attempted.' }

Push-Location $GhidraMcpDir
try {
    & .\gradlew.bat clean buildExtension "-PGHIDRA_INSTALL_DIR=$GhidraDir"
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
    $artifact = Join-Path $GhidraMcpDir 'build\distributions\GhidraMCP-7.0.0.zip'
    if (-not (Test-Path -LiteralPath $artifact)) { throw "Build finished but ZIP not found: $artifact" }
    Write-Host "`nBuilt extension: $artifact" -ForegroundColor Green
    Write-Host 'Install this ZIP via Ghidra 12.1.4 -> File -> Install Extensions.'
} finally { Pop-Location }
