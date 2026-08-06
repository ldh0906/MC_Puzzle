param(
    [string]$MinMemory = "2G",
    [string]$MaxMemory = "4G",
    [int]$ReadyTimeoutSeconds = 75,
    [int]$WorldVerifyTimeoutSeconds = 150
)

$ErrorActionPreference = "Stop"
$serverRoot = $PSScriptRoot
$projectRoot = Split-Path -Parent $serverRoot
$pluginSource = Join-Path $projectRoot "mcpuzzle-paper\build\libs\mcpuzzle-paper-0.1.0-SNAPSHOT.jar"
$pluginTarget = Join-Path $serverRoot "plugins\MCPuzzle.jar"
$paperJar = Join-Path $serverRoot "paper-1.20.1-196.jar"
$hashFile = Join-Path $projectRoot "resource-pack\build\MCPuzzle-1.0.0.sha1"
$packUrl = "http://127.0.0.1:8123/MCPuzzle-1.0.0.zip"
$activeMap = Join-Path $serverRoot "plugins\MCPuzzle\map-packs\a-to-z-archive-20\map.jsonc"
$latestLog = Join-Path $serverRoot "logs\latest.log"

foreach ($requiredFile in @($pluginSource, $paperJar, $hashFile)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "검증에 필요한 파일이 없습니다: $requiredFile"
    }
}

$paperJarFullPath = [System.IO.Path]::GetFullPath($paperJar)
$runningServer = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object {
    $_.CommandLine -and $_.CommandLine.IndexOf($paperJarFullPath, [System.StringComparison]::OrdinalIgnoreCase) -ge 0
} | Select-Object -First 1
if ($null -ne $runningServer) {
    throw "검증 대상 Paper가 이미 실행 중입니다(PID $($runningServer.ProcessId)). 서버를 정상 종료한 뒤 다시 실행하세요."
}

New-Item -ItemType Directory -Path (Split-Path -Parent $pluginTarget) -Force | Out-Null
Copy-Item -LiteralPath $pluginSource -Destination $pluginTarget -Force
$expectedHash = (Get-Content -Raw -LiteralPath $hashFile).Trim().ToLowerInvariant()

