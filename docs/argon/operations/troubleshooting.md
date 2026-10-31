# Troubleshooting Guide

This guide covers common startup, authentication, file upload, and operational issues in HomeCare Connect, along with their root causes and verified solutions.

---

## 1. Automated Failure Diagnostics in `start-service.sh`

When `./start-service.sh start` fails to bring up the backend or frontend, [`explain_failure()` in `start-service.sh`](file:///home/sivaram/Github/homecare-connect/start-service.sh#L179-L228) automatically scans `.run/backend.log` or `.run/frontend.log` for known error patterns (`Port ... was already in use`, `FlywayException`, `JWT secret must be at least 32 bytes`, `Connection to localhost:5432 refused`, etc.) and prints both the root cause and the last 40 lines of the log.

---

## 2. Startup & Configuration Issues

### 2.1 `IllegalStateException: JWT secret must be at least 32 bytes (set JWT_SECRET)`
- **Cause**: [`JwtService.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/JwtService.java#L27-L33) enforces a minimum 32-byte (256-bit) HMAC-SHA256 secret. Either `JWT_SECRET` was not set when starting the backend manually, or a short value was configured in `.env.local`.
- **Solution**:
  - Use `./start-service.sh start` (which auto-generates a 48-byte secret in `.run/jwt.secret`), or generate one manually:
    ```bash
    export JWT_SECRET="$(openssl rand -base64 48)"
    ```

### 2.2 `IllegalStateException: app.notifications.email.* is required when email notifications are enabled`
- **Cause**: `EMAIL_ENABLED=true` is set, but one of `EMAIL_SMTP_HOST`, `EMAIL_SMTP_USERNAME`, `EMAIL_SMTP_PASSWORD`, `EMAIL_FROM`, or `EMAIL_ADMIN_RECIPIENTS` is blank or contains an invalid email format (e.g., `"Display Name <user@example.com>"`).
- **Solution**:
  - Set `EMAIL_ENABLED=false` for local test mode, OR provide bare email addresses (`user@example.com`) matching `^[^@\s,<>]+@[^@\s,<>]+\.[^@\s,<>]+$` for `EMAIL_FROM` and `EMAIL_ADMIN_RECIPIENTS`.

### 2.3 `IllegalStateException: app.notifications.whatsapp.* is required when WhatsApp notifications are enabled`
- **Cause**: `WHATSAPP_ENABLED=true` is set, but `WHATSAPP_PHONE_NUMBER_ID` or `WHATSAPP_ACCESS_TOKEN` is blank, or `WHATSAPP_ADMIN_RECIPIENT` is still set to the placeholder `+94000000000`.
- **Solution**:
  - Set `WHATSAPP_ENABLED=false` for local test mode, OR configure a real E.164 phone number (`+9477...`) in `WHATSAPP_ADMIN_RECIPIENT`.

### 2.4 Port `5432`, `8080`, or `5173` Already in Use
- **Cause**: Another local PostgreSQL instance, Tomcat server, or Vite server is bound to the default port.
- **Solution**:
  - Stop existing services with `./start-service.sh stop`, check `lsof -i :8080`, or override ports:
    ```bash
    DB_PORT=5433 BACKEND_PORT=8081 FRONTEND_PORT=5174 ./start-service.sh start
    ```

---

## 3. Authentication & Google Sign-In Issues

### 3.1 Google Sign-In Button Does Not Appear on `/login` or `/register`
- **Cause**: [`GoogleSignInButton.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/components/GoogleSignInButton.jsx#L3-L10) returns `null` when `import.meta.env.VITE_GOOGLE_CLIENT_ID` is empty.
- **Solution**:
  - Set `GOOGLE_CLIENT_ID=<your-client-id>.apps.googleusercontent.com` in `.env.local` and run `./start-service.sh restart` (Vite reads `VITE_*` variables at startup).

### 3.2 Google Sign-In Fails with `401 Invalid Google ID token` or `idpiframe_initialization_failed`
- **Cause**:
  1. `http://localhost:5173` (or your CloudFront domain) is not listed under **Authorized JavaScript origins** in Google Cloud Console for that OAuth 2.0 Web Client ID.
  2. Frontend `VITE_GOOGLE_CLIENT_ID` and backend `GOOGLE_CLIENT_ID` do not match.
- **Solution**:
  - Add `http://localhost:5173` to **Authorized JavaScript origins** in Google Cloud Console -> APIs & Services -> Credentials, and ensure both backend and frontend use the exact same Client ID.

### 3.3 `401 This account uses Google sign-in. Please continue with Google.`
- **Cause**: The user originally registered via Google (`users.password_hash IS NULL`) and is attempting to sign in with an email and password on `/login`.
- **Solution**: Click **Continue with Google** on `/login`.

---

## 4. Directory Visibility & Booking Issues

### 4.1 Caregiver Saved Profile, But Does Not Appear on the Landing Page Directory
- **Cause**: [`DirectoryService.search`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/directory/DirectoryService.java#L38-L41) requires **four conditions** for a caregiver to appear in `GET /api/caregivers`:
  1. `profile.isComplete()` is `true` (`age`, `experienceYears`, `jobType`, `qualification` are non-null/non-blank, and `rates` has at least one entry).
  2. `profile.isAvailable()` is `true`.
  3. `profile.isListed()` is `true` (all required documents for the caregiver's `jobType` in `document_requirements` have `status = 'APPROVED'`).
- **Solution**:
  - Log in as the caregiver and check `/profile`:
    - Ensure the availability toggle at the top is set to **Available for care requests**.
    - Check the **Identity & Qualification Documents** section at the bottom of `/profile`. If any required document is missing, `PENDING`, or `REJECTED`, upload it and have an admin approve it at `/admin/verifications`.

### 4.2 Care Seeker Gets `409 Conflict` When Requesting a Booking
- **Cause**: [`BookingService.create`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/BookingService.java#L72-L82) enforces two anti-spam limits:
  1. `"You already have an open booking request with this caregiver."` (Seeker already has a `REQUESTED` or `CONFIRMED` booking with this caregiver).
  2. `"You already have 3 open booking requests. Cancel one or wait for admin confirmation before creating more."`
- **Solution**:
  - Open **My Bookings** (`/bookings`) and cancel an existing `REQUESTED` booking, or have an admin confirm/complete existing requests at `/admin`.

---

## 5. Browser Pop-Up Blocker When Viewing Verification Documents
- **Symptom**: Clicking **View file** on `/profile` or `/admin/verifications` shows an error `"Pop-up blocked — allow pop-ups to view the document."`
- **Cause**: Because document endpoints require an `Authorization: Bearer <token>` header, the frontend fetches the binary stream via `apiBlob(url)` and opens the resulting `blob:` URL via `window.open(blobUrl, '_blank')`. Some browsers block `window.open` when called after an `await` promise resolves.
- **Solution**: Allow pop-ups for `http://localhost:5173` (or your production domain) in your browser's address bar.
