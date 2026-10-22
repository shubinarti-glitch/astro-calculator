# Runs compiled app JUnit tests directly. This avoids the Gradle worker classloader
# failure in Windows checkouts whose absolute path contains Cyrillic characters.
param(
    [ValidateSet('StandardDebug', 'GoogleplayDebug')]
    [string[]]$Variants = @('StandardDebug', 'GoogleplayDebug'),
    [string]$OutputDirectory = (Join-Path (Split-Path $PSScriptRoot -Parent) '.codex-artifacts/qa-1.7.5')
)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    $javaCommand = Get-Command java.exe -ErrorAction Stop
    $env:JAVA_HOME = Split-Path (Split-Path $javaCommand.Source -Parent) -Parent
}
$java = Join-Path $env:JAVA_HOME 'bin/java.exe'
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$testClasses = @(
    Get-ChildItem -LiteralPath 'app/src/test' -Recurse -Filter '*.kt' | ForEach-Object {
        $source = Get-Content -LiteralPath $_.FullName -Raw
        $package = [regex]::Match($source, '(?m)^package\s+([\w.]+)').Groups[1].Value
        foreach ($match in [regex]::Matches($source, '(?m)^class\s+(\w+)')) {
            if ($source -match '@Test') { "$package.$($match.Groups[1].Value)" }
        }
    }
)
if ($testClasses.Count -eq 0) { throw 'No app unit-test classes discovered.' }
foreach ($variant in $Variants) {
    $compileLog = Join-Path $OutputDirectory "$variant-unit-compile.log"
    $compile = & .\gradlew.bat ":app:compile${variant}UnitTestKotlin" ":app:compile${variant}UnitTestJavaWithJavac" --console=plain 2>&1
    $compileExit = $LASTEXITCODE
    $compile | Tee-Object -FilePath $compileLog | Write-Output
    if ($compileExit -ne 0) { throw "$variant compilation failed. See $compileLog" }
    $classpathOutput = & .\gradlew.bat ":app:print${variant}UnitTestClasspath" -q --console=plain 2>&1
    if ($LASTEXITCODE -ne 0) { throw "$variant classpath lookup failed: $classpathOutput" }
    $classpath = $classpathOutput | Where-Object { "$_" -match 'junit.*\.jar' } | Select-Object -Last 1
    if (-not $classpath) { throw "$variant unit-test classpath was not returned." }
    Set-Content -LiteralPath (Join-Path $OutputDirectory "$variant-unit-classpath.txt") -Value $classpath -Encoding utf8
    $log = Join-Path $OutputDirectory "$variant-unit-junit.log"
    $result = & $java -cp "$classpath" org.junit.runner.JUnitCore @testClasses 2>&1
    $testExit = $LASTEXITCODE
    $result | Tee-Object -FilePath $log | Write-Output
    if ($testExit -ne 0) { throw "$variant app unit tests failed. See $log" }
    Write-Output "PASS: $variant ($($testClasses.Count) test classes). Log: $log"
}
