# Deployment Architecture Diagrams

This document illustrates both the **Local Development Topology** (managed by `start-service.sh` and Docker Compose) and the **AWS Production Topology** (managed by `deploy/aws/*.sh` and GitHub Actions).

---

## 1. Local Development Topology

Managed by [`start-service.sh`](file:///home/sivaram/Github/homecare-connect/start-service.sh) and [`docker-compose.yml`](file:///home/sivaram/Github/homecare-connect/docker-compose.yml).

```mermaid
flowchart LR
    subgraph Host["Developer Workstation (Linux / macOS)"]
        Browser["Browser\nhttp://localhost:5173"]

        subgraph ViteProc["Vite Dev Server (:5173)\n(.run/frontend.pid)"]
            ViteSPA["React HMR Server"]
            ViteProxy["Vite /api Proxy\n(vite.config.js)"]
        end

        subgraph SpringProc["Spring Boot Process (:8080)\n(.run/backend.pid)"]
            BootApp["HomecareApplication\n(Java 17 JVM)"]
            RunFiles[".run/jwt.secret\n.run/backend.log\n.run/frontend.log"]
            Uploads["uploads/photos\nuploads/documents"]
        end

        subgraph Docker["Docker Engine (docker-compose.yml)"]
            PG[("Container: homecare-postgres\npostgres:16-alpine (:5432)\nVolume: pgdata")]
        end
    end

    Browser -->|"GET /* (SPA assets)"| ViteSPA
    Browser -->|"HTTP /api/*"| ViteProxy
    ViteProxy -->|"Forwards to\nhttp://localhost:8080/api/*"| BootApp
    BootApp -->|"JDBC :5432\n(homecare / homecare)"| PG
    BootApp -->|"Reads/Writes"| Uploads
    BootApp -.->|"Logs & PID"| RunFiles
```

---

## 2. AWS Production & CI/CD Topology

Provisioned via [`setup-s3-cloudfront.sh`](file:///home/sivaram/Github/homecare-connect/deploy/aws/setup-s3-cloudfront.sh), [`add-api-origin-cloudfront.sh`](file:///home/sivaram/Github/homecare-connect/deploy/aws/add-api-origin-cloudfront.sh), [`deploy-frontend.sh`](file:///home/sivaram/Github/homecare-connect/deploy/aws/deploy-frontend.sh), and [`.github/workflows/deploy-frontend.yml`](file:///home/sivaram/Github/homecare-connect/.github/workflows/deploy-frontend.yml).

```mermaid
flowchart TB
    subgraph Users["End Users"]
        Viewer["Web / Mobile Browser"]
    end

    subgraph GitHub["GitHub Repository"]
        GHA["GitHub Actions Runner\n(.github/workflows/deploy-frontend.yml)"]
    end

    subgraph AWS["Amazon Web Services (AWS)"]
        subgraph Edge["Amazon CloudFront CDN (HTTPS)"]
            CF["CloudFront Distribution\n(PriceClass_100, HTTP/2 & HTTP/3)"]
            DefaultBeh["Default Behavior (*)\nRedirect HTTP->HTTPS\nCacheOptimized\n403/404 -> /index.html (200 OK)"]
            ApiBeh["Path Behavior (/api/*)\nViewerProtocolPolicy: https-only\nCachingDisabled (4135ea2d...)\nAllViewerExceptHostHeader (b689b0a8...)"]
        end

        subgraph FrontendOrigin["Frontend Origin (Private S3)"]
            S3[("S3 Bucket (AES256, All Public Access Blocked)\n- /assets/* (max-age=31536000, immutable)\n- /index.html (no-cache, no-store)")]
        end

        subgraph BackendOrigin["Backend Origin (Compute & Data)"]
            EC2["Backend Host (EC2 / ALB)\nSpring Boot :8080"]
            EBS["Mounted Disk Volume\n(uploads/photos, uploads/documents)"]
            RDS[("PostgreSQL 16\n(RDS or Self-Hosted)")]
        end
    end

    Viewer -->|"HTTPS Requests"| CF
    CF -->|"/* (Static Assets & SPA Routes)"| DefaultBeh
    CF -->|"/api/* (REST & Multipart Calls)"| ApiBeh

    DefaultBeh -->|"Origin Access Control (OAC)\nSigV4 Signed Requests"| S3
    ApiBeh -->|"HTTP (:8080)\nForwards Auth, Query, Body"| EC2

    EC2 -->|"JDBC"| RDS
    EC2 -->|"File I/O"| EBS

    GHA -->|"1. npm ci && npm run build\n2. aws s3 sync dist/ s3://bucket"| S3
    GHA -->|"3. aws cloudfront create-invalidation\n(--paths '/*')"| CF
```

---

## 3. Why CloudFront Proxies `/api/*` in Production

In [`add-api-origin-cloudfront.sh`](file:///home/sivaram/Github/homecare-connect/deploy/aws/add-api-origin-cloudfront.sh), the EC2 backend is attached as a custom HTTP origin behind the same CloudFront distribution that serves the S3 frontend:
1. **Eliminates Mixed-Content Blocks**: Browsers block `https://<cloudfront-domain>` pages from calling an `http://<ec2-ip>:8080` API directly (`Mixed Content`). By routing `/api/*` through CloudFront (`VITE_API_BASE_URL=""`), the browser communicates exclusively over HTTPS with CloudFront, while CloudFront forwards `/api/*` requests to the EC2 origin over HTTP.
2. **Eliminates Cross-Origin Preflight Overhead**: Serving both `/*` and `/api/*` from the same CloudFront origin makes all API requests same-origin from the browser's perspective.
3. **Header & Query Forwarding**: Using AWS managed `OriginRequestPolicy` `AllViewerExceptHostHeader` (`b689b0a8-53d0-40ab-baf2-68738e2966ac`) and `CachePolicy` `CachingDisabled` (`4135ea2d-6df8-44a3-9df3-4b5a84be39ad`) ensures `Authorization: Bearer <jwt>`, query filters (`?jobType=...`), and multipart upload bodies reach Spring Boot untouched and uncached.
