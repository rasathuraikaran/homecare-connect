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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The receipt a seeker sends after a bank transfer: stored privately, emailed to the admin with the file attached. */
@SpringBootTest(properties = {
        "app.payhere.mode=off",
        "app.payhere.site-url=https://shop.homecare.test",
        "app.bank.name=Test Bank",
        "app.bank.account-name=Karan Rasa",
        "app.bank.account-number=1234567890",
        "spring.datasource.url=jdbc:h2:mem:payments-receipts;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PaymentReceiptTest.Config.class)
class PaymentReceiptTest {

    static final RecordingSender SENDER = new RecordingSender();
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 1};
    private static final byte[] PDF = "%PDF-1.4 a receipt".getBytes();

    @TestConfiguration
    static class Config {
        @Bean @Primary RecordingSender recordingSender() { return SENDER; }
    }

    @Autowired MockMvc mvc;
    @Autowired NotificationDispatcher dispatcher;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReceiptStorage storage;

    private PaymentTestKit kit;

    @BeforeEach
    void start() {
        kit = new PaymentTestKit(mvc, jdbc, "pr");
        SENDER.sent.clear();
        dispatcher.dispatchDue();
        SENDER.sent.clear();
    }

    private void bank(Scene s, boolean on) throws Exception {
        kit.setSwitches(s.admin(), "{\"bankEnabled\":" + on + "}").andExpect(status().isOk());
    }

    /** A seeker with a completed visit that is waiting for a bank transfer (total 1050). */
    private String owing(Scene s) throws Exception {
        bank(s, true);
        return kit.completedWithCharge(s, "1000", "50");
    }

    private ResultActions upload(Person who, String id, byte[] bytes, String note) throws Exception {
        var req = multipart("/api/bookings/" + id + "/pay/receipt").file(new MockMultipartFile("file", "slip.png", "image/png", bytes));
        if (note != null) req.param("note", note);
        return mvc.perform(req.header("Authorization", who.auth()));
    }

    @Test
    void theReceiptIsKeptForTheAdminAndEmailedToThemWithTheFileAttached() throws Exception {
        Scene s = kit.scene();
        String id = owing(s);

        upload(s.seeker(), id, PNG, "  Paid from HNB today  ").andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber()).andExpect(jsonPath("$.note").value("Paid from HNB today"));

        // both sides see that a receipt was sent; it never marks anything paid
        Map<String, Object> seen = kit.seekerView(s.seeker(), id);
        assertEquals("DUE", seen.get("state"));
        List<?> sent = (List<?>) seen.get("receipts");
        assertEquals(1, sent.size());
        assertEquals(1, ((List<?>) kit.adminView(s.admin(), id).get("receipts")).size());
        Number receiptId = (Number) ((Map<?, ?>) sent.get(0)).get("id");

        dispatcher.dispatchDue();
        List<RecordingSender.Mail> mails = SENDER.to("alerts@homecare.test", "Receipt received for request #" + id);
        assertEquals(1, mails.size());
        String body = mails.get(0).body();
        assertTrue(body.contains("Sam Seeker says they paid by bank transfer"), body);
        assertTrue(body.contains("Amount: LKR 1050.00"), body);
        assertTrue(body.contains("HC" + id), body);
        assertTrue(body.contains("Their note: Paid from HNB today"), body);
        assertTrue(body.contains("https://shop.homecare.test/admin"), body);
        assertEquals(1, mails.get(0).attachments().size());
        assertEquals("receipt-HC" + id + ".png", mails.get(0).attachments().get(0).fileName());
        assertEquals("image/png", mails.get(0).attachments().get(0).contentType());
        assertArrayEquals(PNG, mails.get(0).attachments().get(0).content());

        // the admin can open the file; nobody else can, and it is never cached
        mvc.perform(get("/api/admin/bookings/" + id + "/receipts/" + receiptId + "/file").header("Authorization", s.admin().auth()))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(PNG));
        mvc.perform(get("/api/admin/bookings/" + id + "/receipts/" + receiptId + "/file").header("Authorization", s.seeker().auth())).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/bookings/" + id + "/receipts/" + receiptId + "/file")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/bookings/" + id + "/receipts/999999/file").header("Authorization", s.admin().auth())).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/bookings/999999/receipts/" + receiptId + "/file").header("Authorization", s.admin().auth())).andExpect(status().isNotFound());

        // a PDF works too, and the admin still has to mark it paid
        upload(s.seeker(), id, PDF, null).andExpect(status().isCreated()).andExpect(jsonPath("$.note").doesNotExist());
        dispatcher.dispatchDue();
        assertEquals("receipt-HC" + id + ".pdf", SENDER.to("alerts@homecare.test", "Receipt received").get(1).attachments().get(0).fileName());
        mvc.perform(post("/api/admin/bookings/" + id + "/payment/mark-paid").header("Authorization", s.admin().auth())).andExpect(status().isOk());
        bank(s, false);
    }

    @Test
    void onlyTheOwnerOfAnUnpaidBankTransferCanSendAnActualReceipt() throws Exception {
        Scene s = kit.scene();
        String id = owing(s);
        Person other = kit.register("Other Seeker", "CARE_SEEKER");

        upload(other, id, PNG, null).andExpect(status().isNotFound());
        upload(s.admin(), id, PNG, null).andExpect(status().isNotFound());
        upload(s.caregiver(), id, PNG, null).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/bookings/" + id + "/pay/receipt").file(new MockMultipartFile("file", "a.png", "image/png", PNG))).andExpect(status().isUnauthorized());

        // the file's content decides, not its name or what the browser says
        upload(s.seeker(), id, "just some text".getBytes(), null).andExpect(status().isBadRequest());
        upload(s.seeker(), id, new byte[0], null).andExpect(status().isBadRequest());
        upload(s.seeker(), id, new byte[5 * 1024 * 1024 + 1], null).andExpect(status().isPayloadTooLarge());
        mvc.perform(multipart("/api/bookings/" + id + "/pay/receipt").header("Authorization", s.seeker().auth())).andExpect(status().isBadRequest());
        upload(s.seeker(), id, PNG, "x".repeat(301)).andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("select count(*) from payment_receipts where booking_id = ?", Integer.class, Long.valueOf(id)));
        dispatcher.dispatchDue();
        assertTrue(SENDER.to("alerts@homecare.test", "Receipt received").isEmpty(), "a refused upload tells the admin nothing");

        // five receipts at most for one request
        for (int i = 0; i < 5; i++) upload(s.seeker(), id, PNG, null).andExpect(status().isCreated());
        upload(s.seeker(), id, PNG, null).andExpect(status().isConflict());

        // nothing to send a receipt for: not completed, already paid, or the switch is off
        String waiting = kit.confirmed(new Scene(s.admin(), kit.register("Pat Seeker", "CARE_SEEKER"), s.caregiver()));
        upload(s.seeker(), waiting, PNG, null).andExpect(status().isNotFound());   // someone else's request
        mvc.perform(post("/api/admin/bookings/" + id + "/payment/mark-paid").header("Authorization", s.admin().auth())).andExpect(status().isOk());
        upload(s.seeker(), id, PNG, null).andExpect(status().isConflict());
        bank(s, false);
    }

    @Test
    void withoutABankTransferOrACompletedVisitThereIsNothingToSendAReceiptFor() throws Exception {
        Scene s = kit.scene();
        bank(s, true);
        String confirmed = kit.confirmed(s);
        upload(s.seeker(), confirmed, PNG, null).andExpect(status().isConflict());   // not completed yet

        kit.setStatus(s, confirmed, "{\"status\":\"COMPLETED\",\"fee\":50}").andExpect(status().isOk());
        bank(s, false);
        upload(s.seeker(), confirmed, PNG, null).andExpect(status().isConflict());   // bank transfer switched off
    }

    @Test
    void deletingTheSeekersAccountAlsoRemovesTheReceiptFiles() throws Exception {
        Scene s = kit.scene();
        String id = owing(s);
        upload(s.seeker(), id, PNG, null).andExpect(status().isCreated());
        String file = jdbc.queryForObject("select file_name from payment_receipts where booking_id = ?", String.class, Long.valueOf(id));
        assertTrue(storage.exists(file));

        mvc.perform(delete("/api/admin/users/" + s.seeker().id()).header("Authorization", s.admin().auth())).andExpect(status().isNoContent());

        assertFalse(storage.exists(file), "the file went with the account");
        assertEquals(0, jdbc.queryForObject("select count(*) from payment_receipts where booking_id = ?", Integer.class, Long.valueOf(id)));
        bank(s, false);
    }
}
