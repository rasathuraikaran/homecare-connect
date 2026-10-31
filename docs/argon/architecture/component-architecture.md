# Component Architecture

## 1. Layered Architecture Overview

HomeCare Connect follows a clean, vertical-slice **package-by-feature** architecture on the backend paired with a **role-aware component/page** architecture on the frontend.

```mermaid
flowchart LR
    subgraph Frontend["React 18 SPA (frontend/src)"]
        Pages["Pages<br/>(Welcome, Login, Register, Dashboard,<br/>SeekerHome, CaregiverHome, Profile,<br/>Services, Admin, AdminVerification, AdminUsers)"]
        Components["Shared Components<br/>(AuthLayout, AdminShell, CaregiverCard,<br/>BookingDialog, ReviewsDialog, VerificationCard,<br/>DocumentRulesPanel, MyRequests, ProfilePreview)"]
        AuthCtx["State & Auth<br/>(AuthContext.jsx)"]
        ApiClient["HTTP Client<br/>(api/client.js)"]
    end

    subgraph Backend["Spring Boot 3.3 Backend (com.homecare)"]
        Filter["Security Layer<br/>(SecurityConfig, JwtAuthFilter, ApiExceptionHandler)"]
        Web["Controller Layer<br/>(*Controller)"]
        Domain["Service Layer<br/>(*Service, NotificationDispatcher, VerificationBadges)"]
        Data["Persistence & Storage Layer<br/>(*Repository, DocumentRules, PhotoStorage, DocumentStorage)"]
    end

    Pages --> Components
    Pages --> AuthCtx
    Components --> ApiClient
    AuthCtx --> ApiClient
    ApiClient -->|REST JSON / Multipart| Filter
    Filter --> Web
    Web --> Domain
    Domain --> Data
```

---

## 2. Backend Layer Responsibilities

| Layer | Classes | Responsibilities |
|---|---|---|
| **Security & Cross-Cutting** | [`SecurityConfig`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/config/SecurityConfig.java), [`JwtAuthFilter`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/JwtAuthFilter.java), [`ApiExceptionHandler`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/config/ApiExceptionHandler.java) | Stateless session policy, CORS headers, JWT extraction and principal resolution, endpoint role guards (`ROLE_CAREGIVER`, `ROLE_CARE_SEEKER`, `ROLE_ADMIN`), unified JSON error responses. |
| **REST Controllers** | [`AuthController`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/AuthController.java), [`ProfileController`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/ProfileController.java), [`CaregiverDirectoryController`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/directory/CaregiverDirectoryController.java), [`ReviewController`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/review/ReviewController.java), [`BookingController`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/BookingController.java), [`AdminBookingController`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/AdminBookingController.java), [`VerificationController`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/VerificationController.java), [`AdminVerificationController`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/AdminVerificationController.java), [`AdminRequirementController`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/AdminRequirementController.java), [`AdminUserController`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/admin/AdminUserController.java) | HTTP routing, `@Valid` request DTO binding, `@AuthenticationPrincipal User` injection, HTTP status codes and response headers (`Cache-Control`, `X-Content-Type-Options`). Note: `CaregiverDirectoryController` is a read-only query controller that directly coordinates `CaregiverProfileRepository`, `ReviewRepository`, and `VerificationBadges`. |
| **Application Services** | [`AuthService`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/AuthService.java), [`ProfileService`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/ProfileService.java), [`ReviewService`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/review/ReviewService.java), [`BookingService`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/BookingService.java), [`AdminBookingService`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/AdminBookingService.java), [`NotificationService`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/NotificationService.java), [`VerificationService`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/VerificationService.java), [`AdminVerificationService`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/AdminVerificationService.java), [`AdminRequirementService`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/AdminRequirementService.java), [`AdminUserService`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/admin/AdminUserService.java) | Transaction demarcation (`@Transactional`), domain business rules, state machine validation, domain event publishing/listening, and post-commit file cleanup registration. |
| **Repositories & Storage** | [`UserRepository`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/user/UserRepository.java), [`CaregiverProfileRepository`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/CaregiverProfileRepository.java), [`ReviewRepository`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/review/ReviewRepository.java), [`BookingRepository`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/BookingRepository.java), [`NotificationRepository`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/notification/NotificationRepository.java), [`CaregiverDocumentRepository`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/CaregiverDocumentRepository.java), [`DocumentRules`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/DocumentRules.java), [`PhotoStorage`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/PhotoStorage.java), [`DocumentStorage`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/DocumentStorage.java) | JPQL/SQL queries, batch projections, `JdbcTemplate` access for `document_requirements`, and local disk I/O with magic-byte verification. |

