# AWS Production Deployment Guide

HomeCare Connect includes a complete set of AWS CLI provisioning and deployment scripts in `deploy/aws/` for hosting the React frontend on **Amazon S3 + CloudFront** (using Origin Access Control) and routing `/api/*` traffic through CloudFront to the **Spring Boot backend**.

---

## 1. Infrastructure Scripts Inventory (`deploy/aws/`)

| File | Purpose |
|---|---|
| [`setup-s3-cloudfront.sh`](file:///home/sivaram/Github/homecare-connect/deploy/aws/setup-s3-cloudfront.sh) | One-time provisioning script: creates a private S3 bucket (AES256 encrypted, all public access blocked), creates a CloudFront Origin Access Control (OAC), creates a CloudFront distribution with SPA fallback (`403`/`404` -> `/index.html` `200 OK`), attaches the S3 bucket policy for CloudFront OAC, and writes `.deploy-env`. |
| [`add-api-origin-cloudfront.sh`](file:///home/sivaram/Github/homecare-connect/deploy/aws/add-api-origin-cloudfront.sh) | Attaches the backend server (`BACKEND_HOST` + `BACKEND_PORT`) as a second custom origin on the existing CloudFront distribution and adds a `/api/*` cache behavior (`https-only`, `CachingDisabled`, `AllViewerExceptHostHeader`). |
| [`deploy-frontend.sh`](file:///home/sivaram/Github/homecare-connect/deploy/aws/deploy-frontend.sh) | Repeatable build & deploy script: runs `npm ci && npm run build`, syncs hashed assets (`--exclude index.html`) with 1-year immutable cache headers, uploads `index.html` with `no-cache, no-store, must-revalidate`, and triggers a CloudFront `/*` cache invalidation. |
| [`provision-and-deploy-policy.json`](file:///home/sivaram/Github/homecare-connect/deploy/aws/provision-and-deploy-policy.json) | IAM policy required for an operator running `setup-s3-cloudfront.sh` and `deploy-frontend.sh` (`s3:*` on the bucket + `cloudfront:*`). |
| [`github-actions-deploy-policy.json`](file:///home/sivaram/Github/homecare-connect/deploy/aws/github-actions-deploy-policy.json) | Least-privilege IAM policy for CI/CD (`s3:ListBucket`, `s3:GetObject`, `s3:PutObject`, `s3:DeleteObject`, `cloudfront:CreateInvalidation`). |

---

## 2. Step-by-Step AWS Frontend & API Proxy Setup

### Step 1: Provision Private S3 Bucket & CloudFront Distribution
```bash
AWS_REGION=us-east-1 \
BUCKET_NAME=homecare-connect-web-prod \
./deploy/aws/setup-s3-cloudfront.sh
```
This outputs `.deploy-env` at the repository root containing `AWS_REGION`, `BUCKET_NAME`, `DISTRIBUTION_ID`, and `CLOUDFRONT_DOMAIN`.

### Step 2: Attach Backend API Origin (`/api/*`) to CloudFront
To serve `/api/*` over HTTPS under the same CloudFront domain (`VITE_API_BASE_URL=""`):
```bash
DISTRIBUTION_ID=<your-cloudfront-distribution-id> \
BACKEND_HOST=<your-ec2-public-dns-or-alb-hostname> \
BACKEND_PORT=8080 \
./deploy/aws/add-api-origin-cloudfront.sh
```

> [!IMPORTANT]
> CloudFront custom origins require a **DNS hostname** (`ec2-xx-xx-xx-xx.compute-1.amazonaws.com` or an ALB/custom domain), not a bare IPv4 address. If you pass a raw IP (`18.212.164.25`), `add-api-origin-cloudfront.sh` automatically converts it to `ec2-18-212-164-25.compute-1.amazonaws.com`.

### Step 3: Build & Deploy the Frontend
```bash
VITE_API_BASE_URL="" \
VITE_GOOGLE_CLIENT_ID="<your-google-client-id>" \
./deploy/aws/deploy-frontend.sh
```

---

## 3. Caching & Security Design in CloudFront

1. **Private S3 Bucket with Origin Access Control (OAC)**:
   - `aws s3api put-public-access-block` blocks all public ACLs and policies (`BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true`).
   - Only `cloudfront.amazonaws.com` with `AWS:SourceArn == arn:aws:cloudfront::<account>:distribution/<id>` can read objects from S3 via SigV4 OAC signing.
2. **Two-Tier Cache Headers (`deploy-frontend.sh`)**:
   - **Static Hashed Assets (`assets/*`)**: Uploaded with `Cache-Control: public, max-age=31536000, immutable`. Because Vite hashes asset filenames on every build, browsers and CloudFront edge nodes can cache them for 1 year.
   - **SPA Entry Point (`index.html`)**: Uploaded with `Cache-Control: no-cache, no-store, must-revalidate` so browsers always fetch the latest bundle references after a deployment.
3. **Unbuffered `/api/*` Pass-Through (`add-api-origin-cloudfront.sh`)**:
   - `AllowedMethods`: `GET, HEAD, OPTIONS, PUT, POST, PATCH, DELETE`
   - `CachePolicyId`: `4135ea2d-6df8-44a3-9df3-4b5a84be39ad` (AWS Managed `CachingDisabled`)
   - `OriginRequestPolicyId`: `b689b0a8-53d0-40ab-baf2-68738e2966ac` (AWS Managed `AllViewerExceptHostHeader`) — forwards `Authorization`, `Content-Type`, and all query parameters to Spring Boot.

---

## 4. Backend Production Deployment Checklist

When deploying the Spring Boot backend (`backend/target/homecare-connect-0.0.1-SNAPSHOT.jar`) to EC2 or a container platform:
1. **Build the JAR**:
   ```bash
   cd backend && ./mvnw clean package -DskipTests
   ```
2. **Configure Required Production Environment Variables**:
   - `DB_URL`, `DB_USER`, `DB_PASSWORD` (pointing to production PostgreSQL 16 with SSL enabled)
   - `JWT_SECRET` (at least 32 cryptographically random bytes)
   - `CORS_ALLOWED_ORIGINS=https://<your-cloudfront-or-custom-domain>`
   - `PHOTOS_DIR=/var/lib/homecare/uploads/photos` and `DOCUMENTS_DIR=/var/lib/homecare/uploads/documents` (mounted on persistent EBS storage so uploads survive container/process restarts)
   - `EMAIL_ENABLED=true` (+ `EMAIL_SMTP_*`, `EMAIL_FROM`, `EMAIL_ADMIN_RECIPIENTS`)
   - `WHATSAPP_ENABLED=true` (+ `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_ADMIN_RECIPIENT`)
3. **Single-Instance Requirement (Current Architecture)**:
   - Run **1 backend instance** unless you migrate `uploads/` to shared storage (e.g., S3) and add `FOR UPDATE SKIP LOCKED` to `NotificationRepository.findDue`, as documented in [Architecture Decisions](../architecture/architecture-decisions.md).
