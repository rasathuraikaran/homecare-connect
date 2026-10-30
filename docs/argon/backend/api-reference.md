# REST API Reference

This document specifies all **29 REST API endpoints** exposed by the HomeCare Connect Spring Boot backend (`http://localhost:8080/api`).

---

## 1. Authentication & Conventions

- **Base Path**: `/api`
- **Authentication Header**: Protected endpoints require `Authorization: Bearer <jwt>`.
- **Content Types**:
  - Standard requests/responses: `application/json`
  - File uploads (`/api/profile/me/photo`, `/api/profile/me/documents`): `multipart/form-data` with part name `file`
  - Binary downloads (`/api/caregivers/{id}/photo`, `/api/profile/me/documents/{docType}/file`, `/api/admin/verifications/documents/{docId}/file`): `image/jpeg`, `image/png`, `image/webp`, or `application/pdf`
- **Standard Error Payload**:
  ```json
  {
    "message": "Validation failed",
    "errors": {
      "fieldName": "Human-readable validation message"
    }
  }
  ```

---

## 2. Authentication Endpoints (`AuthController`)

Source: [`AuthController.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/AuthController.java)

### 2.1 `POST /api/auth/register`
- **Auth**: Public (`permitAll`)
- **Request Body ([`RegisterRequest`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/RegisterRequest.java))**:
  ```json
  {
    "email": "nina@example.com",
    "password": "Password123",
    "fullName": "Nina Perera",
    "phone": "+94 77 123 4567",
    "role": "CAREGIVER"
  }
  ```
  - `email`: `@NotBlank`, `@Email`, `@Size(max = 255)`
  - `password`: `@NotBlank`, `@Size(min = 8, max = 72)`
  - `fullName`: `@NotBlank`, `@Size(max = 150)`
  - `phone`: `@Size(max = 30)` (optional)
  - `role`: `@NotNull` (`"CAREGIVER"` or `"CARE_SEEKER"`)
- **Responses**:
  - `201 Created`: [`AuthResponse`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/AuthResponse.java)
    ```json
    {
      "token": "eyJhbGciOiJIUzI1NiJ9...",
      "user": {
        "id": 1,
        "email": "nina@example.com",
        "fullName": "Nina Perera",
        "phone": "+94 77 123 4567",
        "role": "CAREGIVER",
        "admin": false
      }
    }
    ```
  - `400 Bad Request`: Field validation error
  - `409 Conflict`: `"An account with this email already exists"`

### 2.2 `POST /api/auth/login`
- **Auth**: Public (`permitAll`)
- **Request Body ([`LoginRequest`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/LoginRequest.java))**:
  ```json
  {
    "email": "nina@example.com",
    "password": "Password123"
  }
  ```
- **Responses**:
  - `200 OK`: `AuthResponse`
  - `401 Unauthorized`: `"Invalid email or password"` or `"This account uses Google sign-in. Please continue with Google."`

### 2.3 `POST /api/auth/google`
- **Auth**: Public (`permitAll`)
- **Request Body ([`GoogleLoginRequest`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/GoogleLoginRequest.java))**:
  ```json
  {
    "idToken": "<google_id_token>",
    "role": "CARE_SEEKER",
    "phone": "+94 71 999 0000"
  }
  ```
  - `idToken`: `@NotBlank`
  - `role`: Optional for returning users; required (`"CAREGIVER"` or `"CARE_SEEKER"`) when creating a brand-new account.
  - `phone`: Optional (`@Size(max = 30)`)
- **Responses**:
  - `200 OK`: `AuthResponse`
  - `401 Unauthorized`: Invalid or unverified Google ID token
  - `428 Precondition Required`: Brand-new Google user submitted without `role`
  - `503 Service Unavailable`: `GOOGLE_CLIENT_ID` is not configured on the backend

### 2.4 `GET /api/auth/me`
- **Auth**: Any authenticated user (`authenticated()`)
- **Responses**:
  - `200 OK`: [`UserResponse`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/UserResponse.java) (`id`, `email`, `fullName`, `phone`, `role`, `admin`)
  - `401 Unauthorized`: Missing, expired, or invalid JWT

---

## 3. Caregiver Profile Endpoints (`CaregiverProfileController`)

Source: [`CaregiverProfileController.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/CaregiverProfileController.java)  
**Auth**: Requires `ROLE_CAREGIVER` (`403 Forbidden` for `CARE_SEEKER`).

