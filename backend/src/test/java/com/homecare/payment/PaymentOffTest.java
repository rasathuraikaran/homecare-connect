package com.homecare.payment;

import com.homecare.payment.PaymentTestKit.Scene;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static com.homecare.payment.PaymentTestKit.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The default: online payments are off, so the app behaves exactly as it did before they existed. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentOffTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private PaymentTestKit kit;

    @BeforeEach
    void start() {
        kit = new PaymentTestKit(mvc, jdbc, "po");
    }

    @Test
    void completingAVisitAsksForNoFeeAndNothingCanBePaid() throws Exception {
        Scene s = kit.scene();
        mvc.perform(get("/api/payments/config").header("Authorization", s.seeker().auth())).andExpect(jsonPath("$.mode").value("OFF"));
        String id = kit.confirmed(s);

        kit.setStatus(s, id, "{\"status\":\"COMPLETED\",\"fee\":100}").andExpect(status().isBadRequest());   // no fees while payments are off
        kit.setStatus(s, id, "{\"status\":\"COMPLETED\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.payment.state").value("NONE"));

        assertEquals("NONE", kit.seekerView(s.seeker(), id).get("state"));
        mvc.perform(post("/api/bookings/" + id + "/pay").header("Authorization", s.seeker().auth())).andExpect(status().isConflict());
        mvc.perform(post("/api/admin/bookings/" + id + "/payment/resend").header("Authorization", s.admin().auth())).andExpect(status().isConflict());
        mvc.perform(post("/api/payments/simulate").header("Authorization", s.seeker().auth()).contentType(JSON)
                .content("{\"orderId\":\"HC1-X\",\"outcome\":\"success\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/payments/payhere/notify").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("merchant_id", "").param("order_id", "HC1-X")).andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("select count(*) from notifications where template in ('fee_due', 'payment_receipt', 'payment_received')", Integer.class));
    }
}
