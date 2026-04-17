$ErrorActionPreference = "Stop"

$env:TP_ENVIRONMENT = "staging"
./scripts/run-content-proof-suite.ps1
