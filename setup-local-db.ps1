$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (Test-Path 'application-local.properties') { throw 'Local configuration already exists; refusing to replace credentials.' }
$password = 'Lc!' + [Guid]::NewGuid().ToString('N') + '9a'
$sql = @"
IF DB_ID(N'leafcare') IS NULL
    CREATE DATABASE leafcare COLLATE Latin1_General_100_CI_AS_SC_UTF8;
GO
IF SUSER_ID(N'leafcare_app') IS NOT NULL
    THROW 50000, 'leafcare_app already exists; refusing to change its password.', 1;
CREATE LOGIN leafcare_app WITH PASSWORD = '$password', CHECK_POLICY = ON;
GO
USE leafcare;
CREATE USER leafcare_app FOR LOGIN leafcare_app;
GRANT CONNECT, CREATE TABLE TO leafcare_app;
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES, ALTER ON SCHEMA::dbo TO leafcare_app;
GO
"@
$sql | & sqlcmd.exe -S '.\SQLEXPRESS' -E -C -b
if ($LASTEXITCODE -ne 0) { throw 'Database setup failed' }
@"
spring.datasource.password=$password
"@ | Set-Content 'application-local.properties' -Encoding ascii
Write-Output 'Created leafcare database and application login; credentials saved in ignored application-local.properties.'