### 3.1 `GET /api/profile/me`
- **Response (`200 OK` — [`ProfileResponse`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/ProfileResponse.java))**:
  ```json
  {
    "userId": 1,
    "fullName": "Nina Perera",
    "email": "nina@example.com",
    "phone": "+94 77 123 4567",
    "address": "Colombo 05",
    "age": 34,
    "experienceYears": 9,
    "jobType": "NURSING",
    "qualification": "BSc Nursing, ICU & elderly post-op care",
    "rates": {
      "HOURLY": 28.00,
      "SESSION": 95.00,
      "DAILY": 180.00
    },
    "available": true,
    "listed": true,
    "photoUrl": "/api/caregivers/1/photo",
    "complete": true
  }
  ```

### 3.2 `PUT /api/profile/me`
- **Request Body ([`ProfileUpdateRequest`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/ProfileUpdateRequest.java))**:
  ```json
  {
    "fullName": "Nina Perera",
    "phone": "+94 77 123 4567",
    "address": "Colombo 05",
    "age": 34,
    "experienceYears": 9,
    "jobType": "NURSING",
    "qualification": "BSc Nursing, ICU & elderly post-op care",
    "rates": {
      "HOURLY": 28.00,
      "DAILY": 180.00
    },
    "available": true
  }
  ```
  - `fullName`: `@NotBlank`, `@Size(max = 150)`
  - `phone`: `@Size(max = 30)`
  - `address`: `@NotBlank`, `@Size(max = 300)`
  - `age`: `@NotNull`, `@Min(18)`, `@Max(100)`
  - `experienceYears`: `@NotNull`, `@Min(0)`, `@Max(60)`
  - `jobType`: `@NotNull` (`CARETAKER` | `NURSING` | `PHYSIOTHERAPIST`)
  - `qualification`: `@NotBlank`, `@Size(max = 255)`
  - `rates`: `@NotEmpty`, values `@DecimalMin("0.01")`, `@DecimalMax("999999.99")`
  - `available`: Optional `Boolean` (defaults to `true` if null)
- **Response (`200 OK`)**: Updated `ProfileResponse`. Also triggers synchronous recomputation of `listed` if `jobType` changed.

### 3.3 `PATCH /api/profile/me/availability`
- **Request Body**: `{ "available": false }` (`@NotNull Boolean available`)
- **Response (`200 OK`)**: Updated `ProfileResponse`.

### 3.4 `POST /api/profile/me/photo`
- **Request**: `multipart/form-data` with field `file` (`JPEG`, `PNG`, or `WEBP`, max 5 MB, verified via magic bytes).
- **Response (`200 OK`)**: Updated `ProfileResponse`.

### 3.5 `DELETE /api/profile/me/photo`
- **Response (`200 OK`)**: Updated `ProfileResponse` (`photoUrl = null`).

---

## 4. Caregiver Verification Document Endpoints (`CaregiverVerificationController`)

Source: [`CaregiverVerificationController.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/CaregiverVerificationController.java)  
**Auth**: Requires `ROLE_CAREGIVER`.

### 4.1 `GET /api/profile/me/documents`
- **Response (`200 OK` — [`MyVerificationResponse`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/CaregiverVerificationService.java#L325-L331))**:
  ```json
  {
    "jobType": "NURSING",
    "listed": false,
    "verificationStatus": "IN_REVIEW",
    "requiredTypes": ["NURSING_CERTIFICATE", "NIC"],
    "documents": [
      {
        "id": 12,
        "docType": "NIC",
        "status": "PENDING",
        "revision": 1,
        "rejectReason": null,
        "uploadedAt": "2026-10-30T10:00:00Z",
        "reviewedAt": null,
        "fileUrl": "/api/profile/me/documents/NIC/file"
      }
    ]
  }
  ```

### 4.2 `POST /api/profile/me/documents?docType=<DocumentType>`
- **Query Param**: `docType` (`NIC` | `GS_LETTER` | `NURSING_CERTIFICATE` | `DEGREE_CERTIFICATE`)
- **Multipart Body**: `file` (`PDF`, `JPEG`, `PNG`, or `WEBP`, max 10 MB, verified via magic bytes).
- **Responses**:
  - `201 Created`: `CaregiverDocumentDto`
  - `400 Bad Request`: Caregiver has not set `jobType` yet, or invalid file format
  - `409 Conflict`: Document is already `APPROVED`

### 4.3 `GET /api/profile/me/documents/{docType}/file`
- **Response (`200 OK`)**: Raw binary stream (`Content-Disposition: inline`, `Cache-Control: no-store`).

### 4.4 `DELETE /api/profile/me/documents/{docType}`
- **Responses**:
  - `204 No Content`: Document deleted (only allowed when `status` is `PENDING` or `REJECTED`)
  - `409 Conflict`: Cannot delete an `APPROVED` document

---

## 5. Public Caregiver Directory Endpoints (`DirectoryController`)

Source: [`DirectoryController.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/directory/DirectoryController.java)  
**Auth**: Public (`permitAll`). Only returns caregivers with `listed = true` (and `available = true` for search).

