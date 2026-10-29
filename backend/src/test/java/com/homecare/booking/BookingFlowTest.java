package com.homecare.booking;

import com.homecare.notification.EmailSendException;
import com.homecare.notification.EmailSender;
import com.homecare.notification.NotificationDispatcher;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(BookingFlowTest.Config.class)
class BookingFlowTest {

    /** A stand-in for the mail server that records what was sent and can be told to fail. Email is the default alert channel. */
    static class FakeSender implements EmailSender {
        record Mail(String to, String subject, String body) {}

        final List<Mail> sent = Collections.synchronizedList(new ArrayList<>());
        volatile RuntimeException failWith;

        void reset() { sent.clear(); failWith = null; }

        @Override
        public Outcome send(String to, String subject, String body) {
            if (failWith != null) throw failWith;
            sent.add(new Mail(to, subject, body));
            return Outcome.SENT;
        }
    }

    static final FakeSender SENDER = new FakeSender();

    @TestConfiguration
    static class Config {
        @Bean @Primary FakeSender fakeSender() { return SENDER; }
    }

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired MockMvc mvc;
    @Autowired NotificationDispatcher dispatcher;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void drain() {
        SENDER.reset();
        dispatcher.dispatchDue();   // send anything left over from another test, then start clean
        SENDER.reset();
    }

    // ------------------------------------------------------------------ helpers

    private String[] register(String email, String name, String role) throws Exception {
        String res = mvc.perform(post("/api/auth/register").contentType(JSON)
                        .content("{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"password123\",\"role\":\"%s\"}".formatted(name, email, role)))
                .andReturn().getResponse().getContentAsString();
        return new String[]{"Bearer " + JsonPath.read(res, "$.token"), String.valueOf((Integer) JsonPath.read(res, "$.user.id"))};
    }

    private String[] caregiver(String email, String name) throws Exception {
        String[] cg = register(email, name, "CAREGIVER");
        mvc.perform(put("/api/caregivers/me/profile").header("Authorization", cg[0]).contentType(JSON)
                .content("{\"fullName\":\"%s\",\"jobType\":\"NURSING\",\"rates\":{\"HOURLY\":1800,\"DAILY\":9000}}".formatted(name)))
                .andExpect(status().isOk());
        return cg;
    }

    private String[] admin(String email) throws Exception {
        String[] a = register(email, "Admin Person", "CARE_SEEKER");
        jdbc.update("update users set is_admin = true where email = ?", email);   // the only way to become admin
        return a;
    }

