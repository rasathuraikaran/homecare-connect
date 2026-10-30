# Container & Component Architecture Diagrams (C4 Levels 2 & 3)

This document visualizes the containers (deployable runtime units) and internal backend/frontend components of HomeCare Connect.

---

## 1. Container Diagram (C4 Level 2)

```mermaid
flowchart LR
    subgraph Client["User Browser"]
        SPA["React 18 SPA\n(Vite Bundle, React Router v6)"]
    end

    subgraph AppServer["Backend Host (Local JVM / AWS EC2)"]
        API["Spring Boot 3.3.5 API\n(Java 17, Embedded Tomcat :8080)"]
        Sched["NotificationScheduler\n(@Scheduled 5s Polling Thread)"]
        Disk["Local File Storage\n(uploads/photos, uploads/documents)"]
    end

    subgraph DataTier["Database Tier"]
        PG[("PostgreSQL 16\n(8 Tables, Flyway V1-V11)")]
    end

    subgraph External["External APIs"]
        Google["Google OAuth2 JWKS"]
        SMTP["SMTP Server (:587/:465)"]
        Meta["Meta Graph API (v22.0)"]
    end

    SPA -->|"REST JSON & Multipart HTTP/HTTPS\n(/api/* + Bearer JWT)"| API
    API -->|"JPA / Hibernate & JdbcTemplate\n(TCP :5432)"| PG
    API -->|"Read / Write UUID Files\n(java.nio.file.Files)"| Disk
    Sched -->|"Polls PENDING notifications\n& updates status"| PG
    Sched -->|"Sends booking emails"| SMTP
    Sched -->|"Sends WhatsApp template messages"| Meta
    API -->|"Verifies Google ID Tokens"| Google
```

---

## 2. Backend Component Diagram (C4 Level 3)

```mermaid
flowchart TB
    subgraph Security["Security & Cross-Cutting Layer"]
        SecCfg["SecurityConfig\n(FilterChain & URL Rules)"]
        JwtFilter["JwtAuthFilter\n(Bearer Token + DB Lookup)"]
        ExHandler["ApiExceptionHandler\n(@RestControllerAdvice)"]
    end

    subgraph Controllers["REST Controllers (@RestController)"]
        AuthCtrl["AuthController\n(/api/auth/*)"]
        ProfCtrl["CaregiverProfileController\n(/api/profile/me/*)"]
        VerCtrl["CaregiverVerificationController\n(/api/profile/me/documents/*)"]
        DirCtrl["DirectoryController\n(/api/caregivers/*)"]
        RevCtrl["ReviewController\n(/api/caregivers/{id}/reviews)"]
        BookCtrl["BookingController\n(/api/bookings/*)"]
        AdmBookCtrl["AdminBookingController\n(/api/admin/bookings/*)"]
        AdmVerCtrl["AdminVerificationController\n(/api/admin/verifications/*,\n/api/admin/document-requirements)"]
        AdmUserCtrl["AdminUserController\n(/api/admin/users/*)"]
    end

    subgraph Services["Domain Services (@Service)"]
        AuthSvc["AuthService\n+ JwtService + GoogleIdTokenService"]
        ProfSvc["CaregiverProfileService"]
        VerSvc["CaregiverVerificationService"]
        DirSvc["DirectoryService"]
        RevSvc["ReviewService"]
        BookSvc["BookingService"]
        NotifSvc["NotificationService"]
        NotifDisp["NotificationDispatcher\n+ NotificationScheduler"]
        AdmUserSvc["AdminUserService"]
    end

    subgraph StorageAndGateways["Storage & External Gateways"]
        PhotoStore["PhotoStorage\n(uploads/photos)"]
        DocStore["DocumentStorage\n(uploads/documents)"]
        EmailSend["EmailSender\n(SmtpEmailSender / LoggingEmailSender)"]
        WaSend["WhatsAppSender\n(WhatsAppCloudSender / LoggingWhatsAppSender)"]
    end

    subgraph Persistence["Persistence Layer (Spring Data JPA + JdbcTemplate)"]
        UserRepo["UserRepository"]
        ProfRepo["CaregiverProfileRepository"]
        DocRepo["CaregiverDocumentRepository"]
        ReqJdbc["JdbcTemplate\n(document_requirements)"]
        RevRepo["ReviewRepository"]
        BookRepo["BookingRepository"]
        NotifRepo["NotificationRepository"]
    end

    AuthCtrl --> AuthSvc
    ProfCtrl --> ProfSvc
    VerCtrl --> VerSvc
    DirCtrl --> DirSvc
    DirCtrl --> PhotoStore
    RevCtrl --> RevSvc
    BookCtrl --> BookSvc
    AdmBookCtrl --> BookSvc
    AdmBookCtrl --> NotifSvc
    AdmBookCtrl --> DirSvc
    AdmVerCtrl --> VerSvc
    AdmUserCtrl --> AdmUserSvc

    AuthSvc --> UserRepo
    ProfSvc --> ProfRepo
    ProfSvc --> PhotoStore
    ProfSvc -.->|"Publishes ProfileJobChanged"| VerSvc

    VerSvc --> DocRepo
    VerSvc --> ProfRepo
    VerSvc --> DocStore
    VerSvc --> ReqJdbc

    DirSvc --> ProfRepo
    DirSvc --> UserRepo
    DirSvc --> RevSvc

    RevSvc --> RevRepo
    RevSvc --> ProfRepo
    RevSvc --> UserRepo

    BookSvc --> BookRepo
    BookSvc --> ProfRepo
    BookSvc --> UserRepo
    BookSvc --> NotifSvc

    NotifSvc --> NotifRepo
    NotifSvc --> NotifDisp
    NotifDisp --> NotifRepo
    NotifDisp --> EmailSend
    NotifDisp --> WaSend

    AdmUserSvc --> UserRepo
    AdmUserSvc --> ProfRepo
    AdmUserSvc --> BookRepo
    AdmUserSvc --> RevRepo
    AdmUserSvc --> DocRepo
    AdmUserSvc --> VerSvc
    AdmUserSvc --> PhotoStore
```

