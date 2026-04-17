Param(
  [string]$DatabaseUrl = $env:DATABASE_URL,
  [string]$S3Bucket = $env:S3_BUCKET,
  [string]$AwsRegion = $env:AWS_REGION
)

$ErrorActionPreference = "Stop"

Write-Host "[encryption-check] Starting TutorPutor at-rest verification"

if (-not $DatabaseUrl) {
  Write-Warning "DATABASE_URL is not set. Skipping Postgres probe."
} else {
  $psql = Get-Command psql -ErrorAction SilentlyContinue
  if (-not $psql) {
    Write-Warning "psql is not available on PATH. Skipping Postgres probe."
  } else {
    Write-Host "[encryption-check] Running Postgres posture probe"
    & psql "$DatabaseUrl" -f "scripts/verify-postgres-at-rest-encryption.sql"
  }
}

if (-not $S3Bucket) {
  Write-Warning "S3_BUCKET is not set. Skipping object storage probe."
} else {
  $aws = Get-Command aws -ErrorAction SilentlyContinue
  if (-not $aws) {
    Write-Warning "aws CLI is not available on PATH. Skipping object storage probe."
  } else {
    Write-Host "[encryption-check] Checking bucket encryption for $S3Bucket"
    if ($AwsRegion) {
      & aws s3api get-bucket-encryption --bucket $S3Bucket --region $AwsRegion
      & aws s3api get-public-access-block --bucket $S3Bucket --region $AwsRegion
    } else {
      & aws s3api get-bucket-encryption --bucket $S3Bucket
      & aws s3api get-public-access-block --bucket $S3Bucket
    }
  }
}

Write-Host "[encryption-check] Completed"
