package com.homecare.payment;

import com.homecare.notification.EmailSender;
import com.homecare.notification.NotificationDispatcher;
import com.homecare.payment.PaymentTestKit.Person;
import com.homecare.payment.PaymentTestKit.Scene;
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
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static com.homecare.payment.PaymentTestKit.JSON;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Payments against a pretend PayHere sandbox: a made-up merchant, and callbacks signed here the way PayHere signs them.
 * The signatures are worked out again in this test with plain MD5, apart from the code being tested.
 */
@SpringBootTest(properties = {
        "app.payhere.mode=sandbox",
        "app.payhere.merchant-id=1230000",
        "app.payhere.merchant-secret=test-merchant-secret-value",
        "app.payhere.public-api-url=https://api.homecare.test",
        "app.payhere.site-url=https://shop.homecare.test",
        "spring.datasource.url=jdbc:h2:mem:payments-flow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})   // its own database, apart from the other tests
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PaymentFlowTest.Config.class)
class PaymentFlowTest {

    static class FakeSender implements EmailSender {
        record Mail(String to, String subject, String body) {}

        final List<Mail> sent = Collections.synchronizedList(new ArrayList<>());

        @Override
        public Outcome send(String to, String subject, String body) {
            sent.add(new Mail(to, subject, body));
            return Outcome.SENT;
        }
    }

    static final FakeSender SENDER = new FakeSender();

    @TestConfiguration
    static class Config {
        @Bean @Primary FakeSender fakeSender() { return SENDER; }
    }

    private static final String MERCHANT = "1230000";
    private static final String SECRET = "test-merchant-secret-value";

    @Autowired MockMvc mvc;
    @Autowired NotificationDispatcher dispatcher;
    @Autowired JdbcTemplate jdbc;

    private PaymentTestKit kit;

    @BeforeEach
    void start() {
        kit = new PaymentTestKit(mvc, jdbc, "pf");
        SENDER.sent.clear();
        dispatcher.dispatchDue();   // send anything left over from another test, then start clean
        SENDER.sent.clear();
    }

    // ------------------------------------------------------------------ helpers

    private static String md5(String text) {
        try {
            return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /** What PayHere would send to the notify address, signed with the given secret. */
    private ResultActions callback(String merchant, String order, String amount, String currency, String code, String secret) throws Exception {
        String signature = md5(merchant + order + amount + currency + code + md5(secret));
        return mvc.perform(post("/api/payments/payhere/notify").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("merchant_id", merchant).param("order_id", order).param("payment_id", "320032")
                .param("payhere_amount", amount).param("payhere_currency", currency).param("status_code", code)
                .param("md5sig", signature).param("method", "VISA").param("status_message", "ok"));
    }

    private ResultActions genuine(String order, String amount, String code) throws Exception {
        return callback(MERCHANT, order, amount, "LKR", code, SECRET);
    }

    /** The seeker presses Pay now. @return the order id */
    private String startPaying(Scene s, String bookingId) throws Exception {
        String res = mvc.perform(post("/api/bookings/" + bookingId + "/pay").header("Authorization", s.seeker().auth()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.orderId");
    }

    private List<FakeSender.Mail> mails(String to, String subjectStart) {
        return SENDER.sent.stream().filter(m -> m.to().equals(to) && m.subject().startsWith(subjectStart)).toList();
    }

    private int rows(String where) {
        return jdbc.queryForObject("select count(*) from payments where " + where, Integer.class);
    }

    private String state(Scene s, String id) throws Exception {
        return (String) kit.seekerView(s.seeker(), id).get("state");
    }

    // ------------------------------------------------------------------ the admin asks for a fee

    @Test
    void completingAVisitNeedsAGoodFeeAndEmailsTheSeeker() throws Exception {
        Scene s = kit.scene();
        String id = kit.confirmed(s);

        for (String body : List.of("{\"status\":\"COMPLETED\"}", "{\"status\":\"COMPLETED\",\"fee\":0}",
                "{\"status\":\"COMPLETED\",\"fee\":-5}", "{\"status\":\"COMPLETED\",\"fee\":10000000}")) {
            kit.setStatus(s, id, body).andExpect(status().isBadRequest());
        }
        kit.setStatus(s, id, "{\"status\":\"CANCELLED\",\"fee\":100}").andExpect(status().isBadRequest());   // a fee only goes with completing
        mvc.perform(get("/api/bookings/mine").header("Authorization", s.seeker().auth()))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$[0].payment.state").value("NONE"));
        dispatcher.dispatchDue();
        assertTrue(mails(s.seeker().email(), "Please pay").isEmpty(), "a refused request tells nobody anything");

        kit.setStatus(s, id, "{\"status\":\"COMPLETED\",\"fee\":450.5}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.payment.state").value("DUE"))
                .andExpect(jsonPath("$.payment.fee").value(450.5));

        Map<String, Object> seen = kit.seekerView(s.seeker(), id);
        assertEquals("DUE", seen.get("state"));
        assertEquals(450.5, ((Number) seen.get("fee")).doubleValue());
        assertNull(seen.get("lastAttempt"));
        assertEquals("DUE", kit.adminView(s.admin(), id).get("state"));

        dispatcher.dispatchDue();
        List<FakeSender.Mail> due = mails(s.seeker().email(), "Please pay");
        assertEquals(1, due.size());
        assertEquals("Please pay your HomeCare Connect fee for request #" + id, due.get(0).subject());
        assertTrue(due.get(0).body().contains("LKR 450.50"), due.get(0).body());
        assertTrue(due.get(0).body().contains("Nina Fernando"), due.get(0).body());
        assertTrue(due.get(0).body().contains("https://shop.homecare.test"), due.get(0).body());
    }

    @Test
    void theConfigTellsThePageWhetherFeesAreOnAndShowsNoSecrets() throws Exception {
        Scene s = kit.scene();
        String body = mvc.perform(get("/api/payments/config").header("Authorization", s.seeker().auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("SANDBOX"))
                .andExpect(jsonPath("$.currency").value("LKR"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains(SECRET) || body.contains(MERCHANT), body);
        mvc.perform(get("/api/payments/config")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ the seeker starts paying

    @Test
    void onlyTheOwnerCanPayAndOnlyForACompletedVisitWithAFee() throws Exception {
        Scene s = kit.scene();
        String completed = kit.completed(s, "300");
        String confirmed = kit.confirmed(s);   // after the first: a seeker cannot have two open requests with one caregiver
        Person other = kit.register("Other Seeker", "CARE_SEEKER");

        mvc.perform(post("/api/bookings/" + confirmed + "/pay").header("Authorization", s.seeker().auth())).andExpect(status().isConflict());
        mvc.perform(post("/api/bookings/" + completed + "/pay").header("Authorization", other.auth())).andExpect(status().isNotFound());
        mvc.perform(post("/api/bookings/" + completed + "/pay").header("Authorization", s.admin().auth())).andExpect(status().isNotFound());
        mvc.perform(post("/api/bookings/" + completed + "/pay").header("Authorization", s.caregiver().auth())).andExpect(status().isForbidden());
        mvc.perform(post("/api/bookings/" + completed + "/pay")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/bookings/999999/pay").header("Authorization", s.seeker().auth())).andExpect(status().isNotFound());
        assertEquals(0, rows("booking_id = " + completed), "nothing was started by the refused tries");

        mvc.perform(post("/api/bookings/" + completed + "/pay").header("Authorization", s.seeker().auth())).andExpect(status().isOk());
    }

    @Test
    void theCheckoutIsSignedBuiltFromOurDataAndNeverShowsTheSecret() throws Exception {
        Scene s = kit.scene();
        String id = kit.completed(s, "450.5");

        // the browser cannot choose the amount: whatever it sends is ignored
        String res = mvc.perform(post("/api/bookings/" + id + "/pay").header("Authorization", s.seeker().auth()).contentType(JSON)
                        .content("{\"amount\":1,\"fee\":1}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertEquals("SANDBOX", JsonPath.read(res, "$.mode"));
        assertEquals("https://sandbox.payhere.lk/pay/checkout", JsonPath.read(res, "$.actionUrl"));
        String order = JsonPath.read(res, "$.orderId");
        assertTrue(order.matches("HC" + id + "-[0-9A-F]{10}"), order);
        assertEquals(order, JsonPath.read(res, "$.fields.order_id"));
        assertEquals(MERCHANT, JsonPath.read(res, "$.fields.merchant_id"));
        assertEquals("450.50", JsonPath.read(res, "$.fields.amount"));
        assertEquals("LKR", JsonPath.read(res, "$.fields.currency"));
        assertEquals(md5(MERCHANT + order + "450.50" + "LKR" + md5(SECRET)), JsonPath.read(res, "$.fields.hash"));
        assertEquals("https://shop.homecare.test/pay/return?booking=" + id, JsonPath.read(res, "$.fields.return_url"));
        assertEquals("https://shop.homecare.test/pay/cancel?booking=" + id, JsonPath.read(res, "$.fields.cancel_url"));
        assertEquals("https://api.homecare.test/api/payments/payhere/notify", JsonPath.read(res, "$.fields.notify_url"));
        assertEquals("Sam", JsonPath.read(res, "$.fields.first_name"));
        assertEquals("Seeker", JsonPath.read(res, "$.fields.last_name"));
        assertEquals(s.seeker().email(), JsonPath.read(res, "$.fields.email"));
        assertEquals("+94 77 123 4567", JsonPath.read(res, "$.fields.phone"));
        assertEquals("5 Lake Road, Colombo", JsonPath.read(res, "$.fields.address"));
        assertEquals("Colombo", JsonPath.read(res, "$.fields.city"));
        assertEquals("Sri Lanka", JsonPath.read(res, "$.fields.country"));
        assertEquals("Booking fee, request #" + id, JsonPath.read(res, "$.fields.items"));
        assertFalse(res.contains(SECRET), "the merchant secret never leaves the server");

        assertEquals(1, rows("order_id = '" + order + "' and status = 'PENDING' and amount = 450.50"));
        assertEquals("DUE", state(s, id), "starting to pay is not paying");
    }

    // ------------------------------------------------------------------ PayHere's callback

    @Test
    void aGenuineCallbackMarksTheFeePaidAndTellsBothSides() throws Exception {
        Scene s = kit.scene();
        String id = kit.completed(s, "450.5");
        dispatcher.dispatchDue();
        SENDER.sent.clear();
        String order = startPaying(s, id);

        genuine(order, "450.50", "2").andExpect(status().isOk()).andExpect(content().string("OK"));

        Map<String, Object> seen = kit.seekerView(s.seeker(), id);
        assertEquals("PAID", seen.get("state"));
        assertNotNull(seen.get("paidAt"));
        assertEquals("PAYHERE", seen.get("source"));
        assertEquals(false, seen.get("needsAttention"));
        assertEquals("PAID", kit.adminView(s.admin(), id).get("state"));
        assertEquals(1, rows("order_id = '" + order + "' and status = 'PAID' and method = 'VISA' and payhere_payment_id = '320032' and status_code = 2"));

        dispatcher.dispatchDue();
        List<FakeSender.Mail> receipt = mails(s.seeker().email(), "Your payment for request");
        assertEquals(1, receipt.size());
        assertTrue(receipt.get(0).body().contains("LKR 450.50"), receipt.get(0).body());
        List<FakeSender.Mail> toAdmin = mails("alerts@homecare.test", "Payment received for request #" + id);
        assertEquals(1, toAdmin.size());
        assertTrue(toAdmin.get(0).body().contains("320032"), "the admin gets PayHere's payment id to find it in the dashboard");
        assertTrue(toAdmin.get(0).body().contains("Sam Seeker"), toAdmin.get(0).body());

        // once paid, nothing more to pay or send
        mvc.perform(post("/api/bookings/" + id + "/pay").header("Authorization", s.seeker().auth())).andExpect(status().isConflict());
        mvc.perform(post("/api/admin/bookings/" + id + "/payment/resend").header("Authorization", s.admin().auth())).andExpect(status().isConflict());
        mvc.perform(post("/api/admin/bookings/" + id + "/payment/mark-paid").header("Authorization", s.admin().auth())).andExpect(status().isConflict());
        mvc.perform(put("/api/admin/bookings/" + id + "/fee").header("Authorization", s.admin().auth()).contentType(JSON).content("{\"fee\":10}")).andExpect(status().isConflict());
    }

    @Test
    void messagesThatAreNotGenuineAreRefusedAndChangeNothing() throws Exception {
        Scene s = kit.scene();
        String id = kit.completed(s, "450.5");
        String order = startPaying(s, id);
        Person stranger = kit.register("Stranger", "CARE_SEEKER");
        String otherOrder = startPaying(new Scene(s.admin(), stranger, s.caregiver()), kit.completed(new Scene(s.admin(), stranger, s.caregiver()), "450.5"));

        // signed with the wrong secret, or for another merchant, or for an order nobody started
        callback(MERCHANT, order, "450.50", "LKR", "2", "some-other-secret").andExpect(status().isBadRequest());
        callback("9999999", order, "450.50", "LKR", "2", SECRET).andExpect(status().isBadRequest());
        genuine("HC1-DOESNOTEXIST", "450.50", "2").andExpect(status().isBadRequest());
        // a genuine signature, but not for what we asked: less money, or another currency
        genuine(order, "1.00", "2").andExpect(status().isBadRequest());
        callback(MERCHANT, order, "450.50", "USD", "2", SECRET).andExpect(status().isBadRequest());
        // a status PayHere does not use, and an amount that is not a number
        genuine(order, "450.50", "7").andExpect(status().isBadRequest());
        genuine(order, "lots", "2").andExpect(status().isBadRequest());
        // a message with a changed status: the signature was made for the old one
        String signedForFailed = md5(MERCHANT + order + "450.50" + "LKR" + "-2" + md5(SECRET));
        mvc.perform(post("/api/payments/payhere/notify").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("merchant_id", MERCHANT).param("order_id", order).param("payhere_amount", "450.50").param("payhere_currency", "LKR")
                .param("status_code", "2").param("md5sig", signedForFailed)).andExpect(status().isBadRequest());
        // missing signature, missing everything
        mvc.perform(post("/api/payments/payhere/notify").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("merchant_id", MERCHANT).param("order_id", order).param("payhere_amount", "450.50").param("payhere_currency", "LKR")
                .param("status_code", "2")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/payments/payhere/notify").contentType(MediaType.APPLICATION_FORM_URLENCODED)).andExpect(status().isBadRequest());
        // a signed message for someone else's order must not touch this one
        genuine(otherOrder, "450.50", "-2").andExpect(status().isOk());

        Map<String, Object> seen = kit.seekerView(s.seeker(), id);
        assertEquals("DUE", seen.get("state"));
        assertEquals("PENDING", seen.get("lastAttempt"), "still waiting for the real answer");
        assertEquals(1, rows("order_id = '" + order + "' and status = 'PENDING' and status_code is null and payhere_payment_id is null"));
        dispatcher.dispatchDue();
        assertTrue(mails(s.seeker().email(), "Your payment").isEmpty());
    }

    @Test
    void theSameCallbackSentTwiceCountsOnce() throws Exception {
        Scene s = kit.scene();
        String id = kit.completed(s, "200");
        dispatcher.dispatchDue();
        SENDER.sent.clear();
        String order = startPaying(s, id);

        genuine(order, "200.00", "2").andExpect(status().isOk());
        genuine(order, "200.00", "2").andExpect(status().isOk());   // PayHere repeats itself when it did not hear back
        genuine(order, "200.00", "-2").andExpect(status().isOk());   // a late "failed" never undoes a payment

        assertEquals("PAID", state(s, id));
        assertEquals(1, rows("booking_id = " + id + " and status = 'PAID'"));
        assertEquals(0, rows("booking_id = " + id + " and status <> 'PAID'"));
        dispatcher.dispatchDue();
        assertEquals(1, mails(s.seeker().email(), "Your payment").size());
        assertEquals(1, mails("alerts@homecare.test", "Payment received").size());
        assertEquals(false, kit.seekerView(s.seeker(), id).get("needsAttention"));
    }

    @Test
    void aFailedOrCancelledTryCanBeFollowedByAGoodOne() throws Exception {
        Scene s = kit.scene();
        String id = kit.completed(s, "200");

        String first = startPaying(s, id);
        genuine(first, "200.00", "0").andExpect(status().isOk());
        assertEquals("PENDING", kit.seekerView(s.seeker(), id).get("lastAttempt"));
        genuine(first, "200.00", "-2").andExpect(status().isOk());
        Map<String, Object> seen = kit.seekerView(s.seeker(), id);
        assertEquals("DUE", seen.get("state"));
        assertEquals("FAILED", seen.get("lastAttempt"));

        String second = startPaying(s, id);
        assertNotEquals(first, second, "every try has its own order");
        genuine(second, "200.00", "-1").andExpect(status().isOk());
        assertEquals("CANCELED", kit.seekerView(s.seeker(), id).get("lastAttempt"));

        String third = startPaying(s, id);
        genuine(third, "200.00", "2").andExpect(status().isOk());
        assertEquals("PAID", state(s, id));
        assertEquals(1, rows("order_id = '" + first + "' and status = 'FAILED'"));
        assertEquals(1, rows("order_id = '" + second + "' and status = 'CANCELED'"));
    }

    @Test
    void aSecondPaymentIsKeptAndFlaggedForARefund() throws Exception {
        Scene s = kit.scene();
        String id = kit.completed(s, "200");
        dispatcher.dispatchDue();
        SENDER.sent.clear();
        String a = startPaying(s, id);
        String b = startPaying(s, id);   // two tabs open at once

        genuine(a, "200.00", "2").andExpect(status().isOk());
        genuine(b, "200.00", "2").andExpect(status().isOk());   // the money was taken, so it is recorded, not ignored

        Map<String, Object> seen = kit.seekerView(s.seeker(), id);
        assertEquals("PAID", seen.get("state"));
        assertEquals(true, seen.get("needsAttention"));
        assertEquals(true, kit.adminView(s.admin(), id).get("needsAttention"));
        assertEquals(2, rows("booking_id = " + id + " and status = 'PAID'"));
        dispatcher.dispatchDue();
        assertEquals(1, mails(s.seeker().email(), "Your payment").size(), "one receipt, not two");
        assertEquals(1, mails("alerts@homecare.test", "Payment received").size());
    }

    @Test
    void aChargeBackPutsTheFeeBackToDueAndFlagsIt() throws Exception {
        Scene s = kit.scene();
        String id = kit.completed(s, "200");
        String order = startPaying(s, id);
        genuine(order, "200.00", "2").andExpect(status().isOk());
        assertEquals("PAID", state(s, id));

        genuine(order, "200.00", "-3").andExpect(status().isOk());

        Map<String, Object> seen = kit.seekerView(s.seeker(), id);
        assertEquals("DUE", seen.get("state"));
        assertEquals("CHARGEDBACK", seen.get("lastAttempt"));
        assertEquals(true, seen.get("needsAttention"));
        mvc.perform(post("/api/bookings/" + id + "/pay").header("Authorization", s.seeker().auth())).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ the admin sorts things out

    @Test
    void changingTheFeeMakesNewAttemptsUseItAndFlagsMoneyThatDoesNotMatch() throws Exception {
        Scene s = kit.scene();
        String id = kit.completed(s, "500");
        String old = startPaying(s, id);
        dispatcher.dispatchDue();
        SENDER.sent.clear();

        mvc.perform(put("/api/admin/bookings/" + id + "/fee").header("Authorization", s.admin().auth()).contentType(JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/bookings/" + id + "/fee").header("Authorization", s.admin().auth()).contentType(JSON).content("{\"fee\":0}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/bookings/" + id + "/fee").header("Authorization", s.admin().auth()).contentType(JSON).content("{\"fee\":600}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payment.fee").value(600.0)).andExpect(jsonPath("$.payment.state").value("DUE"));

        dispatcher.dispatchDue();
        List<FakeSender.Mail> told = mails(s.seeker().email(), "Please pay");
        assertEquals(1, told.size());
        assertTrue(told.get(0).body().contains("LKR 600.00"), told.get(0).body());

        String res = mvc.perform(post("/api/bookings/" + id + "/pay").header("Authorization", s.seeker().auth())).andReturn().getResponse().getContentAsString();
        assertEquals("600.00", JsonPath.read(res, "$.fields.amount"));
        assertEquals(1, rows("order_id = '" + old + "' and status = 'CANCELED'"), "the attempt for the old amount was closed");

        // the seeker still had the old page open and paid 500: PayHere took it, so it is recorded and flagged
        genuine(old, "500.00", "2").andExpect(status().isOk());
        Map<String, Object> seen = kit.adminView(s.admin(), id);
        assertEquals("PAID", seen.get("state"));
        assertEquals(true, seen.get("needsAttention"));
    }

    @Test
    void theFeeOnlyChangesForACompletedVisit() throws Exception {
        Scene s = kit.scene();
        String id = kit.confirmed(s);
        mvc.perform(put("/api/admin/bookings/" + id + "/fee").header("Authorization", s.admin().auth()).contentType(JSON).content("{\"fee\":600}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/admin/bookings/999999/fee").header("Authorization", s.admin().auth()).contentType(JSON).content("{\"fee\":600}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void anAdminCanSendThePaymentEmailAgain() throws Exception {
        Scene s = kit.scene();
        String id = kit.completed(s, "300");
        dispatcher.dispatchDue();
        SENDER.sent.clear();

        mvc.perform(post("/api/admin/bookings/" + id + "/payment/resend").header("Authorization", s.admin().auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payment.state").value("DUE"));
        dispatcher.dispatchDue();
        assertEquals(1, mails(s.seeker().email(), "Please pay").size());

        String noFee = kit.confirmed(s);
        mvc.perform(post("/api/admin/bookings/" + noFee + "/payment/resend").header("Authorization", s.admin().auth())).andExpect(status().isConflict());
    }

    @Test
    void anAdminCanRecordAPaymentMadeOutsidePayHere() throws Exception {
        Scene s = kit.scene();
        String id = kit.completed(s, "300");
        String order = startPaying(s, id);   // the seeker had started online, then paid at the bank instead
        dispatcher.dispatchDue();
        SENDER.sent.clear();

        mvc.perform(post("/api/admin/bookings/" + id + "/payment/mark-paid").header("Authorization", s.admin().auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.state").value("PAID"))
                .andExpect(jsonPath("$.payment.source").value("MANUAL"));

        assertEquals("PAID", state(s, id));
        assertEquals(1, rows("booking_id = " + id + " and source = 'MANUAL' and status = 'PAID' and amount = 300.00"));
        dispatcher.dispatchDue();
        assertEquals(1, mails(s.seeker().email(), "Your payment").size(), "the seeker gets a receipt");
        assertTrue(mails("alerts@homecare.test", "Payment received").isEmpty(), "the admin did it themselves, so no alert");
        mvc.perform(post("/api/bookings/" + id + "/pay").header("Authorization", s.seeker().auth())).andExpect(status().isConflict());
        mvc.perform(post("/api/admin/bookings/" + id + "/payment/mark-paid").header("Authorization", s.admin().auth())).andExpect(status().isConflict());

        // the online try that was open can still land, and is then flagged as a double payment
        genuine(order, "300.00", "2").andExpect(status().isOk());
        assertEquals(true, kit.seekerView(s.seeker(), id).get("needsAttention"));

        String noFee = kit.confirmed(s);
        mvc.perform(post("/api/admin/bookings/" + noFee + "/payment/mark-paid").header("Authorization", s.admin().auth())).andExpect(status().isConflict());
    }

    @Test
    void onlyAdminsCanUseTheAdminPaymentRoutes() throws Exception {
        Scene s = kit.scene();
        String id = kit.completed(s, "300");
        String seeker = s.seeker().auth();

        mvc.perform(put("/api/admin/bookings/" + id + "/fee").header("Authorization", seeker).contentType(JSON).content("{\"fee\":1}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/bookings/" + id + "/payment/resend").header("Authorization", seeker)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/bookings/" + id + "/payment/mark-paid").header("Authorization", seeker)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/bookings/" + id + "/payment/mark-paid").header("Authorization", s.caregiver().auth())).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/bookings/" + id + "/payment/mark-paid")).andExpect(status().isUnauthorized());
        assertEquals("DUE", state(s, id));
    }

    // ------------------------------------------------------------------ side effects on the rest of the app

    @Test
    void paymentEmailsDoNotReplaceTheNewRequestAlertOnTheAdminPage() throws Exception {
        Scene s = kit.scene();
        String id = kit.confirmed(s);
        dispatcher.dispatchDue();
        mvc.perform(get("/api/admin/bookings").header("Authorization", s.admin().auth()))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].alert.status").value("SENT"));

        kit.setStatus(s, id, "{\"status\":\"COMPLETED\",\"fee\":300}").andExpect(status().isOk());
        String order = startPaying(s, id);
        genuine(order, "300.00", "2").andExpect(status().isOk());
        dispatcher.dispatchDue();   // the fee and receipt emails go out too

        assertFalse(mails(s.seeker().email(), "Please pay").isEmpty());
        mvc.perform(get("/api/admin/bookings").header("Authorization", s.admin().auth()))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].alert.status").value("SENT"))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].alert.channel").value("EMAIL"));
        // sending the alert again looks only at the alert for the new request
        mvc.perform(post("/api/admin/bookings/" + id + "/resend-alert").header("Authorization", s.admin().auth())).andExpect(status().isConflict());
    }

    @Test
    void thePretendPayHereIsNotAvailableAgainstTheRealOne() throws Exception {
        Scene s = kit.scene();
        String id = kit.completed(s, "300");
        String order = startPaying(s, id);
        mvc.perform(post("/api/payments/simulate").header("Authorization", s.seeker().auth()).contentType(JSON)
                .content("{\"orderId\":\"" + order + "\",\"outcome\":\"success\"}")).andExpect(status().isNotFound());
        assertEquals("DUE", state(s, id), "a seeker cannot pay for free");
    }
}
