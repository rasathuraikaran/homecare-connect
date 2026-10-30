# Core Workflow & State Machine Diagrams

This document provides sequence and state machine diagrams for the core business workflows in HomeCare Connect:
1. Caregiver Profile & Document Verification Workflow (including dynamic document requirement toggling)
2. Care Seeker Booking Request & Transactional Outbox Notification Workflow
3. Entity State Machines (`BookingStatus`, `DocumentStatus`, `VerificationStatus`, and `Notification.Status`)
4. Admin Account Deletion & Post-Commit File Cleanup Workflow

---

## 1. Caregiver Verification & Dynamic Listing Eligibility Workflow

Implemented across [`CaregiverProfileService.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/CaregiverProfileService.java), [`CaregiverVerificationService.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/CaregiverVerificationService.java), and [`DirectoryService.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/directory/DirectoryService.java).

```mermaid
sequenceDiagram
    autonumber
    participant CG as "Caregiver (ProfilePage)"
    participant Admin as "Admin (AdminVerificationPage)"
    participant ProfSvc as "CaregiverProfileService"
    participant VerSvc as "CaregiverVerificationService"
    participant DocStore as "DocumentStorage (Disk)"
    participant DB as "PostgreSQL"

    Note over CG,DB: Step 1: Caregiver sets Job Type (e.g. NURSING)
    CG->>ProfSvc: PUT /api/profile/me { jobType: "NURSING", ... }
    ProfSvc->>DB: Save caregiver_profiles (job_type = 'NURSING')
    ProfSvc->>VerSvc: Publish ProfileJobChanged(userId, oldJob, NURSING)
    VerSvc->>DB: Query document_requirements WHERE job_type = 'NURSING' AND required = TRUE
    alt No required docs for NURSING (Default V11 state)
        VerSvc->>DB: UPDATE caregiver_profiles SET listed = TRUE
    else Required docs exist (e.g. NIC, NURSING_CERTIFICATE) & not yet APPROVED
        VerSvc->>DB: UPDATE caregiver_profiles SET listed = FALSE
    end
    ProfSvc-->>CG: 200 OK ProfileResponse (listed = true/false)

    Note over CG,DB: Step 2: Admin toggles a Document Requirement ON
    Admin->>VerSvc: PUT /api/admin/document-requirements { jobType: "NURSING", docType: "NIC", required: true }
    VerSvc->>DB: UPSERT document_requirements (required = true)
    VerSvc->>DB: SELECT user_id FROM caregiver_profiles WHERE job_type = 'NURSING'
    loop For each NURSING caregiver
        VerSvc->>VerSvc: refreshListed(userId)
        VerSvc->>DB: UPDATE caregiver_profiles SET listed = FALSE (if NIC not APPROVED)
    end
    VerSvc-->>Admin: 200 OK RequirementsMatrixResponse

    Note over CG,DB: Step 3: Caregiver Uploads Required Document
    CG->>VerSvc: POST /api/profile/me/documents?docType=NIC (multipart PDF/JPEG/PNG/WEBP <= 10MB)
    VerSvc->>DocStore: store(file) -> Verify magic bytes & write UUID file
    DocStore-->>VerSvc: StoredFile(filename, contentType)
    VerSvc->>DB: UPSERT caregiver_documents (status = 'PENDING', revision = revision + 1)
    VerSvc->>VerSvc: refreshListed(userId) -> listed remains FALSE while PENDING
    VerSvc-->>CG: 201 Created CaregiverDocumentDto (status = PENDING)

    Note over CG,DB: Step 4: Admin Approves Document
    Admin->>VerSvc: POST /api/admin/verifications/documents/{docId}/approve
    VerSvc->>DB: UPDATE caregiver_documents SET status = 'APPROVED', reviewed_by = adminId
    VerSvc->>VerSvc: refreshListed(userId)
    VerSvc->>DB: All required docs APPROVED -> UPDATE caregiver_profiles SET listed = TRUE
    VerSvc-->>Admin: 200 OK CaregiverDocumentDto (status = APPROVED)
```

---

## 2. Care Seeker Booking & Outbox Notification Workflow

Implemented across [`BookingService.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/BookingService.java), [`NotificationService.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/NotificationService.java), [`NotificationScheduler.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/NotificationScheduler.java), and [`NotificationDispatcher.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/NotificationDispatcher.java).

```mermaid
sequenceDiagram
    autonumber
    participant Seeker as "Care Seeker (BookingRequestModal)"
    participant BookSvc as "BookingService"
    participant NotifSvc as "NotificationService"
    participant DB as "PostgreSQL"
    participant Sched as "NotificationScheduler (Every 5s)"
    participant Disp as "NotificationDispatcher"
    participant Channels as "EmailSender / WhatsAppSender"

    Seeker->>BookSvc: POST /api/bookings { caregiverId, seekerPhone, address, rateType, note }
    BookSvc->>DB: Verify caregiver exists & caregiver.listed == true
    BookSvc->>DB: Check existsBySeekerIdAndCaregiverIdAndStatusIn(REQUESTED, CONFIRMED)
    BookSvc->>DB: Check countBySeekerIdAndStatus(REQUESTED) < 3
    BookSvc->>DB: Snapshot quoted_amount from caregiver_rates[rateType]
    BookSvc->>DB: INSERT INTO bookings (status = 'REQUESTED', ...)
    BookSvc->>NotifSvc: enqueueNewBookingAlerts(booking, seeker, caregiverUser, profile)
    NotifSvc->>DB: INSERT INTO notifications (channel = 'EMAIL', status = 'PENDING', ...)
    NotifSvc->>DB: INSERT INTO notifications (channel = 'WHATSAPP', status = 'PENDING', ...)
    BookSvc-->>Seeker: 201 Created BookingResponse (Transaction Commits)

    loop Every 5,000 ms (app.notifications.poll-interval-ms)
        Sched->>Disp: dispatchDue()
        Disp->>DB: SELECT * FROM notifications WHERE status='PENDING' AND next_attempt_at <= now() LIMIT 20
        loop For each due notification (Isolated TransactionTemplate)
            Disp->>Channels: send(recipient, subject/template, body/vars)
            alt Delivery Succeeds
                Channels-->>Disp: OK
                Disp->>DB: UPDATE notifications SET status='SENT', sent_at=now(), attempts=attempts+1
            else Transient Failure (attempts < 5)
                Channels-->>Disp: SendFailure(message, permanent=false)
                Disp->>DB: UPDATE notifications SET status='PENDING', attempts=attempts+1,\nnext_attempt_at = now() + min(30s * 2^(attempts-1), 15m)
            else Permanent Failure (4xx != 429) or attempts == 5
                Channels-->>Disp: SendFailure(message, permanent=true)
                Disp->>DB: UPDATE notifications SET status='FAILED', attempts=attempts+1, last_error=...
            end
        end
    end
```

---

## 3. Domain State Machines

### 3.1 Booking Lifecycle (`BookingStatus`)
Defined in [`BookingStatus.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/BookingStatus.java) and enforced in [`BookingService.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/BookingService.java#L103-L162).

```mermaid
stateDiagram-v2
    [*] --> REQUESTED : Care Seeker submits booking\n(POST /api/bookings)

    REQUESTED --> CONFIRMED : Admin confirms after phone coordination\n(PATCH /api/admin/bookings/{id})
    REQUESTED --> CANCELLED : Care Seeker cancels (POST /api/bookings/{id}/cancel)\nOR Admin cancels (PATCH /api/admin/bookings/{id})

    CONFIRMED --> COMPLETED : Admin marks service completed\n(PATCH /api/admin/bookings/{id})
    CONFIRMED --> CANCELLED : Admin cancels confirmed booking\n(PATCH /api/admin/bookings/{id})

    COMPLETED --> [*]
    CANCELLED --> [*]
```

### 3.2 Document Review Lifecycle (`DocumentStatus`)
Defined in [`DocumentStatus.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/DocumentStatus.java) and enforced in [`CaregiverVerificationService.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/CaregiverVerificationService.java#L118-L232).

```mermaid
stateDiagram-v2
    [*] --> PENDING : Caregiver uploads document\n(POST /api/profile/me/documents, revision = 1)

    PENDING --> APPROVED : Admin approves\n(POST /api/admin/verifications/documents/{id}/approve)
    PENDING --> REJECTED : Admin rejects with reason\n(POST /api/admin/verifications/documents/{id}/reject)
    PENDING --> PENDING : Caregiver replaces file\n(revision++)
    PENDING --> [*] : Caregiver deletes own unapproved doc\n(DELETE /api/profile/me/documents/{type})

    REJECTED --> PENDING : Caregiver uploads replacement\n(revision++, reject_reason cleared)
    REJECTED --> [*] : Caregiver deletes rejected doc\n(DELETE /api/profile/me/documents/{type})

    APPROVED --> APPROVED : Caregiver cannot delete or overwrite\n(409 Conflict)
```

### 3.3 Outbox Notification Lifecycle (`Notification.Status`)
Defined in [`Notification.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/Notification.java).

