param(
  [string]$ApiUrl = $env:TUTORPUTOR_API_URL,
  [string]$Platform = "canvas"
)

if ([string]::IsNullOrWhiteSpace($ApiUrl)) {
  Write-Error "Set TUTORPUTOR_API_URL before running this script."
  exit 1
}

Invoke-RestMethod -Method Get -Uri "$ApiUrl/integration/lti/config/$Platform" | ConvertTo-Json -Depth 10
