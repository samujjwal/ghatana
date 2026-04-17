param(
  [string]$Environment = "local"
)

Write-Host "Running remediation proof suite for '$Environment'"
./scripts/collect-critical-journey-evidence.ps1 -Environment $Environment
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

./scripts/collect-gdpr-deletion-evidence.ps1 -Environment $Environment
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

./scripts/verify-at-rest-encryption.ps1
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Remediation proof suite complete"
