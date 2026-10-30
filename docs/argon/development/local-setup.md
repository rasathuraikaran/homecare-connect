# Local Setup & CLI Reference

HomeCare Connect includes a comprehensive orchestration script ([`start-service.sh`](file:///home/sivaram/Github/homecare-connect/start-service.sh)) that manages the full local development lifecycle: PostgreSQL container, Spring Boot backend, Vite frontend, demo data seeding, admin privileges, and failure diagnostics.

---

## 1. `start-service.sh` Command Reference

Run `./start-service.sh <command>` from the repository root (`/home/sivaram/Github/homecare-connect`):

| Command | Description |
|---|---|
| `./start-service.sh start` | Validates prerequisites, starts PostgreSQL (`:5432`), starts Spring Boot (`:8080`), and starts Vite (`:5173`). |
| `./start-service.sh start --demo` | Runs `start` and then seeds 5 demo caregivers (with SVG portraits and rate tiers), 1 demo care seeker, and 3 sample reviews. |
| `./start-service.sh stop` | Gracefully stops Vite (`SIGTERM` -> `SIGKILL`), stops Spring Boot (along with child Maven/Java processes), and stops the `homecare-postgres` container. |
| `./start-service.sh restart` | Runs `stop` followed by `start`. |
| `./start-service.sh status` | Displays PID, port, and live health probes (`pg_isready`, `GET /api/caregivers`, `GET http://localhost:5173`) for all three tiers. |
| `./start-service.sh logs` | Tails both `.run/backend.log` and `.run/frontend.log`. |
| `./start-service.sh logs backend` | Tails `.run/backend.log` only. |
| `./start-service.sh logs frontend` | Tails `.run/frontend.log` only. |
| `./start-service.sh logs db` | Runs `docker compose logs -f postgres`. |
| `./start-service.sh seed-demo` | Seeds demo data into an already-running stack (idempotent: skips registration if `nina@demo.local` etc. already exist). |
| `./start-service.sh make-admin <email>` | Grants `is_admin = TRUE` to `<email>` in PostgreSQL. Append `--revoke` to set `is_admin = FALSE`. |
| `./start-service.sh reset-db` | Stops the stack, runs `docker compose down -v` to wipe the `pgdata` volume, and deletes all local files in `uploads/photos/`. |

---

## 2. Runtime Artifacts Directory (`.run/`)

When started via `./start-service.sh`, runtime metadata and logs are written to `.run/` (git-ignored):

| File Path | Purpose |
|---|---|
| `.run/jwt.secret` | Auto-generated 48-byte base64 HMAC-SHA256 key (`chmod 600`) used when `JWT_SECRET` is not set in the environment. Persists across restarts so active browser sessions remain valid. |
| `.run/backend.pid` | PID of the background `./mvnw spring-boot:run` process. |
| `.run/backend.log` | Combined `stdout`/`stderr` log of the Spring Boot backend. |
| `.run/frontend.pid` | PID of the background `npm run dev` process. |
| `.run/frontend.log` | Combined `stdout`/`stderr` log of the Vite dev server. |

---

## 3. Overriding Default Ports

If ports `5432`, `8080`, or `5173` are occupied on your machine, you can override them inline or inside `.env.local`:

```bash
DB_PORT=5433 BACKEND_PORT=8081 FRONTEND_PORT=5174 ./start-service.sh start
```

> [!NOTE]
> If you change `BACKEND_PORT` away from `8080`, also update the `/api` proxy target in [`frontend/vite.config.js`](file:///home/sivaram/Github/homecare-connect/frontend/vite.config.js#L8-L11) or set `VITE_API_BASE_URL=http://localhost:<BACKEND_PORT>` in `frontend/.env.local`.

---

## 4. Running Services Manually (Without `start-service.sh`)

If you prefer to run the backend in an IDE (e.g., IntelliJ IDEA or VS Code) and the frontend in a separate terminal:

### Step 1: Start PostgreSQL 16
```bash
docker compose up -d postgres
```

### Step 2: Run the Spring Boot Backend
```bash
cd backend
export JWT_SECRET="$(openssl rand -base64 48)"
./mvnw spring-boot:run
```

### Step 3: Run the Vite Frontend
```bash
cd frontend
npm install
npm run dev
```

---

## 5. Resetting Your Local Environment

To completely wipe the database and uploaded photos:

```bash
./start-service.sh reset-db
rm -rf uploads/documents/*
```

> [!TIP]
> Notice that `./start-service.sh reset-db` clears `uploads/photos/` ([`start-service.sh` line 647](file:///home/sivaram/Github/homecare-connect/start-service.sh#L647)). Running `rm -rf uploads/documents/*` alongside it ensures any local caregiver verification documents uploaded during testing are also removed.
