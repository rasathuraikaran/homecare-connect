# Contributing & Developer Extension Guide

This guide explains how to extend HomeCare Connect safely while maintaining consistency across the database, Spring Boot backend, React frontend, and automated test suite.

---

## 1. Development Workflow

1. **Create a Feature Branch**:
   ```bash
   git checkout -b feat/<short-description>
   ```
2. **Start the Local Stack**:
   ```bash
   ./start-service.sh start --demo
   ```
3. **Run Backend Tests Before & After Changes**:
   ```bash
   cd backend && ./mvnw test
   ```
4. **Verify Frontend Production Build**:
   ```bash
   cd frontend && npm run build
   ```

---

## 2. How-To: Adding a New `JobType`

If you need to add a new caregiver profession (for example, `SPEECH_THERAPIST`):

1. **Backend Enum ([`JobType.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/JobType.java))**:
   - Add `SPEECH_THERAPIST("Speech Therapist")` to `JobType`.
2. **Backend Document Mapping ([`DocumentType.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/DocumentType.java#L23-L30))**:
   - Update `DocumentType.allowedFor(JobType)` to include the `case SPEECH_THERAPIST -> List.of(...)` branch (because `switch (jobType)` on `JobType` is an exhaustive switch expression, the compiler will flag this automatically).
3. **Flyway Database Migration (`backend/src/main/resources/db/migration/V12__...sql`)**:
   - Insert default rows into `document_requirements (job_type, doc_type, required)` for the new `JobType`.
4. **Frontend Constants**:
   - Add the new job type to `JOB_TYPES` in [`frontend/src/constants/jobTypes.js`](file:///home/sivaram/Github/homecare-connect/frontend/src/constants/jobTypes.js).
   - Add its allowed document types to `DOCS_BY_JOB_TYPE` in [`frontend/src/constants/documentTypes.js`](file:///home/sivaram/Github/homecare-connect/frontend/src/constants/documentTypes.js).
5. **Tests**:
   - Add a test case in [`ProfileAndDirectoryTest.java`](file:///home/sivaram/Github/homecare-connect/backend/src/test/java/com/homecare/profile/ProfileAndDirectoryTest.java) and [`CaregiverVerificationControllerTest.java`](file:///home/sivaram/Github/homecare-connect/backend/src/test/java/com/homecare/verification/CaregiverVerificationControllerTest.java).

---

## 3. How-To: Adding a New `RateType`

If you need to add a new pricing tier (for example, `WEEKLY`):

1. **Backend Enum ([`RateType.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/profile/RateType.java))**:
   - Add `WEEKLY("Weekly", "/wk")` to `RateType`.
   - Note: [`ApiExceptionHandler.rateLabel`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/common/ApiExceptionHandler.java#L85-L94) dynamically reads `RateType.valueOf(key).getLabel()`, so validation error messages will automatically support the new rate type.
2. **Frontend Constants ([`frontend/src/constants/rateTypes.js`](file:///home/sivaram/Github/homecare-connect/frontend/src/constants/rateTypes.js))**:
   - Add `{ value: 'WEEKLY', label: 'Weekly', unit: '/wk', hint: '...' }` to `RATE_TYPES`.
   - [`RateCardGrid.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/components/RateCardGrid.jsx), [`ProfilePage.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/pages/ProfilePage.jsx), and [`BookingRequestModal.jsx`](file:///home/sivaram/Github/homecare-connect/frontend/src/components/BookingRequestModal.jsx) all iterate over `RATE_TYPES` dynamically.

---

## 4. How-To: Adding a New `DocumentType`

If you need to add a new verification document type (for example, `POLICE_CLEARANCE`):

1. **Backend Enum ([`DocumentType.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/verification/DocumentType.java))**:
   - Add `POLICE_CLEARANCE("Police clearance certificate")` and include it in the relevant `allowedFor(JobType)` lists.
2. **Flyway Migration (`V12__...sql`)**:
   - Seed `(job_type, 'POLICE_CLEARANCE', FALSE)` rows in `document_requirements`.
3. **Frontend Constants ([`frontend/src/constants/documentTypes.js`](file:///home/sivaram/Github/homecare-connect/frontend/src/constants/documentTypes.js))**:
   - Add `POLICE_CLEARANCE` to `DOCUMENT_TYPES` and `DOCS_BY_JOB_TYPE`.

---

## 5. Pull Request Checklist

Before submitting changes:
- [ ] All 11 JUnit test suites pass (`cd backend && ./mvnw test`).
- [ ] Frontend builds cleanly (`cd frontend && npm run build`).
- [ ] New endpoints have explicit authorization rules in [`SecurityConfig.java`](file:///home/sivaram/Github/homecare-connect/backend/src/main/java/com/homecare/config/SecurityConfig.java) and Jakarta Bean Validation annotations on request DTOs.
- [ ] Public endpoints never expose caregiver `email`, `phone`, `address`, or reviewer surnames.
- [ ] No secrets, tokens, or `.env.local` values are committed to Git.
- [ ] Technical documentation under `docs/argon/` is updated to reflect any new endpoints, tables, or configuration variables.
