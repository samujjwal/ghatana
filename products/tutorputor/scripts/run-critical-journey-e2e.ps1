param(
  [string]$BaseUrl = $env:TUTORPUTOR_BASE_URL,
  [string]$ApiUrl = $env:TUTORPUTOR_API_URL
)

if ([string]::IsNullOrWhiteSpace($BaseUrl) -or [string]::IsNullOrWhiteSpace($ApiUrl)) {
  Write-Error "Set TUTORPUTOR_BASE_URL and TUTORPUTOR_API_URL before running this script."
  exit 1
}

Write-Host "Running critical journey Playwright suite against $BaseUrl"
corepack pnpm playwright test tests/e2e --reporter=line
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Running GDPR deletion flow verification helper"
./scripts/verify-gdpr-delete-flow.ps1 -ApiUrl $ApiUrl
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Critical journey run completed"
