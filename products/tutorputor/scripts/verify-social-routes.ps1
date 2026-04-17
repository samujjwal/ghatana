param(
  [string]$ApiUrl = $env:TUTORPUTOR_API_URL,
  [string]$TenantId = $env:TUTORPUTOR_TEST_TENANT_ID,
  [string]$UserId = $env:TUTORPUTOR_TEST_USER_ID
)

if ([string]::IsNullOrWhiteSpace($ApiUrl) -or [string]::IsNullOrWhiteSpace($TenantId) -or [string]::IsNullOrWhiteSpace($UserId)) {
  Write-Error "Set TUTORPUTOR_API_URL, TUTORPUTOR_TEST_TENANT_ID, and TUTORPUTOR_TEST_USER_ID."
  exit 1
}

Invoke-RestMethod -Method Get -Uri "$ApiUrl/engagement/social/feed?limit=20" -Headers @{ "x-tenant-id" = $TenantId; "x-user-id" = $UserId } | ConvertTo-Json -Depth 10
