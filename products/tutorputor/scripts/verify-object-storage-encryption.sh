#!/usr/bin/env bash
set -euo pipefail

BUCKET="${S3_BUCKET:-}"
REGION="${AWS_REGION:-}"

if [[ -z "$BUCKET" ]]; then
  echo "S3_BUCKET is required"
  exit 1
fi

if ! command -v aws >/dev/null 2>&1; then
  echo "aws CLI is required"
  exit 1
fi

if [[ -n "$REGION" ]]; then
  aws s3api get-bucket-encryption --bucket "$BUCKET" --region "$REGION"
  aws s3api get-public-access-block --bucket "$BUCKET" --region "$REGION"
else
  aws s3api get-bucket-encryption --bucket "$BUCKET"
  aws s3api get-public-access-block --bucket "$BUCKET"
fi
