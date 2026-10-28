#!/usr/bin/env bash
#
# start-service.sh - run all of HomeCare Connect on this computer.
#
#   ./start-service.sh                start the database, backend and frontend
#   ./start-service.sh start --demo   same, then add a few demo caregivers and a demo care seeker
#   ./start-service.sh stop           stop everything (your data is kept)
#   ./start-service.sh restart
#   ./start-service.sh status
#   ./start-service.sh logs [db|backend|frontend]
#   ./start-service.sh seed-demo      add demo accounts to a running app
#   ./start-service.sh make-admin you@example.com   let that account open the Admin page (add --remove to undo)
#   ./start-service.sh reset-db       DELETE all data and begin with an empty database
#
# Needs: Docker (running), Java 17+, Node 18+. Maven is downloaded for you (backend/mvnw).
# Optional: GOOGLE_CLIENT_ID=... enables "Continue with Google".
# Optional: put secrets in a file named .env.local next to this script (it is git-ignored), for example
# the EMAIL_* settings that switch on real email alerts to the admin (or WHATSAPP_* for WhatsApp). See README.md.
# Ports can be changed with DB_PORT, BACKEND_PORT and FRONTEND_PORT.
# Works on macOS and Linux. On Windows, run it inside WSL or Git Bash.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT/backend"
FRONTEND_DIR="$ROOT/frontend"
RUN_DIR="$ROOT/.run"


