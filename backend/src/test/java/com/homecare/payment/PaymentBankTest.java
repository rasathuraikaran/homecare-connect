package com.homecare.payment;

import com.homecare.notification.NotificationDispatcher;
import com.homecare.payment.PaymentTestKit.Person;
import com.homecare.payment.PaymentTestKit.RecordingSender;
import com.homecare.payment.PaymentTestKit.Scene;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static com.homecare.payment.PaymentTestKit.JSON;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Paying by bank transfer, with PayHere not set up: the admin's switches, the total and the bank details. */
@SpringBootTest(properties = {
        "app.payhere.mode=off",
        "app.payhere.site-url=https://shop.homecare.test",
        "app.bank.name=Test Bank",
        "app.bank.branch=Jaffna",
        "app.bank.account-name=Karan Rasa",
        "app.bank.account-number=1234567890",
        "spring.datasource.url=jdbc:h2:mem:payments-bank;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PaymentBankTest.Config.class)
class PaymentBankTest {

    static final RecordingSender SENDER = new RecordingSender();

    @TestConfiguration
    static class Config {
        @Bean @Primary RecordingSender recordingSender() { return SENDER; }
    }

    @Autowired MockMvc mvc;
    @Autowired NotificationDispatcher dispatcher;
    @Autowired JdbcTemplate jdbc;

    private PaymentTestKit kit;

    @BeforeEach
    void start() {
        kit = new PaymentTestKit(mvc, jdbc, "pb");
        SENDER.sent.clear();
        dispatcher.dispatchDue();
        SENDER.sent.clear();
    }

    private void bank(Scene s, boolean on) throws Exception {
        kit.setSwitches(s.admin(), "{\"bankEnabled\":" + on + "}").andExpect(status().isOk());
    }

    @Test
    void onlyAdminsChangeTheSwitchesAndAWayThatIsNotSetUpCannotBeSwitchedOn() throws Exception {
        Scene s = kit.scene();
        bank(s, false);

        mvc.perform(get("/api/admin/payment-settings").header("Authorization", s.admin().auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payhere.on").value(false))
                .andExpect(jsonPath("$.payhere.available").value(false))
                .andExpect(jsonPath("$.payhereMode").value("OFF"))
                .andExpect(jsonPath("$.bank.on").value(false))
                .andExpect(jsonPath("$.bank.available").value(true));

        mvc.perform(get("/api/admin/payment-settings").header("Authorization", s.seeker().auth())).andExpect(status().isForbidden());
        kit.setSwitches(s.seeker(), "{\"bankEnabled\":true}").andExpect(status().isForbidden());
        kit.setSwitches(s.caregiver(), "{\"bankEnabled\":true}").andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/payment-settings").contentType(JSON).content("{\"bankEnabled\":true}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/payments/config").header("Authorization", s.seeker().auth())).andExpect(jsonPath("$.bank").value(false));

        kit.setSwitches(s.admin(), "{\"payhereEnabled\":true}").andExpect(status().isBadRequest());   // no PayHere keys on this server
        kit.setSwitches(s.admin(), "{}").andExpect(status().isOk());                                   // nothing given, nothing changed

        kit.setSwitches(s.admin(), "{\"bankEnabled\":true}").andExpect(status().isOk()).andExpect(jsonPath("$.bank.on").value(true));
        String config = mvc.perform(get("/api/payments/config").header("Authorization", s.seeker().auth()))
                .andExpect(jsonPath("$.bank").value(true)).andExpect(jsonPath("$.payhere").value(false))
                .andReturn().getResponse().getContentAsString();
        assertFalse(config.contains("1234567890") || config.contains("Test Bank"), "the config holds no bank details: " + config);
        bank(s, false);
    }

    @Test
    void withBothWaysOffCompletingAVisitAsksForNothing() throws Exception {
        Scene s = kit.scene();
        bank(s, false);
        String id = kit.confirmed(s);

        kit.setStatus(s, id, "{\"status\":\"COMPLETED\",\"fee\":100}").andExpect(status().isBadRequest());
        kit.setStatus(s, id, "{\"status\":\"COMPLETED\",\"caregiverAmount\":100}").andExpect(status().isBadRequest());
        kit.setStatus(s, id, "{\"status\":\"COMPLETED\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.payment.state").value("NONE"));

        dispatcher.dispatchDue();
        assertTrue(SENDER.to(s.seeker().email(), "").isEmpty(), "the seeker is told nothing about paying");
        mvc.perform(get("/api/bookings/" + id + "/pay/bank").header("Authorization", s.seeker().auth())).andExpect(status().isConflict());
    }

    @Test
    void theSeekerPaysTheCaregiversChargePlusTheFeeToTheAdminsAccount() throws Exception {
        Scene s = kit.scene();
        bank(s, true);
        String confirmed = kit.confirmed(s);
        kit.setStatus(s, confirmed, "{\"status\":\"COMPLETED\"}").andExpect(status().isBadRequest());                                  // the fee is needed
        kit.setStatus(s, confirmed, "{\"status\":\"COMPLETED\",\"fee\":450,\"caregiverAmount\":-1}").andExpect(status().isBadRequest());
        kit.setStatus(s, confirmed, "{\"status\":\"COMPLETED\",\"fee\":450,\"caregiverAmount\":9000}").andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.fee").value(450.0))
                .andExpect(jsonPath("$.payment.caregiverAmount").value(9000.0))
                .andExpect(jsonPath("$.payment.total").value(9450.0))
                .andExpect(jsonPath("$.payment.state").value("DUE"));
        String id = confirmed;

        Map<String, Object> seen = kit.seekerView(s.seeker(), id);
        assertEquals(9450.0, ((Number) seen.get("total")).doubleValue());
        assertEquals(9000.0, ((Number) seen.get("caregiverAmount")).doubleValue());

        mvc.perform(get("/api/bookings/" + id + "/pay/bank").header("Authorization", s.seeker().auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bank").value("Test Bank"))
                .andExpect(jsonPath("$.branch").value("Jaffna"))
                .andExpect(jsonPath("$.accountName").value("Karan Rasa"))
                .andExpect(jsonPath("$.accountNumber").value("1234567890"))
                .andExpect(jsonPath("$.reference").value("HC" + id))
                .andExpect(jsonPath("$.total").value(9450.0));
        // the details are for the owner only
        Person other = kit.register("Other Seeker", "CARE_SEEKER");
        mvc.perform(get("/api/bookings/" + id + "/pay/bank").header("Authorization", other.auth())).andExpect(status().isNotFound());
        mvc.perform(get("/api/bookings/" + id + "/pay/bank").header("Authorization", s.admin().auth())).andExpect(status().isNotFound());
        mvc.perform(get("/api/bookings/" + id + "/pay/bank").header("Authorization", s.caregiver().auth())).andExpect(status().isForbidden());
        mvc.perform(get("/api/bookings/" + id + "/pay/bank")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/bookings/" + id + "/pay").header("Authorization", s.seeker().auth())).andExpect(status().isConflict());   // no card

        dispatcher.dispatchDue();
        List<RecordingSender.Mail> due = SENDER.to(s.seeker().email(), "Please pay");
        assertEquals(1, due.size());
        String body = due.get(0).body();
        assertTrue(body.contains("LKR 9450.00"), body);
        assertTrue(body.contains("LKR 9000.00 for the caregiver + LKR 450.00 service fee"), body);
        assertTrue(body.contains("Bank: Test Bank, Branch: Jaffna, Account name: Karan Rasa, Account number: 1234567890"), body);
        assertTrue(body.contains("reference HC" + id), body);
        assertFalse(body.contains("Pay now"), "card payment is off, so the email does not offer it: " + body);

        assertEquals(9450.0, ((Number) kit.adminView(s.admin(), id).get("total")).doubleValue());

        // the admin sees the money in the bank and marks it paid
        mvc.perform(post("/api/admin/bookings/" + id + "/payment/mark-paid").header("Authorization", s.admin().auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payment.state").value("PAID"));
        assertEquals(1, jdbc.queryForObject("select count(*) from payments where booking_id = ? and source = 'MANUAL' and status = 'PAID' and amount = 9450.00", Integer.class, Long.valueOf(id)));
        dispatcher.dispatchDue();
        List<RecordingSender.Mail> receipt = SENDER.to(s.seeker().email(), "Your payment for request");
        assertEquals(1, receipt.size());
        assertTrue(receipt.get(0).body().contains("LKR 9450.00"), receipt.get(0).body());
        mvc.perform(get("/api/bookings/" + id + "/pay/bank").header("Authorization", s.seeker().auth())).andExpect(status().isConflict());
        bank(s, false);
    }

    @Test
    void theFeeAloneStillWorksAndChangingTheAmountsUpdatesTheTotalAndTellsTheSeeker() throws Exception {
        Scene s = kit.scene();
        bank(s, true);
        String id = kit.completed(s, "300");
        assertEquals(300.0, ((Number) kit.seekerView(s.seeker(), id).get("total")).doubleValue());
        assertNull(kit.seekerView(s.seeker(), id).get("caregiverAmount"));
        dispatcher.dispatchDue();
        SENDER.sent.clear();

        mvc.perform(put("/api/admin/bookings/" + id + "/fee").header("Authorization", s.admin().auth()).contentType(JSON).content("{\"fee\":500,\"caregiverAmount\":8000}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payment.total").value(8500.0));
        dispatcher.dispatchDue();
        assertTrue(SENDER.to(s.seeker().email(), "Please pay").get(0).body().contains("LKR 8500.00"));

        mvc.perform(put("/api/admin/bookings/" + id + "/fee").header("Authorization", s.admin().auth()).contentType(JSON).content("{\"fee\":500,\"caregiverAmount\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payment.total").value(500.0)).andExpect(jsonPath("$.payment.caregiverAmount").doesNotExist());
        mvc.perform(put("/api/admin/bookings/" + id + "/fee").header("Authorization", s.admin().auth()).contentType(JSON).content("{\"fee\":500,\"caregiverAmount\":-5}"))
                .andExpect(status().isBadRequest());
        bank(s, false);
    }

    @Test
    void switchingBankTransferOffStopsNewAsksButWhatIsAlreadyOwedCanStillBeRecorded() throws Exception {
        Scene s = kit.scene();
        bank(s, true);
        String owed = kit.completedWithCharge(s, "9000", "450");

        bank(s, false);
        mvc.perform(get("/api/bookings/" + owed + "/pay/bank").header("Authorization", s.seeker().auth())).andExpect(status().isConflict());
        assertEquals("DUE", kit.seekerView(s.seeker(), owed).get("state"), "the fee is still owed");
        mvc.perform(post("/api/admin/bookings/" + owed + "/payment/resend").header("Authorization", s.admin().auth())).andExpect(status().isConflict());
        mvc.perform(post("/api/admin/bookings/" + owed + "/payment/mark-paid").header("Authorization", s.admin().auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payment.state").value("PAID"));

        String next = kit.confirmed(s);   // the earlier one is completed, so the same seeker can ask again
        kit.setStatus(s, next, "{\"status\":\"COMPLETED\",\"fee\":50}").andExpect(status().isBadRequest());
        kit.setStatus(s, next, "{\"status\":\"COMPLETED\"}").andExpect(status().isOk()).andExpect(jsonPath("$.payment.state").value("NONE"));
    }
}