---

## 3. Inter-Module Package Dependencies

The backend packages maintain clean directional boundaries:

```mermaid
flowchart TD
    user["com.homecare.user"]
    profile["com.homecare.profile"]
    auth["com.homecare.auth"]
    config["com.homecare.config"]
    review["com.homecare.review"]
    verification["com.homecare.verification"]
    directory["com.homecare.directory"]
    notification["com.homecare.notification"]
    booking["com.homecare.booking"]
    admin["com.homecare.admin"]

    config --> auth
    auth --> user
    auth --> profile
    profile --> user
    review --> user
    review --> profile
    verification --> user
    verification --> profile
    directory --> profile
    directory --> review
    directory --> verification
    booking --> user
    booking --> profile
    booking --> notification
    admin --> user
    admin --> profile
    admin --> booking
    admin --> review
    admin --> verification
```

> [!NOTE]
> Decoupling between `profile` and `verification`: `com.homecare.profile` does **not** depend on `com.homecare.verification`. Instead, when a caregiver's job changes in [`ProfileService.save`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/ProfileService.java#L46), it publishes [`ProfileJobChanged`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/ProfileJobChanged.java#L4), which `VerificationService` consumes via `@EventListener` to avoid a circular dependency between `profile` and `verification`.

---

## 4. Frontend Component Hierarchy

```mermaid
flowchart TD
    App["App.jsx<br/>(useRoleTheme + Routes)"]
    Welcome["WelcomePage.jsx<br/>(GuestOnly)"]
    Login["LoginPage.jsx<br/>(GuestOnly + RequireRole)"]
    Register["RegisterPage.jsx<br/>(GuestOnly + RequireRole)"]
    Dashboard["DashboardPage.jsx<br/>(RequireAuth)"]
    Profile["ProfilePage.jsx<br/>(RequireAuth)"]
    Services["ServicesPage.jsx<br/>(RequireAuth)"]
    Admin["AdminPage.jsx<br/>(RequireAuth + RequireAdmin)"]
    AdminVer["AdminVerificationPage.jsx<br/>(RequireAuth + RequireAdmin)"]
    AdminUsers["AdminUsersPage.jsx<br/>(RequireAuth + RequireAdmin)"]

    App --> Welcome & Login & Register & Dashboard & Profile & Services & Admin & AdminVer & AdminUsers

    Welcome & Login & Register --> AuthLayout["AuthLayout.jsx"]
    Login & Register --> GoogleButton["GoogleButton.jsx"]
    Login & Register --> RoleBadge["RoleBadge.jsx"]

    Dashboard --> CaregiverHome["CaregiverHome.jsx"]
    Dashboard --> SeekerHome["SeekerHome.jsx"]

    CaregiverHome --> VerificationCard["VerificationCard.jsx"]
    CaregiverHome --> ProfilePreview["ProfilePreview.jsx"]
    CaregiverHome --> ReviewList["ReviewList.jsx"]

    Profile --> ProfilePreview

    SeekerHome --> CaregiverCard["CaregiverCard.jsx"]
    SeekerHome --> BookingDialog["BookingDialog.jsx"]
    SeekerHome --> ReviewsDialog["ReviewsDialog.jsx"]
    SeekerHome --> MyRequests["MyRequests.jsx"]

    Admin & AdminVer & AdminUsers --> AdminShell["AdminShell.jsx"]
    AdminShell --> AdminTabs["AdminTabs.jsx"]
    AdminVer --> DocumentRulesPanel["DocumentRulesPanel.jsx"]
```
