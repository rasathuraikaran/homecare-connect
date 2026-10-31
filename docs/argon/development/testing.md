# Testing Guide

HomeCare Connect has an automated JUnit 5 and Spring Boot integration/unit test suite under `backend/src/test/java/com/homecare/` covering authentication, profiles, directory filtering, reviews, bookings, outbox notifications, SMTP/WhatsApp senders, caregiver verification, and admin user management.

---

## 1. Running the Test Suite

Run all tests from the `backend/` directory using the Maven Wrapper:

```bash
cd backend
./mvnw test
```

To run a single test class:

```bash
cd backend
./mvnw test -Dtest=CaregiverVerificationControllerTest
```

> [!NOTE]
> Running `./mvnw test` does **not** require Docker or PostgreSQL to be running. The `@ActiveProfiles("test")` profile uses an in-memory H2 database configured in PostgreSQL compatibility mode (`jdbc:h2:mem:homecare;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH`) and executes all Flyway migrations (`V1`–`V11`) automatically.

---

## 2. Test Configuration (`application-test.yml`)

[`backend/src/main/resources/application-test.yml`](file:///home/sivaram/Github/homecare-connect/backend/src/main/resources/application-test.yml) configures the test environment:
- **Database**: H2 in-memory (`MODE=PostgreSQL`), with Flyway migrations enabled so every test validates the real SQL migrations.
- **File Uploads**: Isolated to `target/test-photos` and `target/test-documents`.
- **Scheduler Disabled**: `spring.task.scheduling.pool.size: 0` and `app.notifications.poll-interval-ms: 3600000` prevent [`NotificationScheduler`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/NotificationScheduler.java) from running asynchronously in the background during tests. Instead, tests call `notificationDispatcher.dispatchDue()` directly for deterministic assertions.

---

## 3. Inventory of Automated Test Classes (11 Test Suites)

| Test Class | Type | Key Scenarios Verified |
|---|---|---|
| [`AuthControllerTest`](file:///home/sivaram/Github/homecare-connect/backend/src/test/java/com/homecare/auth/AuthControllerTest.java) | `@SpringBootTest` + `@AutoConfigureMockMvc` | Email registration (`201`), duplicate email (`409`), weak password (`400`), login (`200`), wrong password (`401`), `/api/auth/me` with valid/missing token (`200`/`401`), and admin flag propagation (`userRepository.setAdmin(id, true)`). |
| [`GoogleAuthTest`](file:///home/sivaram/Github/homecare-connect/backend/src/test/java/com/homecare/auth/GoogleAuthTest.java) | `@SpringBootTest` + `@TestConfiguration` (`StubConfig` for [`GoogleTokenVerifier`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/GoogleTokenVerifier.java)) | First-time Google login without role (`428 Precondition Required`), first-time Google login with role (`200` creates account), returning Google user (`200` without needing role), existing email/password account linking (`google_sub` attached), Google-only user attempting password login (`401`), and invalid Google token (`401`). |
| [`ProfileAndDirectoryTest`](file:///home/sivaram/Github/homecare-connect/backend/src/test/java/com/homecare/profile/ProfileAndDirectoryTest.java) | `@SpringBootTest` + `@AutoConfigureMockMvc` | Caregiver profile creation/update, Care Seeker forbidden from updating profile (`403`), directory filtering (`minAge`, `maxAge`, `minExperience`, `jobType`, `rateType`, `maxRate`, `q`), availability toggle (`PATCH /api/profile/me/availability`) hiding unavailable caregivers, valid PNG/JPEG upload and magic-byte spoofing rejection (`400`), and verifying caregiver phone/email are never leaked in public directory responses. |
| [`ReviewControllerTest`](file:///home/sivaram/Github/homecare-connect/backend/src/test/java/com/homecare/review/ReviewControllerTest.java) | `@SpringBootTest` + `@AutoConfigureMockMvc` | Care Seeker submitting a review (`200`), average rating and review count aggregation on directory cards, reviewer name shortening (`"Priya Subramaniam"` -> `"Priya S."`), idempotent review update by same seeker (1 review per `(caregiver, seeker)` pair), Caregiver forbidden from reviewing (`403`), and rating bounds validation (`0` or `6` -> `400`). |
| [`BookingAndAdminTest`](file:///home/sivaram/Github/homecare-connect/backend/src/test/java/com/homecare/booking/BookingAndAdminTest.java) | `@SpringBootTest` + `@AutoConfigureMockMvc` | Seeker creating a booking (`201`) with snapshotted `quotedAmount` and enqueued `EMAIL` + `WHATSAPP` outbox notifications, duplicate open booking rejection (`409`), max 3 open requests cap (`409`), Caregiver forbidden from creating bookings (`403`), Seeker cancelling `REQUESTED` booking (`200`), Admin confirming (`CONFIRMED`) and completing (`COMPLETED`) bookings, Seeker blocked from cancelling once `CONFIRMED` (`409`), and non-admin blocked from `/api/admin/**` (`403`). |
| [`NotificationDispatchTest`](file:///home/sivaram/Github/homecare-connect/backend/src/test/java/com/homecare/notification/NotificationDispatchTest.java) | `@SpringBootTest` + `@TestConfiguration` (`FakeSender`, `FakeWhatsApp`) | Outbox dispatch marking `SENT`, transient failure scheduling exponential backoff (`PENDING`), permanent failure (`FAILED`), retry exhaustion after `MAX_ATTEMPTS = 5` (`FAILED`), and admin manual retry (`POST /api/admin/bookings/{id}/notifications/retry`) resetting `FAILED` rows to `PENDING` (`attempts = 0`) and immediately re-dispatching. |
| [`EmailConfigValidationTest`](file:///home/sivaram/Github/homecare-connect/backend/src/test/java/com/homecare/notification/EmailConfigValidationTest.java) | Pure JUnit 5 Unit Test | Validates [`EmailConfig`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/EmailConfig.java) fail-fast startup rules (`host`, `port`, `username`, `password`, `from`, `adminRecipients`), CRLF/display-name header injection rejection, and `parseRecipients` deduplication. |
| [`SmtpEmailSenderTest`](file:///home/sivaram/Github/homecare-connect/backend/src/test/java/com/homecare/notification/SmtpEmailSenderTest.java) | Pure JUnit 5 Unit Test (`CapturingMailSender`) | Verifies `SimpleMailMessage` construction (`from`, multiple `to` recipients, `subject`, `text`) and verifies `MailException` translates to a retryable `Notification.SendFailure` (`permanent = false`). |
| [`WhatsAppSenderTest`](file:///home/sivaram/Github/homecare-connect/backend/src/test/java/com/homecare/notification/WhatsAppSenderTest.java) | Pure JUnit 5 Unit Test (`MockRestServiceServer`) | Verifies [`WhatsAppConfig`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/WhatsAppConfig.java) fail-fast validation, verifies `POST /v22.0/{phoneId}/messages` request headers (`Bearer <token>`) and JSON payload (`template.name = "new_booking_details"`), and verifies retry semantics (`200` -> success, `400` -> `permanent = true`, `429` -> `permanent = false`, `503` -> `permanent = false`). |
| [`CaregiverVerificationControllerTest`](file:///home/sivaram/Github/homecare-connect/backend/src/test/java/com/homecare/verification/CaregiverVerificationControllerTest.java) | `@SpringBootTest` + `@AutoConfigureMockMvc` | Default `NOT_REQUIRED` status (`listed = true`), admin toggling `document_requirements` (`PUT /api/admin/document-requirements`) delisting unverified caregivers of that `JobType`, booking blocked for unlisted caregivers (`404`), uploading PDF/PNG documents (`201`), admin rejecting with reason (`ACTION_NEEDED`) and re-uploading (`revision = 2`), admin approving all required docs (`VERIFIED`, `listed = true`), and blocking deletion/replacement of `APPROVED` documents (`409`). |
| [`AdminUserControllerTest`](file:///home/sivaram/Github/homecare-connect/backend/src/test/java/com/homecare/admin/AdminUserControllerTest.java) | `@SpringBootTest` + `@AutoConfigureMockMvc` | Admin listing/searching users by role/query, inspecting full user detail (`profile`, `documents`, `bookingsAsSeeker`, `bookingsAsCaregiver`, `reviewsReceived`, `reviewsWritten`), non-admin forbidden (`403`), deleting a caregiver cascading all rows and deleting uploaded files from disk, and blocking self-deletion or deletion of another admin (`409`). |
