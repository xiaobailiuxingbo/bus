param(
    [switch]$Build,
    [string]$JavaHomePath = 'D:/dev/jdk-17.0.9/jdk-17.0.9',
    [string]$MavenHomePath = 'D:/dev/apache-maven-3.9.4'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$javaExecutable = Join-Path $JavaHomePath 'bin/java.exe'
$mavenExecutable = Join-Path $MavenHomePath 'bin/mvn.cmd'
if (-not (Test-Path -LiteralPath $javaExecutable)) { throw "JDK not found: $JavaHomePath" }
if (-not (Test-Path -LiteralPath (Join-Path $projectRoot '.local/application-dev.yml'))) {
    throw 'Create .local/application-dev.yml from docs/application-dev.local.example.yml first.'
}
$previousJavaHome = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $JavaHomePath
    if ($Build) {
        & $mavenExecutable -f (Join-Path $projectRoot 'backend/pom.xml') -Pdev -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
    }
    $jarPath = Join-Path $projectRoot 'backend/ruoyi-admin/target/ruoyi-admin.jar'
    if (-not (Test-Path -LiteralPath $jarPath)) { throw 'Build backend first (use -Build).' }
    $localConfigPath = ((Join-Path $projectRoot '.local') -replace '\\', '/') + '/'
    & $javaExecutable -jar $jarPath "--spring.config.additional-location=optional:file:$localConfigPath"
    if ($LASTEXITCODE -ne 0) { throw 'Backend exited with an error.' }
}
finally {
    $env:JAVA_HOME = $previousJavaHome
}
