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
- **Caregivers** build a profile (job, photo, address, experience, qualifications, charges per hour / session / day / full day and night), add documents such as their NIC (see below), see how complete the profile is, switch availability on or off, and read their reviews.
- **Care seekers** pick Caretaker, Nurse or Physiotherapist, browse caregiver cards with prices and ratings, leave a review, and **request** a caregiver.
- **Admin** gets an email for every request (or a WhatsApp message, if you prefer) and follows it up on the Admin page (see below). The Admin page also has a **Verification** tab for checking caregivers' documents and an **Accounts** tab for finding and deleting caregiver and care seeker accounts.

## Booking requests and alerts for the admin

1. A care seeker presses **Request** on a caregiver, adds a phone number and the address where the care is needed (and optionally a charge and a note), and confirms. Nothing is charged.
2. The request is saved with status *Requested*, and an alert is queued for the admin.
3. Within a few seconds the admin gets one email with everything needed to follow up: the request number, the seeker's name, phone and email, the caregiver and their job, the charge and the note. For example, with the subject `New booking request #12: Sam Seeker asked for Nina Fernando`:

   ```text
   New booking request #12

   Care seeker: Sam Seeker
   Phone: +94 77 123 4567
   Email: sam@example.com
   Care needed at: 5 Lake Road, Colombo

   Caregiver: Nina Fernando (Nursing)
   Charge: LKR 9,000 per day
   Note: Mornings only, please call after 6pm

   Please call the care seeker, then update the request on the HomeCare Connect admin page.

   Admin page: http://localhost:5173/admin
   ```

4. The admin calls the seeker, arranges it with the caregiver, and marks the request *Confirmed*, *Completed* or *Cancelled*. When online payments are on (see **Booking fee and payments**), marking a visit *Completed* also asks for the fee the seeker pays.

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
3. Create a message **template** (category *Utility*, language English) named `new_booking_details`, with exactly this text and its nine variables, and wait for Meta to approve it. Keep the blank lines:

   ```text
   New booking request {{1}}

   Care seeker: {{2}}
   Phone: {{3}}
   Email: {{4}}
   Care needed at: {{5}}

   Caregiver: {{6}} ({{7}})
   Charge: {{8}}
   Note: {{9}}

   Please call the care seeker, then update the request on the HomeCare Connect admin page.
   ```

   Meta asks for an example value for each variable. Use: `#12`, `Sam Seeker`, `+94 77 123 4567`, `sam@example.com`, `5 Lake Road, Colombo`, `Nina Fernando`, `Nursing`, `LKR 9,000 per day`, `Mornings only`. (A template made earlier with fewer variables will not work with this version, so create this new one.)

   (Messages to a number that has not written to you in the last 24 hours must use an approved template.)
4. For real use, add a dedicated sender number, and create a **permanent token** (a system user in Business settings). The temporary token expires after a short time.
5. Copy `.env.local.example` to `.env.local`, fill in the `WHATSAPP_*` values, set `NOTIFY_CHANNEL=whatsapp` and `WHATSAPP_ENABLED=true`, and run `chmod 600 .env.local`.
6. `./start-service.sh restart`. If a setting is missing, the backend refuses to start and names it. Make a request and check that the Admin page shows **WhatsApp alert sent**.

Meta charges per message and its prices and rules change, so check their current pricing page for your country. The token is never written to logs, and `.env.local` is git-ignored.

Not built yet: delivery receipts and replies from WhatsApp (webhooks), and alerts when a seeker cancels. Alerts are sent by a single backend process; running several backends needs a lock around the sender.

## Booking fee and payments (PayHere)

