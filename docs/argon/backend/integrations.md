# External Integrations & Storage Gateways

HomeCare Connect integrates with three external third-party services (**Google Identity Services**, **SMTP Mail Server**, and **Meta WhatsApp Cloud API**) and manages two local disk storage gateways ([`PhotoStorage`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/PhotoStorage.java) and [`DocumentStorage`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/DocumentStorage.java)).

---

## 1. Google Identity Services (OAuth 2.0 / OIDC)

- **Backend Implementation**: [`GoogleTokenVerifier.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/GoogleTokenVerifier.java) and [`GoogleIdTokenService.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/GoogleIdTokenService.java)
- **Frontend Implementation**: [`GoogleSignInButton.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/components/GoogleSignInButton.jsx)
- **Library**: `com.google.api-client:google-api-client:2.7.0`
- **Configuration**: `GOOGLE_CLIENT_ID` (`app.google.client-id`) and `VITE_GOOGLE_CLIENT_ID`

### How It Works
1. On the frontend, if `import.meta.env.VITE_GOOGLE_CLIENT_ID` is non-empty, `GoogleSignInButton.jsx` dynamically injects `https://accounts.google.com/gsi/client` and renders the official Google Sign-In button (`theme: 'outline', size: 'large', width: 320`).
2. When the user signs in with Google, Google returns a signed OIDC `credential` (ID token) to the browser callback.
3. The browser sends `POST /api/auth/google` with `{ idToken, role, phone }`.
4. `GoogleIdTokenService` validates the RSA signature against Google's JWKS keys, checks `aud == GOOGLE_CLIENT_ID`, checks `email_verified == true`, and extracts `(sub, email, name)`.

---

## 2. SMTP Email Integration

- **Implementation**: [`EmailConfig.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/EmailConfig.java), [`EmailProperties.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/EmailProperties.java), [`EmailSender.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/EmailSender.java), [`SmtpEmailSender.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/SmtpEmailSender.java), and [`LoggingEmailSender.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/LoggingEmailSender.java)
- **Library**: `org.springframework.boot:spring-boot-starter-mail` (`JavaMailSenderImpl`)

### Key Security & Reliability Features
1. **Conditional Test/Prod Wiring**:
   - `EMAIL_ENABLED=false` (default): `LoggingEmailSender` is registered (`@ConditionalOnProperty(..., havingValue = "false", matchIfMissing = true)`), logging the simulated email so local development works offline.
   - `EMAIL_ENABLED=true`: `EmailConfig` validates all `EMAIL_*` properties at startup, configures `JavaMailSenderImpl`, and registers `SmtpEmailSender`.
2. **Enforced Transport Encryption (SSL / STARTTLS)**:
   - Port `465` enables implicit SSL (`mail.smtp.ssl.enable=true`, `mail.smtp.ssl.checkserveridentity=true`).
   - Any other port (e.g., `587`) enforces mandatory STARTTLS (`mail.smtp.starttls.enable=true`, `mail.smtp.starttls.required=true`, `mail.smtp.ssl.checkserveridentity=true`). Plaintext SMTP fallback is prohibited.
3. **Strict Socket Timeouts**:
   - `mail.smtp.connectiontimeout = 5000` (5s)
   - `mail.smtp.timeout = 10000` (10s)
   - `mail.smtp.writetimeout = 10000` (10s)
4. **Header Injection Prevention**:
   - [`EmailConfig.requireValidAddress`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/EmailConfig.java#L83-L94) validates `EMAIL_FROM` and every comma-separated address in `EMAIL_ADMIN_RECIPIENTS` against [`EmailProperties.ADDRESS`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/EmailProperties.java#L17-L18) (`^[^@\s,<>]+@[^@\s,<>]+\.[^@\s,<>]+$`) and rejects `\r` or `\n`.

---

## 3. Meta WhatsApp Cloud API Integration

- **Implementation**: [`WhatsAppConfig.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/WhatsAppConfig.java), [`WhatsAppProperties.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/WhatsAppProperties.java), [`WhatsAppSender.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/WhatsAppSender.java), [`WhatsAppCloudSender.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/WhatsAppCloudSender.java), and [`LoggingWhatsAppSender.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/LoggingWhatsAppSender.java)
- **HTTP Client**: Spring `RestClient` with `SimpleClientHttpRequestFactory` (`5s` connect timeout, `10s` read timeout).

### Template Specification (`new_booking_details`)
When a booking is created, [`NotificationService.enqueueNewBookingAlerts`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/NotificationService.java#L41-L65) enqueues a WhatsApp notification using template `new_booking_details` (`language = "en"`) with **9 positional body variables** (sanitized by [`NotificationService.cleanParam`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/NotificationService.java#L168-L176) to strip newlines/tabs, collapse >3 consecutive spaces, and cap at 200 characters per Meta's template rules):

| Variable Position | Value Source | Example |
|---|---|---|
| `{{1}}` | Booking ID | `"#42"` |
| `{{2}}` | Care Seeker Full Name | `"Priya Subramaniam"` |
| `{{3}}` | Care Seeker Phone | `"+94 71 555 9900"` |
| `{{4}}` | Care Seeker Email | `"seeker@demo.local"` |
| `{{5}}` | Care Address (`booking.address`) | `"12 Flower Rd, Colombo 07"` |
| `{{6}}` | Caregiver Full Name | `"Nina Perera"` |
| `{{7}}` | Caregiver Job Type Label | `"Nursing"` |
| `{{8}}` | Quoted Rate & Amount | `"Hourly @ $28.00"` |
| `{{9}}` | Booking Note | `"Post-op wound dressing needed at 9 AM."` |

### Error Classification (Transient vs. Permanent)
In [`WhatsAppCloudSender.sendTemplate`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/WhatsAppCloudSender.java#L75-L120):
- **HTTP 429 (Rate Limit)**, **HTTP 5xx**, or **`ResourceAccessException` (Network/Timeout)** → throws `Notification.SendFailure(msg, permanent = false)` so [`NotificationDispatcher`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/NotificationDispatcher.java) schedules an exponential backoff retry.
- **HTTP 4xx (other than 429)** (e.g., `400 Bad Request` invalid template or `401` invalid token) → throws `Notification.SendFailure(msg, permanent = true)` so the notification immediately transitions to `FAILED` without wasting retry attempts.

---

## 4. Local Disk Storage Gateways (`PhotoStorage` & `DocumentStorage`)

| Attribute | [`PhotoStorage`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/PhotoStorage.java) | [`DocumentStorage`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/DocumentStorage.java) |
|---|---|---|
| **Default Directory** | `uploads/photos` (`PHOTOS_DIR`) | `uploads/documents` (`DOCUMENTS_DIR`) |
| **Max File Size** | `5 MB` (`5 * 1024 * 1024` bytes) | `10 MB` (`10 * 1024 * 1024` bytes) |
| **Allowed MIME Types** | `image/jpeg`, `image/png`, `image/webp` | `application/pdf`, `image/jpeg`, `image/png`, `image/webp` |
| **Magic-Byte Check** | `FF D8 FF` (JPEG), `89 50 4E 47...` (PNG), `RIFF....WEBP` (WebP) | Same as `PhotoStorage` + `25 50 44 46 2D` (`%PDF-`) |
| **Filename Pattern** | `^[a-f0-9-]{36}\.(jpg\|png\|webp)$` | `^[a-f0-9-]{36}\.(pdf\|jpg\|png\|webp)$` |
| **Serving Access Control** | Public `GET /api/caregivers/{id}/photo` (only if `profile.listed == true`) | Authenticated `GET /api/profile/me/documents/{docType}/file` (owner only) or `GET /api/admin/verifications/documents/{docId}/file` (`ROLE_ADMIN` only) |
