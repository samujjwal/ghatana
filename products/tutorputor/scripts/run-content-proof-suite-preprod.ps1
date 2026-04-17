$ErrorActionPreference = "Stop"

$env:TP_ENVIRONMENT = "preprod"
./scripts/run-content-proof-suite.ps1