### 5.1 `GET /api/caregivers`
- **Query Parameters** (all optional):
  - `minAge` (`0`–`120`), `maxAge` (`0`–`120`)
  - `minExperience` (`0`–`80`)
  - `jobType` (`CARETAKER` | `NURSING` | `PHYSIOTHERAPIST`)
  - `rateType` (`HOURLY` | `SESSION` | `DAILY` | `FULL_DAY_NIGHT`)
  - `maxRate` (`>= 0.01`, requires `rateType` to take effect)
  - `q` (max 100 chars; case-insensitive substring match against `fullName`, `qualification`, or `jobType`)
- **Response (`200 OK`)**: Array of [`CaregiverCard`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/directory/CaregiverCard.java) objects:
  ```json
  [
    {
      "id": 1,
      "fullName": "Nina Perera",
      "age": 34,
      "experienceYears": 9,
      "jobType": "NURSING",
      "qualification": "BSc Nursing, ICU & elderly post-op care",
      "rates": { "HOURLY": 28.00, "DAILY": 180.00 },
      "available": true,
      "listed": true,
      "photoUrl": "/api/caregivers/1/photo",
      "averageRating": 5.0,
      "reviewCount": 2
    }
  ]
  ```

### 5.2 `GET /api/caregivers/{id}`
- **Response (`200 OK`)**: Single `CaregiverCard` (returns `404 Not Found` if `listed == false` or profile is incomplete).

### 5.3 `GET /api/caregivers/{id}/photo`
- **Response (`200 OK`)**: Binary image (`Cache-Control: max-age=3600`), or `404 Not Found` if unlisted or no photo uploaded.

---

## 6. Review Endpoints (`ReviewController`)

Source: [`ReviewController.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/review/ReviewController.java)

### 6.1 `GET /api/caregivers/{caregiverId}/reviews`
- **Auth**: Public (`permitAll`)
- **Response (`200 OK`)**: Array of [`ReviewResponse`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/review/ReviewResponse.java) ordered by `updatedAt DESC` (`reviewerName` shortened to `"First L."`).

### 6.2 `POST /api/caregivers/{caregiverId}/reviews`
- **Auth**: Requires `ROLE_CARE_SEEKER`
- **Request Body ([`ReviewRequest`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/review/ReviewRequest.java))**:
  ```json
  {
    "rating": 5,
    "comment": "Kind, punctual, and very gentle with my mother."
  }
  ```
  - `rating`: `@NotNull`, `@Min(1)`, `@Max(5)`
  - `comment`: Optional (`@Size(max = 1000)`)
- **Response (`200 OK`)**: Upserted `ReviewResponse`.

---

## 7. Care Seeker Booking Endpoints (`BookingController`)

Source: [`BookingController.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/BookingController.java)  
**Auth**: Requires `ROLE_CARE_SEEKER`.