$processInfo = [System.Diagnostics.ProcessStartInfo]::new()
$processInfo.FileName = (Get-Command java).Source
$processInfo.WorkingDirectory = $serverRoot
$processInfo.UseShellExecute = $false
$processInfo.CreateNoWindow = $true
$processInfo.RedirectStandardInput = $true
$processInfo.RedirectStandardOutput = $true
$processInfo.RedirectStandardError = $true
$processInfo.Arguments = "-Xms$MinMemory -Xmx$MaxMemory -jar `"$paperJar`" --nogui"

$serverProcess = [System.Diagnostics.Process]::new()
$serverProcess.StartInfo = $processInfo
if (-not $serverProcess.Start()) {
    throw "Paper 프로세스를 시작하지 못했습니다."
}
$stdoutTask = $serverProcess.StandardOutput.ReadToEndAsync()
$stderrTask = $serverProcess.StandardError.ReadToEndAsync()
$download = $null
$resourcePackReady = $false
$pluginReady = $false
$startedAt = [DateTime]::UtcNow

try {
    $deadline = [DateTime]::UtcNow.AddSeconds($ReadyTimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline -and -not $serverProcess.HasExited -and -not ($resourcePackReady -and $pluginReady)) {
        if (-not $resourcePackReady) {
            try {
            $httpClient = [System.Net.Http.HttpClient]::new()
            $httpClient.Timeout = [TimeSpan]::FromSeconds(2)
            try {
                $download = $httpClient.GetByteArrayAsync($packUrl).GetAwaiter().GetResult()
                if ($download.Length -gt 0) {
                    $resourcePackReady = $true
                }
            } finally {
                $httpClient.Dispose()
            }
            } catch {
                # Startup polling intentionally tolerates connection refusal.
            }
        }
        if (Test-Path -LiteralPath $latestLog -PathType Leaf) {
            $logInfo = Get-Item -LiteralPath $latestLog
            if ($logInfo.LastWriteTimeUtc -ge $startedAt.AddSeconds(-1)) {
                $pluginReady = (Get-Content -Raw -LiteralPath $latestLog) -match [regex]::Escape(
                    "MCPuzzle READY maze=a-to-z-archive-20 version=2.1.0-a20 rooms=20"
                )
            }
        }
        if (-not ($resourcePackReady -and $pluginReady)) { Start-Sleep -Milliseconds 500 }
    }
    if (-not $resourcePackReady) {
        throw "로컬 리소스팩 주소가 제한 시간 안에 준비되지 않았습니다."
    }
    if (-not $pluginReady) {
        $startupTail = if (Test-Path -LiteralPath $latestLog -PathType Leaf) {
            (Get-Content -Tail 40 -LiteralPath $latestLog) -join "`n"
        } else {
            "latest.log 없음"
        }
        throw "MCPuzzle가 20개 방 준비 완료 상태에 도달하지 못했습니다.`n$startupTail"
    }
    if (-not (Test-Path -LiteralPath $activeMap -PathType Leaf)) {
        throw "배포된 활성 맵 파일이 없습니다: $activeMap"
    }
    $deployedMap = Get-Content -Raw -LiteralPath $activeMap | ConvertFrom-Json
    if ($deployedMap.mapVersion -ne "2.1.0-a20" -or $deployedMap.rooms.Count -ne 20) {
        throw "서버 데이터 폴더의 활성 맵 버전 또는 방 수가 빌드와 다릅니다."
    }

    $serverProcess.StandardInput.WriteLine("maze admin verify-world")
    $serverProcess.StandardInput.Flush()
    $worldVerifyPassed = $false
    $worldVerifyFailed = $false
    $verifyDeadline = [DateTime]::UtcNow.AddSeconds($WorldVerifyTimeoutSeconds)
    while ([DateTime]::UtcNow -lt $verifyDeadline -and -not $serverProcess.HasExited -and -not ($worldVerifyPassed -or $worldVerifyFailed)) {
        if (Test-Path -LiteralPath $latestLog -PathType Leaf) {
            $verifyLog = Get-Content -Raw -LiteralPath $latestLog
            $worldVerifyPassed = $verifyLog -match "MCPuzzle WORLD_VERIFY PASS rooms=20"
            $worldVerifyFailed = $verifyLog -match "MCPuzzle WORLD_VERIFY FAIL"
        }
        if (-not ($worldVerifyPassed -or $worldVerifyFailed)) { Start-Sleep -Milliseconds 500 }
    }
    if (-not $worldVerifyPassed) {
        $verifyTail = (Get-Content -Tail 60 -LiteralPath $latestLog) -join "`n"
        throw "20방 임시 월드 생성 검증에 실패했거나 시간 초과했습니다.`n$verifyTail"
    }

    $sha1 = [System.Security.Cryptography.SHA1]::Create()
    try {
        $downloadHash = -join ($sha1.ComputeHash($download) | ForEach-Object { $_.ToString("x2") })
    } finally {
        $sha1.Dispose()
    }
    Write-Host "다운로드 크기: $($download.Length) bytes"
    Write-Host "다운로드 SHA-1: $downloadHash"
    if ($downloadHash -ne $expectedHash) {
        throw "다운로드한 리소스팩 SHA-1이 빌드 결과와 다릅니다."
    }

    $serverProcess.StandardInput.WriteLine("stop")
    $serverProcess.StandardInput.Flush()
    if (-not $serverProcess.WaitForExit(30000)) {
        throw "Paper가 30초 안에 정상 종료되지 않았습니다."
    }
} finally {
    if (-not $serverProcess.HasExited) {
        try {
            $serverProcess.StandardInput.WriteLine("stop")
            $serverProcess.StandardInput.Flush()
        } catch {
            # The exact process started above is force-stopped only if stdin has already failed.
        }
        if (-not $serverProcess.WaitForExit(10000)) {
            $serverProcess.Kill()
            $serverProcess.WaitForExit()
        }
    }
}

$stdout = $stdoutTask.GetAwaiter().GetResult()
$stderr = $stderrTask.GetAwaiter().GetResult()
$importantLines = ($stdout + "`n" + $stderr) -split "`r?`n" | Where-Object {
    $_ -match "MCPuzzle|resource|리소스|Done \(|ERROR|WARN|Exception|Stopping server|Saving players"
}
$importantLines | Select-Object -Last 80
Write-Host "Paper 종료 코드: $($serverProcess.ExitCode)"
if ($serverProcess.ExitCode -ne 0) {
    throw "Paper가 오류 코드로 종료되었습니다."
}
