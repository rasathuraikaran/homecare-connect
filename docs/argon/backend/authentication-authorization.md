# Authentication & Authorization

This document explains how authentication, JWT token management, Google OAuth2 verification, and role-based access control (RBAC) are implemented in the HomeCare Connect backend.

---

## 1. Security Filter Chain & URL Authorization Rules

Configured in [`SecurityConfig.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/config/SecurityConfig.java#L41-L82):

- **Session Policy**: `SessionCreationPolicy.STATELESS` (no `JSESSIONID` cookies or server-side HTTP sessions).
- **CSRF**: Disabled (`AbstractHttpConfigurer::disable`) because authentication relies exclusively on the `Authorization: Bearer <token>` HTTP header rather than ambient browser cookies.
- **Exception Handling**: Unauthenticated requests to protected endpoints return `401 Unauthorized` via `HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)`. Authenticated requests lacking the required role return `403 Forbidden`.

### Endpoint Matcher Matrix

| Order | Matcher Pattern | HTTP Method | Required Authority |
|---|---|---|---|
| 1 | `/api/auth/register`, `/api/auth/login`, `/api/auth/google` | `POST` | `permitAll()` |
| 2 | `/api/caregivers`, `/api/caregivers/*`, `/api/caregivers/*/photo`, `/api/caregivers/*/reviews` | `GET` | `permitAll()` |
| 3 | `/api/admin/**` | Any | `hasRole("ADMIN")` (`ROLE_ADMIN`) |
| 4 | `/api/caregivers/*/reviews` | `POST` | `hasRole("CARE_SEEKER")` (`ROLE_CARE_SEEKER`) |
| 5 | `/api/bookings/**` | Any | `hasRole("CARE_SEEKER")` (`ROLE_CARE_SEEKER`) |
| 6 | `/api/profile/**` | Any | `hasRole("CAREGIVER")` (`ROLE_CAREGIVER`) |
| 7 | `anyRequest()` (e.g., `GET /api/auth/me`) | Any | `authenticated()` |

---

## 2. JWT Token Architecture (`JwtService` & `JwtAuthFilter`)

### 2.1 Signing Key Enforcement
[`JwtService.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/JwtService.java#L23-L34) requires `app.jwt.secret` (`JWT_SECRET`) to be at least **32 UTF-8 bytes** (256 bits) to satisfy HMAC-SHA256 (`HS256`) cryptographic key-length requirements (`Keys.hmacShaKeyFor`). If `JWT_SECRET` is missing or shorter than 32 bytes, the Spring context fails fast during startup with `IllegalStateException`.

### 2.2 JWT Claims Structure
When [`JwtService.issueToken(User)`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/JwtService.java#L36-L45) signs a token, it encodes:
- `sub` (Subject): `String.valueOf(user.getId())`
- `email`: `user.getEmail()`
- `role`: `user.getRole().name()` (`"CAREGIVER"` or `"CARE_SEEKER"`)
- `admin`: `user.isAdmin()` (`true` or `false`)
- `iat` (Issued At): Current epoch timestamp
- `exp` (Expiration): `now + app.jwt.expiration-minutes` (default `10080` minutes = 7 days)

### 2.3 Per-Request Database Verification (`JwtAuthFilter`)
Rather than trusting the `role` and `admin` claims inside the JWT blindly for 7 days, [`JwtAuthFilter.doFilterInternal`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/JwtAuthFilter.java#L37-L77) performs a lightweight primary-key lookup (`userRepository.findById(claims.userId())`) on every authenticated request:
1. **Immediate Account Deletion Enforcement**: If an admin deletes a user via `DELETE /api/admin/users/{id}`, any previously issued JWT for that user is immediately rejected on the very next request because `findById` returns `Optional.empty()`.
2. **Immediate Admin Promotion/Revocation**: If an operator runs `./start-service.sh make-admin <email>` (or `--revoke`), the user's `ROLE_ADMIN` authority updates immediately without requiring the user to sign out and back in.
3. **Principal Injection**: Controllers access the verified caller via `@AuthenticationPrincipal AuthenticatedUser principal` ([`AuthenticatedUser.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/AuthenticatedUser.java)).

---

## 3. Password Hashing

- Passwords are hashed using `BCryptPasswordEncoder` (default strength 10) configured in [`SecurityConfig.passwordEncoder()`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/config/SecurityConfig.java#L80-L82).
- [`RegisterRequest`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/RegisterRequest.java) enforces `@Size(min = 8, max = 72)` on passwords (`72` bytes is the maximum input length processed by the BCrypt algorithm).
- Users who register via Google Sign-In have `password_hash = NULL` in the `users` table (`V2__google_login.sql`). If a Google-only user attempts to log in with a password at `POST /api/auth/login`, [`AuthService.login`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/AuthService.java#L65-L70) explicitly checks `user.getPasswordHash() == null` and returns `401 Unauthorized` with `"This account uses Google sign-in. Please continue with Google."`

---

## 4. Google OAuth2 ID Token Verification

Implemented in [`GoogleIdTokenService.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/GoogleIdTokenService.java) and [`AuthService.loginWithGoogle`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/AuthService.java#L80-L120):

1. **Cryptographic Verification**: Uses `com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier` with `NetHttpTransport` and `GsonFactory` to verify the token's RSA signature against Google's public keys, check expiration, and enforce `Collections.singletonList(clientId)` audience matching.
2. **Email Verification Check**: Requires `Boolean.TRUE.equals(payload.getEmailVerified())`.
3. **Account Resolution & Linking Strategy**:
   - First searches `userRepository.findByGoogleSub(googleUser.sub())`.
   - If not found by `google_sub`, searches `userRepository.findByEmailIgnoreCase(googleUser.email())`. If a password-based account with the same email exists, it links the account by setting `existing.setGoogleSub(googleUser.sub())`.
   - If no account exists and `request.role()` is `null`, throws `428 Precondition Required` so the frontend can prompt the user to select `CARE_SEEKER` or `CAREGIVER`.

---

## 5. Orthogonal Admin Privilege Model

- `users.role` is strictly `CAREGIVER` or `CARE_SEEKER` (enforced by a database `CHECK` constraint in `V1__create_users.sql`).
- `users.is_admin` is a separate `BOOLEAN` column added in `V8__bookings_and_notifications.sql`.
- There is intentionally **no REST API endpoint** that sets `is_admin = true`. Admin privileges can only be granted or revoked via direct database access (`./start-service.sh make-admin <email>`), eliminating any possibility of privilege escalation via the web API.
