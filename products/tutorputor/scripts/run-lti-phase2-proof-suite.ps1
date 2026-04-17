$ErrorActionPreference = "Stop"

& "$PSScriptRoot\verify-lti-phase2-routes.ps1"
& "$PSScriptRoot\collect-lti-phase2-validation-evidence.ps1"
