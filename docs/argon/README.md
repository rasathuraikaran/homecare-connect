# HomeCare Connect — Technical Documentation

Welcome to the comprehensive technical documentation for **HomeCare Connect**, a full-stack marketplace connecting **Caregivers** (Caretakers, Nurses, Physiotherapists) with **Care Seekers** (families), moderated by **Administrators** who coordinate booking requests, verify caregiver documents, configure job-level document requirements, and manage user accounts.

---

## Documentation Sitemap

### Phase 1: Project Discovery & Technical Audit
- [Phase 1 Discovery & Codebase Audit](./phase-1-discovery.md) — Complete inventory of source modules, dependencies, configurations, database tables, endpoints, tests, and code audit findings (unused imports, duplicated constants, transaction cleanup inconsistencies, and scaling boundaries).

### Architecture & System Design (`architecture/`)
- [System Overview](./architecture/system-overview.md) — High-level architecture, domain model, user personas, technology stack, and runtime topology.
- [Architecture Decisions (ADRs)](./architecture/architecture-decisions.md) — Key architectural patterns, design principles, and trade-offs backed by codebase evidence.
- [Component Architecture](./architecture/component-architecture.md) — Application layers, module boundaries, package dependencies, and frontend/backend component interactions.
- [Data Flow & Execution Lifecycle](./architecture/data-flow.md) — Request lifecycle, synchronous vs. asynchronous flows, transaction boundaries, and post-commit file synchronization.
- **Architecture Diagrams (`architecture/diagrams/`)**:
  - [System Context Diagram](./architecture/diagrams/system-context.md)
  - [Container & Component Architecture Diagram](./architecture/diagrams/container-component.md)
  - [Database Entity-Relationship (ER) Diagram](./architecture/diagrams/database-er.md)
  - [Authentication & Authorization Flow Diagrams](./architecture/diagrams/authentication-flow.md)
  - [Core Workflow Sequence & State Diagrams](./architecture/diagrams/core-workflows.md)
  - [Deployment Architecture Diagram](./architecture/diagrams/deployment-architecture.md)

### Development Guide (`development/`)
- [Getting Started](./development/getting-started.md) — One-command quickstart (`./start-service.sh start --demo`) and first-time walkthrough.
- [Prerequisites](./development/prerequisites.md) — Required runtime versions (Docker, Java 17+, Node 18+, curl) and OS notes.
- [Local Setup](./development/local-setup.md) — Automated script lifecycle vs. manual service startup, port overrides, and demo seeding.
- [Configuration Reference](./development/configuration.md) — Complete reference for `.env.local`, `application.yml`, `application-test.yml`, and Vite environment variables.
- [Coding Standards & Conventions](./development/coding-standards.md) — Java/Spring Boot and React/Vite conventions, DTO validation, privacy masking, and CSS theming.
- [Testing Guide](./development/testing.md) — Test suite architecture, H2 PostgreSQL compatibility mode, test doubles, and running tests.

### Backend Reference (`backend/`)
- [Module Overview](./backend/module-overview.md) — Package-by-feature organization (`auth`, `user`, `profile`, `directory`, `review`, `booking`, `notification`, `verification`, `admin`, `config`).
- [REST API Reference](./backend/api-reference.md) — Complete specification of all 29 HTTP endpoints, request/response payloads, validation constraints, and status codes.
- [Authentication & Authorization](./backend/authentication-authorization.md) — Stateless JWT lifecycle, `JwtAuthFilter` database resolution, Google OAuth2 ID token verification, and role/admin guards.
- [Error Handling Strategy](./backend/error-handling.md) — Global `ApiExceptionHandler`, `ErrorResponse` contract, and field validation formatting.
- [External Integrations](./backend/integrations.md) — Google Identity Services, SMTP Email (`JavaMailSender`), Meta WhatsApp Business Cloud API (`RestClient`), and local magic-byte file storage.

### Frontend Reference (`frontend/`)
- [Frontend Architecture](./frontend/frontend-architecture.md) — React 18 SPA structure, routing guards, role-based CSS theming, API client mixed-content handling, and dialog/polling patterns.

### Database Reference (`database/`)
- [Database Schema Dictionary](./database/schema.md) — Detailed column-by-column specification, constraints, check rules, and indexes for all 8 application tables.
- [Entity Relationships & Transaction Boundaries](./database/entity-relationships.md) — 1:1 and 1:N relationships, `ON DELETE CASCADE` / `ON DELETE SET NULL` behavior, and JPA mapping details.
- [Database Migrations (Flyway)](./database/migrations.md) — Chronological breakdown of Flyway migrations `V1__create_users.sql` through `V11__addresses_and_document_rules.sql`.

### Infrastructure & Deployment (`infrastructure/`)
- [Deployment Architecture](./infrastructure/deployment.md) — AWS S3 + CloudFront OAC + EC2 `/api/*` reverse proxy architecture and production checklist.
- [Docker Configuration](./infrastructure/docker.md) — `docker-compose.yml` PostgreSQL 16 service, healthchecks, and volume persistence.
- [CI/CD Pipeline](./infrastructure/ci-cd.md) — GitHub Actions workflow (`deploy-frontend.yml`), IAM policies, caching strategy, and CloudFront invalidation.
- [Monitoring & Health Checks](./infrastructure/monitoring.md) — Process liveness checks, database readiness probes, and outbox alert state tracking.

### Operations (`operations/`)
- [Operational Runbook](./operations/operational-runbook.md) — Day-2 operations: managing admins (`make-admin`), configuring SMTP/WhatsApp, document requirement rollouts, and account deletion.
- [Logging & Privacy](./operations/logging.md) — Log locations (`.run/`), PII masking rules (`SmtpEmailSender`, `WhatsAppCloudSender`), and admin audit trails.
- [Troubleshooting Guide](./operations/troubleshooting.md) — Common startup, port conflict, CORS, mixed-content, email/WhatsApp, and upload errors with resolutions.

### Contributing
- [Contributing Guide](./contributing.md) — How to add new caregiver job types, rate types, document types, database migrations, and tests.
