# Offline build for horde-soldiers-v2 (no network needed).
# Uses cached Gradle 8.8 + ForgeGradle 6.0.54 + cached Forge 1.20.1-47.4.28 artifacts + libs/tacz jar.
# Usage: powershell -File build-offline.ps1
#
# Produces TWO jars:
#   build\libs\horde_soldiers-2.0.1-dev.jar - javac output compiled against the MOJMAP forge jar.
#     DEV/TEST ONLY. The production game runs with SRG names (ModLauncher naming: srg), so installing
#     this jar into the instance crashes with NoSuchMethodError (e.g. EntityType$Builder.of -> m_20704_).
#     It exists only so LogicTests can run headlessly on the mojmap classpath.
#   build\libs\hzcode-2.0.1.jar     - PRODUCTION jar built by cached Gradle. ForgeGradle reobfs
#     mojmap->srg automatically. THIS is the only jar to install into the instance mods folder.
$root = $PSScriptRoot
$jdks = Join-Path $env:USERPROFILE '.gradle\jdks'
$javac = (Get-ChildItem -Recurse $jdks -Filter javac.exe | Select-Object -First 1).FullName
$jarTool = Join-Path (Split-Path $javac) 'jar.exe'
$java = Join-Path (Split-Path $javac) 'java.exe'

