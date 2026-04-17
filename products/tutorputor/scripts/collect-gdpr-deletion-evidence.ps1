param(
  [string]$Environment = "local",
  [string]$OutputDir = "docs/operations"
)

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outputPath = Join-Path $OutputDir "gdpr-delete-$Environment-$timestamp.log"

Write-Host "Collecting GDPR deletion evidence for environment '$Environment'"
./scripts/verify-gdpr-delete-flow.ps1 *>&1 | Tee-Object -FilePath $outputPath
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Evidence log written to $outputPath"
