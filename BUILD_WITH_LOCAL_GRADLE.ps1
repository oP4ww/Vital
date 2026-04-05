Set-Location $PSScriptRoot
Write-Host "Building Vital Popups with local Gradle..."
gradle build
$exitCode = $LASTEXITCODE
if ($exitCode -eq 0) {
    Write-Host "`nBUILD SUCCEEDED. Opening build\libs..."
    Invoke-Item ".\build\libs"
} else {
    Write-Host "`nBUILD FAILED with exit code $exitCode"
}
Read-Host "Press Enter to close"
