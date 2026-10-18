# Run the exact release signing validation task in an isolated project with no keys.
# Never renames, edits or copies the production signing.properties / keystore.
$ErrorActionPreference = 'Stop'
$androidRoot = Split-Path $PSScriptRoot -Parent
$source = Get-Content -LiteralPath (Join-Path $androidRoot 'app/build.gradle.kts') -Raw
$start = $source.IndexOf('val validateReleaseSigning = tasks.register(')
$end = $source.IndexOf('tasks.configureEach', $start)
if ($start -lt 0 -or $end -lt 0) { throw 'Release validation task not found.' }
$checkRoot = Join-Path ([IO.Path]::GetTempPath()) ('astrosmap-signing-check-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $checkRoot | Out-Null
Set-Content -LiteralPath (Join-Path $checkRoot 'settings.gradle.kts') -Value 'rootProject.name = "signing-negative-test"' -Encoding utf8
Set-Content -LiteralPath (Join-Path $checkRoot 'build.gradle.kts') -Value ('import java.util.Properties' + [Environment]::NewLine + 'import java.io.File' + [Environment]::NewLine + $source.Substring($start, $end - $start)) -Encoding utf8
$output = & (Join-Path $androidRoot 'gradlew.bat') -p $checkRoot validateReleaseSigning --console=plain 2>&1
$exitCode = $LASTEXITCODE
$output | Write-Output
if ($exitCode -eq 0 -or ($output -join [Environment]::NewLine) -notmatch 'Release requires signing.properties; debug signing is forbidden') {
    throw 'Missing signing credentials did not fail with the expected release guard.'
}
Write-Output "PASS: unsigned release rejected. Isolated check files: $checkRoot"
