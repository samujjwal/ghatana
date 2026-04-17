param(
  [string]$Environment = "local",
  [string]$OutputDir = "docs/operations"
)

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outputPath = Join-Path $OutputDir "encryption-$Environment-$timestamp.log"

Write-Host "Collecting encryption evidence for '$Environment'"
./scripts/verify-at-rest-encryption.ps1 *>&1 | Tee-Object -FilePath $outputPath
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Evidence log written to $outputPath"
