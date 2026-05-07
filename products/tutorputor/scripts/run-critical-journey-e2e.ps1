param(
  [string]$BaseUrl = $env:TUTORPUTOR_BASE_URL,
  [string]$AdminUrl = $env:TUTORPUTOR_ADMIN_URL,
  [string]$ApiUrl = $env:TUTORPUTOR_API_URL,
  [switch]$SkipGdpr,
  [switch]$RequireMaestro
)

if ([string]::IsNullOrWhiteSpace($BaseUrl) -or [string]::IsNullOrWhiteSpace($AdminUrl)) {
  Write-Error "Set TUTORPUTOR_BASE_URL and TUTORPUTOR_ADMIN_URL before running this script."
  exit 1
}

$TutorPutorRoot = Split-Path -Parent $PSScriptRoot
$WebApp = Join-Path $TutorPutorRoot "apps/tutorputor-web"
$AdminApp = Join-Path $TutorPutorRoot "apps/tutorputor-admin"
$MobileApp = Join-Path $TutorPutorRoot "apps/tutorputor-mobile"

$env:BASE_URL = $BaseUrl
$env:ADMIN_URL = $AdminUrl
$env:PLAYWRIGHT_SKIP_WEBSERVER = "true"

Write-Host "Running web critical learner journey suite against $BaseUrl"
Push-Location $WebApp
corepack pnpm exec playwright test `
  e2e/critical-learner-journey.spec.ts `
  e2e/offline-resume-critical-journey.spec.ts `
  --reporter=line
Pop-Location
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Running admin critical journey suite against $AdminUrl"
Push-Location $AdminApp
corepack pnpm exec playwright test e2e/admin-core.spec.ts --reporter=line
Pop-Location
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Checking mobile critical journey coverage"
$MobileFlows = @("login.yaml", "dashboard.yaml", "modules.yaml", "ai-tutor.yaml", "offline.yaml", "navigation.yaml")
foreach ($Flow in $MobileFlows) {
  $FlowPath = Join-Path $MobileApp "e2e/$Flow"
  if (-not (Test-Path $FlowPath)) {
    Write-Error "Missing mobile E2E flow: $FlowPath"
    exit 1
  }
}

$Maestro = Get-Command maestro -ErrorAction SilentlyContinue
if ($Maestro) {
  Push-Location $MobileApp
  maestro test e2e/
  Pop-Location
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} elseif ($RequireMaestro) {
  Write-Error "Maestro is required but was not found on PATH."
  exit 1
} else {
  Write-Host "Maestro not found; validated mobile flow files only. Use -RequireMaestro in full device CI."
}

if (-not $SkipGdpr) {
  if ([string]::IsNullOrWhiteSpace($ApiUrl)) {
    Write-Error "Set TUTORPUTOR_API_URL or pass -SkipGdpr."
    exit 1
  }

  Write-Host "Running GDPR deletion flow verification helper"
  & (Join-Path $PSScriptRoot "verify-gdpr-delete-flow.ps1") -ApiUrl $ApiUrl
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

Write-Host "Critical journey run completed"
