$ErrorActionPreference = "Stop"

$env:TP_ENVIRONMENT = "production"
./scripts/run-content-proof-suite.ps1
