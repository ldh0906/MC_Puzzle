param(
    [switch]$AcceptEula,
    [string]$MinMemory = "2G",
    [string]$MaxMemory = "4G"
)

$ErrorActionPreference = "Stop"
$serverRoot = $PSScriptRoot
$projectRoot = Split-Path -Parent $serverRoot
$pluginSource = Join-Path $projectRoot "mcpuzzle-paper\build\libs\mcpuzzle-paper-0.1.0-SNAPSHOT.jar"
$pluginDirectory = Join-Path $serverRoot "plugins"
$pluginTarget = Join-Path $pluginDirectory "MCPuzzle.jar"
$paperJar = Join-Path $serverRoot "paper-1.20.1-196.jar"
$eulaFile = Join-Path $serverRoot "eula.txt"

if (-not (Test-Path -LiteralPath $paperJar -PathType Leaf)) {
    throw "Paper JAR이 없습니다: $paperJar"
}
if (-not (Test-Path -LiteralPath $pluginSource -PathType Leaf)) {
    throw "플러그인 JAR이 없습니다. 먼저 .\gradlew.bat clean test build를 실행하세요."
}

$javaOutput = & cmd.exe /d /c "java -version 2>&1"
$javaExitCode = $LASTEXITCODE
$javaVersion = ($javaOutput | Select-Object -First 1) -join ""
if ($javaExitCode -ne 0 -or $javaVersion -notmatch 'version "(?<major>\d+)') {
    throw "Java 실행 파일/버전을 확인할 수 없습니다. Java 17을 설치하세요."
}
if ([int]$Matches.major -lt 17) {
    throw "Java 17 이상이 필요합니다. 현재: $javaVersion"
}

if ($AcceptEula) {
    Set-Content -LiteralPath $eulaFile -Value "eula=true" -Encoding ascii
}
if (-not (Test-Path -LiteralPath $eulaFile -PathType Leaf) -or
    -not (Select-String -LiteralPath $eulaFile -Pattern '^eula=true$' -Quiet)) {
    throw "Mojang EULA 동의가 필요합니다. 동의하는 경우에만 -AcceptEula를 사용하세요."
}

New-Item -ItemType Directory -Path $pluginDirectory -Force | Out-Null
Copy-Item -LiteralPath $pluginSource -Destination $pluginTarget -Force

Push-Location $serverRoot
try {
    & java "-Xms$MinMemory" "-Xmx$MaxMemory" -jar $paperJar --nogui
} finally {
    Pop-Location
}
