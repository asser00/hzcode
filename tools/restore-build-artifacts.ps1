# Restores local-only build artifacts that are NOT tracked in git:
#   build\cp\*.jar             <- every jar listed in build_classpath.txt (gradle cache paths)
#   libs\tacz-1.20.1-1.2.0.jar <- mapped/dev TACZ jar (from ..\horde-soldiers-v2\libs)
# Usage: powershell -File tools\restore-build-artifacts.ps1
$root = Split-Path -Parent $PSScriptRoot
$cpDir = Join-Path $root 'build\cp'
New-Item -ItemType Directory -Path $cpDir -Force | Out-Null

$paths = (Get-Content (Join-Path $root 'build_classpath.txt') -Raw) -split ';' |
    ForEach-Object { $_.Trim() } | Where-Object { $_ }
$ok = 0; $missing = @()
foreach ($p in $paths) {
    if (Test-Path -LiteralPath $p) { Copy-Item -LiteralPath $p -Destination $cpDir -Force; $ok++ }
    else { $missing += $p }
}
Write-Output "classpath jars copied into build\cp: $ok (missing sources: $($missing.Count))"

$tacz = Join-Path $root 'libs\tacz-1.20.1-1.2.0.jar'
if (Test-Path $tacz) {
    Write-Output "tacz jar already present: $tacz"
} else {
    $src = Join-Path (Split-Path -Parent $root) 'horde-soldiers-v2\libs\tacz-1.20.1-1.2.0.jar'
    if (-not (Test-Path $src)) { throw "Cannot restore libs\tacz-1.20.1-1.2.0.jar - source not found: $src" }
    New-Item -ItemType Directory -Path (Join-Path $root 'libs') -Force | Out-Null
    Copy-Item $src $tacz -Force
    Write-Output "tacz jar restored: $tacz"
}
