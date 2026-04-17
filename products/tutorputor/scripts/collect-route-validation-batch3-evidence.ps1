$ErrorActionPreference = "Stop"

$root = Resolve-Path "$PSScriptRoot\.."
$stamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
$outFile = Join-Path $root "docs\operations\ROUTE_VALIDATION_BATCH3_EVIDENCE_LOCAL_2026-04-16.md"

@"
# Route Validation Batch 3 Evidence - Local

## Execution Context
- Date: 2026-04-16
- Captured At: $stamp
- Environment: local

## Command
- ./scripts/verify-route-validation-batch3.ps1

## Notes
- Attach command output and reviewer annotations.
"@ | Set-Content -Path $outFile -Encoding UTF8

Write-Host "Wrote $outFile"
