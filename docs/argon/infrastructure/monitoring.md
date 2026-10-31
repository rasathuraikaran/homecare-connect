# Monitoring, Health Checks & Observability

This document describes how to monitor service health, database connectivity, and asynchronous notification delivery in HomeCare Connect.

---

## 1. Local & Container Health Probes

### 1.1 Unified Status Command (`./start-service.sh status`)
Implemented in [`status_stack()` in `start-service.sh`](file:///home/sivaram/Github/homecare-connect/start-service.sh#L411-L448):

```bash
./start-service.sh status
```

Checks three live health indicators:
1. **PostgreSQL (`:5432`)**: Executes `pg_isready -U homecare -d homecare` inside the `homecare-postgres` container.
2. **Spring Boot Backend (`:8080`)**: Verifies the PID in `.run/backend.pid` is alive and sends an HTTP probe to `GET http://localhost:8080/api/caregivers`.
3. **Vite Frontend (`:5173`)**: Verifies the PID in `.run/frontend.pid` is alive and sends an HTTP probe to `GET http://localhost:5173`.

### 1.2 HTTP Health Check Endpoints for Load Balancers (ALB / CloudFront)
Note that `spring-boot-starter-actuator` is **not** included in `backend/pom.xml`. When configuring an external load balancer or uptime monitor against the backend:
- **Public Database-Backed Liveness/Readiness Probe**: `GET /api/caregivers`
  - Returns `200 OK` (`application/json`) and exercises the full stack: Tomcat -> Spring Security filter chain -> `DirectoryController` -> `DirectoryService` -> PostgreSQL query (`caregiver_profiles`).
- **Lightweight Security Filter Probe**: `GET /api/auth/me` (without token)
  - Returns `401 Unauthorized` immediately from `SecurityConfig` without hitting PostgreSQL (`start-service.sh` uses this in `http_responding` to confirm Tomcat is accepting requests).

---

## 2. Outbox Notification Delivery Monitoring

Because booking alerts (`EMAIL` and `WHATSAPP`) are dispatched asynchronously via the `notifications` outbox table, HomeCare Connect provides built-in visibility into delivery health both in the **Admin UI** and in **PostgreSQL**.

### 2.1 Admin UI Notification Badges & History (`/admin`)
- On [`AdminPage.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/pages/AdminPage.jsx), each booking card displays a [`NotificationBadge.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/components/NotificationBadge.jsx) (`sent / total`, e.g., `2/2 alerts sent`, `1 failed`).
- Clicking **Show alerts** expands [`AdminNotificationList.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/components/AdminNotificationList.jsx), which queries `GET /api/admin/bookings/{id}/notifications` to display each channel's `status` (`PENDING`, `SENT`, `FAILED`), `attempts` (`0`–`5`), `sentAt`, and `lastError`.
- If any channel is `FAILED`, clicking **Retry alerts** calls `POST /api/admin/bookings/{id}/notifications/retry`.

### 2.2 SQL Queries for Monitoring the Outbox Queue
Operators can inspect notification queue health directly in PostgreSQL:

```sql
-- 1. Count notifications by channel and status over the last 24 hours
SELECT channel, status, COUNT(*) AS total
FROM notifications
WHERE created_at >= now() - INTERVAL '24 hours'
GROUP BY channel, status
ORDER BY channel, status;

-- 2. Inspect stuck or permanently failed notifications with their last error
SELECT id, booking_id, channel, status, attempts, last_error, next_attempt_at, created_at
FROM notifications
WHERE status = 'FAILED' OR (status = 'PENDING' AND attempts > 0)
ORDER BY created_at DESC
LIMIT 50;
```

---

## 3. Admin Operational Counters

The backend exposes two lightweight counter endpoints polled every 30 seconds by the Admin UI:
1. **`GET /api/admin/bookings/counts`** ([`AdminBookingController.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/booking/AdminBookingController.java#L41-L44)):
   - Returns `{ "REQUESTED": n, "CONFIRMED": n, "COMPLETED": n, "CANCELLED": n }`.
2. **`GET /api/admin/verifications/counts`** ([`AdminVerificationController.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/AdminVerificationController.java#L42-L45)):
   - Returns `{ "pendingCaregivers": n }` (count of distinct caregivers with at least one `PENDING` document upload).
