param(
  [string]$Environment = "local",
  [string]$OutputDir = "docs/operations"
)

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outputPath = Join-Path $OutputDir "critical-journey-$Environment-$timestamp.log"

Write-Host "Collecting critical-journey evidence for environment '$Environment'"
corepack pnpm playwright test tests/e2e --reporter=line *>&1 | Tee-Object -FilePath $outputPath
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Evidence log written to $outputPath"
