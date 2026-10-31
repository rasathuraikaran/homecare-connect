# Flyway Database Migrations (`V1` – `V11`)

HomeCare Connect manages all schema changes through versioned SQL migrations located in `backend/src/main/resources/db/migration/`. Flyway runs automatically on application startup (`spring.flyway.enabled: true`) before Hibernate validates the entity mappings (`spring.jpa.hibernate.ddl-auto: validate`).

---

## 1. Migration History & Evolution Walkthrough

| Version | File | Summary of Schema Changes | Architectural Context |
|---|---|---|---|
| **V1** | [`V1__create_users.sql`](file:///home/sivaram/Github/homecare-connect/backend/src/main/resources/db/migration/V1__create_users.sql) | Creates `users` table (`id`, `email`, `password_hash`, `full_name`, `phone`, `role`, `created_at`) with `CHECK (role IN ('CAREGIVER', 'CARE_SEEKER'))`. | Initial user authentication and persona separation. |
| **V2** | [`V2__google_login.sql`](file:///home/sivaram/Github/homecare-connect/backend/src/main/resources/db/migration/V2__google_login.sql) | `ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL`, adds `google_sub VARCHAR(64)` and unique index `idx_users_google_sub`. | Enables passwordless Google OAuth2 sign-in and account linking. |
| **V3** | [`V3__caregiver_profiles.sql`](file:///home/sivaram/Github/homecare-connect/backend/src/main/resources/db/migration/V3__caregiver_profiles.sql) | Creates `caregiver_profiles` (`user_id` PK/FK, `age` 18–100, `experience_years` 0–60, `qualification`, `photo_file`, `updated_at`). | Core caregiver profile and portrait photo support. |
| **V4** | [`V4__caregiver_job_type.sql`](file:///home/sivaram/Github/homecare-connect/backend/src/main/resources/db/migration/V4__caregiver_job_type.sql) | Adds `job_type VARCHAR(30)` to `caregiver_profiles`. | Categorizes caregivers into `CARETAKER`, `NURSING`, and `PHYSIOTHERAPIST`. |
| **V5** | [`V5__caregiver_rates.sql`](file:///home/sivaram/Github/homecare-connect/backend/src/main/resources/db/migration/V5__caregiver_rates.sql) | Creates `caregiver_rates` (`user_id`, `rate_type`, `amount > 0`, PK `(user_id, rate_type)`). | Supports multiple pricing tiers (`HOURLY`, `SESSION`, `DAILY`, `FULL_DAY_NIGHT`) per caregiver. |
| **V6** | [`V6__caregiver_availability.sql`](file:///home/sivaram/Github/homecare-connect/backend/src/main/resources/db/migration/V6__caregiver_availability.sql) | Adds `available BOOLEAN NOT NULL DEFAULT TRUE` to `caregiver_profiles`. | Allows caregivers to pause directory visibility without losing profile data. |
| **V7** | [`V7__reviews.sql`](file:///home/sivaram/Github/homecare-connect/backend/src/main/resources/db/migration/V7__reviews.sql) | Creates `reviews` table with `rating BETWEEN 1 AND 5`, `UNIQUE (caregiver_id, reviewer_id)`, and `idx_reviews_caregiver`. | Allows care seekers to rate and review caregivers (1 upsertable review per pair). |
| **V8** | [`V8__bookings_and_notifications.sql`](file:///home/sivaram/Github/homecare-connect/backend/src/main/resources/db/migration/V8__bookings_and_notifications.sql) | Adds `is_admin BOOLEAN NOT NULL DEFAULT FALSE` to `users`; creates `bookings` and `notifications` tables with composite indexes. | Introduces admin-mediated booking workflow and transactional outbox alerting. |
| **V9** | [`V9__widen_notification_columns.sql`](file:///home/sivaram/Github/homecare-connect/backend/src/main/resources/db/migration/V9__widen_notification_columns.sql) | Widens `notifications.recipient` (`40` -> `500`) and `notifications.params` (`1000` -> `4000`). | Needed because `EMAIL` notifications store comma-separated admin email addresses in `recipient` and full multi-line plain-text email bodies in `params`. |
| **V10** | [`V10__caregiver_documents.sql`](file:///home/sivaram/Github/homecare-connect/backend/src/main/resources/db/migration/V10__caregiver_documents.sql) | Adds `verified BOOLEAN NOT NULL DEFAULT FALSE` to `caregiver_profiles`; creates `caregiver_documents` with `UNIQUE (user_id, doc_type)`. | Introduces caregiver document uploads (`NIC`, `GS_LETTER`, `NURSING_CERTIFICATE`, `DEGREE_CERTIFICATE`) and admin review queue. |
| **V11** | [`V11__addresses_and_document_rules.sql`](file:///home/sivaram/Github/homecare-connect/backend/src/main/resources/db/migration/V11__addresses_and_document_rules.sql) | Adds `address VARCHAR(300)` to `caregiver_profiles` and `bookings`; renames `caregiver_profiles.verified` to `listed` and sets `listed = TRUE WHERE job_type IS NOT NULL`; creates and seeds `document_requirements` (`required = FALSE` by default). | Decouples directory listing (`listed`) from mandatory verification until admins selectively enable requirements per `(job_type, doc_type)`. |

---

## 2. Notable Schema Evolution Details (`V10` -> `V11`)

In `V10__caregiver_documents.sql`, `caregiver_profiles.verified` defaulted to `FALSE`, which meant every caregiver was hidden from the public directory until an admin manually approved all their documents.

In `V11__addresses_and_document_rules.sql`, the product team evolved this into a **dynamic policy matrix**:
1. Renamed `caregiver_profiles.verified` to `listed` (`ALTER TABLE caregiver_profiles RENAME COLUMN verified TO listed`).
2. Created `document_requirements (job_type, doc_type, required)` and seeded all 6 `(job_type, doc_type)` combinations with `required = FALSE`.
3. Backfilled `UPDATE caregiver_profiles SET listed = TRUE WHERE job_type IS NOT NULL` so caregivers with a selected job type are immediately listed while requirements default to `FALSE`.
4. Whenever an admin toggles a requirement to `required = TRUE` via `PUT /api/admin/document-requirements`, [`CaregiverVerificationService.setRequirement`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/CaregiverVerificationService.java#L218-L232) immediately recomputes `listed` for every caregiver of that `job_type`.

---

## 3. Authoring a New Migration (`V12+`)

When adding a new database migration:
1. Create `backend/src/main/resources/db/migration/V12__<descriptive_snake_case_name>.sql`.
2. **Never edit an existing `V1`–`V11` file**, as Flyway verifies checksums against `flyway_schema_history` and will fail startup if an applied migration is modified.
3. Ensure SQL syntax is compatible with both **PostgreSQL 16** and **H2 in PostgreSQL mode** (`jdbc:h2:mem:homecare;MODE=PostgreSQL`), since `./mvnw test` executes all Flyway migrations against H2.
   - Use standard SQL types (`BIGINT GENERATED BY DEFAULT AS IDENTITY`, `VARCHAR(n)`, `BOOLEAN`, `TIMESTAMPTZ`, `NUMERIC(10, 2)`).
4. Verify your migration by running `cd backend && ./mvnw test`.