### 7.1 `POST /api/bookings`
- **Request Body ([`BookingCreateRequest`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/BookingCreateRequest.java))**:
  ```json
  {
    "caregiverId": 1,
    "seekerPhone": "+94 71 555 9900",
    "address": "12 Flower Rd, Colombo 07",
    "rateType": "HOURLY",
    "note": "Post-op wound dressing needed at 9 AM."
  }
  ```
  - `caregiverId`: `@NotNull`
  - `seekerPhone`: `@NotBlank`, `@Size(max = 30)`
  - `address`: `@NotBlank`, `@Size(max = 300)`
  - `rateType`: Optional (`RateType`; if provided, must exist in caregiver's rates)
  - `note`: Optional (`@Size(max = 500)`)
- **Responses**:
  - `201 Created`: [`BookingResponse`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/BookingResponse.java)
  - `404 Not Found`: Caregiver does not exist or `listed == false`
  - `409 Conflict`: Seeker already has an open (`REQUESTED` or `CONFIRMED`) booking with this caregiver, or seeker already has 3 open `REQUESTED` bookings.

### 7.2 `GET /api/bookings/mine`
- **Response (`200 OK`)**: Array of `BookingResponse` for the authenticated seeker (`createdAt DESC`).

### 7.3 `POST /api/bookings/{id}/cancel`
- **Responses**:
  - `200 OK`: Updated `BookingResponse` (`status = "CANCELLED"`)
  - `403 Forbidden`: Booking belongs to another seeker
  - `409 Conflict`: Booking is no longer in `REQUESTED` status

---

## 8. Admin Booking & Notification Endpoints (`AdminBookingController`)

Source: [`AdminBookingController.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/AdminBookingController.java)  
**Auth**: Requires `ROLE_ADMIN`.

| Method & Path | Description & Parameters | Response |
|---|---|---|
| `GET /api/admin/bookings` | Optional `?status=REQUESTED\|CONFIRMED\|COMPLETED\|CANCELLED`. Lists up to 200 bookings with caregiver & seeker contact details and embedded `NotificationSummary`. | `200 OK`: `List<AdminBookingResponse>` |
| `GET /api/admin/bookings/counts` | Returns total booking counts grouped by `BookingStatus`. | `200 OK`: `Map<BookingStatus, Long>` |
| `GET /api/admin/bookings/{id}` | Returns a single `AdminBookingResponse` by booking ID. | `200 OK`: `AdminBookingResponse` |
| `GET /api/admin/bookings/caregivers/{id}` | Returns full [`AdminCaregiverCard`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/directory/AdminCaregiverCard.java) including caregiver `email`, `phone`, and `address` (even if unlisted). | `200 OK`: `AdminCaregiverCard` |
| `PATCH /api/admin/bookings/{id}` | Body: `{ "status": "CONFIRMED" \| "COMPLETED" \| "CANCELLED" }`. Enforces state transition rules. | `200 OK`: `AdminBookingResponse` (`409` on invalid transition) |
| `GET /api/admin/bookings/{id}/notifications` | Lists all outbox notification attempts (`EMAIL`, `WHATSAPP`) for a booking. | `200 OK`: `List<NotificationAttemptResponse>` |
| `POST /api/admin/bookings/{id}/notifications/retry` | Resets `FAILED` notifications for the booking to `PENDING` (`attempts = 0`) and triggers `dispatchDue()` immediately. | `200 OK`: `List<NotificationAttemptResponse>` |

---

## 9. Admin Verification & Document Requirement Endpoints (`AdminVerificationController`)

Source: [`AdminVerificationController.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/AdminVerificationController.java)  
**Auth**: Requires `ROLE_ADMIN`.

| Method & Path | Description & Parameters | Response |
|---|---|---|
| `GET /api/admin/verifications` | Optional `?status=PENDING\|INCOMPLETE\|VERIFIED\|ALL` (default `PENDING`). Lists caregivers sorted with pending reviews first. | `200 OK`: `List<AdminCaregiverVerificationDto>` |
| `GET /api/admin/verifications/counts` | Returns `{ "pendingCaregivers": <count> }` for the admin nav badge. | `200 OK`: `Map<String, Long>` |
| `GET /api/admin/verifications/{userId}` | Returns verification details and uploaded documents for a single caregiver. | `200 OK`: `AdminCaregiverVerificationDto` |
| `GET /api/admin/verifications/documents/{docId}/file` | Streams raw document bytes (`PDF`/`JPEG`/`PNG`/`WEBP`) with `Content-Disposition: inline`. | `200 OK`: Binary stream |
| `POST /api/admin/verifications/documents/{docId}/approve` | Marks document `APPROVED`, records `reviewed_by` and `reviewed_at`, and recomputes `profile.listed`. | `200 OK`: `CaregiverDocumentDto` |
| `POST /api/admin/verifications/documents/{docId}/reject` | Body: `{ "reason": "..." }` (`@NotBlank`, max 300 chars). Marks document `REJECTED` and recomputes `profile.listed`. | `200 OK`: `CaregiverDocumentDto` |
| `GET /api/admin/document-requirements` | Returns the mandatory document requirement matrix for all `JobType` values. | `200 OK`: `RequirementsMatrixResponse` |
| `PUT /api/admin/document-requirements` | Body: `{ "jobType": "NURSING", "docType": "NIC", "required": true }`. Updates the requirement and immediately recomputes `listed` for all caregivers of that `jobType`. | `200 OK`: `RequirementsMatrixResponse` |

---

## 10. Admin User Management Endpoints (`AdminUserController`)

Source: [`AdminUserController.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/admin/AdminUserController.java)  
**Auth**: Requires `ROLE_ADMIN`.

| Method & Path | Description & Parameters | Response |
|---|---|---|
| `GET /api/admin/users` | Optional `?role=CAREGIVER\|CARE_SEEKER` and `?q=<search>`. Lists users with booking/review counts and caregiver verification summaries. | `200 OK`: `List<UserSummaryResponse>` |
| `GET /api/admin/users/{id}` | Returns comprehensive audit view (`user`, `profile`, `documents`, `bookingsAsSeeker`, `bookingsAsCaregiver`, `reviewsReceived`, `reviewsWritten`). | `200 OK`: `UserDetailResponse` |
| `DELETE /api/admin/users/{id}` | Permanently deletes a non-admin user, cascading all database records and deleting uploaded files after commit. Blocks self-delete or deleting another admin (`409 Conflict`). | `204 No Content` |
