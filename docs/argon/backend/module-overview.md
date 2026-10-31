# Backend Module Overview

The Spring Boot backend (`backend/src/main/java/com/homecare/`) is organized into 11 domain-driven packages under `com.homecare`. Every module has explicit boundaries, responsibilities, and dependencies.

---

## 1. Entry Point

- [`HomecareApplication.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/HomecareApplication.java): Annotated with `@SpringBootApplication` and `@EnableScheduling` (which powers the outbox [`NotificationScheduler`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/NotificationScheduler.java)).

---

## 2. Package-by-Package Reference

### 2.1 `com.homecare.config` — Security & CORS Configuration
| Class | Responsibility |
|---|---|
| [`SecurityConfig.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/config/SecurityConfig.java) | Configures the stateless Spring Security `SecurityFilterChain`, disables CSRF/sessions, registers [`JwtAuthFilter`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/JwtAuthFilter.java) before `UsernamePasswordAuthenticationFilter`, defines URL/role authorization rules, provides the `BCryptPasswordEncoder` bean, and configures `CorsConfigurationSource`. |

### 2.2 `com.homecare.common` — Centralized Exception Handling
| Class | Responsibility |
|---|---|
| [`ApiExceptionHandler.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/common/ApiExceptionHandler.java) | `@RestControllerAdvice` that translates validation failures (`MethodArgumentNotValidException`, `ConstraintViolationException`), `ResponseStatusException`, `HttpMessageNotReadableException`, `MaxUploadSizeExceededException`, and uncaught exceptions into uniform `ErrorResponse(String message, Map<String, String> errors)` JSON responses. |

### 2.3 `com.homecare.user` — User Identity & Roles
| Class | Responsibility |
|---|---|
| [`User.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/user/User.java) | JPA `@Entity` mapped to `users`. Supports both password-authenticated (`new User(...)`) and Google-authenticated (`User.fromGoogle(...)`) accounts. |
| [`Role.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/user/Role.java) | Enum of primary user personas: `CAREGIVER`, `CARE_SEEKER`. |
| [`UserRepository.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/user/UserRepository.java) | Spring Data JPA repository providing `findByEmailIgnoreCase`, `existsByEmailIgnoreCase`, `findByGoogleSub`, `setAdmin`, and `searchForAdmin` (JPQL case-insensitive search across `email`, `fullName`, and `phone`). |

### 2.4 `com.homecare.auth` — Authentication, JWT & Google OAuth2
| Class | Responsibility |
|---|---|
| [`AuthController.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/AuthController.java) | Exposes `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/google`, and `GET /api/auth/me`. |
| [`AuthService.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/AuthService.java) | Implements email registration, BCrypt password login, Google ID token login (including account linking by email and `428 Precondition Required` role prompt for new users), and current-user lookup. |
| [`JwtService.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/JwtService.java) | Issues and verifies HMAC-SHA256 (`HS256`) JWTs using `io.jsonwebtoken:jjwt-*`. Enforces a minimum 32-byte secret key at startup. |
| [`JwtAuthFilter.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/JwtAuthFilter.java) | `OncePerRequestFilter` that parses `Authorization: Bearer <token>`, loads the [`User`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/user/User.java) from PostgreSQL, and populates `SecurityContextHolder` with [`AuthenticatedUser`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/AuthenticatedUser.java) and `ROLE_<role>` / `ROLE_ADMIN` authorities. |
| [`GoogleTokenVerifier.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/GoogleTokenVerifier.java) & [`GoogleIdTokenService.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/GoogleIdTokenService.java) | Verifies Google ID tokens against Google's public certs and `app.google.client-id`. |
| DTO Records | [`RegisterRequest`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/RegisterRequest.java), [`LoginRequest`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/LoginRequest.java), [`GoogleLoginRequest`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/GoogleLoginRequest.java), [`AuthResponse`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/AuthResponse.java), [`UserResponse`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/UserResponse.java), [`AuthenticatedUser`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/AuthenticatedUser.java). |

