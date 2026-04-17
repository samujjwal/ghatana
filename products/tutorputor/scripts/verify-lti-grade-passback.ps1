param(
  [string]$ApiUrl = $env:TUTORPUTOR_API_URL,
  [string]$TenantId = $env:TUTORPUTOR_TEST_TENANT_ID,
  [string]$UserId = "lti-user-1"
)

if ([string]::IsNullOrWhiteSpace($ApiUrl) -or [string]::IsNullOrWhiteSpace($TenantId)) {
  Write-Error "Set TUTORPUTOR_API_URL and TUTORPUTOR_TEST_TENANT_ID."
  exit 1
}

$payload = @{
  sessionId = "session-test"
  userId = $UserId
  score = 90
  maxScore = 100
  lineItemId = "line-item-test"
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri "$ApiUrl/integration/lti/grade-passback" -Headers @{ "x-tenant-id" = $TenantId } -Body $payload -ContentType "application/json" | ConvertTo-Json -Depth 10
