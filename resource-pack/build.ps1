param(
    [string]$Version = "1.0.0"
)

$ErrorActionPreference = "Stop"
$packRoot = Join-Path $PSScriptRoot "source"
$buildRoot = Join-Path $PSScriptRoot "build"
$archive = Join-Path $buildRoot "MCPuzzle-$Version.zip"

foreach ($required in @("pack.mcmeta", "pack.png", "assets")) {
    if (-not (Test-Path -LiteralPath (Join-Path $packRoot $required))) {
        throw "필수 리소스팩 항목이 없습니다: $required"
    }
}

$textures = Get-ChildItem -LiteralPath (Join-Path $packRoot "assets\mcpuzzle\textures\item") -Filter "*.png" -File
$models = Get-ChildItem -LiteralPath (Join-Path $packRoot "assets\mcpuzzle\models\item") -Filter "*.json" -File
if ($textures.Count -ne 16 -or $models.Count -ne 16) {
    throw "아이콘 텍스처와 모델은 각각 16개여야 합니다. textures=$($textures.Count), models=$($models.Count)"
}

New-Item -ItemType Directory -Path $buildRoot -Force | Out-Null
if (Test-Path -LiteralPath $archive) {
    Remove-Item -LiteralPath $archive -Force
}

Add-Type -AssemblyName System.IO.Compression
$resolvedPackRoot = (Resolve-Path -LiteralPath $packRoot).Path.TrimEnd('\', '/')
$archiveStream = [System.IO.File]::Open($archive, [System.IO.FileMode]::CreateNew)
$zip = [System.IO.Compression.ZipArchive]::new(
    $archiveStream,
    [System.IO.Compression.ZipArchiveMode]::Create,
    $false
)
try {
    $sourceFiles = Get-ChildItem -LiteralPath $resolvedPackRoot -Recurse -File | Sort-Object FullName
    foreach ($sourceFile in $sourceFiles) {
        $relativePath = $sourceFile.FullName.Substring($resolvedPackRoot.Length).TrimStart('\', '/').Replace('\', '/')
        $entry = $zip.CreateEntry($relativePath, [System.IO.Compression.CompressionLevel]::Optimal)
        $entry.LastWriteTime = [System.DateTimeOffset]::new(2020, 1, 1, 0, 0, 0, [System.TimeSpan]::Zero)
        $inputStream = [System.IO.File]::OpenRead($sourceFile.FullName)
        $outputStream = $entry.Open()
        try {
            $inputStream.CopyTo($outputStream)
        } finally {
            $outputStream.Dispose()
            $inputStream.Dispose()
        }
    }
} finally {
    $zip.Dispose()
    $archiveStream.Dispose()
}

$stream = [System.IO.File]::OpenRead($archive)
$sha1 = [System.Security.Cryptography.SHA1]::Create()
try {
    $hashBytes = $sha1.ComputeHash($stream)
    $hash = -join ($hashBytes | ForEach-Object { $_.ToString("x2") })
} finally {
    $sha1.Dispose()
    $stream.Dispose()
}
Set-Content -LiteralPath (Join-Path $buildRoot "MCPuzzle-$Version.sha1") -Value $hash -Encoding ascii
Write-Host "리소스팩: $archive"
Write-Host "SHA-1:    $hash"
