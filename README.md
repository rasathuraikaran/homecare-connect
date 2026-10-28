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
| `./start-service.sh make-admin you@example.com` | Let that account open the Admin page (add `--remove` to undo) |
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
- **Care seekers** pick Caretaker, Nurse or Physiotherapist, browse caregiver cards with prices and ratings, leave a review, and **request** a caregiver.
- **Admin** gets an email for every request (or a WhatsApp message, if you prefer) and follows it up on the Admin page (see below).

## Booking requests and alerts for the admin

1. A care seeker presses **Request** on a caregiver, adds a phone number (and optionally a charge and a note), and confirms. Nothing is charged.
2. The request is saved with status *Requested*, and an alert is queued for the admin.
3. Within a few seconds the admin gets one email with everything needed to follow up: the request number, the seeker's name, phone and email, the caregiver and their job, the charge and the note. For example, with the subject `New booking request #12: Sam Seeker asked for Nina Fernando`:

   ```text
   New booking request #12

   Care seeker: Sam Seeker
   Phone: +94 77 123 4567
   Email: sam@example.com

   Caregiver: Nina Fernando (Nursing)
   Charge: LKR 9,000 per day
   Note: Mornings only, please call after 6pm

   Please call the care seeker, then update the request on the HomeCare Connect admin page.

   Admin page: http://localhost:5173/admin
   ```

4. The admin calls the seeker, arranges it with the caregiver, and marks the request *Confirmed*, *Completed* or *Cancelled*.

Because the alert carries the seeker's phone, email and note, send it only to a mailbox you control. (By WhatsApp it also passes through Meta's servers and stays in the admin's chat history.)

The request is saved first and the alert is sent in the background, so a request is never lost when the mail server (or WhatsApp) is slow or down. Failed alerts are retried up to 5 times; the Admin page shows whether each alert was sent or failed, with the reason, and a **Send alert again** button.

**Test mode (the default).** No mail account is needed. The whole email, filled in as in the example above, is only written to `.run/backend.log` (keep that file private, it holds the seeker's phone and email), and the Admin page says "Email alert not sent (test mode)". Use this to try everything first.

**Becoming an admin.** Nobody can sign up as admin on the website. Register a normal account, then run:

```bash
./start-service.sh make-admin you@example.com
```

Sign in with it and an **Admin** link appears at the top of the page.

### Switching on real email (Gmail or any SMTP mail server)

You need a mailbox the app can send from. A free Gmail account made just for this app is enough to start.

1. In the Google account that will send the alerts, turn on **2-Step Verification**, then create an **app password** (Google Account, Security, App passwords). Google's screens change, so follow their current guide. The app password is a 16-letter code; the normal password will not work.
2. Copy `.env.local.example` to `.env.local`, run `chmod 600 .env.local`, and fill in:

   ```text
   EMAIL_ENABLED=true
   EMAIL_SMTP_HOST=smtp.gmail.com
   EMAIL_SMTP_PORT=587
   EMAIL_USERNAME=your-sending-account@gmail.com
   EMAIL_PASSWORD=the-16-letter-app-password
   EMAIL_ADMIN_TO=the-admin@example.com
   ```

   `EMAIL_ADMIN_TO` is where the alerts go; several addresses can be separated by commas.
3. `./start-service.sh restart`. If a setting is missing, the backend refuses to start and names it. Make a request and check that the Admin page shows **Email alert sent** and the email arrives. The first one may land in spam: open it and choose *Not spam*.

Another provider (Brevo, SendGrid, Amazon SES and others) works the same way: use the SMTP host, port and login it gives you, and set `EMAIL_FROM` to an address it lets you send from. Port 587 (STARTTLS) and 465 (SSL) are supported, and the app will not send the password over an unencrypted connection. The password is never written to logs, and `.env.local` is git-ignored.

### Using WhatsApp instead (Meta's WhatsApp Business Platform, Cloud API)

Set `NOTIFY_CHANNEL=whatsapp` and the alerts go to WhatsApp instead of email (one channel at a time). You do these steps in Meta's website; the app does the rest. Meta's screens and rules change, so follow their current "Get started" guide for WhatsApp.

1. Create a Meta developer account and a business, create an app, and add the **WhatsApp** product.
2. **Try it with Meta's test number first.** The API Setup page gives you a test sender; add the admin's number as a recipient and send Meta's sample message to prove the phone receives it.
3. Create a message **template** (category *Utility*, language English) named `new_booking_details`, with exactly this text and its eight variables, and wait for Meta to approve it. Keep the blank lines:

   ```text
   New booking request {{1}}

   Care seeker: {{2}}
   Phone: {{3}}
   Email: {{4}}

   Caregiver: {{5}} ({{6}})
   Charge: {{7}}
   Note: {{8}}

   Please call the care seeker, then update the request on the HomeCare Connect admin page.
   ```

   Meta asks for an example value for each variable. Use: `#12`, `Sam Seeker`, `+94 77 123 4567`, `sam@example.com`, `Nina Fernando`, `Nursing`, `LKR 9,000 per day`, `Mornings only`. (A template made earlier with only two variables will not work with this version, so create this new one.)

   (Messages to a number that has not written to you in the last 24 hours must use an approved template.)
4. For real use, add a dedicated sender number, and create a **permanent token** (a system user in Business settings). The temporary token expires after a short time.
5. Copy `.env.local.example` to `.env.local`, fill in the `WHATSAPP_*` values, set `NOTIFY_CHANNEL=whatsapp` and `WHATSAPP_ENABLED=true`, and run `chmod 600 .env.local`.
6. `./start-service.sh restart`. If a setting is missing, the backend refuses to start and names it. Make a request and check that the Admin page shows **WhatsApp alert sent**.

Meta charges per message and its prices and rules change, so check their current pricing page for your country. The token is never written to logs, and `.env.local` is git-ignored.

Not built yet: delivery receipts and replies from WhatsApp (webhooks), and alerts when a seeker cancels. Alerts are sent by a single backend process; running several backends needs a lock around the sender.

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
