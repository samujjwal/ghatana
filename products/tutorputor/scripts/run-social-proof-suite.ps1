param(
  [string]$Environment = "local"
)

Write-Host "Running social proof suite for '$Environment'"
./scripts/collect-social-validation-evidence.ps1
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

./scripts/verify-social-routes.ps1
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Social proof suite complete"