if [ -t 1 ]; then
  BOLD=$'\033[1m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; RED=$'\033[31m'; DIM=$'\033[2m'; RESET=$'\033[0m'
else
  BOLD=""; GREEN=""; YELLOW=""; RED=""; DIM=""; RESET=""
fi
say()  { printf '%s\n' "${BOLD}$*${RESET}"; }
ok()   { printf '%s\n' "${GREEN}✔${RESET} $*"; }
warn() { printf '%s\n' "${YELLOW}!${RESET} $*"; }
die()  { printf '%s\n' "${RED}✖ $*${RESET}" >&2; exit 1; }

# Reads KEY=value lines from a file WITHOUT running it as shell code, so spaces, $ or quotes in a value
# cannot break or hijack the script. A setting already present in the environment wins over the file.
load_env_file() {
  local file="$1" line key value
  [ -f "$file" ] || return 0
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"
    line="${line#"${line%%[![:space:]]*}"}"
    case "$line" in ''|'#'*) continue ;; esac
    case "$line" in *=*) ;; *) warn "Ignored a line in $(basename "$file") that is not KEY=value"; continue ;; esac
    key="${line%%=*}"
    value="${line#*=}"
    if ! [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then warn "Ignored an invalid setting name in $(basename "$file")"; continue; fi
    case "$value" in
      \"*\") value="${value#\"}"; value="${value%\"}" ;;
      \'*\') value="${value#\'}"; value="${value%\'}" ;;
    esac
    if [ -z "${!key+x}" ]; then export "$key=$value"; fi
  done <"$file"
}

load_env_file "$ROOT/.env.local"

export DB_PORT="${DB_PORT:-5434}"
BACKEND_PORT="${BACKEND_PORT:-8081}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
DB_CONTAINER="homecare-db"
DEMO_PASSWORD="${DEMO_PASSWORD:-demo-pass-123}"

mkdir -p "$RUN_DIR"

# ---------------------------------------------------------------- helpers

compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose -f "$ROOT/docker-compose.yml" "$@"
  else
    docker-compose -f "$ROOT/docker-compose.yml" "$@"
  fi
}

need() { command -v "$1" >/dev/null 2>&1 || die "$1 is not installed. $2"; }

check_prereqs() {
  need docker "Install Docker Desktop: https://docs.docker.com/get-docker/"
  docker info >/dev/null 2>&1 || die "Docker is installed but not running. Start Docker Desktop, then try again."
  docker compose version >/dev/null 2>&1 || command -v docker-compose >/dev/null 2>&1 \
    || die "Docker Compose is missing. Update Docker Desktop."
  need java "Install Java 17 or newer: https://adoptium.net"
  need node "Install Node 18 or newer: https://nodejs.org"
  need npm "It comes with Node: https://nodejs.org"
  need curl "Install curl with your package manager."

  local jv jmajor nv nmajor
  jv=$(java -version 2>&1 | awk -F'"' '/version/ {print $2; exit}')
  jmajor=${jv%%.*}
  [ "$jmajor" = "1" ] && jmajor=$(printf '%s' "$jv" | cut -d. -f2)
  [ "${jmajor:-0}" -ge 17 ] 2>/dev/null || die "Java 17 or newer is required (found ${jv:-unknown})."

  nv=$(node -v); nmajor=${nv#v}; nmajor=${nmajor%%.*}
  [ "${nmajor:-0}" -ge 18 ] 2>/dev/null || die "Node 18 or newer is required (found $nv)."

  [ -x "$BACKEND_DIR/mvnw" ] || die "backend/mvnw is missing or not executable. Run: chmod +x backend/mvnw"
}

port_open() {
  if command -v nc >/dev/null 2>&1; then
    nc -z -w 1 127.0.0.1 "$1" >/dev/null 2>&1 || nc -z -w 1 ::1 "$1" >/dev/null 2>&1
  else
    (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null
  fi
}

who_uses() {
  command -v lsof >/dev/null 2>&1 || return 0
  lsof -nP -iTCP:"$1" -sTCP:LISTEN 2>/dev/null | awk 'NR==2 {print $1 " (PID " $2 ")"}' || true
}

require_free_port() { # label port ENV_VAR
  if port_open "$2"; then
    local who; who=$(who_uses "$2")
    die "Port $2 ($1) is already in use${who:+ by $who}. Stop that program, or pick another port: $3=<port> ./start-service.sh"
  fi
}

pid_file() { printf '%s' "$RUN_DIR/$1.pid"; }

# A pid counts as ours only if its command line still looks right (guards against a reused pid)
marker_for() { case "$1" in backend) printf 'homecare-backend' ;; frontend) printf 'vite' ;; esac; }

is_up() {
  local f pid
  f=$(pid_file "$1")
  [ -f "$f" ] || return 1
  pid=$(cat "$f")
  kill -0 "$pid" 2>/dev/null || return 1
  ps -p "$pid" -o command= 2>/dev/null | grep -q "$(marker_for "$1")"
}

stop_proc() {
  local f pid i
  f=$(pid_file "$1")
  [ -f "$f" ] || return 0
  if is_up "$1"; then
    pid=$(cat "$f")
    kill "$pid" 2>/dev/null || true
    for i in 1 2 3 4 5 6 7 8 9 10; do
      kill -0 "$pid" 2>/dev/null || break
      sleep 1
    done
    if kill -0 "$pid" 2>/dev/null; then kill -9 "$pid" 2>/dev/null || true; fi
  fi
  rm -f "$f"
}

# Show the actual reason a program stopped, not a wall of stack trace
explain_failure() {
  local log="$1" reason
  reason=$(grep -E 'IllegalStateException:|^Description:|^Action:|Port [0-9]+ was already in use|Address already in use' "$log" 2>/dev/null | tail -n 3 | cut -c1-300 || true)
  if [ -n "$reason" ]; then printf '%s\n' "$reason" | sed 's/^/  /'; else tail -n 15 "$log" 2>/dev/null || true; fi
}

wait_http() { # url name seconds
  local i code
  for i in $(seq 1 "$3"); do
    if ! is_up "$2"; then
      explain_failure "$RUN_DIR/$2.log"
      die "The $2 stopped unexpectedly. Full log: .run/$2.log"
    fi
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 "$1" || true)
    if [ -n "$code" ] && [ "$code" != "000" ]; then return 0; fi
    sleep 1
  done
  die "The $2 did not answer within $3 seconds. See: ./start-service.sh logs $2"
}

