param(
  [string]$OutputDir = "docs/operations"
)

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outputPath = Join-Path $OutputDir "lti-validation-$timestamp.log"

Write-Host "Collecting LTI validation evidence"
corepack pnpm vitest run src/modules/integration/lti/routes.test.ts *>&1 | Tee-Object -FilePath $outputPath
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Evidence log written to $outputPath"
