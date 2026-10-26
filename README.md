# HomeCare Connect

Marketplace where caregivers (caretakers, nurses, physiotherapists) register a profile and care seekers find and pick them. React + Spring Boot + PostgreSQL.

## Quick start (one command)

You need **Docker** (running), **Java 17+** and **Node 18+**. On Windows use WSL or Git Bash.

```bash
git clone https://github.com/rasathuraikaran/homecare-connect.git
cd homecare-connect
./start-service.sh start --demo
```

Then open **http://localhost:5173**. `--demo` adds a few demo caregivers and a demo care seeker so there is something to look at (the script prints their logins).

The first run downloads Maven and the project's dependencies, so it takes a few minutes. After that it starts in about 20 seconds.

| Command | What it does |
|---|---|
| `./start-service.sh` | Start the database, backend and frontend |
| `./start-service.sh stop` | Stop everything (data is kept) |
| `./start-service.sh status` | Show what is running |
| `./start-service.sh logs [db\|backend\|frontend]` | Follow a log (files are also in `.run/`) |
| `./start-service.sh seed-demo` | Add demo accounts to a running app |
| `./start-service.sh reset-db` | Delete all data and start empty (asks first) |

If a port is taken, choose another: `BACKEND_PORT=8082 ./start-service.sh` (also `DB_PORT`, `FRONTEND_PORT`).
Demo accounts use the password `demo-pass-123`; change it with `DEMO_PASSWORD`. They are for your own computer only.

### Running the pieces by hand

```bash
docker compose up -d                          # Postgres on :5434
cd backend && ./mvnw spring-boot:run          # API on :8081
cd frontend && npm install && npm run dev     # UI on :5173 (proxies /api to :8081)
```

## What it does

- Two kinds of account: **care seeker** (light blue pages) and **caregiver** (green pages), with email and password or Google sign-in.
- **Caregivers** build a profile (job, photo, experience, qualifications, charges per hour / session / day / full day and night), see how complete it is, switch availability on or off, and read their reviews.
- **Care seekers** pick Caretaker, Nurse or Physiotherapist, browse caregiver cards with prices and ratings, and leave a review.

## Google sign-in (optional)

Without it, the "Continue with Google" button is disabled and email sign-up still works.

1. In Google Cloud create an OAuth client of type **Web application** and add `http://localhost:5173` as an authorised JavaScript origin.
2. Give the script the client ID, either way:
   ```bash
   GOOGLE_CLIENT_ID=xxxx.apps.googleusercontent.com ./start-service.sh
   # or save it once for the frontend (this file is git-ignored; the script reads it too)
   echo 'VITE_GOOGLE_CLIENT_ID=xxxx.apps.googleusercontent.com' > frontend/.env.local
   ```

While the Google consent screen is in "Testing", only the Gmail addresses listed as test users can sign in.

## Before putting this on a real server

The defaults are for development only: the database password is `homecare`, and the script makes a random login secret for your computer. On a server set `JWT_SECRET`, `DB_PASSWORD` and `CORS_ORIGIN` yourself, use HTTPS, and do not use the demo accounts.
