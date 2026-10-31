# Docker & Container Configuration

HomeCare Connect uses Docker Compose ([`docker-compose.yml`](file:///home/sivaram/Github/homecare-connect/docker-compose.yml)) to run a local PostgreSQL 16 instance for development and testing.

---

## 1. `docker-compose.yml` Specification

Source: [`/home/sivaram/Github/homecare-connect/docker-compose.yml`](file:///home/sivaram/Github/homecare-connect/docker-compose.yml)

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: homecare-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-homecare}
      POSTGRES_USER: ${POSTGRES_USER:-homecare}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-homecare}
    ports:
      - "${DB_PORT:-5432}:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-homecare} -d ${POSTGRES_DB:-homecare}"]
      interval: 5s
      timeout: 5s
      retries: 10

volumes:
  pgdata:
```

---

## 2. Configuration Details

| Setting | Default | Description |
|---|---|---|
| **Image** | `postgres:16-alpine` | Lightweight Alpine Linux image running PostgreSQL 16. |
| **Container Name** | `homecare-postgres` | Referenced by [`start-service.sh`](file:///home/sivaram/Github/homecare-connect/start-service.sh) for `pg_isready` checks and `psql` admin commands. |
| **Host Port Mapping** | `${DB_PORT:-5432}:5432` | Maps host port `5432` (or custom `DB_PORT`) to container port `5432`. |
| **Named Volume** | `pgdata` | Persists `/var/lib/postgresql/data` across container restarts (`./start-service.sh stop` / `start`). Only removed when running `./start-service.sh reset-db` (`docker compose down -v`). |
| **Healthcheck** | `pg_isready -U homecare -d homecare` | Runs every 5s (`retries: 10`). [`start-service.sh start_db()`](file:///home/sivaram/Github/homecare-connect/start-service.sh#L230-L250) polls `pg_isready` inside the container for up to 30 seconds before starting the Spring Boot backend. |

---

## 3. Useful Docker Commands

### Inspect Database Logs
```bash
./start-service.sh logs db
# Or directly:
docker compose logs -f postgres
```

### Open an Interactive `psql` Shell
```bash
docker compose exec postgres psql -U homecare -d homecare
```

### Inspect Tables & Migration History Inside `psql`
```sql
-- List all 8 application tables + flyway_schema_history
\dt

-- Check applied Flyway migrations (V1 through V11)
SELECT installed_rank, version, description, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;
```

### Wipe Database Volume & Re-initialize
```bash
./start-service.sh reset-db
```
