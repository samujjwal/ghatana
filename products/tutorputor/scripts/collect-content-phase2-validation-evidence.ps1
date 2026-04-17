$ErrorActionPreference = "Stop"

$root = Resolve-Path "$PSScriptRoot\.."
$stamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
$outFile = Join-Path $root "docs\operations\CONTENT_PHASE2_EVIDENCE_LOCAL_2026-04-16.md"

@"
# Content Phase 2 Evidence - Local

## Execution Context
- Date: 2026-04-16
- Captured At: $stamp
- Environment: local

## Command
- ./scripts/verify-content-phase2-routes.ps1

## Notes
- Update this file with captured stdout, stderr, and reviewer annotations.
"@ | Set-Content -Path $outFile -Encoding UTF8

Write-Host "Wrote $outFile"
