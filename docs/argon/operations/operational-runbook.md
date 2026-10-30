# Operational Runbook

This runbook provides step-by-step procedures for common administrative and operational tasks in HomeCare Connect.

---

## 1. Granting & Revoking Admin Privileges

By design, no REST API endpoint can grant or revoke `is_admin`. Admin privileges are managed exclusively at the database level.

### Local Environment (via `start-service.sh`)
- **Grant Admin**:
  ```bash
  ./start-service.sh make-admin user@example.com
  ```
- **Revoke Admin**:
  ```bash
  ./start-service.sh make-admin user@example.com --revoke
  ```

### Production PostgreSQL Environment
Connect to the production PostgreSQL database and execute:
```sql
-- Grant admin privilege
UPDATE users
SET is_admin = TRUE
WHERE lower(email) = lower('user@example.com')
RETURNING id, email, full_name, role, is_admin;

-- Revoke admin privilege
UPDATE users
SET is_admin = FALSE
WHERE lower(email) = lower('user@example.com')
RETURNING id, email, full_name, role, is_admin;
```
> [!NOTE]
> Because [`JwtAuthFilter`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/JwtAuthFilter.java) loads the [`User`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/user/User.java) row on every HTTP request, the user's `ROLE_ADMIN` authority updates immediately on their next API call. In the browser UI, clicking **Refresh** or calling `refreshMe()` updates the top navigation bar.

---

## 2. Switching Email Alerts from Test Mode to Live SMTP

By default, `EMAIL_ENABLED=false` routes booking emails to [`LoggingEmailSender`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/LoggingEmailSender.java). To enable real SMTP delivery:

1. Obtain an SMTP account (for Gmail with 2-Step Verification, generate a 16-character **App Password**).
2. Set the following variables in `.env.local` (or production environment):
   ```dotenv
   EMAIL_ENABLED=true
   EMAIL_SMTP_HOST=smtp.gmail.com
   EMAIL_SMTP_PORT=587
   EMAIL_SMTP_USERNAME=alerts@yourdomain.com
   EMAIL_SMTP_PASSWORD=<16-char-app-password>
   EMAIL_FROM=alerts@yourdomain.com
   EMAIL_ADMIN_RECIPIENTS=admin1@yourdomain.com,admin2@yourdomain.com
   ```
3. Restart the backend (`./start-service.sh restart`).
4. Verify in `.run/backend.log` that startup succeeds without `IllegalStateException`.

---

## 3. Switching WhatsApp Alerts from Test Mode to Meta Cloud API

By default, `WHATSAPP_ENABLED=false` routes WhatsApp alerts to [`LoggingWhatsAppSender`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/LoggingWhatsAppSender.java). To enable live WhatsApp delivery:

1. In **Meta Business Suite / WhatsApp Manager**, create and get approval for an English (`en`) message template named **`new_booking_details`** with **9 body variables** (`{{1}}` through `{{9}}`):
   - `{{1}}`: Booking ID (`#42`)
   - `{{2}}`: Seeker Name
   - `{{3}}`: Seeker Phone
   - `{{4}}`: Seeker Email
   - `{{5}}`: Care Address
   - `{{6}}`: Caregiver Name
   - `{{7}}`: Caregiver Job Type
   - `{{8}}`: Quoted Rate & Charge
   - `{{9}}`: Booking Note
2. Set the following variables in `.env.local` (or production environment):
   ```dotenv
   WHATSAPP_ENABLED=true
   WHATSAPP_PHONE_NUMBER_ID=<meta-phone-number-id>
   WHATSAPP_ACCESS_TOKEN=<meta-permanent-system-user-token>
   WHATSAPP_ADMIN_RECIPIENT=+94771234567
   WHATSAPP_API_VERSION=v22.0
   ```
3. Restart the backend (`./start-service.sh restart`).

---

## 4. Rolling Out Mandatory Caregiver Document Verification

In `V11__addresses_and_document_rules.sql`, all document requirements start with `required = FALSE` so caregivers can list immediately during initial onboarding. When you are ready to enforce mandatory verification for a category (e.g., `NURSING`):

1. **Pre-Flight Check**:
   - Navigate to **Admin -> Verification** (`/admin/verifications`) and filter by `All` or `Incomplete`.
   - Review any `PENDING` documents uploaded by `NURSING` caregivers so already-compliant nurses are `APPROVED` before you flip the switch.
2. **Enable Requirement**:
   - In the **Mandatory document rules** panel at the top of `/admin/verifications`, check the box for `NIC` and/or `Nursing certificate` under **Nursing**.
   - **Immediate Effect**: [`CaregiverVerificationService.setRequirement`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/CaregiverVerificationService.java#L218-L232) updates `document_requirements` and immediately sets `listed = FALSE` for any `NURSING` caregiver who does not have an `APPROVED` document for that type.
3. **Caregiver Experience**:
   - Delisted caregivers see an `INCOMPLETE`, `IN_REVIEW`, or `ACTION_NEEDED` status banner on `/profile` guiding them to upload the required document. Once an admin clicks **Approve**, `listed` automatically flips back to `TRUE`.

---

## 5. Retrying Failed Booking Notifications

If an SMTP outage or expired WhatsApp token causes booking notifications to enter `FAILED` status (after 5 automatic attempts):
1. Fix the underlying credential or network issue and restart the backend if environment variables changed.
2. Open **Admin -> Bookings** (`/admin`).
3. Locate the booking showing a red `failed` badge, click **Show alerts**, and click **Retry alerts**.
4. This calls `POST /api/admin/bookings/{id}/notifications/retry`, which resets `FAILED` rows to `PENDING` (`attempts = 0`, `next_attempt_at = now()`) and immediately invokes `NotificationDispatcher.dispatchDue()`.

---

## 6. Executing a User Account Deletion

1. Navigate to **Admin -> Users** (`/admin/users`).
2. Search for the user by name, email, or phone and click **View details** to inspect their profile, bookings, reviews, and uploaded documents.
3. Click **Delete user...** and confirm the deletion modal.
4. **What Happens**:
   - All rows in `users`, `caregiver_profiles`, `caregiver_rates`, `caregiver_documents`, `bookings` (as seeker or caregiver), `notifications`, and `reviews` (written or received) are deleted atomically in PostgreSQL.
   - After the database transaction commits, `PhotoStorage` and `DocumentStorage` delete the user's physical photo and document files from `uploads/photos` and `uploads/documents`.
   - Any active JWT held by that user immediately stops working on the next API request.
