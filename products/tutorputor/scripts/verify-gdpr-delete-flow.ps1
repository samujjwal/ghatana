param(
  [string]$ApiUrl = $env:TUTORPUTOR_API_URL,
  [string]$TenantId = $env:TUTORPUTOR_TEST_TENANT_ID,
  [string]$UserId = $env:TUTORPUTOR_TEST_DELETE_USER_ID
)

if ([string]::IsNullOrWhiteSpace($ApiUrl) -or [string]::IsNullOrWhiteSpace($TenantId) -or [string]::IsNullOrWhiteSpace($UserId)) {
  Write-Error "Set TUTORPUTOR_API_URL, TUTORPUTOR_TEST_TENANT_ID, and TUTORPUTOR_TEST_DELETE_USER_ID."
  exit 1
}

$payload = @{ userId = $UserId; retentionDays = 30 } | ConvertTo-Json

Write-Host "Submitting deletion request for user '$UserId'"
$response = Invoke-RestMethod -Method Post -Uri "$ApiUrl/compliance/deletion/request" -Body $payload -ContentType "application/json" -Headers @{ "x-tenant-id" = $TenantId; "x-user-role" = "admin" }
$response | ConvertTo-Json -Depth 10

Write-Host "Run scripts/verify-gdpr-delete-cascade.sql with matching tenant/user values to complete verification."