### 2.5 `com.homecare.profile` — Caregiver Profile, Rates & Photo Storage
| Class | Responsibility |
|---|---|
| [`CaregiverProfileController.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/CaregiverProfileController.java) | Exposes `GET /api/profile/me`, `PUT /api/profile/me`, `PATCH /api/profile/me/availability`, `POST /api/profile/me/photo`, and `DELETE /api/profile/me/photo` for `ROLE_CAREGIVER`. |
| [`CaregiverProfileService.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/CaregiverProfileService.java) | Manages caregiver profile updates, availability toggling, photo replacement, and publishes [`ProfileJobChanged`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/ProfileJobChanged.java) events when `jobType` changes. |
| [`CaregiverProfile.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/CaregiverProfile.java) | JPA `@Entity` mapped to `caregiver_profiles` with `@ElementCollection` `rates` mapped to `caregiver_rates` (`@BatchSize(size = 50)`). |
| [`PhotoStorage.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/PhotoStorage.java) | Stores, loads, and deletes caregiver profile photos (`JPEG`, `PNG`, `WEBP` up to 5 MB) in `uploads/photos` with magic-byte validation. |
| Enums & DTOs | [`JobType`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/JobType.java) (`CARETAKER`, `NURSING`, `PHYSIOTHERAPIST`), [`RateType`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/RateType.java) (`HOURLY`, `SESSION`, `DAILY`, `FULL_DAY_NIGHT`), [`ProfileUpdateRequest`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/ProfileUpdateRequest.java), [`ProfileResponse`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/ProfileResponse.java), [`CaregiverProfileRepository`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/CaregiverProfileRepository.java). |

### 2.6 `com.homecare.directory` — Public Caregiver Search & Cards
| Class | Responsibility |
|---|---|
| [`DirectoryController.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/directory/DirectoryController.java) | Public endpoints `GET /api/caregivers`, `GET /api/caregivers/{id}`, and `GET /api/caregivers/{id}/photo`. |
| [`DirectoryService.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/directory/DirectoryService.java) | Filters `listed = true` and `available = true` caregivers by age, experience, `jobType`, `rateType`/`maxRate`, and free-text query `q` (matching name, qualification, or job type), then attaches batch-aggregated review summaries. |
| DTOs | [`CaregiverCard`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/directory/CaregiverCard.java) (public, no PII), [`AdminCaregiverCard`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/directory/AdminCaregiverCard.java) (admin-only, includes `email`, `phone`, `address`). |

### 2.7 `com.homecare.review` — Ratings & Reviews
| Class | Responsibility |
|---|---|
| [`ReviewController.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/review/ReviewController.java) | Exposes public `GET /api/caregivers/{id}/reviews` and `ROLE_CARE_SEEKER` `POST /api/caregivers/{id}/reviews`. |
| [`ReviewService.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/review/ReviewService.java) | Upserts one review per `(caregiverId, reviewerId)` pair, computes average ratings (`ROUND(AVG, 1)`), and shortens reviewer names (`"First L."`). |
| Entity, Repo & DTOs | [`Review`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/review/Review.java), [`ReviewRepository`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/review/ReviewRepository.java), [`ReviewRequest`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/review/ReviewRequest.java), [`ReviewResponse`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/review/ReviewResponse.java), [`ReviewSummary`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/review/ReviewSummary.java). |

