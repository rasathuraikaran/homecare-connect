# Getting Started

This guide walks you through starting HomeCare Connect locally in under five minutes, seeding sample demo data, and exploring all three user personas (**Care Seeker**, **Caregiver**, and **Admin**).

---

## 1. Quickstart (One Command)

Ensure you have Docker, Java 17+, and Node.js 18+ installed (see [Prerequisites](./prerequisites.md)), then run from the repository root:

```bash
./start-service.sh start --demo
```

This single command:
1. Validates all required CLI prerequisites (`docker`, `java`, `node`, `npm`, `curl`).
2. Loads `.env.local` if present and generates a persistent 48-byte base64 JWT secret in `.run/jwt.secret` if `JWT_SECRET` is not set.
3. Starts the `postgres:16-alpine` container (`homecare-postgres` on port `5432`) via Docker Compose and waits for `pg_isready`.
4. Starts the Spring Boot backend (`:8080`), automatically applying Flyway migrations `V1`–`V11`.
5. Starts the Vite React development server (`:5173`).
6. Seeds 5 demo caregivers (`CARETAKER`, `NURSING`, `PHYSIOTHERAPIST` with sample SVG portraits and rate tiers) and 1 demo care seeker with sample reviews.

---

## 2. Accessing the Application

Once `./start-service.sh start --demo` completes, open:

- **Frontend Web App**: http://localhost:5173
- **Backend REST API**: http://localhost:8080/api/caregivers

---

## 3. Demo Accounts & Credentials

All accounts created by `./start-service.sh start --demo` (or `./start-service.sh seed-demo`) share the password:

- **Password**: `demo-pass-123`

| Email | Full Name | Role | Job Type | Sample Rates |
|---|---|---|---|---|
| `nina@demo.local` | Nina Perera | `CAREGIVER` | `NURSING` | Hourly \$28, Session \$95, Daily \$180, Full Day & Night \$260 |
| `meena@demo.local` | Meena Fernando | `CAREGIVER` | `CARETAKER` | Hourly \$18, Daily \$120, Full Day & Night \$190 |
| `ravi@demo.local` | Ravi Jayasinghe | `CAREGIVER` | `PHYSIOTHERAPIST` | Hourly \$35, Session \$110 |
| `sara@demo.local` | Sara Wickrama | `CAREGIVER` | `NURSING` | Hourly \$24, Session \$80, Daily \$155 |
| `dev@demo.local` | Dev Silva | `CAREGIVER` | `CARETAKER` | Hourly \$16, Daily \$105, Full Day & Night \$170 |
| `seeker@demo.local` | Priya Subramaniam | `CARE_SEEKER` | — | Authors 3 sample reviews |

---

## 4. Promoting an Account to Admin

By default, new registrations and demo accounts have `is_admin = false`. To explore the **Admin Portal** (`/admin`, `/admin/verifications`, `/admin/users`), promote `seeker@demo.local` (or any registered email) using:

```bash
./start-service.sh make-admin seeker@demo.local
```

Then log in at http://localhost:5173/login as `seeker@demo.local` (`demo-pass-123`) and click **Admin** in the top navigation bar.

---

## 5. Stopping the Stack

```bash
./start-service.sh stop
```

To check the health of running services at any time:

```bash
./start-service.sh status
```

---

## 6. Next Steps
- [Prerequisites](./prerequisites.md) — Required runtime versions and OS utilities.
- [Local Setup & CLI Reference](./local-setup.md) — Full guide to `start-service.sh` subcommands, log inspection, and database resets.
- [Configuration & Environment Variables](./configuration.md) — Enabling Google Sign-In, SMTP Email, and Meta WhatsApp alerts locally.