    private String book(String seekerAuth, String caregiverId, String extra) {
        try {
            return mvc.perform(post("/api/bookings").header("Authorization", seekerAuth).contentType(JSON)
                            .content("{\"caregiverId\":%s,\"phone\":\"+94 77 123 4567\"%s}".formatted(caregiverId, extra)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private String alertStatus(String adminAuth, String bookingId) throws Exception {
        String body = mvc.perform(get("/api/admin/bookings").header("Authorization", adminAuth)).andReturn().getResponse().getContentAsString();
        List<String> s = JsonPath.read(body, "$[?(@.id == " + bookingId + ")].alert.status");
        return s.isEmpty() ? null : s.get(0);
    }

    // ------------------------------------------------------------------ the happy path

    @Test
    void confirmingARequestAlertsTheAdminAndTheAdminCanFollowIt() throws Exception {
        String[] nina = caregiver("bk-nina@test.com", "Nina Fernando");
        String[] seeker = register("bk-seeker@test.com", "Sam Seeker", "CARE_SEEKER");
        String[] admin = admin("bk-admin@test.com");

        String res = book(seeker[0], nina[1], ",\"rateType\":\"DAILY\",\"note\":\"  Mornings only  \"");
        String id = String.valueOf((Integer) JsonPath.read(res, "$.id"));
        assertEquals("REQUESTED", JsonPath.read(res, "$.status"));
        assertEquals(9000.0, ((Number) JsonPath.read(res, "$.quotedAmount")).doubleValue());
        assertEquals("Mornings only", JsonPath.read(res, "$.note"));

        // queued, not yet sent: the seeker was not made to wait for the mail server
        assertTrue(SENDER.sent.isEmpty());
        assertEquals("PENDING", alertStatus(admin[0], id));

        assertEquals(1, dispatcher.dispatchDue());
        assertEquals(1, SENDER.sent.size());
        FakeSender.Mail mail = SENDER.sent.get(0);
        assertEquals("alerts@homecare.test", mail.to());
        assertEquals("New booking request #" + id + ": Sam Seeker asked for Nina Fernando", mail.subject());
        assertEquals("""
                New booking request #%s

                Care seeker: Sam Seeker
                Phone: +94 77 123 4567
                Email: bk-seeker@test.com

                Caregiver: Nina Fernando (Nursing)
                Charge: LKR 9,000 per day
                Note: Mornings only

                Please call the care seeker, then update the request on the HomeCare Connect admin page.

                Admin page: http://localhost:5173/admin""".formatted(id), mail.body(),
                "the email holds the request number, the seeker's contact details, the caregiver, the charge, the note and a link");
        assertEquals("SENT", alertStatus(admin[0], id));
        mvc.perform(get("/api/admin/bookings").header("Authorization", admin[0]))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].alert.channel").value("EMAIL"));

        // the admin sees who to call
        mvc.perform(get("/api/admin/bookings").header("Authorization", admin[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].seekerPhone").value("+94 77 123 4567"))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].seeker.email").value("bk-seeker@test.com"))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].caregiver.name").value("Nina Fernando"));

        // admin moves it along; going backwards is refused
        mvc.perform(patch("/api/admin/bookings/" + id + "/status").header("Authorization", admin[0]).contentType(JSON).content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(patch("/api/admin/bookings/" + id + "/status").header("Authorization", admin[0]).contentType(JSON).content("{\"status\":\"REQUESTED\"}"))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/admin/bookings/" + id + "/status").header("Authorization", admin[0]).contentType(JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/admin/bookings/" + id + "/status").header("Authorization", admin[0]).contentType(JSON).content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isConflict());

        // and the seeker sees their own request
        mvc.perform(get("/api/bookings/mine").header("Authorization", seeker[0]))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].caregiverName").value("Nina Fernando"));
        mvc.perform(get("/api/admin/bookings").param("status", "REQUESTED").header("Authorization", admin[0]))
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());
    }

    // ------------------------------------------------------------------ mail trouble never loses a booking

    @Test
    void theAlertStillShowsEveryDetailWhenNoChargeOrNoteWasGiven() throws Exception {
        String[] nina = caregiver("bk-full-nina@test.com", "Nina Fernando");
        String[] seeker = register("bk-full-seeker@test.com", "Sam Seeker", "CARE_SEEKER");

        book(seeker[0], nina[1], "");
        dispatcher.dispatchDue();

        assertEquals(1, SENDER.sent.size());
        String body = SENDER.sent.get(0).body();
        assertTrue(body.contains("Care seeker: Sam Seeker"), body);
        assertTrue(body.contains("Phone: +94 77 123 4567"), body);
        assertTrue(body.contains("Email: bk-full-seeker@test.com"), body);
        assertTrue(body.contains("Caregiver: Nina Fernando (Nursing)"), body);
        assertTrue(body.contains("Charge: Not chosen yet"), body);
        assertTrue(body.contains("Note: None"), body);
    }

    @Test
    void failedAlertsAreRetriedLaterAndCanBeResent() throws Exception {
        String[] cg = caregiver("bk-retry-cg@test.com", "Rita Retry");
        String[] seeker = register("bk-retry-s@test.com", "Sam Seeker", "CARE_SEEKER");
        String[] admin = admin("bk-retry-admin@test.com");
        String id = String.valueOf((Integer) JsonPath.read(book(seeker[0], cg[1], ""), "$.id"));

        // the mail server is down: the booking exists, the alert waits and is not hammered again straight away
        SENDER.failWith = new EmailSendException("Could not send the email: Connection timed out", true);
        assertEquals(1, dispatcher.dispatchDue());
        assertEquals("PENDING", alertStatus(admin[0], id));
        assertEquals(0, dispatcher.dispatchDue(), "the retry is scheduled for later");

        // a permanent problem (for example a wrong password) stops retrying and tells the admin
        jdbc.update("update notifications set next_attempt_at = current_timestamp - interval '1' minute where booking_id = ?", Long.parseLong(id));
        SENDER.failWith = new EmailSendException("The mail server refused the username or password.", false);
        assertEquals(1, dispatcher.dispatchDue());
        assertEquals("FAILED", alertStatus(admin[0], id));
        mvc.perform(get("/api/admin/bookings").header("Authorization", admin[0]))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].alert.error").value("The mail server refused the username or password."));

        // after fixing the problem the admin can queue it again
        SENDER.failWith = null;
        mvc.perform(post("/api/admin/bookings/" + id + "/resend-alert").header("Authorization", admin[0]))
                .andExpect(status().isOk()).andExpect(jsonPath("$.alert.status").value("PENDING"));
        dispatcher.dispatchDue();
        assertEquals("SENT", alertStatus(admin[0], id));
        mvc.perform(post("/api/admin/bookings/" + id + "/resend-alert").header("Authorization", admin[0]))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ rules

    @Test
    void requestsAreValidatedAndLimited() throws Exception {
        String[] a = caregiver("bk-r-a@test.com", "Cara A");
        String[] b = caregiver("bk-r-b@test.com", "Cara B");
        String[] c = caregiver("bk-r-c@test.com", "Cara C");
        String[] d = caregiver("bk-r-d@test.com", "Cara D");
        String[] seeker = register("bk-r-seeker@test.com", "Sam Seeker", "CARE_SEEKER");
        String auth = seeker[0];

        // a bad phone number and a missing one are explained, together
        mvc.perform(post("/api/bookings").header("Authorization", auth).contentType(JSON).content("{\"caregiverId\":" + a[1] + ",\"phone\":\"abc\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[?(@ == 'Enter a valid phone number')]").isNotEmpty());
        mvc.perform(post("/api/bookings").header("Authorization", auth).contentType(JSON).content("{\"caregiverId\":" + a[1] + "}"))
                .andExpect(status().isBadRequest());
        // a charge type the caregiver does not offer
        mvc.perform(post("/api/bookings").header("Authorization", auth).contentType(JSON)
                        .content("{\"caregiverId\":" + a[1] + ",\"phone\":\"0771234567\",\"rateType\":\"SESSION\"}"))
                .andExpect(status().isBadRequest());
        // unknown caregiver, and a seeker's id is not a caregiver
        mvc.perform(post("/api/bookings").header("Authorization", auth).contentType(JSON).content("{\"caregiverId\":999999,\"phone\":\"0771234567\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/bookings").header("Authorization", auth).contentType(JSON).content("{\"caregiverId\":" + seeker[1] + ",\"phone\":\"0771234567\"}"))
                .andExpect(status().isNotFound());

        // a caregiver who switched availability off cannot be requested
        mvc.perform(put("/api/caregivers/me/availability").header("Authorization", d[0]).contentType(JSON).content("{\"available\":false}"));
        mvc.perform(post("/api/bookings").header("Authorization", auth).contentType(JSON).content("{\"caregiverId\":" + d[1] + ",\"phone\":\"0771234567\"}"))
                .andExpect(status().isConflict());

        book(auth, a[1], "");
        // the same caregiver twice
        mvc.perform(post("/api/bookings").header("Authorization", auth).contentType(JSON).content("{\"caregiverId\":" + a[1] + ",\"phone\":\"0771234567\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("You already have an open request with this caregiver"));
        book(auth, b[1], "");
        book(auth, c[1], "");
        // three waiting is the limit, so one account cannot flood the admin
        mvc.perform(post("/api/bookings").header("Authorization", auth).contentType(JSON).content("{\"caregiverId\":" + d[1] + ",\"phone\":\"0771234567\"}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/caregivers/me/availability").header("Authorization", d[0]).contentType(JSON).content("{\"available\":true}"));
        mvc.perform(post("/api/bookings").header("Authorization", auth).contentType(JSON).content("{\"caregiverId\":" + d[1] + ",\"phone\":\"0771234567\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("3 requests waiting")));
    }

    @Test
    void seekersCanCancelOnlyTheirOwnWaitingRequests() throws Exception {
        String[] cg = caregiver("bk-c-cg@test.com", "Cleo Cancel");
        String[] owner = register("bk-c-owner@test.com", "Owner Seeker", "CARE_SEEKER");
        String[] other = register("bk-c-other@test.com", "Other Seeker", "CARE_SEEKER");
        String[] admin = admin("bk-c-admin@test.com");
        String id = String.valueOf((Integer) JsonPath.read(book(owner[0], cg[1], ""), "$.id"));

        mvc.perform(post("/api/bookings/" + id + "/cancel").header("Authorization", other[0])).andExpect(status().isNotFound());
        mvc.perform(post("/api/bookings/" + id + "/cancel").header("Authorization", owner[0])).andExpect(status().isNoContent());
        mvc.perform(get("/api/bookings/mine").header("Authorization", owner[0])).andExpect(jsonPath("$[0].status").value("CANCELLED"));
        mvc.perform(post("/api/bookings/" + id + "/cancel").header("Authorization", owner[0])).andExpect(status().isConflict());

        // after cancelling, the same caregiver can be requested again
        String second = String.valueOf((Integer) JsonPath.read(book(owner[0], cg[1], ""), "$.id"));
        mvc.perform(patch("/api/admin/bookings/" + second + "/status").header("Authorization", admin[0]).contentType(JSON).content("{\"status\":\"CONFIRMED\"}"));
        mvc.perform(post("/api/bookings/" + second + "/cancel").header("Authorization", owner[0])).andExpect(status().isConflict());
    }

    @Test
    void onlyTheRightPeopleCanUseEachEndpoint() throws Exception {
        String[] cg = caregiver("bk-a-cg@test.com", "Cora Access");
        String[] seeker = register("bk-a-seeker@test.com", "Sam Seeker", "CARE_SEEKER");
        String body = "{\"caregiverId\":" + cg[1] + ",\"phone\":\"0771234567\"}";

        mvc.perform(post("/api/bookings").contentType(JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/bookings").header("Authorization", cg[0]).contentType(JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/bookings")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/bookings").header("Authorization", seeker[0])).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/bookings").header("Authorization", cg[0])).andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/bookings/1/status").header("Authorization", seeker[0]).contentType(JSON).content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void nobodyCanSignUpAsAdmin() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(JSON)
                        .content("{\"fullName\":\"Sneaky\",\"email\":\"bk-sneaky@test.com\",\"password\":\"password123\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType(JSON)
                        .content("{\"fullName\":\"Sneaky\",\"email\":\"bk-sneaky2@test.com\",\"password\":\"password123\",\"role\":\"CARE_SEEKER\",\"admin\":true,\"isAdmin\":true}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.user.admin").value(false));
    }
}