The platform earns a **booking fee**, paid by the care seeker through [PayHere](https://www.payhere.lk) after the visit. It is separate from the caregiver's own charge. PayHere pays the money out to the bank account in **your PayHere merchant profile**; the app never sees or stores a bank account or a card number.

1. The request goes as before: Requested, then the admin confirms it and arranges the visit.
2. After the visit the admin marks it **Completed** and types the fee for that request (5% of the quoted charge is suggested; it can be anything).
3. The seeker gets an email, and a **Pay now** button appears under **Your requests**. It opens PayHere's own payment page.
4. When PayHere confirms, the request shows **Paid** on both sides and the seeker gets a receipt. The admin is emailed too, with PayHere's payment id.

On the Admin page a completed request shows the fee and *Unpaid* or *Paid*, with **Send payment email again**, **Change fee** and **Mark paid outside PayHere** (for a bank deposit or cash). A failed or cancelled payment can simply be tried again.

**Modes.** Set `PAYHERE_MODE` (in `.env.local`):

| Mode | What it does |
| --- | --- |
| `off` (default) | No fees. Marking a visit completed works exactly as before. |
| `simulated` | A built-in pretend PayHere page, to try the whole flow on your own computer. Nothing is charged and nothing leaves your computer. **Never use it on a real server:** any care seeker could mark their own fee paid. |
| `sandbox` | PayHere's test system, with test cards. Needs the settings below. |
| `live` | Real money. Needs the settings below and an approved PayHere merchant account. |

**Try it now (simulated).**

```bash
PAYHERE_MODE=simulated ./start-service.sh restart
```

Make a request, confirm it as admin, mark it completed with a fee, then sign in as the seeker and press **Pay now**. The simulator page lets you pay, fail or cancel. To keep it from emailing real people while you try, add `EMAIL_ENABLED=false`.

**Setting up the real PayHere.**

1. Create a merchant account at payhere.lk and give it the bank account the money should go to. PayHere's screens and approval steps change, so follow their current guide.
2. In PayHere add the website's domain (the address families open). PayHere then gives you a **Merchant ID** and a **Merchant Secret** for that domain. Payments only work from the domain you registered.
3. PayHere tells our server about each payment by calling it, so the backend needs a **public https address**: set `PUBLIC_API_URL` to it (for example `https://api.example.com`). PayHere will call `PUBLIC_API_URL/api/payments/payhere/notify`. A `localhost` address cannot be reached, so for the sandbox on your own computer use a tunnel service that gives you a public address.
4. Put the values in `.env.local` (git-ignored) and restart:

   ```text
   PAYHERE_MODE=sandbox
   PAYHERE_MERCHANT_ID=the-merchant-id
   PAYHERE_MERCHANT_SECRET=the-merchant-secret
   PUBLIC_API_URL=https://your-public-backend-address
   PUBLIC_URL=https://the-address-families-open
   ```

   `PUBLIC_URL` is where PayHere sends families back to (it falls back to `CORS_ORIGIN`). The app refuses to start in `sandbox` or `live` mode if a value is missing, and the secret is never written to logs.
5. **Sandbox first.** It is a separate copy of PayHere with its own account, Merchant ID and Secret (a sandbox account cannot be turned into a live one). PayHere's test cards that succeed: Visa `4916217501611292`, MasterCard `5307732125531191`, American Express `346781005510225`; any other card fails. When it works, switch to `live` with the live account's values.

**How a payment is protected.** The amount always comes from our database, never from the browser. A payment only counts when PayHere's own signed message says so: the page the family returns to proves nothing. The signature, the merchant id, the order, the amount and the currency must all match, repeated messages are counted once, and a second payment for a fee that is already paid is kept and flagged instead of lost.

**Not built yet.**
- **Refunds** are done in the PayHere dashboard. The app flags a fee that was paid twice, charged back, or paid in a different amount than the fee now set, so the admin knows to look.
- Payouts to caregivers (the platform only collects its own fee), receipts as PDF, USD, and asking PayHere for a payment's status if a message is ever missed.
- Nothing stops a seeker who has not paid from making new requests; the admin sees *Unpaid* and can send the email again.
- Payment emails always go by email, even when `NOTIFY_CHANNEL=whatsapp`.

Two things to check in the PayHere sandbox the first time: the city sent to PayHere is the last part of the care address (after the last comma), and PayHere must accept payments from the domain you registered.

## Caregiver documents and verification

Documents are **optional by default**. A caregiver can add them when they set up their profile (the **Documents** part of the form) or later on their dashboard (the **Verification** card): a caretaker can add an NIC and a Grama Niladhari (GS) letter, a nurse a nursing qualification certificate and an NIC, a physiotherapist a degree certificate and an NIC. JPG, PNG, WebP or PDF, up to 5 MB, checked by content and not by name.

1. An admin opens **Admin, Verification**, views each file, and approves it or rejects it with a reason. The caregiver sees the reason and uploads the file again; a new upload always starts as waiting for review.
2. An approved document earns a mark on the caregiver's card that families see, such as "NIC verified".
3. **Making a document mandatory.** At the top of the Verification tab, **Document rules** has a switch for every document of every job. Switch one on and the caregivers of that job who do not have it approved are hidden from families and cannot be requested, until it is approved. Before the change takes effect the page says how many caregivers it will hide or show and asks you to confirm. Switch it off again and they come back.

An optional document, even a rejected one, never hides a caregiver. A caregiver also needs to give their **address** (required in the profile form); only the admin sees it. The list of documents each job can take is in one place, `DocumentType` in `backend/src/main/java/com/homecare/verification`.

**Privacy.** An NIC is sensitive. Documents are saved on the server's disk in `uploads/documents` (git-ignored; `UPLOADS_DIR` moves it), are never served from a public address (profile photos are, documents are not), and only an admin can open one, through the Admin page. Replaced or removed files are deleted. Who may hold these files, for how long, and how they are backed up still needs your decision (and, for real users, a check against Sri Lanka's data protection rules) before you go live.

**Existing accounts.** Caregivers who signed up before addresses were asked have none until they save their profile again. Older booking requests have no address either.

## Deleting an account (admin)

On the Admin page open **Accounts**, find the person by name or email (or filter caregivers and care seekers), and press **Delete**. The page first shows what goes with the account and asks you to type their email before the button works.

What is removed, for good:
- the account and sign-in;
- for a caregiver: the profile, charges, photo and uploaded documents (the files on disk too);
- every booking request they made or received, with its alert, and every review they wrote or received.

So deleting a caregiver also removes the requests care seekers made for them, and deleting a care seeker lowers the caregivers' review counts. The person is signed out at once and could sign up again later, as a new account. There is no "block but keep the history" option yet.

Admin accounts cannot be deleted from the page, not even your own: first run `./start-service.sh make-admin EMAIL --remove`, then delete the account.

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

The defaults are for development only: the database password is `homecare`, and the script makes a random login secret for your computer. On a server set `JWT_SECRET`, `DB_PASSWORD` and `CORS_ORIGIN` yourself, use HTTPS, and do not use the demo accounts. For payments set `PAYHERE_MODE=live` with the live `PAYHERE_MERCHANT_ID`, `PAYHERE_MERCHANT_SECRET`, `PUBLIC_API_URL` and `PUBLIC_URL`, and never leave `simulated` on.
