param(
  [string]$OutputDir = "docs/operations"
)

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outputPath = Join-Path $OutputDir "social-validation-$timestamp.log"

Write-Host "Collecting social validation evidence"
corepack pnpm vitest run src/modules/engagement/social/__tests__/routes.test.ts *>&1 | Tee-Object -FilePath $outputPath
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Evidence log written to $outputPath"
