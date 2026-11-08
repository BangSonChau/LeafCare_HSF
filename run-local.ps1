$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not (Test-Path 'application-local.properties') -and -not $env:DB_PASSWORD) {
    throw 'Set DB_PASSWORD (and optionally DB_URL/DB_USERNAME), or create application-local.properties before running.'
}
if (-not (Test-Path 'node_modules/.bin/tailwindcss.cmd')) {
    & npm.cmd ci
    if ($LASTEXITCODE -ne 0) { throw 'npm ci failed' }
}
& npm.cmd run build
if ($LASTEXITCODE -ne 0) { throw 'CSS build failed' }
$mvnCmd = if (Test-Path '.\mvnw.cmd') { '.\mvnw.cmd' } else { 'mvn.cmd' }
& $mvnCmd 'spring-boot:run' '-Dspring-boot.run.arguments=--server.port=8081'
exit $LASTEXITCODE
