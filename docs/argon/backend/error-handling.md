# Error Handling Strategy

HomeCare Connect implements a centralized, structured error handling architecture across the Spring Boot backend ([`ApiExceptionHandler.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/common/ApiExceptionHandler.java)) and the React frontend ([`client.js`](file:///home/sivaram/Github/homecare-connect/frontend/src/api/client.js)).

---

## 1. Standard Error Response Schema

Every error returned by the backend conforms to [`ApiExceptionHandler.ErrorResponse`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/common/ApiExceptionHandler.java#L106):

```json
{
  "message": "Validation failed",
  "errors": {
    "email": "must be a well-formed email address",
    "rates[HOURLY]": "Rate for Hourly must be at least $0.01"
  }
}
```

- `message` (`String`): A top-level human-readable summary suitable for displaying in a toast or alert banner.
- `errors` (`Map<String, String>`): Key-value map of field-specific validation errors (empty `{}` when the error is not tied to specific request fields).

---

## 2. Exception Translation Matrix (`ApiExceptionHandler`)

Defined in [`ApiExceptionHandler.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/common/ApiExceptionHandler.java):

| Exception Type | HTTP Status | `message` Value | `errors` Map Population |
|---|---|---|---|
| `MethodArgumentNotValidException` (Jakarta `@Valid` on `@RequestBody`) | `400 Bad Request` | First human-readable field error (via `describe(FieldError)`), or `"Validation failed"` | Populated with `{ fieldName: errorMessage }` for every invalid field. |
| `ConstraintViolationException` (`@Validated` on path/query parameters) | `400 Bad Request` | `"Invalid request parameters"` | Populated with `{ paramName: violation.getMessage() }` (strips method prefix via `leafName()`). |
| `HttpMessageNotReadableException` (Malformed JSON or invalid enum value) | `400 Bad Request` | `"Invalid request payload. Please check the values and try again."` | `{}` |
| `MaxUploadSizeExceededException` (Multipart upload exceeds limit) | `400 Bad Request` | `"Uploaded file is too large (max 10 MB)"` | `{}` |
| `ResponseStatusException` (Explicitly thrown by domain services) | `ex.getStatusCode()` (`400`, `401`, `403`, `404`, `409`, `428`, `503`) | `ex.getReason()` (or status reason phrase) | `{}` |
| `Exception` (Catch-all fallback) | `500 Internal Server Error` | `"Unexpected server error"` (logs full stack trace via SLF4J `log.error`) | `{}` |

---

## 3. Human-Friendly Rate Map Validation (`describe(FieldError)`)

When validating `Map<RateType, BigDecimal> rates` on [`ProfileUpdateRequest`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/ProfileUpdateRequest.java), Spring's default `FieldError` field name looks like `rates[HOURLY]`.

[`ApiExceptionHandler.describe(FieldError)`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/common/ApiExceptionHandler.java#L65-L94) inspects `rates[<KEY>]` errors and translates them into clear user-facing messages using [`RateType`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/RateType.java) labels (`"Hourly"`, `"Per session"`, `"Daily"`, `"Full day & night"`):
- `null` value → `"Enter an amount for Hourly or untick it"`
- `DecimalMin` violation → `"Rate for Hourly must be at least $0.01"`
- `DecimalMax` violation → `"Rate for Hourly is too large"`

---

## 4. Domain-Level HTTP Status Code Semantics

Domain services use `ResponseStatusException` consistently across all modules:

| HTTP Status | When Used in Domain Services | Example Locations |
|---|---|---|
| `400 Bad Request` | Invalid business input, unquoted rate type, missing `jobType` on doc upload, invalid file magic bytes | [`PhotoStorage`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/PhotoStorage.java), [`DocumentStorage`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/DocumentStorage.java), [`BookingService`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/BookingService.java#L85-L91) |
| `401 Unauthorized` | Wrong password, Google-only account attempting password login, invalid Google ID token | [`AuthService`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/AuthService.java), [`GoogleIdTokenService`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/GoogleIdTokenService.java) |
| `403 Forbidden` | Caller does not own the resource (e.g., cancelling another seeker's booking) | [`BookingService.cancelMine`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/BookingService.java#L106-L108) |
| `404 Not Found` | Entity does not exist, caregiver is not `listed`, or file is missing on disk | [`DirectoryService`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/directory/DirectoryService.java), [`BookingService`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/BookingService.java#L68-L71) |
| `409 Conflict` | Duplicate email on registration, >3 open bookings, duplicate open booking for same caregiver, invalid booking state transition, modifying an `APPROVED` document, or deleting an admin account | [`AuthService`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/AuthService.java#L44), [`BookingService`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/BookingService.java#L72-L82), [`CaregiverVerificationService`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/CaregiverVerificationService.java#L126-L129), [`AdminUserService`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/admin/AdminUserService.java#L104-L109) |
| `428 Precondition Required` | First-time Google sign-in requires the user to choose a role (`CARE_SEEKER` or `CAREGIVER`) | [`AuthService.loginWithGoogle`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/AuthService.java#L102-L107) |
| `503 Service Unavailable` | Google Sign-In called when `app.google.client-id` is not configured | [`GoogleIdTokenService.verify`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/GoogleIdTokenService.java#L41-L45) |

---

## 5. Frontend Error Handling (`frontend/src/api/client.js`)

In [`client.js`](file:///home/sivaram/Github/homecare-connect/frontend/src/api/client.js#L35-L45), non-2xx HTTP responses are parsed and thrown as an enriched `Error` object:

```javascript
if (!res.ok) {
  const err = new Error(data?.message || `Request failed (${res.status})`)
  err.status = res.status
  err.errors = data?.errors || {}
  throw err
}
```

Frontend pages inspect:
- `err.status === 428` in [`LoginPage.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/pages/LoginPage.jsx#L36-L40) to reveal the Google role selection step.
- `err.errors` in [`RegisterPage.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/pages/RegisterPage.jsx) and [`ProfilePage.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/pages/ProfilePage.jsx) to highlight individual form inputs.
- `err.message` to display user-friendly error banners.