### 2.8 `com.homecare.booking` — Booking Requests & Admin Coordination
| Class | Responsibility |
|---|---|
| [`BookingController.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/BookingController.java) | Exposes `POST /api/bookings`, `GET /api/bookings/mine`, and `POST /api/bookings/{id}/cancel` for `ROLE_CARE_SEEKER`. |
| [`AdminBookingController.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/AdminBookingController.java) | Exposes `GET /api/admin/bookings`, `GET /api/admin/bookings/counts`, `GET /api/admin/bookings/{id}`, `GET /api/admin/bookings/caregivers/{id}`, `PATCH /api/admin/bookings/{id}`, `GET /api/admin/bookings/{id}/notifications`, and `POST /api/admin/bookings/{id}/notifications/retry` for `ROLE_ADMIN`. |
| [`BookingService.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/BookingService.java) | Enforces `MAX_OPEN_REQUESTS = 3`, blocks duplicate open bookings for the same caregiver, snapshots `quotedAmount`, enqueues outbox notifications, and enforces state transitions (`REQUESTED` -> `CONFIRMED`/`CANCELLED` -> `COMPLETED`). |
| Entity, Enum, Repo & DTOs | [`Booking`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/Booking.java), [`BookingStatus`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/BookingStatus.java), [`BookingRepository`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/BookingRepository.java), [`BookingCreateRequest`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/BookingCreateRequest.java), [`BookingResponse`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/BookingResponse.java), [`AdminBookingResponse`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/AdminBookingResponse.java), [`BookingStatusUpdateRequest`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/BookingStatusUpdateRequest.java). |

### 2.9 `com.homecare.notification` — Transactional Outbox, Email & WhatsApp
| Class | Responsibility |
|---|---|
| [`Notification.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/Notification.java) & [`NotificationRepository.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/NotificationRepository.java) | Outbox entity mapped to `notifications` with exponential backoff (`30s * 2^(attempts-1)` capped at `15m`, `MAX_ATTEMPTS = 5`). |
| [`NotificationService.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/NotificationService.java) | Enqueues `EMAIL` and `WHATSAPP` outbox rows when a booking is created, summarizes delivery statuses per booking, and resets `FAILED` rows on manual admin retry. |
| [`NotificationScheduler.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/NotificationScheduler.java) & [`NotificationDispatcher.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/NotificationDispatcher.java) | Polls every `5,000 ms` and dispatches batches of 20 due notifications using isolated `TransactionTemplate` transactions. |
| Email Delivery | [`EmailConfig`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/EmailConfig.java), [`EmailProperties`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/EmailProperties.java), [`EmailSender`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/EmailSender.java), [`SmtpEmailSender`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/SmtpEmailSender.java), [`LoggingEmailSender`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/LoggingEmailSender.java). |
| WhatsApp Delivery | [`WhatsAppConfig`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/WhatsAppConfig.java), [`WhatsAppProperties`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/WhatsAppProperties.java), [`WhatsAppSender`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/WhatsAppSender.java), [`WhatsAppCloudSender`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/WhatsAppCloudSender.java), [`LoggingWhatsAppSender`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/LoggingWhatsAppSender.java). |

### 2.10 `com.homecare.verification` — Caregiver Document Verification & Listing Rules
| Class | Responsibility |
|---|---|
| [`CaregiverVerificationController.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/CaregiverVerificationController.java) | Exposes `GET /api/profile/me/documents`, `POST /api/profile/me/documents`, `GET /api/profile/me/documents/{docType}/file`, and `DELETE /api/profile/me/documents/{docType}` for `ROLE_CAREGIVER`. |
| [`AdminVerificationController.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/AdminVerificationController.java) | Exposes `GET /api/admin/verifications`, `GET /api/admin/verifications/counts`, `GET /api/admin/verifications/{userId}`, `GET /api/admin/verifications/documents/{docId}/file`, `POST /api/admin/verifications/documents/{docId}/approve`, `POST /api/admin/verifications/documents/{docId}/reject`, `GET /api/admin/document-requirements`, and `PUT /api/admin/document-requirements` for `ROLE_ADMIN`. |
| [`CaregiverVerificationService.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/CaregiverVerificationService.java) | Manages document uploads, admin approvals/rejections, dynamic `document_requirements` matrix (via `JdbcTemplate`), and automatic recomputation of `caregiver_profiles.listed`. |
| [`DocumentStorage.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/DocumentStorage.java) | Stores, loads, and deletes verification documents (`PDF`, `JPEG`, `PNG`, `WEBP` up to 10 MB) in `uploads/documents`. |
| Entity, Enums & Repo | [`CaregiverDocument`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/CaregiverDocument.java), [`CaregiverDocumentRepository`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/CaregiverDocumentRepository.java), [`DocumentType`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/DocumentType.java) (`NIC`, `GS_LETTER`, `NURSING_CERTIFICATE`, `DEGREE_CERTIFICATE`), [`DocumentStatus`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/DocumentStatus.java) (`PENDING`, `APPROVED`, `REJECTED`), [`VerificationStatus`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/VerificationStatus.java) (`NOT_REQUIRED`, `INCOMPLETE`, `IN_REVIEW`, `ACTION_NEEDED`, `VERIFIED`). |

### 2.11 `com.homecare.admin` — Admin User Inspection & Account Deletion
| Class | Responsibility |
|---|---|
| [`AdminUserController.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/admin/AdminUserController.java) | Exposes `GET /api/admin/users`, `GET /api/admin/users/{id}`, and `DELETE /api/admin/users/{id}` for `ROLE_ADMIN`. |
| [`AdminUserService.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/admin/AdminUserService.java) | Aggregates user activity counts (bookings, reviews, verification statuses), builds full user detail views, and executes cascading user deletions with post-commit cleanup of photos and documents. |
