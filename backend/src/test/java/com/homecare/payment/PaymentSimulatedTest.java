package com.homecare.payment;

import com.homecare.payment.PaymentTestKit.Person;
import com.homecare.payment.PaymentTestKit.Scene;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static com.homecare.payment.PaymentTestKit.JSON;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The built-in pretend PayHere, for trying the whole flow on one computer. It goes through the same checks as a real callback. */
@SpringBootTest(properties = {
        "app.payhere.mode=simulated",
        "spring.datasource.url=jdbc:h2:mem:payments-simulated;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentSimulatedTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private PaymentTestKit kit;

    @BeforeEach
    void start() {
        kit = new PaymentTestKit(mvc, jdbc, "ps");
    }

    private String startPaying(Scene s, String bookingId) throws Exception {
        String res = mvc.perform(post("/api/bookings/" + bookingId + "/pay").header("Authorization", s.seeker().auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("SIMULATED"))
                .andExpect(jsonPath("$.actionUrl").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.orderId");
    }

    private ResultActions simulate(Person who, String order, String outcome) throws Exception {
        return mvc.perform(post("/api/payments/simulate").header("Authorization", who.auth()).contentType(JSON)
                .content("{\"orderId\":\"" + order + "\",\"outcome\":\"" + outcome + "\"}"));
    }

    @Test
    void aSeekerCanFailThenPayInThePretendPayHere() throws Exception {
        Scene s = kit.scene();
        mvc.perform(get("/api/payments/config").header("Authorization", s.seeker().auth())).andExpect(jsonPath("$.mode").value("SIMULATED"));
        String id = kit.completed(s, "800");

        String first = startPaying(s, id);
        simulate(s.seeker(), first, "failed").andExpect(status().isNoContent());
        Map<String, Object> seen = kit.seekerView(s.seeker(), id);
        assertEquals("DUE", seen.get("state"));
        assertEquals("FAILED", seen.get("lastAttempt"));

        String second = startPaying(s, id);
        simulate(s.seeker(), second, "cancel").andExpect(status().isNoContent());
        assertEquals("CANCELED", kit.seekerView(s.seeker(), id).get("lastAttempt"));

        String third = startPaying(s, id);
        simulate(s.seeker(), third, "success").andExpect(status().isNoContent());
        seen = kit.seekerView(s.seeker(), id);
        assertEquals("PAID", seen.get("state"));
        assertNotNull(seen.get("paidAt"));
        assertEquals("PAID", kit.adminView(s.admin(), id).get("state"));
        assertEquals(1, jdbc.queryForObject("select count(*) from payments where booking_id = ? and status = 'PAID' and method = 'SIMULATED'", Integer.class, Long.valueOf(id)));
        assertEquals(1, jdbc.queryForObject("select count(*) from notifications where booking_id = ? and template = 'payment_receipt'", Integer.class, Long.valueOf(id)));

        simulate(s.seeker(), third, "success").andExpect(status().isNoContent());   // pressing it twice changes nothing
        assertEquals(1, jdbc.queryForObject("select count(*) from notifications where booking_id = ? and template = 'payment_receipt'", Integer.class, Long.valueOf(id)));
        mvc.perform(post("/api/bookings/" + id + "/pay").header("Authorization", s.seeker().auth())).andExpect(status().isConflict());
    }

    @Test
    void onlyTheOwnerOfThePaymentCanUseIt() throws Exception {
        Scene s = kit.scene();
        String id = kit.completed(s, "800");
        String order = startPaying(s, id);
        Person other = kit.register("Other Seeker", "CARE_SEEKER");

        simulate(other, order, "success").andExpect(status().isNotFound());
        simulate(s.caregiver(), order, "success").andExpect(status().isNotFound());
        mvc.perform(post("/api/payments/simulate").contentType(JSON).content("{\"orderId\":\"" + order + "\",\"outcome\":\"success\"}")).andExpect(status().isUnauthorized());
        simulate(s.seeker(), "HC1-NOSUCHORDER", "success").andExpect(status().isNotFound());
        simulate(s.seeker(), order, "refund").andExpect(status().isBadRequest());
        mvc.perform(post("/api/payments/simulate").header("Authorization", s.seeker().auth()).contentType(JSON).content("{}")).andExpect(status().isBadRequest());
        assertEquals("DUE", kit.seekerView(s.seeker(), id).get("state"));
    }

    @Test
    void noOneOutsideCanForgeACallbackBecauseTheSecretIsPrivateToThisRun() throws Exception {
        Scene s = kit.scene();
        String id = kit.completed(s, "800");
        String order = startPaying(s, id);

        // the made-up merchant id is easy to guess, so the secret is what matters: a guessed one must not work
        for (String guess : new String[]{"simulated-secret-not-a-real-one", "", "secret"}) {
            String sig = PayHereSigner.notifySignature("SIMULATED", order, "800.00", "LKR", "2", guess);
            mvc.perform(post("/api/payments/payhere/notify").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("merchant_id", "SIMULATED").param("order_id", order).param("payhere_amount", "800.00")
                    .param("payhere_currency", "LKR").param("status_code", "2").param("md5sig", sig)).andExpect(status().isBadRequest());
        }
        assertEquals("DUE", kit.seekerView(s.seeker(), id).get("state"));
    }
}