# A random secret for this computer, created once and reused so logins survive restarts
jwt_secret() {
  local f="$RUN_DIR/jwt.secret"
  if [ ! -s "$f" ]; then
    (umask 077; head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n' >"$f")
  fi
  cat "$f"
}

google_client_id() {
  local f="$FRONTEND_DIR/.env.local"
  if [ -n "${GOOGLE_CLIENT_ID:-}" ]; then printf '%s' "$GOOGLE_CLIENT_ID"; return; fi
  if [ -f "$f" ]; then sed -n 's/^VITE_GOOGLE_CLIENT_ID=//p' "$f" | tail -n 1 | tr -d '" \r'; fi
}

# ---------------------------------------------------------------- start

start_db() {
  say "Starting the database (Docker)…"
  if ! docker ps --format '{{.Names}}' | grep -qx "$DB_CONTAINER"; then
    require_free_port "database" "$DB_PORT" DB_PORT
  fi
  compose up -d db >"$RUN_DIR/db.log" 2>&1 || { cat "$RUN_DIR/db.log"; die "Could not start the database container."; }

  local i status
  for i in $(seq 1 60); do
    status=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$DB_CONTAINER" 2>/dev/null || echo missing)
    if [ "$status" = "healthy" ]; then ok "Database ready (port $DB_PORT)"; return 0; fi
    sleep 1
  done
  die "The database did not become ready. See: ./start-service.sh logs db"
}

start_backend() {
  if is_up backend; then ok "Backend already running"; return 0; fi
  require_free_port "backend" "$BACKEND_PORT" BACKEND_PORT

  say "Building the backend (the first run downloads dependencies and can take a few minutes)…"
  if ! (cd "$BACKEND_DIR" && ./mvnw -q -B -DskipTests package) >"$RUN_DIR/backend-build.log" 2>&1; then
    tail -n 30 "$RUN_DIR/backend-build.log"
    die "The backend build failed. Full log: .run/backend-build.log"
  fi
  local jar
  jar=$(ls "$BACKEND_DIR"/target/*.jar 2>/dev/null | head -n 1 || true)
  [ -n "$jar" ] || die "Build finished but no jar was found in backend/target."

  say "Starting the backend…"
  local gid; gid=$(google_client_id)
  (
    cd "$BACKEND_DIR"
    export DB_URL="jdbc:postgresql://localhost:$DB_PORT/homecare"
    export SERVER_PORT="$BACKEND_PORT"
    export CORS_ORIGIN="http://localhost:$FRONTEND_PORT"
    export JWT_SECRET; JWT_SECRET="$(jwt_secret)"
    export GOOGLE_CLIENT_ID="$gid"
    nohup java -jar "$jar" >"$RUN_DIR/backend.log" 2>&1 &
    echo $! >"$(pid_file backend)"
  )
  wait_http "http://localhost:$BACKEND_PORT/api/auth/me" backend 120
  ok "Backend ready (port $BACKEND_PORT)"
}

start_frontend() {
  if is_up frontend; then ok "Frontend already running"; return 0; fi
  require_free_port "frontend" "$FRONTEND_PORT" FRONTEND_PORT

  if [ ! -d "$FRONTEND_DIR/node_modules" ]; then
    say "Installing the frontend packages (first run only)…"
    if ! (cd "$FRONTEND_DIR" && npm ci --no-audit --no-fund) >"$RUN_DIR/frontend-install.log" 2>&1; then
      tail -n 30 "$RUN_DIR/frontend-install.log"
      die "npm install failed. Full log: .run/frontend-install.log"
    fi
  fi

  say "Starting the frontend…"
  local gid; gid=$(google_client_id)
  (
    cd "$FRONTEND_DIR"
    export BACKEND_PORT FRONTEND_PORT
    if [ -n "$gid" ]; then export VITE_GOOGLE_CLIENT_ID="$gid"; fi
    nohup node_modules/.bin/vite >"$RUN_DIR/frontend.log" 2>&1 &
    echo $! >"$(pid_file frontend)"
  )
  wait_http "http://localhost:$FRONTEND_PORT/" frontend 60
  ok "Frontend ready (port $FRONTEND_PORT)"
}

start_all() {
  trap 'echo; warn "Interrupted. Run ./start-service.sh stop to clean up."; exit 130' INT
  check_prereqs
  start_db
  start_backend
  start_frontend
  trap - INT

  echo
  say "HomeCare Connect is running"
  echo "  Open the app:  ${BOLD}http://localhost:$FRONTEND_PORT${RESET}"
  echo "  Backend API:   http://localhost:$BACKEND_PORT/api"
  echo "  Logs:          ./start-service.sh logs   ${DIM}(or look in .run/)${RESET}"
  echo "  Stop:          ./start-service.sh stop"
  echo
  local channel enabled
  channel="$(printf '%s' "${NOTIFY_CHANNEL:-email}" | tr '[:upper:]' '[:lower:]')"
  if [ "$channel" = "whatsapp" ]; then
    enabled="${WHATSAPP_ENABLED:-false}"
  else
    channel="email"
    enabled="${EMAIL_ENABLED:-false}"
  fi
  if [ "$enabled" = "true" ]; then
    echo "  Admin alerts:  ${BOLD}ON${RESET} ${DIM}(by $channel)${RESET}"
  else
    echo "  Admin alerts:  ${BOLD}$channel test mode${RESET} ${DIM}(messages are only written to .run/backend.log, nothing is sent)${RESET}"
  fi
  if [ -z "$(google_client_id)" ]; then
    warn "Google sign-in is off (no GOOGLE_CLIENT_ID). Email sign-up and login work normally."
  fi
}

# ---------------------------------------------------------------- other commands

stop_all() {
  stop_proc frontend
  stop_proc backend
  if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    compose stop db >/dev/null 2>&1 || true
  fi
  ok "Stopped. Your data is kept. Start again with ./start-service.sh"
}

status() {
  local db
  db=$(docker inspect -f '{{if .State.Running}}{{if .State.Health}}{{.State.Health.Status}}{{else}}running{{end}}{{else}}stopped{{end}}' "$DB_CONTAINER" 2>/dev/null || echo "not created")
  printf '  %-10s %s\n' "database" "$db (port $DB_PORT)"
  if is_up backend;  then printf '  %-10s %s\n' "backend"  "running  http://localhost:$BACKEND_PORT";  else printf '  %-10s %s\n' "backend"  "stopped"; fi
  if is_up frontend; then printf '  %-10s %s\n' "frontend" "running  http://localhost:$FRONTEND_PORT"; else printf '  %-10s %s\n' "frontend" "stopped"; fi
}

logs() {
  case "$1" in
    db)               docker logs -f --tail 50 "$DB_CONTAINER" ;;
    backend|frontend) [ -f "$RUN_DIR/$1.log" ] || die "No $1 log yet. Start the app first."; tail -n 50 -f "$RUN_DIR/$1.log" ;;
    *)                die "Usage: ./start-service.sh logs [db|backend|frontend]" ;;
  esac
}

reset_db() {
  check_prereqs
  warn "This DELETES everything in the local database: all users, profiles and reviews."
  printf 'Type "yes" to continue: '
  read -r answer
  [ "$answer" = "yes" ] || { say "Cancelled. Nothing was changed."; exit 0; }
  stop_proc frontend
  stop_proc backend
  compose down -v >/dev/null 2>&1 || die "Could not remove the database."
  ok "Database deleted. Run ./start-service.sh to begin with an empty one."
  echo "  (Uploaded photos in backend/uploads were not touched.)"
}

# Admin accounts can only be created here, never through the website, so nobody can sign up as admin.
make_admin() {
  local email="${1:-}" value="true" ids
  [ -n "$email" ] || die "Usage: ./start-service.sh make-admin you@example.com   (add --remove to undo)"
  if [ "${2:-}" = "--remove" ]; then value="false"; fi
  command -v docker >/dev/null 2>&1 || die "Docker is not installed."
  docker ps --format '{{.Names}}' | grep -qx "$DB_CONTAINER" || die "The database is not running. Start the app first: ./start-service.sh"
  # the email goes in as a psql variable, never pasted into the SQL text
  ids=$(printf '%s\n' "update users set is_admin = $value where lower(email) = lower(:'email') returning id;" \
    | docker exec -i "$DB_CONTAINER" psql -q -tA -U homecare -d homecare -v ON_ERROR_STOP=1 -v email="$email" || true)
  [ -n "$ids" ] || die "No account with the email $email. Register it in the app first, then run this again."
  if [ "$value" = "true" ]; then
    ok "$email is now an admin. Sign in with that account and open the Admin link at the top of the page."
  else
    ok "$email is no longer an admin."
  fi
}

# Demo accounts: handy for showing the app to someone. They only exist on this computer.
API="http://localhost:$BACKEND_PORT/api"

json_token() { sed -n 's/.*"token":"\([^"]*\)".*/\1/p'; }

token_for() { # email fullName role
  local tok
  tok=$(curl -s -X POST "$API/auth/register" -H 'Content-Type: application/json' \
    -d "{\"fullName\":\"$2\",\"email\":\"$1\",\"password\":\"$DEMO_PASSWORD\",\"role\":\"$3\"}" | json_token)
  if [ -z "$tok" ]; then   # already registered: log in instead
    tok=$(curl -s -X POST "$API/auth/login" -H 'Content-Type: application/json' \
      -d "{\"email\":\"$1\",\"password\":\"$DEMO_PASSWORD\"}" | json_token)
  fi
  printf '%s' "$tok"
}

add_caregiver() { # email fullName job years age qualification ratesJson
  local tok code
  tok=$(token_for "$1" "$2" CAREGIVER)
  [ -n "$tok" ] || { warn "Could not create $1 (is the password different from DEMO_PASSWORD?)"; return 0; }
  code=$(curl -s -o /dev/null -w '%{http_code}' -X PUT "$API/caregivers/me/profile" \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $tok" \
    -d "{\"fullName\":\"$2\",\"jobType\":\"$3\",\"experienceYears\":$4,\"age\":$5,\"qualification\":\"$6\",\"rates\":$7}")
  if [ "$code" = "200" ]; then ok "$2  ($1)"; else warn "Profile for $1 was not saved (HTTP $code)"; fi
}

seed_demo() {
  curl -s -o /dev/null --max-time 3 "$API/auth/me" || die "The backend is not running. Start the app first: ./start-service.sh"
  say "Adding demo accounts…"
  add_caregiver nina@demo.local  "Nina Fernando" NURSING          8  34 "Nursing diploma, First aid certified"        '{"HOURLY":1800,"DAILY":9000,"FULL_DAY_NIGHT":15000}'
  add_caregiver meena@demo.local "Meena Selvam"  NURSING          3  29 "Nursing diploma"                              '{"HOURLY":1500,"DAILY":7500}'
  add_caregiver ravi@demo.local  "Ravi Kumar"    CARETAKER        12 45 "Elderly care certificate, Dementia care"      '{"HOURLY":1200,"DAILY":6000,"FULL_DAY_NIGHT":12000}'
  add_caregiver sara@demo.local  "Sara Thomas"   CARETAKER        4  31 "First aid certified"                          '{"SESSION":2500,"DAILY":5500}'
  add_caregiver dev@demo.local   "Dev Anand"     PHYSIOTHERAPIST  6  38 "Physiotherapy assistant"                      '{"SESSION":3500,"HOURLY":2500}'
  # Families only see caregivers whose documents an admin approved. Demo caregivers have no documents, so mark them verified
  # straight in the database (only this script can do that, never the website or the API).
  echo "UPDATE caregiver_profiles SET verified = TRUE WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@demo.local');" \
    | docker exec -i "$DB_CONTAINER" psql -q -tA -U homecare -d homecare -v ON_ERROR_STOP=1 >/dev/null \
    || warn "Could not mark the demo caregivers as verified, so families will not see them."
  local seeker; seeker=$(token_for seeker@demo.local "Demo Seeker" CARE_SEEKER)
  if [ -n "$seeker" ]; then ok "Demo Seeker  (seeker@demo.local)"; else warn "Could not create the demo care seeker."; fi
  echo
  echo "  Log in as a care seeker:  seeker@demo.local   password: $DEMO_PASSWORD"
  echo "  Log in as a caregiver:    nina@demo.local     password: $DEMO_PASSWORD"
}

usage() {
  awk 'NR < 3 { next } /^#/ { sub(/^# ?/, ""); print; next } { exit }' "$0"
}

# ---------------------------------------------------------------- main

cmd="${1:-start}"
case "$cmd" in
  start)      start_all; if [ "${2:-}" = "--demo" ]; then echo; seed_demo; fi ;;
  stop)       stop_all ;;
  restart)    stop_all; start_all ;;
  status)     status ;;
  logs)       logs "${2:-backend}" ;;
  seed-demo)  seed_demo ;;
  make-admin) make_admin "${2:-}" "${3:-}" ;;
  reset-db)   reset_db ;;
  -h|--help|help) usage ;;
  *)          usage; exit 1 ;;
esac
