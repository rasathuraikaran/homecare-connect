# Entity Relationships, ORM Mappings & Foreign Key Cascades

This document explains how JPA entities map to the relational database, how N+1 query pitfalls are avoided, and how foreign-key cascades behave during account or entity deletion.

---

## 1. ORM Mapping Strategy (JPA vs. `JdbcTemplate`)

HomeCare Connect uses **Spring Data JPA (Hibernate)** with `spring.jpa.hibernate.ddl-auto: validate` for 7 of the 8 database tables, and **Spring `JdbcTemplate`** for the `document_requirements` policy matrix table:

| Database Table | Persistence Mechanism | Mapping Details |
|---|---|---|
| `users` | `@Entity` [`User`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/user/User.java) | `@Enumerated(EnumType.STRING)` on `role`. |
| `caregiver_profiles` | `@Entity` [`CaregiverProfile`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/CaregiverProfile.java) | `@Id Long userId` (shared primary key with `users.id`, assigned manually via `new CaregiverProfile(userId)`). |
| `caregiver_rates` | `@ElementCollection` on [`CaregiverProfile.rates`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/CaregiverProfile.java#L47-L56) | `Map<RateType, BigDecimal>` annotated with `@CollectionTable(name = "caregiver_rates", joinColumns = @JoinColumn(name = "user_id"))` and `@BatchSize(size = 50)`. |
| `caregiver_documents` | `@Entity` [`CaregiverDocument`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/CaregiverDocument.java) | `@Enumerated(EnumType.STRING)` on `docType` and `status`. |
| `document_requirements` | `JdbcTemplate` in [`CaregiverVerificationService`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/CaregiverVerificationService.java#L291-L304) | Queried via SQL `SELECT doc_type FROM document_requirements WHERE job_type = ? AND required = TRUE` and updated via `UPDATE` / `INSERT` upsert. |
| `reviews` | `@Entity` [`Review`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/review/Review.java) | Scalar foreign keys `Long caregiverId` and `Long reviewerId`. |
| `bookings` | `@Entity` [`Booking`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/Booking.java) | Scalar foreign keys `Long seekerId` and `Long caregiverId`; `@Enumerated(EnumType.STRING)` on `status` and `rateType`. |
| `notifications` | `@Entity` [`Notification`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/Notification.java) | Scalar foreign key `Long bookingId`; `@Enumerated(EnumType.STRING)` on `channel` and `status`. |

---

## 2. Why Scalar IDs & Batch Queries Are Used Instead of Deep `@ManyToOne` Graphs

Across `Review`, `Booking`, `Notification`, and `CaregiverDocument`, foreign keys are mapped as scalar `Long` fields (`seekerId`, `caregiverId`, `reviewerId`, `bookingId`) rather than lazy/eager `@ManyToOne` entity references.

**Architectural Benefits**:
1. **Predictable SQL & Zero N+1 Surprises**:
   - When [`DirectoryService.search`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/directory/DirectoryService.java#L32-L61) loads matching `CaregiverProfile` rows:
     - Hibernate batch-loads `caregiver_rates` in chunks of 50 via `@BatchSize(size = 50)`.
     - `userRepository.findAllById(userIds)` loads all `User` rows in a single `WHERE id IN (...)` query.
     - `reviewService.summariesForCaregivers(userIds)` computes `AVG(rating)` and `COUNT(id)` for all cards in a single `GROUP BY caregiver_id` query ([`ReviewRepository.summarizeByCaregiverIds`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/review/ReviewRepository.java#L21-L24)).
   - Similarly, [`AdminUserService.search`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/admin/AdminUserService.java#L51-L82) batch-aggregates booking counts (`countBySeekerIds`, `countByCaregiverIds`), review counts (`countByReviewerIds`), and document counts in bulk queries rather than issuing per-row queries.
2. **Clean Module Boundaries**:
   - Domain entities do not hold circular object references across packages (`booking` -> `user` -> `profile` -> `verification`).

---

## 3. Foreign Key Cascade Matrix

All foreign-key integrity rules are enforced at the PostgreSQL database level:

| Child Table & Column | Parent Table & Column | `ON DELETE` Rule | Rationale |
|---|---|---|---|
| `caregiver_profiles.user_id` | `users.id` | `ON DELETE CASCADE` | Profile cannot exist without its owning user account. |
| `caregiver_rates.user_id` | `caregiver_profiles.user_id` | `ON DELETE CASCADE` | Rates belong exclusively to a caregiver profile. |
| `caregiver_documents.user_id` | `caregiver_profiles.user_id` | `ON DELETE CASCADE` | Uploaded document records belong to the caregiver profile. |
| `caregiver_documents.reviewed_by` | `users.id` | `ON DELETE SET NULL` | Preserves the approval status of a caregiver's document even if the reviewing admin's user row is ever removed. |
| `document_requirements.updated_by` | `users.id` | `ON DELETE SET NULL` | Preserves global document requirement rules if the admin who toggled them is removed. |
| `reviews.caregiver_id` | `caregiver_profiles.user_id` | `ON DELETE CASCADE` | Reviews for a deleted caregiver are removed with the profile. |
| `reviews.reviewer_id` | `users.id` | `ON DELETE CASCADE` | Reviews written by a deleted care seeker are removed with the user. |
| `bookings.seeker_id` | `users.id` | `ON DELETE CASCADE` | Bookings requested by a deleted care seeker are removed. |
| `bookings.caregiver_id` | `caregiver_profiles.user_id` | `ON DELETE CASCADE` | Bookings targeting a deleted caregiver are removed. |
| `notifications.booking_id` | `bookings.id` | `ON DELETE CASCADE` | Outbox alert history for a deleted booking is removed automatically. |
