# Frontend Architecture (`frontend/src/`)

The HomeCare Connect frontend is a responsive React 18 Single-Page Application (SPA) built with Vite 5 and React Router v6.

---

## 1. Directory Structure

```text
frontend/src/
├── main.jsx                       # React root mount (<BrowserRouter> + <AuthProvider>)
├── App.jsx                        # Route table, route guards, and useRoleTheme hook
├── styles.css                     # Global stylesheet with Caregiver (teal) & Seeker (amber) themes
├── api/
│   └── client.js                  # Central fetch wrapper (api, apiBlob, error parsing)
├── auth/
│   └── AuthContext.jsx            # Auth state provider (localStorage hc_token & hc_user)
├── constants/                     # Enum definitions synchronized with backend Java enums
│   ├── bookingStatus.js
│   ├── documentTypes.js
│   ├── jobTypes.js
│   ├── notifications.js
│   ├── rateTypes.js
│   └── storageKeys.js
├── components/                    # Reusable UI & domain components (19 files)
│   ├── AdminBookingCard.jsx
│   ├── AdminBookingContactRow.jsx
│   ├── AdminBookingFilters.jsx
│   ├── AdminCaregiverModal.jsx
│   ├── AdminNotificationList.jsx
│   ├── AdminTabs.jsx
│   ├── AvailabilityToggle.jsx
│   ├── BookingRequestModal.jsx
│   ├── CaregiverCard.jsx
│   ├── Footer.jsx
│   ├── GoogleRolePrompt.jsx
│   ├── GoogleSignInButton.jsx
│   ├── Navbar.jsx
│   ├── NotificationBadge.jsx
│   ├── PhotoPicker.jsx
│   ├── RateCardGrid.jsx
│   ├── ReviewSection.jsx
│   ├── StarRating.jsx
│   └── VerificationSection.jsx
└── pages/                         # Route-level page views (12 files)
    ├── AccountPage.jsx
    ├── AdminPage.jsx
    ├── AdminUsersPage.jsx
    ├── AdminVerificationPage.jsx
    ├── CaregiverDetailPage.jsx
    ├── LandingPage.jsx
    ├── LoginPage.jsx
    ├── MyBookingsPage.jsx
    ├── PrivacyPage.jsx
    ├── ProfilePage.jsx
    ├── RegisterPage.jsx
    └── TermsPage.jsx
```

---

## 2. Routing & Route Guards (`src/App.jsx`)

Defined in [`App.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/App.jsx#L19-L68):

| Route Path | Page Component | Guard Component | Access Rule |
|---|---|---|---|
| `/` | [`LandingPage.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/pages/LandingPage.jsx) | None | Public (renders caregiver dashboard banner if logged in as `CAREGIVER`) |
| `/caregivers/:id` | [`CaregiverDetailPage.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/pages/CaregiverDetailPage.jsx) | None | Public |
| `/terms` | [`TermsPage.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/pages/TermsPage.jsx) | None | Public |
| `/privacy` | [`PrivacyPage.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/pages/PrivacyPage.jsx) | None | Public |
| `/login` | [`LoginPage.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/pages/LoginPage.jsx) | `GuestOnly` | Unauthenticated only (redirects `/profile` for Caregiver or `/` for Seeker) |
| `/register` | [`RegisterPage.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/pages/RegisterPage.jsx) | `GuestOnly` | Unauthenticated only |
| `/bookings` | [`MyBookingsPage.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/pages/MyBookingsPage.jsx) | `RequireRole('CARE_SEEKER')` | Authenticated `CARE_SEEKER` only |
| `/profile` | [`ProfilePage.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/pages/ProfilePage.jsx) | `RequireRole('CAREGIVER')` | Authenticated `CAREGIVER` only |
| `/account` | [`AccountPage.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/pages/AccountPage.jsx) | `RequireAuth` | Any authenticated user |
| `/admin` | [`AdminPage.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/pages/AdminPage.jsx) | `RequireAdmin` | Authenticated user with `user.admin === true` |
| `/admin/verifications` | [`AdminVerificationPage.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/pages/AdminVerificationPage.jsx) | `RequireAdmin` | Authenticated user with `user.admin === true` |
| `/admin/users` | [`AdminUsersPage.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/pages/AdminUsersPage.jsx) | `RequireAdmin` | Authenticated user with `user.admin === true` |
| `*` | `Navigate to="/"` | None | Catch-all redirect to `/` |

---

## 3. Authentication Context (`src/auth/AuthContext.jsx`)

[`AuthContext.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/auth/AuthContext.jsx) manages user session persistence and background validation:
1. **Initial Hydration**: Reads `hc_token` and `hc_user` from `localStorage` synchronously during initial state creation so route guards do not flicker on page reload.
2. **Background Token Verification**: If `hc_token` exists on mount, an effect calls `GET /api/auth/me`:
   - If valid (`200 OK`), updates `user` and `localStorage['hc_user']` (picking up any server-side changes such as `admin: true`).
   - If invalid or expired (`401`), clears `hc_token` and `hc_user` and logs the user out.
3. **Exposed Methods**: `user`, `loading`, `login(email, password)`, `register(payload)`, `loginWithGoogle(idToken, role, phone)`, `logout()`, and `refreshMe()`.

---

## 4. API Client & Mixed-Content Protection (`src/api/client.js`)

[`client.js`](file:///home/sivaram/Github/homecare-connect/frontend/src/api/client.js) provides two core functions:
1. **`api(path, options)`**: Sends JSON or `FormData` requests with `Authorization: Bearer <token>`, parses JSON responses (returning `null` on `204 No Content`), and throws structured `Error` objects (`err.status`, `err.errors`) on failure.
2. **`apiBlob(path)`**: Fetches binary files (such as caregiver verification documents at `/api/profile/me/documents/{type}/file` or `/api/admin/verifications/documents/{id}/file`) using the `Authorization: Bearer <token>` header and returns a `Blob` that can be opened via `URL.createObjectURL(blob)` in a new browser tab.
3. **HTTPS Mixed-Content Guard**: [`resolveApiBase()`](file:///home/sivaram/Github/homecare-connect/frontend/src/api/client.js#L1-L14) inspects `import.meta.env.VITE_API_BASE_URL`. If a build accidentally baked an `http://...` API URL into an app served over `https://` (e.g. CloudFront), `resolveApiBase()` logs a console warning and falls back to same-origin `''` so `/api/*` requests route cleanly through CloudFront over HTTPS.

---

## 5. Polling & Debouncing Patterns in the Admin Portal

- **30-Second Auto-Refresh Polling**:
  - [`AdminPage.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/pages/AdminPage.jsx#L47-L50) polls `/api/admin/bookings` and `/api/admin/bookings/counts` every `30,000 ms` so new booking requests and outbox notification status transitions (`PENDING` -> `SENT`/`FAILED`) appear automatically.
  - [`AdminVerificationPage.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/pages/AdminVerificationPage.jsx#L40-L43) and [`AdminTabs.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/components/AdminTabs.jsx#L19-L26) poll `/api/admin/verifications` and `/api/admin/verifications/counts` every `30,000 ms` to keep the pending verification badge up to date.
- **300ms Debounced Search**:
  - [`AdminUsersPage.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/pages/AdminUsersPage.jsx#L38-L41) debounces free-text search input by `300 ms` before calling `GET /api/admin/users?role=...&q=...`.