$jars = @(Get-ChildItem "$root\build\cp","$root\build\jarsbak" -Filter '*.jar' -Recurse -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
# correct fastutil first so MC bootstrap works in tests
$fastutil = $jars | Where-Object { $_ -match 'fastutil-8.5.9.jar$' }
$jars = @($fastutil) + ($jars | Where-Object { $_ -ne $fastutil })
$cp = $jars -join ';'

# ---------- preflight: local build artifacts (untracked, restorable) ----------
if ($jars.Count -eq 0) {
    throw "build\cp has no jars -> empty -cp makes javac fail with 'invalid flag'. Run: powershell -File tools\restore-build-artifacts.ps1"
}
if (-not (Test-Path (Join-Path $root 'libs\tacz-1.20.1-1.2.0.jar'))) {
    throw "Missing libs\tacz-1.20.1-1.2.0.jar (Gradle needs the mapped TACZ jar). Run: powershell -File tools\restore-build-artifacts.ps1"
}


# ---------- 1) dev compile (mojmap) + dev jar + LogicTests ----------
$srcs = Get-ChildItem -LiteralPath "$root\src\main\java" -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName
$out = "$root\build\classes_final"
New-Item -ItemType Directory -Path $out -Force | Out-Null
Write-Output "== compiling main (dev, mojmap) =="
& $javac --release 17 -encoding UTF-8 -nowarn -cp $cp -d $out $srcs 2>&1 | Select-String -Pattern 'error:' | Select-Object -First 20
$classCount = (Get-ChildItem $out -Recurse -Filter '*.class' -ErrorAction SilentlyContinue | Measure-Object).Count
Write-Output "main classes: $classCount"

New-Item -ItemType Directory -Path "$root\build\libs" -Force | Out-Null
$res = "$root\build\res"
New-Item -ItemType Directory -Path "$res\META-INF" -Force | Out-Null
Copy-Item "$root\src\main\resources\*" "$res\" -Recurse -Force
$c = Get-Content "$res\META-INF\mods.toml" -Raw
$c = $c.Replace('${mod_id}','hzcode').Replace('${mod_version}','2.0.1').Replace('${mod_name}','Horde Soldiers').Replace('${mod_license}','All Rights Reserved').Replace('${mod_authors}','NOX').Replace('${mod_description}','Zombie apocalypse hordes: one brain, multiple bodies, shared strategy, independent execution.').Replace('${loaderVersion}','[47,)').Replace('${forge_version_range}','[47,)').Replace('${minecraft_version_range}','[1.20.1,1.21)')
# Write UTF-8 WITHOUT BOM. Windows PowerShell 5.1's '-Encoding utf8' prepends EF BB BF,
# and Forge's ModFileParser hands mods.toml to nightconfig's TOML parser without stripping
# the BOM -> "corrupt or misconfigured toml" -> ModFileLoadingException -> instant crash.
[System.IO.File]::WriteAllText("$res\META-INF\mods.toml", $c, [System.Text.UTF8Encoding]::new($false))
$tomlBytes = [System.IO.File]::ReadAllBytes("$res\META-INF\mods.toml")
if ($tomlBytes.Length -ge 3 -and $tomlBytes[0] -eq 0xEF -and $tomlBytes[1] -eq 0xBB -and $tomlBytes[2] -eq 0xBF) { throw 'mods.toml written with UTF-8 BOM - Forge would reject it' }
Write-Output "mods.toml written BOM-free ($($tomlBytes.Length) bytes)"
$devJar = "$root\build\libs\horde_soldiers-2.0.1-dev.jar"
& $jarTool cf $devJar -C $out . -C $res .
Write-Output "DEV jar (mojmap, tests only - do NOT install into the game): $devJar"

Write-Output "== compiling tests =="
$tsrcs = Get-ChildItem -LiteralPath "$root\src\test\java" -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName
$tout = "$root\build\test-classes"
New-Item -ItemType Directory -Path $tout -Force | Out-Null
$cpTest = (@($devJar) + $jars) -join ';'
& $javac --release 17 -encoding UTF-8 -nowarn -cp $cpTest -d $tout $tsrcs 2>&1 | Select-String -Pattern 'error:' | Select-Object -First 20

Write-Output "== running logic tests (against dev mojmap jar) =="
$cpRun = (@($tout, $devJar) + $jars) -join ';'
& $java -cp $cpRun com.nox.hzcode.test.LogicTests 2>&1 | Select-String -Pattern 'PASS|FAIL|SKIP|LogicTests:'

# ---------- 2) production jar via cached Gradle (ForgeGradle reobfs mojmap->srg) ----------
Write-Output "== production build via cached Gradle (reobf mojmap->srg) =="
$gradleBat = (Get-ChildItem -Recurse (Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-8.8-bin') -Filter 'gradle.bat' -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
if (-not $gradleBat) { throw 'cached gradle-8.8 not found under ~/.gradle/wrapper/dists' }
$env:JAVA_HOME = Split-Path (Split-Path $javac)
Set-Location $root
# NOTE: must be the full `build` task - ForgeGradle only reobfs (mojmap->srg) the jar via the
# reobf step wired into `build`; `gradle jar` alone emits a MOJMAP jar that crashes in-game.
& $gradleBat --offline build --console=plain 2>&1 | Select-String -Pattern 'Task :|BUILD|error:|FAILED' | Select-Object -Last 12
if ($LASTEXITCODE -ne 0) { throw "gradle jar failed (exit $LASTEXITCODE)" }
$prodJar = "$root\build\libs\hzcode-2.0.1.jar"
if (-not (Test-Path $prodJar)) { throw "production jar missing: $prodJar" }

# ---------- 3) verify production jar: SRG reobf, no BOM, no FMLModType, parses ----------
Write-Output "== verifying production jar =="
$chk = Join-Path $env:TEMP ('prodchk-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $chk -Force | Out-Null
Set-Location $chk
& $jarTool xf $prodJar META-INF/MANIFEST.MF META-INF/mods.toml 'com/nox/horde_soldiers/entities/ModEntities.class'
$tb = [System.IO.File]::ReadAllBytes((Join-Path $chk 'META-INF\mods.toml'))
if ($tb.Length -ge 3 -and $tb[0] -eq 0xEF -and $tb[1] -eq 0xBB -and $tb[2] -eq 0xBF) { throw 'production jar mods.toml has a UTF-8 BOM - Forge would reject it' }
Write-Output 'mods.toml: BOM-free'
$mf = Get-Content (Join-Path $chk 'META-INF\MANIFEST.MF') -Raw
if ($mf -match 'FMLModType') { throw 'production jar manifest contains FMLModType - forgespi 7.0.1 rejects Type.GAME (IllegalArgumentException during mod scan)' }
Write-Output 'MANIFEST.MF: no FMLModType (forgespi 7.0.1 safe)'
$bc = & $javac.Replace('javac.exe','javap.exe') -c -p (Join-Path $chk 'com\nox\hzcode\entities\ModEntities.class')
if ($bc -match 'EntityType\$Builder\.m_20704_') { Write-Output 'reobf OK: EntityType$Builder.of remapped to SRG m_20704_' } else { throw 'production jar is NOT reobfuscated to SRG (m_20704_ missing) - do not install' }
if ($bc -match 'EntityType\$Builder\.of:') { throw 'production jar still contains mojmap call Builder.of - not reobfuscated' }
$cls = (& $jarTool tf $prodJar | Where-Object { $_ -match '\.class$' } | Measure-Object).Count
Write-Output "production jar classes: $cls"
if ($cls -lt 51) { throw "production jar has only $cls classes" }
Set-Location $root
Write-Output "PRODUCTION jar (install THIS into the instance mods folder): $prodJar"
