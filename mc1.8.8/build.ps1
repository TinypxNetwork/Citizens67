param(
    [string] $Maven = 'mvn',
    [switch] $Offline
)

$ErrorActionPreference = 'Stop'
$protocolSha = '41d7d8e99e21eebd343c04c07e6b5afff79cad28240afe1dbad0cce52402d2a6'
$temporary = $null
$mavenArgs = @('-B', '-Pmc18', '-Dmaven.javadoc.skip=true', '-DBUILD_NUMBER=MC1.8.8-packet', 'package')
if ($Offline) {
    $mavenArgs = @('-o') + $mavenArgs
}

Push-Location -LiteralPath $PSScriptRoot
try {
    if (-not $Offline) {
        $protocolJar = $env:TINYPX_PROTOCOLLIB_JAR
        if ([string]::IsNullOrWhiteSpace($protocolJar)) {
            $cached = Join-Path $env:USERPROFILE '.m2\repository\com\comphenix\protocol\ProtocolLib\5.0.0\ProtocolLib-5.0.0.jar'
            if ((Test-Path -LiteralPath $cached -PathType Leaf) -and (Get-FileHash -LiteralPath $cached -Algorithm SHA256).Hash -eq $protocolSha) {
                $protocolJar = $cached
            } else {
                $temporary = New-TemporaryFile
                Invoke-WebRequest -UseBasicParsing -TimeoutSec 180 -Uri 'https://github.com/dmulloy2/ProtocolLib/releases/download/5.0.0/ProtocolLib.jar' -OutFile $temporary.FullName
                $protocolJar = $temporary.FullName
            }
        }
        if (-not (Test-Path -LiteralPath $protocolJar -PathType Leaf) -or (Get-FileHash -LiteralPath $protocolJar -Algorithm SHA256).Hash -ne $protocolSha) {
            throw 'ProtocolLib 5.0.0 checksum mismatch.'
        }
        & $Maven '-B' '-N' 'org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file' "-Dfile=$protocolJar" '-DgroupId=com.comphenix.protocol' '-DartifactId=ProtocolLib' '-Dversion=5.0.0' '-Dpackaging=jar' '-DgeneratePom=true'
        if ($LASTEXITCODE -ne 0) {
            throw "ProtocolLib preparation failed ($LASTEXITCODE)."
        }
    }
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
    if ($null -ne $temporary) {
        Remove-Item -LiteralPath $temporary.FullName -Force -ErrorAction SilentlyContinue
    }
}