```mermaid
stateDiagram-v2
    [*] --> PENDING : Enqueued on Booking Creation\n(attempts = 0, next_attempt_at = now)

    PENDING --> SENT : Channel delivery succeeds\n(attempts++, sent_at = now)
    PENDING --> PENDING : Transient error & attempts < 5\n(attempts++, exponential backoff 30s..15m)
    PENDING --> FAILED : Permanent error OR attempts == 5

    FAILED --> PENDING : Admin clicks "Retry alerts"\n(POST /api/admin/bookings/{id}/notifications/retry)\n(attempts reset to 0, next_attempt_at = now)
    SENT --> [*]
```

---

## 4. Admin Account Deletion & Post-Commit File Cleanup

Implemented in [`AdminUserService.deleteUser`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/admin/AdminUserService.java#L99-L133).

```mermaid
sequenceDiagram
    autonumber
    participant Admin as "Admin (AdminUsersPage)"
    participant AdmCtrl as "AdminUserController"
    participant AdmSvc as "AdminUserService"
    participant VerSvc as "CaregiverVerificationService"
    participant DB as "PostgreSQL"
    participant TxSync as "TransactionSynchronizationManager"
    participant Disk as "PhotoStorage & DocumentStorage"

    Admin->>AdmCtrl: DELETE /api/admin/users/{id}
    AdmCtrl->>AdmSvc: deleteUser(targetId, adminId)
    AdmSvc->>DB: SELECT * FROM users WHERE id = targetId
    alt targetId == adminId OR target.isAdmin()
        AdmSvc-->>Admin: 409 Conflict ("Cannot delete admin or own account")
    else Non-admin user
        AdmSvc->>VerSvc: deleteAllForUser(targetId)
        VerSvc->>DB: Collect caregiver_documents file_names & delete document rows
        VerSvc->>TxSync: Register afterCommit -> documentStorage.deleteIfExists(files)
        AdmSvc->>DB: Collect caregiver_profiles.photo_file
        AdmSvc->>DB: DELETE FROM users WHERE id = targetId\n(Cascades to profiles, rates, reviews, bookings, notifications)
        AdmSvc->>TxSync: Register afterCommit -> photoStorage.deleteIfExists(photoFile)
        AdmSvc->>DB: Commit Transaction
        TxSync->>Disk: Execute afterCommit callbacks (Delete physical files from uploads/)
        AdmSvc-->>Admin: 204 No Content
    end
```
