# Architecture Decisions (ADRs)

This document captures the significant architectural decisions implemented in **HomeCare Connect**, the rationale behind them, and their concrete codebase realization.

---

## ADR-01: Transactional Outbox Pattern for Admin Booking Alerts

- **Context**: When a care seeker requests a caregiver, the administrator must be notified via SMTP Email or Meta's WhatsApp Business Cloud API. External network calls to SMTP servers or Meta's Graph API can be slow, rate-limited, or temporarily unavailable.
- **Decision**: Persist a [`Notification`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/Notification.java#L9) record in the `notifications` table within the **same `@Transactional` boundary** as the [`Booking`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/Booking.java#L11) creation ([`BookingService.create`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/BookingService.java#L38-L71)). A background scheduler ([`NotificationScheduler`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/NotificationScheduler.java#L12)) polls every 5 seconds and dispatches due notifications in isolated per-notification transactions via [`NotificationDispatcher`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/NotificationDispatcher.java#L45-L60).
- **Consequences**:
  - Booking requests return immediately to the user and are never lost during third-party outages.
  - Temporary failures retry up to 5 times with exponential backoff (`30s`, `60s`, `2m`, `4m`, capped at `15m`), while permanent failures (e.g., invalid SMTP credentials or WhatsApp HTTP 400/401) halt immediately and expose the error reason on the Admin page with a manual **Send alert again** action ([`AdminBookingService.resendAlert`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/AdminBookingService.java#L58-L71)).

---

## ADR-02: Materialized `listed` Flag on `caregiver_profiles` with Event-Driven Recomputation

- **Context**: Care seekers browse caregivers by `jobType`. Whether a caregiver is eligible to be shown depends on whether they have `APPROVED` documents for all document types currently marked mandatory (`required = true` in `document_requirements`) for their job. Evaluating multi-table document subqueries on every directory search would complicate queries and hurt read performance.
- **Decision**: Store a boolean column `listed` directly on `caregiver_profiles` ([`V11__addresses_and_document_rules.sql`](file:///home/sivaram/Github/homecare-connect/backend/src/main/resources/db/migration/V11__addresses_and_document_rules.sql#L8)). Directory queries filter simply by `p.available = true and p.listed = true` ([`CaregiverProfileRepository.findCards`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/CaregiverProfileRepository.java#L13-L16)). Recompute `listed` synchronously on every state transition that can affect it:
  1. Caregiver changes `jobType` -> [`ProfileService.save`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/ProfileService.java#L46) publishes [`ProfileJobChanged`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/ProfileJobChanged.java#L4) -> handled by [`VerificationService.onJobChanged`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/VerificationService.java#L82-L85).
  2. Caregiver uploads or deletes a document -> [`VerificationService.upload`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/VerificationService.java#L68) / [`remove`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/VerificationService.java#L78).
  3. Admin approves or rejects a document -> [`AdminVerificationService.review`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/AdminVerificationService.java#L97).
  4. Admin toggles a mandatory document requirement -> [`AdminRequirementService.set`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/AdminRequirementService.java#L51) calls [`VerificationService.refreshJob`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/VerificationService.java#L96-L109).

---

## ADR-03: Optimistic Concurrency via `revision` Counter on Document Reviews

- **Context**: A caregiver might upload a replacement document while an administrator is viewing the previous file. If the administrator clicks "Approve" without knowing the file was replaced, an uninspected document would be marked `APPROVED`.
- **Decision**: Maintain an integer `revision` column on `caregiver_documents` ([`CaregiverDocument.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/CaregiverDocument.java#L30)), incremented on every [`replaceFile`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/CaregiverDocument.java#L53-L61). Admin review requests (`PATCH /api/admin/documents/{id}`) must supply the `revision` they inspected; [`AdminVerificationService.review`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/AdminVerificationService.java#L82-L84) rejects mismatches with `HTTP 409 CONFLICT`.

---

## ADR-04: Out-of-Band Admin Privilege Provisioning (`users.is_admin`)

- **Context**: Exposing admin registration or promotion in public/web APIs introduces privilege escalation risks.
- **Decision**: Admin status is not a `Role` enum value (`users.role` is strictly `CAREGIVER` or `CARE_SEEKER`, enforced by a database `CHECK` constraint in [`V1__create_users.sql`](file:///home/sivaram/Github/homecare-connect/backend/src/main/resources/db/migration/V1__create_users.sql#L7)). Instead, `users.is_admin` is a boolean column with **no mutator method** on the [`User`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/user/User.java#L32-L33) JPA entity. Admin rights can only be granted or revoked via direct database access (`./start-service.sh make-admin EMAIL [--remove]`). Furthermore, [`AdminUserService.delete`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/admin/AdminUserService.java#L88-L94) forbids deleting any admin account until its admin flag is revoked via CLI.

---

## ADR-05: Live User Resolution in `JwtAuthFilter`

- **Context**: Purely claim-based stateless JWTs continue to grant access until token expiration (120 minutes) even after an account is deleted or its admin privileges are changed.
- **Decision**: [`JwtAuthFilter`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/JwtAuthFilter.java#L35-L43) extracts only the `sub` (user ID) from the signed JWT and loads the current [`User`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/user/User.java#L8) row from `UserRepository` on every authenticated request.
- **Consequences**:
  - Deleting a user account immediately invalidates all active tokens for that user (`HTTP 401`).
  - Granting or revoking `is_admin` via `make-admin` takes effect on the very next API call without requiring re-login.

---

## ADR-06: Content-Based Magic-Byte File Validation & UUID Naming

- **Context**: Client-supplied filenames and `Content-Type` headers in multipart uploads can be spoofed for path traversal or XSS attacks.
- **Decision**: Both [`PhotoStorage`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/PhotoStorage.java#L80-L86) and [`DocumentStorage`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/DocumentStorage.java#L88-L93) inspect the raw magic bytes of uploaded files (`FF D8 FF` for JPG, `89 50 4E 47` for PNG, `RIFF....WEBP` for WebP, and `%PDF-` for PDF), ignore the original filename, store files under `UUID.randomUUID() + "." + ext`, enforce strict regex validation (`^[a-f0-9-]{36}\.(jpg|png|webp|pdf)$`) before reading or deleting, and serve files with `X-Content-Type-Options: nosniff`.

---

## ADR-07: CloudFront Dual-Origin Proxy to Eliminate HTTPS -> HTTP Mixed Content

- **Context**: In production on AWS, the React SPA is served over HTTPS from CloudFront + S3, while the EC2 backend instance may listen on plain HTTP (port 80/8081). Browser security blocks HTTPS pages from making `fetch()` calls to `http://` origins (Mixed Content).
- **Decision**:
  1. Configure CloudFront with two origins: the private S3 bucket (default behavior) and `Backend-API` (custom HTTP origin routing `/api/*` with caching disabled and `AllViewerExceptHostHeader` origin request policy) in [`setup-s3-cloudfront.sh`](file:///home/sivaram/Github/homecare-connect/deploy/aws/setup-s3-cloudfront.sh#L87-L135) and [`add-api-origin-cloudfront.sh`](file:///home/sivaram/Github/homecare-connect/deploy/aws/add-api-origin-cloudfront.sh#L29-L80).
  2. In [`frontend/src/api/client.js`](file:///home/sivaram/Github/homecare-connect/frontend/src/api/client.js#L6-L15), automatically fall back to same-origin `/api` whenever `window.location.protocol === 'https:'` and `VITE_API_BASE_URL` begins with `http://`.
