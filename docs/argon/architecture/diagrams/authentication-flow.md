# Authentication & Authorization Flow Diagrams

This document illustrates the authentication and authorization workflows in HomeCare Connect:
1. Email & Password Registration and Sign-In
2. Google OAuth2 ID Token Sign-In (including two-step role selection for first-time users and automatic account linking)
3. Per-Request JWT Verification and Role-Based Access Control (RBAC)

---

## 1. Email & Password Registration and Sign-In

Implemented in [`AuthController.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/AuthController.java) and [`AuthService.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/AuthService.java#L41-L78).

```mermaid
sequenceDiagram
    autonumber
    participant User as "User Browser (RegisterPage / LoginPage)"
    participant AuthCtrl as "AuthController"
    participant AuthSvc as "AuthService"
    participant BCrypt as "BCryptPasswordEncoder"
    participant UserRepo as "UserRepository"
    participant JwtSvc as "JwtService"

    Note over User,JwtSvc: Flow A: Email/Password Registration (POST /api/auth/register)
    User->>AuthCtrl: POST /api/auth/register { email, password, fullName, phone, role }
    AuthCtrl->>AuthSvc: register(RegisterRequest)
    AuthSvc->>AuthSvc: Normalize email = email.trim().toLowerCase()
    AuthSvc->>UserRepo: existsByEmailIgnoreCase(email)
    alt Email already exists
        UserRepo-->>AuthSvc: true
        AuthSvc-->>User: 409 Conflict ("An account with this email already exists")
    else New Email
        UserRepo-->>AuthSvc: false
        AuthSvc->>BCrypt: encode(password)
        BCrypt-->>AuthSvc: bcryptHash
        AuthSvc->>UserRepo: save(User(email, bcryptHash, fullName, phone, role))
        UserRepo-->>AuthSvc: Saved User (id, admin=false)
        AuthSvc->>JwtSvc: issueToken(user)
        JwtSvc-->>AuthSvc: Signed HS256 JWT (sub=id, email, role, admin)
        AuthSvc-->>User: 201 Created AuthResponse(token, UserResponse)
        User->>User: localStorage.setItem('hc_token', token) & setItem('hc_user', user)
    end

    Note over User,JwtSvc: Flow B: Email/Password Login (POST /api/auth/login)
    User->>AuthCtrl: POST /api/auth/login { email, password }
    AuthCtrl->>AuthSvc: login(LoginRequest)
    AuthSvc->>UserRepo: findByEmailIgnoreCase(email.trim())
    alt User not found
        UserRepo-->>AuthSvc: Optional.empty()
        AuthSvc-->>User: 401 Unauthorized ("Invalid email or password")
    else User found with passwordHash == null (Google-only account)
        UserRepo-->>AuthSvc: User (passwordHash = null)
        AuthSvc-->>User: 401 Unauthorized ("This account uses Google sign-in. Please continue with Google.")
    else User found with passwordHash
        UserRepo-->>AuthSvc: User
        AuthSvc->>BCrypt: matches(rawPassword, user.passwordHash)
        alt Password mismatch
            BCrypt-->>AuthSvc: false
            AuthSvc-->>User: 401 Unauthorized ("Invalid email or password")
        else Password matches
            BCrypt-->>AuthSvc: true
            AuthSvc->>JwtSvc: issueToken(user)
            JwtSvc-->>AuthSvc: Signed HS256 JWT
            AuthSvc-->>User: 200 OK AuthResponse(token, UserResponse)
        end
    end
```

---

## 2. Google OAuth2 ID Token Sign-In & Role Selection Flow

Implemented in [`AuthService.loginWithGoogle`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/AuthService.java#L80-L120), [`GoogleIdTokenService.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/GoogleIdTokenService.java), [`LoginPage.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/pages/LoginPage.jsx), and [`RegisterPage.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/pages/RegisterPage.jsx).

```mermaid
sequenceDiagram
    autonumber
    participant Browser as "Browser (LoginPage / RegisterPage)"
    participant GIS as "Google Identity Services SDK"
    participant AuthCtrl as "AuthController"
    participant AuthSvc as "AuthService"
    participant Verifier as "GoogleIdTokenService"
    participant UserRepo as "UserRepository"
    participant JwtSvc as "JwtService"

    Browser->>GIS: User clicks "Continue with Google"
    GIS-->>Browser: CredentialResponse { credential: "<idToken>" }
    Browser->>AuthCtrl: POST /api/auth/google { idToken, role (optional), phone (optional) }
    AuthCtrl->>AuthSvc: loginWithGoogle(GoogleLoginRequest)
    AuthSvc->>Verifier: verify(idToken)
    alt Google Sign-In not configured (GOOGLE_CLIENT_ID blank)
        Verifier-->>Browser: 503 Service Unavailable
    else Token invalid or email not verified
        Verifier-->>Browser: 401 Unauthorized
    else Valid Google ID Token
        Verifier-->>AuthSvc: GoogleUser(sub, email, fullName)
        AuthSvc->>UserRepo: findByGoogleSub(sub)
        alt Match found by google_sub (Returning Google user)
            UserRepo-->>AuthSvc: Existing User
            AuthSvc->>JwtSvc: issueToken(user)
            AuthSvc-->>Browser: 200 OK AuthResponse(token, user)
        else No match by google_sub -> Check by email
            AuthSvc->>UserRepo: findByEmailIgnoreCase(email)
            alt Existing email account found (Account Linking)
                UserRepo-->>AuthSvc: Existing User
                AuthSvc->>AuthSvc: user.setGoogleSub(sub)
                AuthSvc->>JwtSvc: issueToken(user)
                AuthSvc-->>Browser: 200 OK AuthResponse(token, user)
            else Brand-new user & request.role is null (e.g. from LoginPage)
                AuthSvc-->>Browser: 428 Precondition Required\n("Please choose whether you're a Care Seeker or Caregiver...")
                Browser->>Browser: Render role selector (CARE_SEEKER / CAREGIVER) + optional phone input
                Browser->>AuthCtrl: Re-submit POST /api/auth/google { idToken, role, phone }
                AuthCtrl->>AuthSvc: loginWithGoogle(request)
                AuthSvc->>UserRepo: save(User.fromGoogle(email, sub, fullName, phone, role))
                AuthSvc->>JwtSvc: issueToken(newUser)
                AuthSvc-->>Browser: 200 OK AuthResponse(token, newUser)
            else Brand-new user & request.role is provided (e.g. from RegisterPage)
                AuthSvc->>UserRepo: save(User.fromGoogle(email, sub, fullName, phone, role))
                AuthSvc->>JwtSvc: issueToken(newUser)
                AuthSvc-->>Browser: 200 OK AuthResponse(token, newUser)
            end
        end
    end
```

---

## 3. Per-Request JWT Verification & RBAC Resolution

Implemented in [`JwtAuthFilter.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/auth/JwtAuthFilter.java) and [`SecurityConfig.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/config/SecurityConfig.java).

```mermaid
flowchart TD
    Req["Incoming HTTP Request"] --> CheckHeader{"Has Header\nAuthorization: Bearer <token>?"}
    CheckHeader -- "No" --> Anon["Continue FilterChain\n(Anonymous SecurityContext)"]
    CheckHeader -- "Yes" --> ParseJWT["jwtService.parse(token)\nVerify HMAC-SHA256 & exp"]

    ParseJWT -- "JwtException / Expired" --> ClearCtx["SecurityContextHolder.clearContext()"]
    ClearCtx --> Anon

    ParseJWT -- "Valid Claims (userId)" --> LookupUser["userRepository.findById(claims.userId)"]
    LookupUser -- "Not Found (User Deleted)" --> ClearCtx

    LookupUser -- "User Found" --> BuildAuth["Build Authorities:\n1. ROLE_<user.role> (ROLE_CAREGIVER or ROLE_CARE_SEEKER)\n2. If user.isAdmin() -> add ROLE_ADMIN"]
    BuildAuth --> SetCtx["SecurityContextHolder.setContext(\nUsernamePasswordAuthenticationToken(AuthenticatedUser, authorities))"]
    SetCtx --> SecRules{"SecurityConfig Matcher"}

    Anon --> SecRules

    SecRules -- "Public Endpoint\n(/api/auth/register|login|google, GET /api/caregivers/**)" --> Controller["Invoke Controller"]
    SecRules -- "/api/admin/** requires ROLE_ADMIN" --> CheckAdmin{"Has ROLE_ADMIN?"}
    SecRules -- "/api/profile/** requires ROLE_CAREGIVER" --> CheckCG{"Has ROLE_CAREGIVER?"}
    SecRules -- "POST /api/caregivers/*/reviews or /api/bookings/**\nrequires ROLE_CARE_SEEKER" --> CheckSeeker{"Has ROLE_CARE_SEEKER?"}
    SecRules -- "/api/auth/me requires authenticated()" --> CheckAuth{"Is Authenticated?"}

    CheckAdmin -- "Yes" --> Controller
    CheckAdmin -- "No (Authenticated)" --> F403["403 Forbidden"]
    CheckAdmin -- "No (Anonymous)" --> F401["401 Unauthorized"]

    CheckCG -- "Yes" --> Controller
    CheckCG -- "No" --> F403_401["401 / 403"]

    CheckSeeker -- "Yes" --> Controller
    CheckSeeker -- "No" --> F403_401

    CheckAuth -- "Yes" --> Controller
    CheckAuth -- "No" --> F401
```
