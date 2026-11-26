# One-command offline debug/test run for horde-soldiers-v2.
# Usage: powershell -File tools/run-tests.ps1 [-SkipBuild]
#   1. (optional) offline build via build-offline.ps1
#   2. jar smoke test: class count, mods.toml expanded, tacz mandatory dep, spawn-egg models + lang
#   3. LogicTests (111 checks, headless registry bootstrap)
param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot | Split-Path -Parent
Set-Location $root
$jdk = 'C:\Users\NOXROV\.gradle\jdks\eclipse_adoptium-17-amd64-windows\jdk-17.0.21+9\bin'
$jar = Join-Path $root 'build\libs\hzcode-2.0.1.jar'          # production (SRG) jar - smoke tested
$devJar = Join-Path $root 'build\libs\horde_soldiers-2.0.1-dev.jar'   # mojmap dev jar - used for LogicTests
if (-not $SkipBuild) {
    Write-Output '== build (offline) =='
    powershell -File (Join-Path $root 'build-offline.ps1') 2>&1 | Select-String -Pattern 'main classes|LogicTests:|error:'
}
if (-not (Test-Path $jar)) { throw "production jar missing: $jar (run without -SkipBuild first)" }
if (-not (Test-Path $devJar)) { throw "dev jar missing: $devJar (run without -SkipBuild first)" }
Write-Output '== jar smoke test =='
$list = & "$jdk\jar.exe" tf $jar 2>$null
$classes = ($list | Where-Object { $_ -match '\.class$' }).Count
$eggs = ($list | Where-Object { $_ -match 'models/item/.*_spawn_egg\.json$' }).Count
Write-Output ("jar entries: {0}, classes: {1}, spawn-egg models: {2}" -f $list.Count, $classes, $eggs)
$tmp = Join-Path $env:TEMP ('jarchk-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tmp -Force | Out-Null
Set-Location $tmp
& "$jdk\jar.exe" xf $jar META-INF/mods.toml pack.mcmeta
if (Select-String -Path META-INF\mods.toml -Pattern '\$\{' -Quiet) { throw 'mods.toml has unexpanded placeholders' }
if (-not (Select-String -Path META-INF\mods.toml -Pattern 'modId="tacz"' -Quiet)) { throw 'tacz mandatory dependency missing in mods.toml' }
if ((Select-String -Path META-INF\mods.toml -Pattern 'version="2.0.1"' -Quiet) -eq $false) { throw 'mods.toml version is not 2.0.1' }
# Byte-level BOM check: Get-Content/Select-String silently strip a UTF-8 BOM, so a text
# search cannot catch it. Forge's ModFileParser does NOT strip it and rejects the whole jar.
$tomlBytes = [System.IO.File]::ReadAllBytes((Join-Path $tmp 'META-INF\mods.toml'))
if ($tomlBytes.Length -ge 3 -and $tomlBytes[0] -eq 0xEF -and $tomlBytes[1] -eq 0xBB -and $tomlBytes[2] -eq 0xBF) { throw 'mods.toml starts with UTF-8 BOM (EF BB BF) - Forge rejects it: corrupt or misconfigured toml' }
Write-Output 'mods.toml OK (expanded, version 2.0.1, tacz mandatory, no BOM)'
# production jar must be reobfuscated mojmap->SRG (the game runs with SRG names) and must not
# carry an FMLModType manifest attribute (forgespi 7.0.1 rejects Type.GAME during mod scan)
& "$jdk\jar.exe" xf $jar META-INF/MANIFEST.MF 'com\nox\hzcode\entities\ModEntities.class'
if (Select-String -Path (Join-Path $tmp 'META-INF\MANIFEST.MF') -Pattern 'FMLModType' -Quiet) { throw 'jar manifest contains FMLModType - forgespi 7.0.1 crashes on it (No enum constant IModFile$Type.GAME)' }
$bc = & "$jdk\javap.exe" -c -p (Join-Path $tmp 'com\nox\hzcode\entities\ModEntities.class')
if (-not ($bc -match 'EntityType\$Builder\.m_20704_')) { throw 'jar is not reobfuscated to SRG (m_20704_ missing) - installing it crashes with NoSuchMethodError' }
if ($bc -match 'EntityType\$Builder\.of:') { throw 'jar still contains mojmap call Builder.of - not reobfuscated' }
Write-Output 'reobf OK (SRG m_20704_ present, no mojmap Builder.of), manifest has no FMLModType'
if ($classes -lt 51) { throw "expected >=51 classes, got $classes" }
if ($eggs -ne 5) { throw "expected 5 spawn-egg models, got $eggs" }
Set-Location $root
Write-Output '== logic tests =='
$jars = @(Get-ChildItem (Join-Path $root 'build\cp'), (Join-Path $root 'build\jarsbak') -Filter '*.jar' -Recurse -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
$fastutil = $jars | Where-Object { $_ -match 'fastutil-8.5.9.jar$' }
$jars = @($fastutil) + ($jars | Where-Object { $_ -ne $fastutil })
$cp = (@((Join-Path $root 'build\test-classes'), $devJar) + $jars) -join ';'
$ErrorActionPreference = 'Continue'
& "$jdk\java.exe" -cp $cp com.nox.hzcode.test.LogicTests 2>$null | Select-String -Pattern 'FAIL|SKIP|LogicTests:'
if ($LASTEXITCODE -ne 0) { $ErrorActionPreference = 'Stop'; throw "LogicTests failed (exit $LASTEXITCODE)" }
$ErrorActionPreference = 'Stop'
if ($LASTEXITCODE -ne 0) { throw "LogicTests failed (exit $LASTEXITCODE)" }
Write-Output '== done =='
