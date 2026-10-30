#!/usr/bin/env bash
# Adds the backend EC2/HTTP origin to an existing CloudFront distribution and routes /api/* to it.
# This solves HTTPS -> HTTP Mixed Content errors by letting CloudFront terminate HTTPS and proxy /api/* over HTTP.
#
# Usage:
#   CLOUDFRONT_DISTRIBUTION_ID=E35CZ6LR7PHS3W \
#   BACKEND_ORIGIN_DOMAIN=ec2-32-236-54-158.ap-southeast-2.compute.amazonaws.com \
#   BACKEND_ORIGIN_PORT=80 \
#   ./deploy/aws/add-api-origin-cloudfront.sh

set -euo pipefail

DIST_ID="${CLOUDFRONT_DISTRIBUTION_ID:-E35CZ6LR7PHS3W}"
BACKEND_DOMAIN="${BACKEND_ORIGIN_DOMAIN:-ec2-32-236-54-158.ap-southeast-2.compute.amazonaws.com}"
BACKEND_PORT="${BACKEND_ORIGIN_PORT:-80}"

echo "==> Fetching current CloudFront config for ${DIST_ID}..."
RAW_JSON="$(aws cloudfront get-distribution-config --id "$DIST_ID")"
ETAG="$(echo "$RAW_JSON" | python3 -c "import sys, json; print(json.load(sys.stdin)['ETag'])")"

UPDATED_CONFIG="$(echo "$RAW_JSON" | python3 -c "
import sys, json

data = json.load(sys.stdin)['DistributionConfig']
backend_domain = '${BACKEND_DOMAIN}'
backend_port = int('${BACKEND_PORT}')
origin_id = 'Backend-API'

# 1. Add or update Backend-API origin
origins = [o for o in data['Origins']['Items'] if o['Id'] != origin_id]
origins.append({
    'Id': origin_id,
    'DomainName': backend_domain,
    'OriginPath': '',
    'CustomHeaders': {'Quantity': 0},
    'CustomOriginConfig': {
        'HTTPPort': backend_port,
        'HTTPSPort': 443,
        'OriginProtocolPolicy': 'http-only',
        'OriginSslProtocols': {'Quantity': 1, 'Items': ['TLSv1.2']},
        'OriginReadTimeout': 30,
        'OriginKeepaliveTimeout': 5
    },
    'ConnectionAttempts': 3,
    'ConnectionTimeout': 10,
    'OriginShield': {'Enabled': False},
    'OriginAccessControlId': ''
})
data['Origins']['Items'] = origins
data['Origins']['Quantity'] = len(origins)

# 2. Add or update /api/* CacheBehavior
# Managed-CachingDisabled: 4135ea2d-6df8-44a3-9df3-4b5a84be39ad
# Managed-AllViewerExceptHostHeader: b689b0a8-53d0-40ab-baf2-68738e2966ac
behaviors = data.get('CacheBehaviors', {}).get('Items', [])
behaviors = [b for b in behaviors if b.get('PathPattern') != '/api/*']
behaviors.insert(0, {
    'PathPattern': '/api/*',
    'TargetOriginId': origin_id,
    'TrustedSigners': {'Enabled': False, 'Quantity': 0},
    'TrustedKeyGroups': {'Enabled': False, 'Quantity': 0},
    'ViewerProtocolPolicy': 'redirect-to-https',
    'AllowedMethods': {
        'Quantity': 7,
        'Items': ['GET', 'HEAD', 'OPTIONS', 'PUT', 'POST', 'PATCH', 'DELETE'],
        'CachedMethods': {'Quantity': 2, 'Items': ['GET', 'HEAD']}
    },
    'SmoothStreaming': False,
    'Compress': True,
    'LambdaFunctionAssociations': {'Quantity': 0},
    'FunctionAssociations': {'Quantity': 0},
    'FieldLevelEncryptionId': '',
    'CachePolicyId': '4135ea2d-6df8-44a3-9df3-4b5a84be39ad',
    'OriginRequestPolicyId': 'b689b0a8-53d0-40ab-baf2-68738e2966ac',
    'GrpcConfig': {'Enabled': False}
})
data['CacheBehaviors'] = {
    'Quantity': len(behaviors),
    'Items': behaviors
}

print(json.dumps(data))
")"

echo "==> Updating CloudFront distribution ${DIST_ID} to route /api/* -> http://${BACKEND_DOMAIN}:${BACKEND_PORT}..."
aws cloudfront update-distribution \
  --id "$DIST_ID" \
  --if-match "$ETAG" \
  --distribution-config "$UPDATED_CONFIG" >/dev/null

echo "==> Successfully configured /api/* origin on CloudFront distribution ${DIST_ID}!"
