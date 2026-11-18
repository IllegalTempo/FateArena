$ErrorActionPreference = 'Stop'
$java = 'C:\Program Files\Java\jdk-25.0.2\bin\java.exe'
if (-not (Test-Path -LiteralPath $java)) { throw 'Install JDK 25 or update the Java path in setup-server.ps1.' }
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'Install Git for Windows and reopen your terminal.' }
$tools = Join-Path $PSScriptRoot '.dev-tools'
$server = Join-Path $PSScriptRoot 'server'
New-Item -ItemType Directory -Force $tools, (Join-Path $server 'plugins') | Out-Null
$builder = Join-Path $tools 'BuildTools.jar'
if (-not (Test-Path -LiteralPath $builder)) {
    Invoke-WebRequest 'https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar' -OutFile $builder
}
$env:JAVA_HOME = Split-Path (Split-Path $java -Parent) -Parent
$env:PATH = "$(Join-Path $env:JAVA_HOME 'bin');$env:PATH"
Push-Location $tools
try {
    & $java -jar $builder --rev 26.3
    if ($LASTEXITCODE -ne 0) { throw 'Spigot BuildTools failed. Check the output above.' }
    Copy-Item -LiteralPath (Join-Path $tools 'spigot-26.3.jar') -Destination $server
} finally { Pop-Location }
Write-Host 'Server JAR ready. Select FateArena Server in IntelliJ and run it.'
Write-Host 'On first run, read server/eula.txt and its linked EULA. Set eula=true only if you agree, then run again.'
