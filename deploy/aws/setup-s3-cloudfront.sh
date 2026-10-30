#!/usr/bin/env bash
# Provisions a private S3 bucket and a CloudFront distribution (with OAC and SPA routing)
# for the HomeCare Connect React frontend.
#
# Usage:
#   S3_BUCKET=my-homecare-frontend-bucket AWS_REGION=us-east-1 ./deploy/aws/setup-s3-cloudfront.sh

set -euo pipefail

S3_BUCKET="${S3_BUCKET:-}"
AWS_REGION="${AWS_REGION:-us-east-1}"
OAC_NAME="${OAC_NAME:-${S3_BUCKET}-oac}"

if [[ -z "$S3_BUCKET" ]]; then
  echo "Error: S3_BUCKET is required."
  echo "Usage: S3_BUCKET=your-unique-bucket-name [AWS_REGION=us-east-1] $0"
  exit 1
fi

if ! command -v aws >/dev/null 2>&1; then
  echo "Error: aws CLI is not installed. See https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html"
  exit 1
fi

ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
echo "==> Using AWS Account: ${ACCOUNT_ID}, Region: ${AWS_REGION}, Bucket: ${S3_BUCKET}"

# 1. Create private S3 bucket if it doesn't already exist
if aws s3api head-bucket --bucket "$S3_BUCKET" 2>/dev/null; then
  echo "==> S3 bucket '${S3_BUCKET}' already exists."
else
  echo "==> Creating S3 bucket '${S3_BUCKET}' in ${AWS_REGION}..."
  if [[ "$AWS_REGION" == "us-east-1" ]]; then
    aws s3api create-bucket --bucket "$S3_BUCKET" --region "$AWS_REGION" >/dev/null
  else
    aws s3api create-bucket \
      --bucket "$S3_BUCKET" \
      --region "$AWS_REGION" \
      --create-bucket-configuration LocationConstraint="$AWS_REGION" >/dev/null
  fi
fi

echo "==> Enforcing S3 Block Public Access..."
aws s3api put-public-access-block \
  --bucket "$S3_BUCKET" \
  --public-access-block-configuration \
  "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"

# 2. Create or reuse CloudFront Origin Access Control (OAC)
EXISTING_OAC_ID="$(aws cloudfront list-origin-access-controls \
  --query "OriginAccessControlList.Items[?Name=='${OAC_NAME}'].Id | [0]" \
  --output text)"

if [[ -n "$EXISTING_OAC_ID" && "$EXISTING_OAC_ID" != "None" ]]; then
  OAC_ID="$EXISTING_OAC_ID"
  echo "==> Reusing existing CloudFront OAC: ${OAC_ID}"
else
  echo "==> Creating CloudFront Origin Access Control '${OAC_NAME}'..."
  OAC_ID="$(aws cloudfront create-origin-access-control \
    --origin-access-control-config "{
      \"Name\": \"${OAC_NAME}\",
      \"Description\": \"OAC for ${S3_BUCKET}\",
      \"SigningProtocol\": \"sigv4\",
      \"SigningBehavior\": \"always\",
      \"OriginAccessControlOriginType\": \"s3\"
    }" \
    --query 'OriginAccessControl.Id' \
    --output text)"
  echo "==> Created CloudFront OAC: ${OAC_ID}"
fi

# 3. Create CloudFront distribution (Managed-CachingOptimized policy ID: 658327ea-f89d-4fab-a63d-7e88639e58f6)
ORIGIN_DOMAIN="${S3_BUCKET}.s3.${AWS_REGION}.amazonaws.com"
CALLER_REF="homecare-${S3_BUCKET}-$(date +%s)"

DIST_CONFIG="$(cat <<EOF
{
  "CallerReference": "${CALLER_REF}",
  "Comment": "HomeCare Connect Frontend (${S3_BUCKET})",
  "Enabled": true,
  "HttpVersion": "http2and3",
  "IsIPV6Enabled": true,
  " priceClass": "PriceClass_100",
  "DefaultRootObject": "index.html",
  "Origins": {
    "Quantity": 1,
    "Items": [
      {
        "Id": "S3-${S3_BUCKET}",
        "DomainName": "${ORIGIN_DOMAIN}",
        "OriginAccessControlId": "${OAC_ID}",
        "S3OriginConfig": {
          "OriginAccessIdentity": ""
        }
      }
    ]
  },
  "DefaultCacheBehavior": {
    "TargetOriginId": "S3-${S3_BUCKET}",
    "ViewerProtocolPolicy": "redirect-to-https",
    "AllowedMethods": {
      "Quantity": 2,
      "Items": ["GET", "HEAD"],
      "CachedMethods": {
        "Quantity": 2,
        "Items": ["GET", "HEAD"]
      }
    },
    "Compress": true,
    "CachePolicyId": "658327ea-f89d-4fab-a63d-7e88639e58f6"
  },
  "CustomErrorResponses": {
    "Quantity": 2,
    "Items": [
      {
        "ErrorCode": 403,
        "ResponsePagePath": "/index.html",
        "ResponseCode": "200",
        "ErrorCachingMinTTL": 0
      },
      {
        "ErrorCode": 404,
        "ResponsePagePath": "/index.html",
        "ResponseCode": "200",
        "ErrorCachingMinTTL": 0
      }
    ]
  }
}
EOF
)"

# Fix key casing for PriceClass in JSON payload
DIST_CONFIG="${DIST_CONFIG/\" priceClass\"/\"PriceClass\"}"

echo "==> Creating CloudFront distribution..."
DIST_OUTPUT="$(aws cloudfront create-distribution --distribution-config "$DIST_CONFIG")"
DIST_ID="$(echo "$DIST_OUTPUT" | grep -o '"Id": *"[^"]*"' | head -n1 | cut -d'"' -f4)"
DIST_DOMAIN="$(echo "$DIST_OUTPUT" | grep -o '"DomainName": *"[^"]*"' | head -n1 | cut -d'"' -f4)"
DIST_ARN="arn:aws:cloudfront::${ACCOUNT_ID}:distribution/${DIST_ID}"

# 4. Attach S3 bucket policy granting CloudFront OAC read access
echo "==> Attaching S3 bucket policy for CloudFront distribution ${DIST_ID}..."
BUCKET_POLICY="$(cat <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowCloudFrontServicePrincipalReadOnly",
      "Effect": "Allow",
      "Principal": {
        "Service": "cloudfront.amazonaws.com"
      },
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::${S3_BUCKET}/*",
      "Condition": {
        "StringEquals": {
          "AWS:SourceArn": "${DIST_ARN}"
        }
      }
    }
  ]
}
EOF
)"

aws s3api put-bucket-policy --bucket "$S3_BUCKET" --policy "$BUCKET_POLICY"

cat <<EOF

========================================================================
Provisioning complete!
========================================================================
S3_BUCKET                  = ${S3_BUCKET}
AWS_REGION                 = ${AWS_REGION}
CLOUDFRONT_DISTRIBUTION_ID = ${DIST_ID}
CLOUDFRONT_URL             = https://${DIST_DOMAIN}

Next steps:
1. Deploy from your machine:
   S3_BUCKET=${S3_BUCKET} CLOUDFRONT_DISTRIBUTION_ID=${DIST_ID} VITE_API_BASE_URL=https://your-backend-domain.com ./deploy/aws/deploy-frontend.sh

2. Set on your backend environment so CORS and email links match CloudFront:
   CORS_ORIGIN=https://${DIST_DOMAIN}
   PUBLIC_URL=https://${DIST_DOMAIN}
========================================================================
EOF
