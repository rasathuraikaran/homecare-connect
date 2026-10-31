# Prerequisites

Before running or developing HomeCare Connect locally, verify that the following tools and runtimes are installed on your workstation.

---

## 1. Required Software & Versions

| Tool | Required Version | Verified By `start-service.sh` | Purpose |
|---|---|---|---|
| **Docker Engine** | 20.10+ | `docker info` | Runs the local PostgreSQL 16 database container (`homecare-postgres`). |
| **Docker Compose** | v2 (`docker compose`) or v1 (`docker-compose`) | `compose_cmd()` in [`start-service.sh`](file:///home/sivaram/Github/homecare-connect/start-service.sh#L65-L72) | Orchestrates the PostgreSQL container and persistent `pgdata` volume defined in [`docker-compose.yml`](file:///home/sivaram/Github/homecare-connect/docker-compose.yml). |
| **Java JDK** | **Java 17+** | `java -version` (parses major version >= 17) | Compiles and runs the Spring Boot 3.3.5 backend (`backend/pom.xml` sets `<java.version>17</java.version>`). |
| **Maven Wrapper** | Included (`backend/mvnw`) | Uses `./mvnw` if executable, falls back to system `mvn` | Builds the backend and runs the JUnit 5 test suite. No global Maven installation is required. |
| **Node.js** | **Node 18+** | `node -v` (parses major version >= 18) | Runs the Vite 5 build tool and React 18 development server. |
| **npm** | 9+ (bundled with Node 18+) | `command -v npm` | Installs frontend packages (`frontend/package-lock.json`). |
| **curl** | Any standard version | `command -v curl` | Used by `start-service.sh` for readiness checks (`/api/caregivers`, `:5173`) and seeding demo data via REST API calls. |
| **nc (netcat) or lsof** | Optional (Recommended) | `port_in_use()` in [`start-service.sh`](file:///home/sivaram/Github/homecare-connect/start-service.sh#L156-L163) | Detects port conflicts on `5432`, `8080`, and `5173` before starting services and waits for ports to free on shutdown. |
| **openssl** | Optional (Recommended) | `ensure_jwt_secret()` in [`start-service.sh`](file:///home/sivaram/Github/homecare-connect/start-service.sh#L140-L154) | Generates a cryptographically random 48-byte base64 JWT secret in `.run/jwt.secret` (falls back to `/dev/urandom` if `openssl` is absent). |

---

## 2. How `start-service.sh` Validates Prerequisites

When you run `./start-service.sh start`, the [`check_prereqs()`](file:///home/sivaram/Github/homecare-connect/start-service.sh#L95-L138) function automatically checks all required tools before launching any process:

- If a required command (`docker`, `java`, `node`, `npm`, `curl`) is missing, the script prints every missing dependency and exits with code `1`.
- If the Docker daemon is not running (`docker info` fails), it exits immediately with instructions to start Docker Desktop or the `docker` systemd service.
- If Java is older than 17 or Node.js is older than 18, it reports the detected version and exits cleanly.

---

## 3. Default Network Ports

Ensure the following local TCP ports are free (or override them via environment variables as documented in [Local Setup](./local-setup.md)):

| Service | Default Port | Override Environment Variable |
|---|---|---|
| PostgreSQL 16 (`homecare-postgres`) | `5432` | `DB_PORT` |
| Spring Boot Backend API | `8080` | `BACKEND_PORT` |
| Vite React Frontend Dev Server | `5173` | `FRONTEND_PORT` |
