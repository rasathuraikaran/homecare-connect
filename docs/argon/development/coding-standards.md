# Coding Standards & Design Conventions

This document captures the architectural, security, and coding conventions enforced across the HomeCare Connect backend and frontend codebases.

---

## 1. Backend Java Conventions (`backend/src/main/java/com/homecare/`)

### 1.1 Package-by-Feature & Visibility Encapsulation
- **Domain Packages**: Organize code by business domain (`admin`, `auth`, `booking`, `common`, `config`, `directory`, `notification`, `profile`, `review`, `user`, `verification`) rather than technical horizontal layers (`controllers/`, `services/`, `repositories/`).
- **Package-Private Controllers & DTOs**: When a controller, helper record, or internal bean is not referenced outside its domain package (e.g., [`CaregiverVerificationController`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/CaregiverVerificationController.java), [`AdminVerificationController`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/AdminVerificationController.java), [`AdminUserController`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/admin/AdminUserController.java)), keep it package-private rather than `public`.

### 1.2 Immutable Java `record` DTOs & Never Exposing JPA Entities
- **Never serialize JPA `@Entity` classes directly in HTTP responses.** Always map entities to immutable Java 17 `record` DTOs (`UserResponse`, `ProfileResponse`, `CaregiverCard`, `BookingResponse`, `AdminBookingResponse`, `ReviewResponse`, `CaregiverDocumentDto`, etc.).
- **Why**: Prevents accidental exposure of sensitive columns (`password_hash`, `google_sub`, caregiver `phone`/`email`/`address` on public directory endpoints) and avoids `LazyInitializationException` during Jackson serialization.

### 1.3 Privacy-by-Design in Public Responses
1. **No Contact Info on Public Caregiver Cards**:
   - [`CaregiverCard`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/directory/CaregiverCard.java) (returned by `GET /api/caregivers` and `GET /api/caregivers/{id}`) deliberately omits `email`, `phone`, and `address`.
   - Full caregiver contact details are returned **only** in [`AdminCaregiverCard`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/directory/AdminCaregiverCard.java) (`GET /api/admin/bookings/caregivers/{id}`) and [`AdminUserService.UserDetailResponse`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/admin/AdminUserService.java#L198-L210) (`GET /api/admin/users/{id}`).
2. **Reviewer Name Shortening**:
   - [`ReviewService.shortName(String fullName)`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/review/ReviewService.java#L121-L131) formats a care seeker's full name as `"First L."` (e.g., `"Priya Subramaniam"` -> `"Priya S."`) so public caregiver profiles do not expose full surnames of reviewers.
3. **PII Masking in Production Logs**:
   - [`SmtpEmailSender.maskAddress`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/SmtpEmailSender.java#L65-L71) logs `adm***@example.com`.
   - [`WhatsAppCloudSender.maskPhone`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/WhatsAppCloudSender.java#L152-L157) logs `***4567` (last 4 digits only).

### 1.4 Validation & Error Handling
- Use Jakarta Bean Validation annotations (`@NotBlank`, `@NotNull`, `@Email`, `@Size`, `@Min`, `@Max`, `@DecimalMin`, `@DecimalMax`, `@Valid`) on request records and `@Validated` on controller classes.
- For domain rule violations in `@Service` classes, throw `org.springframework.web.server.ResponseStatusException` with an explicit `HttpStatus`:
  - `HttpStatus.BAD_REQUEST` (`400`) for invalid domain inputs (e.g., unquoted `RateType`, invalid magic bytes).
  - `HttpStatus.UNAUTHORIZED` (`401`) for bad credentials or unverified Google tokens.
  - `HttpStatus.FORBIDDEN` (`403`) for ownership violations (e.g., seeker cancelling someone else's booking).
  - `HttpStatus.NOT_FOUND` (`404`) for missing or unlisted resources.
  - `HttpStatus.CONFLICT` (`409`) for state machine conflicts, duplicate open bookings, exceeding the 3-request limit, or deleting an approved document / admin account.
  - `HttpStatus.PRECONDITION_REQUIRED` (`428`) when a first-time Google user has not yet chosen a role.
  - `HttpStatus.SERVICE_UNAVAILABLE` (`503`) when an optional integration (`GOOGLE_CLIENT_ID`) is not configured.

### 1.5 File Upload Security Rules
Both [`PhotoStorage`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/PhotoStorage.java) and [`DocumentStorage`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/DocumentStorage.java) enforce four mandatory layers of defense:
1. **Strict Size Limit**: `<= 5 MB` for photos, `<= 10 MB` for documents.
2. **Content-Type Whitelist**: `image/jpeg`, `image/png`, `image/webp` (plus `application/pdf` for documents).
3. **Magic-Byte Signature Inspection**: Inspect the first 12 bytes of `file.getInputStream()` (`FF D8 FF` for JPEG, `89 50 4E 47...` for PNG, `RIFF....WEBP` for WebP, `%PDF-` for PDF). Never trust the client-supplied `Content-Type` or filename extension.
4. **UUID Filenames & Path Traversal Verification**: Ignore `file.getOriginalFilename()`. Generate `UUID.randomUUID() + ext`, validate against `^[a-f0-9-]{36}\.(jpg|png|webp|pdf)$`, and verify `target.getParent().equals(root)` before reading or deleting.

---

## 2. Frontend React & CSS Conventions (`frontend/src/`)

### 2.1 Central API Client (`src/api/client.js`)
- Never call `fetch()` directly from page or component files. Always use [`api(path, options)`](file:///home/sivaram/Github/homecare-connect/frontend/src/api/client.js#L49-L51) for JSON endpoints or [`apiBlob(path)`](file:///home/sivaram/Github/homecare-connect/frontend/src/api/client.js#L53-L62) for authenticated binary downloads (such as verification documents).
- Handle field-level validation errors via `err.errors` and top-level messages via `err.message`.

### 2.2 Synchronized Enum Constants (`src/constants/`)
Frontend enum metadata in `frontend/src/constants/` must stay strictly synchronized with backend Java enums:
- [`jobTypes.js`](file:///home/sivaram/Github/homecare-connect/frontend/src/constants/jobTypes.js) ↔ [`JobType.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/JobType.java)
- [`rateTypes.js`](file:///home/sivaram/Github/homecare-connect/frontend/src/constants/rateTypes.js) ↔ [`RateType.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/RateType.java)
- [`documentTypes.js`](file:///home/sivaram/Github/homecare-connect/frontend/src/constants/documentTypes.js) ↔ [`DocumentType.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/DocumentType.java) & [`VerificationStatus.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/VerificationStatus.java)
- [`bookingStatus.js`](file:///home/sivaram/Github/homecare-connect/frontend/src/constants/bookingStatus.js) ↔ [`BookingStatus.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/BookingStatus.java)

### 2.3 Role-Based CSS Custom Property Theming
- Styling lives in [`frontend/src/styles.css`](file:///home/sivaram/Github/homecare-connect/frontend/src/styles.css) using CSS custom properties (`--primary`, `--primary-dark`, `--accent`, etc.).
- [`useRoleTheme`](file:///home/sivaram/Github/homecare-connect/frontend/src/App.jsx#L70-L92) sets `document.documentElement.dataset.theme = 'seeker'` when a Care Seeker is logged in or when a guest selects the Care Seeker radio button on `/register`, switching the UI palette from the default Caregiver teal (`#0f766e`) to the Care Seeker warm amber/terracotta (`#b45309`).
