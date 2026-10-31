# Logging & Audit Trail

This document describes the logging architecture, log file locations, PII masking conventions, and security audit log events in HomeCare Connect.

---

## 1. Log Locations & Inspection

### Local Development (`start-service.sh`)
When started via `./start-service.sh`, logs are written to `.run/`:
- **Backend Log**: `.run/backend.log`
- **Frontend Log**: `.run/frontend.log`
- **PostgreSQL Container Log**: Docker container `homecare-postgres` stdout/stderr

Use `./start-service.sh logs` to tail logs:
```bash
# Tail both backend and frontend logs
./start-service.sh logs

# Tail only backend logs
./start-service.sh logs backend

# Tail only PostgreSQL logs
./start-service.sh logs db
```

---

## 2. Backend Log Levels (`application.yml`)

Configured in [`backend/src/main/resources/application.yml`](file:///home/sivaram/Github/homecare-connect/backend/src/main/resources/application.yml#L47-L50):

```yaml
logging:
  level:
    root: INFO
    com.homecare: INFO
```

All application classes use SLF4J (`org.slf4j.LoggerFactory.getLogger(...)`).

---

## 3. PII Masking vs. Test-Mode Logging

HomeCare Connect distinguishes between **production senders** (`EMAIL_ENABLED=true`, `WHATSAPP_ENABLED=true`) and **local test-mode senders** (`EMAIL_ENABLED=false`, `WHATSAPP_ENABLED=false`):

| Component | Mode | Logged Information & PII Handling |
|---|---|---|
| [`SmtpEmailSender.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/SmtpEmailSender.java#L41-L71) | Production (`EMAIL_ENABLED=true`) | **Masked**: Uses `maskAddress()` to log `adm***@domain.com` and logs only the email `subject` (never the body containing seeker phone/address). |
| [`WhatsAppCloudSender.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/WhatsAppCloudSender.java#L75-L157) | Production (`WHATSAPP_ENABLED=true`) | **Masked**: Uses `maskPhone()` to log only the last 4 digits (`***4567`) and the template name (`new_booking_details`), never the template variables or access token. |
| [`LoggingEmailSender.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/LoggingEmailSender.java) | Test Mode (`EMAIL_ENABLED=false`) | **Unmasked (Dev Only)**: Logs `recipient`, `subject`, and `body` at `INFO` level so developers can inspect simulated emails in `.run/backend.log`. |
| [`LoggingWhatsAppSender.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/LoggingWhatsAppSender.java) | Test Mode (`WHATSAPP_ENABLED=false`) | **Unmasked (Dev Only)**: Logs `recipient`, `templateName`, and `bodyVariables` at `INFO` level for local debugging. |

> [!WARNING]
> In production environments handling real customer bookings, always enable `EMAIL_ENABLED=true` and `WHATSAPP_ENABLED=true` (or restrict log access) so test-mode senders do not write unmasked booking details to application logs.

---

## 4. Admin & Verification Audit Events

The backend records structured audit trails both in **PostgreSQL columns** and in **application logs**:

### 4.1 Database Audit Columns
- **`caregiver_documents.reviewed_by` & `reviewed_at`**: Recorded whenever an admin approves or rejects a caregiver verification document ([`CaregiverVerificationService.approve` / `reject`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/CaregiverVerificationService.java#L172-L216)).
- **`document_requirements.updated_by` & `updated_at`**: Recorded whenever an admin toggles a mandatory document requirement ([`CaregiverVerificationService.setRequirement`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/CaregiverVerificationService.java#L218-L232)).

### 4.2 Application Log Audit Events
- **Admin User Deletion** ([`AdminUserService.deleteUser`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/admin/AdminUserService.java#L125-L126)):
  ```text
  INFO c.h.admin.AdminUserService : Admin 7 deleted user 14 (role=CAREGIVER)
  ```
- **Admin Document Requirement Toggle** ([`CaregiverVerificationService.setRequirement`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/CaregiverVerificationService.java#L230)):
  ```text
  INFO c.h.v.CaregiverVerificationService : Admin 7 set document requirement (NURSING, NIC) = true; refreshed 2 caregiver profiles
  ```
- **Admin Manual Notification Retry** ([`NotificationService.retryBookingNotifications`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/NotificationService.java#L106-L108)):
  ```text
  INFO c.h.n.NotificationService : Reset 2 failed notifications for booking #42
  ```
