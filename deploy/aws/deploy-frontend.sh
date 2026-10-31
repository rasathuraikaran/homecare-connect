#!/usr/bin/env bash
# Builds the Vite React frontend, syncs static files to S3 with optimal Cache-Control headers,
# and invalidates the CloudFront distribution cache.
#
# Usage:
#   S3_BUCKET=my-homecare-frontend-bucket \
#   CLOUDFRONT_DISTRIBUTION_ID=E1234567890ABC \
#   VITE_API_BASE_URL=https://api.example.com \
#   ./deploy/aws/deploy-frontend.sh

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FRONTEND_DIR="${ROOT_DIR}/frontend"
DIST_DIR="${FRONTEND_DIR}/dist"

S3_BUCKET="${S3_BUCKET:-}"
CLOUDFRONT_DISTRIBUTION_ID="${CLOUDFRONT_DISTRIBUTION_ID:-}"
AWS_REGION="${AWS_REGION:-us-east-1}"
SKIP_BUILD="${SKIP_BUILD:-false}"

if [[ -z "$S3_BUCKET" ]]; then
  echo "Error: S3_BUCKET environment variable is required."
  echo "Usage: S3_BUCKET=your-bucket-name [CLOUDFRONT_DISTRIBUTION_ID=E123...] $0"
  exit 1
fi

if [[ "$SKIP_BUILD" != "true" ]]; then
  echo "==> Building frontend in ${FRONTEND_DIR}..."
  pushd "$FRONTEND_DIR" >/dev/null
  npm ci
  npm run build
  popd >/dev/null
fi

if [[ ! -d "$DIST_DIR" ]]; then
  echo "Error: Build output directory '${DIST_DIR}' not found."
  exit 1
fi

echo "==> Syncing hashed static assets to s3://${S3_BUCKET} (long-term cache)..."
aws s3 sync "$DIST_DIR" "s3://${S3_BUCKET}" \
  --region "$AWS_REGION" \
  --delete \
  --exclude "index.html" \
  --cache-control "public,max-age=31536000,immutable"

echo "==> Uploading index.html to s3://${S3_BUCKET} (no-cache for instant updates)..."
aws s3 cp "${DIST_DIR}/index.html" "s3://${S3_BUCKET}/index.html" \
  --region "$AWS_REGION" \
  --content-type "text/html; charset=utf-8" \
  --cache-control "no-cache,no-store,must-revalidate"

if [[ -n "$CLOUDFRONT_DISTRIBUTION_ID" ]]; then
  echo "==> Creating CloudFront cache invalidation for distribution ${CLOUDFRONT_DISTRIBUTION_ID}..."
  INVALIDATION_ID="$(aws cloudfront create-invalidation \
    --distribution-id "$CLOUDFRONT_DISTRIBUTION_ID" \
    --paths "/index.html" "/*" \
    --query 'Invalidation.Id' \
    --output text)"
  echo "==> Invalidation triggered: ${INVALIDATION_ID}"
else
  echo "==> CLOUDFRONT_DISTRIBUTION_ID not set; skipping CloudFront invalidation."
fi

echo "==> Frontend deployment complete!"