---

## 3. Frontend Component Hierarchy Diagram

```mermaid
flowchart TD
    Main["main.jsx\n(BrowserRouter + AuthProvider)"] --> App["App.jsx\n(useRoleTheme + Navbar + Routes)"]

    App --> PublicRoutes["Public Routes"]
    App --> GuestRoutes["GuestOnly Guard"]
    App --> SeekerRoutes["RequireRole('CARE_SEEKER') Guard"]
    App --> CaregiverRoutes["RequireRole('CAREGIVER') Guard"]
    App --> AuthRoutes["RequireAuth Guard"]
    App --> AdminRoutes["RequireAdmin Guard"]

    PublicRoutes --> Landing["LandingPage.jsx\n(Directory Filters + CaregiverCard Grid)"]
    PublicRoutes --> Detail["CaregiverDetailPage.jsx\n(Public Profile + ReviewSection + BookingRequestModal)"]
    PublicRoutes --> Terms["TermsPage.jsx"]
    PublicRoutes --> Privacy["PrivacyPage.jsx"]

    GuestRoutes --> Login["LoginPage.jsx\n(Email/Pass + GoogleSignInButton)"]
    GuestRoutes --> Register["RegisterPage.jsx\n(Role Radio + GoogleSignInButton)"]

    SeekerRoutes --> MyBookings["MyBookingsPage.jsx\n(Seeker Booking List + Cancel)"]

    CaregiverRoutes --> ProfileEdit["ProfilePage.jsx\n(Photo + Bio/Job/Rates + VerificationSection)"]

    AuthRoutes --> Account["AccountPage.jsx\n(Identity Summary + Shortcuts)"]

    AdminRoutes --> AdminBook["AdminPage.jsx\n(AdminTabs + Bookings + Notification Status/Retry)"]
    AdminRoutes --> AdminVer["AdminVerificationPage.jsx\n(AdminTabs + Document Queue + Requirements Matrix)"]
    AdminRoutes --> AdminUsers["AdminUsersPage.jsx\n(AdminTabs + User Search + Detail Drawer + Delete Modal)"]
```
