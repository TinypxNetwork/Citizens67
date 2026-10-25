param(
    [string] $Maven = 'mvn',
    [switch] $Offline
)

$ErrorActionPreference = 'Stop'
$mavenArgs = @('-B', '-Pmc18', '-Dmaven.javadoc.skip=true', '-DBUILD_NUMBER=MC1.8.8-packet', 'package')
if ($Offline) {
    $mavenArgs = @('-o') + $mavenArgs
}

Push-Location -LiteralPath $PSScriptRoot
try {
    & $Maven @mavenArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed ($LASTEXITCODE)."
    }
    $artifact = Join-Path $PSScriptRoot 'dist\target\Citizens-2.0.35-bMC1.8.8-packet.jar'
    $outputPath = Join-Path (Split-Path -Parent $PSScriptRoot) 'Citizens-2.0.35-MC1.8.8-Packet.jar'
    Copy-Item -LiteralPath $artifact -Destination $outputPath -Force
    Write-Output "Built: $outputPath"
} finally {
    Pop-Location
}
