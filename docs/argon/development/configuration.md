# Configuration & Environment Variables

HomeCare Connect uses environment variables for all environment-specific settings and secrets. No credentials or API keys are committed to source control.

---

## 1. How Configuration Files Are Loaded

1. **Root `.env.local` (Recommended for Local Dev)**:
   - Copy [`.env.local.example`](file:///home/sivaram/Github/homecare-connect/.env.local.example) to `.env.local` in the repository root.
   - [`start-service.sh`](file:///home/sivaram/Github/homecare-connect/start-service.sh#L17-L23) automatically sources `.env.local` with `set -a; source .env.local; set +a` before launching PostgreSQL, Spring Boot, and Vite.
   - If `GOOGLE_CLIENT_ID` is set in `.env.local` and `VITE_GOOGLE_CLIENT_ID` is not explicitly set, `start-service.sh` automatically copies `GOOGLE_CLIENT_ID` into `VITE_GOOGLE_CLIENT_ID` so both backend and frontend share the same OAuth2 Client ID.
2. **Backend Spring Boot Config ([`backend/src/main/resources/application.yml`](file:///home/sivaram/Github/homecare-connect/backend/src/main/resources/application.yml))**:
   - Maps environment variables using `${ENV_VAR:default}` syntax.
3. **Frontend Vite Config ([`frontend/.env.example`](file:///home/sivaram/Github/homecare-connect/frontend/.env.example))**:
   - Reads `VITE_*` variables from the process environment or `frontend/.env.local` at dev-server startup or `npm run build` time.

---

## 2. Backend Environment Variables Reference

Defined in [`backend/src/main/resources/application.yml`](file:///home/sivaram/Github/homecare-connect/backend/src/main/resources/application.yml):

### 2.1 Server, Database & Storage

| Environment Variable | Spring Property | Default Value | Required in Prod? | Description |
|---|---|---|---|---|
| `SERVER_PORT` | `server.port` | `8080` | No | HTTP port for the embedded Tomcat server (`start-service.sh` passes `--server.port=$BACKEND_PORT`). |
| `DB_URL` | `spring.datasource.url` | `jdbc:postgresql://localhost:5432/homecare` | **Yes** | JDBC connection URL for PostgreSQL 16. |
| `DB_USER` | `spring.datasource.username` | `homecare` | **Yes** | PostgreSQL database username. |
| `DB_PASSWORD` | `spring.datasource.password` | `homecare` | **Yes** | PostgreSQL database password. |
| `CORS_ALLOWED_ORIGINS` | `app.cors.allowed-origins` | `http://localhost:5173` | **Yes** | Comma-separated list of origins permitted by [`SecurityConfig.corsConfigurationSource`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/config/SecurityConfig.java#L85-L98). |
| `PHOTOS_DIR` | `app.photos.dir` | `uploads/photos` | No | Filesystem directory where [`PhotoStorage`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/PhotoStorage.java) writes caregiver profile photos (max 5 MB). |
| `DOCUMENTS_DIR` | `app.documents.dir` | `uploads/documents` | No | Filesystem directory where [`DocumentStorage`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/DocumentStorage.java) writes verification documents (max 10 MB). |

### 2.2 Authentication (JWT & Google OAuth2)

| Environment Variable | Spring Property | Default Value | Required in Prod? | Description |
|---|---|---|---|---|
| `JWT_SECRET` | `app.jwt.secret` | *(Empty — fail-fast)* | **Yes** | Secret key for HMAC-SHA256 JWT signing. Enforced by [`JwtService`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/JwtService.java#L27-L33) to be **at least 32 UTF-8 bytes** (`256 bits`); application startup fails with `IllegalStateException` if shorter or unset. |
| `JWT_EXPIRATION_MINUTES` | `app.jwt.expiration-minutes` | `10080` (7 days) | No | Lifetime of issued JWT tokens in minutes. |
| `GOOGLE_CLIENT_ID` | `app.google.client-id` | `""` (Disabled) | Optional | Google OAuth 2.0 Web Client ID (`*.apps.googleusercontent.com`). When blank, `POST /api/auth/google` returns `503 Service Unavailable`. |

### 2.3 Notifications & Alerting (Outbox Dispatcher, Email & WhatsApp)

| Environment Variable | Spring Property | Default Value | Required When Enabled? | Description |
|---|---|---|---|---|
| `NOTIFICATION_POLL_MS` | `app.notifications.poll-interval-ms` | `5000` | No | Interval in milliseconds between [`NotificationScheduler`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/NotificationScheduler.java) polling ticks. |
| `EMAIL_ENABLED` | `app.notifications.email.enabled` | `false` | — | When `false`, activates [`LoggingEmailSender`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/LoggingEmailSender.java) (test mode). When `true`, activates [`SmtpEmailSender`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/SmtpEmailSender.java) and validates all `EMAIL_*` settings at startup. |
| `EMAIL_SMTP_HOST` | `app.notifications.email.host` | `""` | **Yes** (`EMAIL_ENABLED=true`) | SMTP server hostname (e.g., `smtp.gmail.com`). |
| `EMAIL_SMTP_PORT` | `app.notifications.email.port` | `587` | **Yes** (`EMAIL_ENABLED=true`) | SMTP port (`1`–`65535`). Port `465` enables implicit SSL (`mail.smtp.ssl.enable=true`); other ports (e.g. `587`) enforce STARTTLS (`mail.smtp.starttls.required=true`). |
| `EMAIL_SMTP_USERNAME` | `app.notifications.email.username` | `""` | **Yes** (`EMAIL_ENABLED=true`) | SMTP authentication username. |
| `EMAIL_SMTP_PASSWORD` | `app.notifications.email.password` | `""` | **Yes** (`EMAIL_ENABLED=true`) | SMTP authentication password or App Password (never logged). |
| `EMAIL_FROM` | `app.notifications.email.from` | `""` | **Yes** (`EMAIL_ENABLED=true`) | Sender email address. Validated against `^[^@\s,<>]+@[^@\s,<>]+\.[^@\s,<>]+$` to prevent header injection. |
| `EMAIL_ADMIN_RECIPIENTS` | `app.notifications.email.admin-recipients` | `""` | **Yes** (`EMAIL_ENABLED=true`) | Comma-separated list of admin email addresses that receive new booking alerts. Each address is validated against the strict email regex at startup. Note: if empty even in test mode (`EMAIL_ENABLED=false`), no `EMAIL` row is enqueued. |
| `WHATSAPP_ENABLED` | `app.notifications.whatsapp.enabled` | `false` | — | When `false`, activates [`LoggingWhatsAppSender`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/LoggingWhatsAppSender.java) (test mode). When `true`, activates [`WhatsAppCloudSender`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/WhatsAppCloudSender.java) and validates all `WHATSAPP_*` settings at startup. |
| `WHATSAPP_PHONE_NUMBER_ID` | `app.notifications.whatsapp.phone-number-id` | `""` | **Yes** (`WHATSAPP_ENABLED=true`) | Meta WhatsApp Business Phone Number ID. |
| `WHATSAPP_ACCESS_TOKEN` | `app.notifications.whatsapp.access-token` | `""` | **Yes** (`WHATSAPP_ENABLED=true`) | Meta Graph API permanent or system-user Bearer token (never logged). |
| `WHATSAPP_ADMIN_RECIPIENT` | `app.notifications.whatsapp.admin-recipient` | `"+94000000000"` | **Yes** (`WHATSAPP_ENABLED=true`) | Destination admin phone number in E.164 format (digits and `+`). Must be changed away from `"+94000000000"` when `WHATSAPP_ENABLED=true`. |
| `WHATSAPP_API_VERSION` | `app.notifications.whatsapp.api-version` | `"v22.0"` | No | Meta Graph API version segment. |
| `WHATSAPP_BASE_URL` | `app.notifications.whatsapp.base-url` | `"https://graph.facebook.com"` | No | Base URL for Meta Graph API calls. |

---

## 3. Frontend Environment Variables Reference

| Environment Variable | Default Value | Read By | Description |
|---|---|---|---|
| `VITE_GOOGLE_CLIENT_ID` | `""` | [`GoogleSignInButton.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/components/GoogleSignInButton.jsx#L3) | Google OAuth 2.0 Web Client ID. Must match backend `GOOGLE_CLIENT_ID`. When empty, the "Continue with Google" button is not rendered. |
| `VITE_API_BASE_URL` | `""` (Same-origin `/api/*`) | [`client.js`](file:///home/sivaram/Github/homecare-connect/frontend/src/api/client.js#L1-L14) | Optional backend origin prefix. Leave empty (`""`) in local dev (uses Vite proxy) and in CloudFront deployments (uses `/api/*` behavior). Note: `client.js` automatically ignores `http://` values when the page is loaded over `https://` to prevent mixed-content browser errors. |

---

## 4. Test Profile Overrides (`application-test.yml`)

During `./mvnw test`, [`backend/src/main/resources/application-test.yml`](file:///home/sivaram/Github/homecare-connect/backend/src/main/resources/application-test.yml) overrides the following properties so tests run fast, offline, and deterministically:
- `spring.datasource.url`: `jdbc:h2:mem:homecare;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH`
- `app.jwt.secret`: Fixed 64-character test key
- `app.photos.dir`: `target/test-photos`
- `app.documents.dir`: `target/test-documents`
- `app.notifications.poll-interval-ms`: `3600000` (1 hour) and `spring.task.scheduling.pool.size: 0` (so tests invoke `NotificationDispatcher.dispatchDue()` deterministically rather than racing a background thread).
