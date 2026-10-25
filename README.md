# HomeCare Connect

Marketplace where caregivers register and care seekers pick them. React + Spring Boot + PostgreSQL.

## Run
```bash
docker compose up -d                 # Postgres on :5434
cd backend && mvn spring-boot:run    # API on :8081
cd frontend && npm install && npm run dev   # UI on :5173 (proxies /api to :8081)
```

## Done so far
- Register / login for two roles: `CARE_SEEKER` and `CAREGIVER` (JWT, BCrypt, Flyway)
- `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me`

## Google sign-in
Create an OAuth **Web application** client in Google Cloud (origin `http://localhost:5173`), then set the same ID in both places:
```bash
export GOOGLE_CLIENT_ID=xxxx.apps.googleusercontent.com          # backend
echo 'VITE_GOOGLE_CLIENT_ID=xxxx.apps.googleusercontent.com' > frontend/.env.local   # frontend
```
Without it the button shows as "not configured" and `/api/auth/google` returns 503.
