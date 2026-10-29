package com.homecare.notification;

import com.jayway.jsonpath.JsonPath;
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

/** Email is the default channel; this proves NOTIFY_CHANNEL=whatsapp still sends the same details by WhatsApp. */
@SpringBootTest(properties = {
        "app.notifications.channel=whatsapp",
        "app.whatsapp.admin-to=94771234567",
        "spring.datasource.url=jdbc:h2:mem:whatsapp-channel;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})   // its own database, apart from the other tests
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(WhatsAppChannelTest.Config.class)
class WhatsAppChannelTest {

    static class FakeWhatsApp implements WhatsAppSender {
        record Message(String to, String template, List<String> params) {}

        final List<Message> sent = Collections.synchronizedList(new ArrayList<>());

        @Override
        public Outcome send(String to, String template, String language, List<String> bodyParams) {
            sent.add(new Message(to, template, bodyParams));
            return Outcome.SENT;
        }
    }

    static class FakeMail implements EmailSender {
        final List<String> sent = Collections.synchronizedList(new ArrayList<>());

        @Override
        public Outcome send(String to, String subject, String body) {
            sent.add(subject);
            return Outcome.SENT;
        }
    }

    static final FakeWhatsApp WHATSAPP = new FakeWhatsApp();
    static final FakeMail MAIL = new FakeMail();

    @TestConfiguration
    static class Config {
        @Bean @Primary FakeWhatsApp fakeWhatsApp() { return WHATSAPP; }
        @Bean @Primary FakeMail fakeMail() { return MAIL; }
    }

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired MockMvc mvc;
    @Autowired NotificationDispatcher dispatcher;
    @Autowired JdbcTemplate jdbc;

    private String[] register(String email, String name, String role) throws Exception {
        String res = mvc.perform(post("/api/auth/register").contentType(JSON)
                        .content("{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"password123\",\"role\":\"%s\"}".formatted(name, email, role)))
                .andReturn().getResponse().getContentAsString();
        return new String[]{"Bearer " + JsonPath.read(res, "$.token"), String.valueOf((Integer) JsonPath.read(res, "$.user.id"))};
    }

    @Test
    void whenWhatsAppIsChosenTheAlertGoesThereWithAllDetailsAndNotByEmail() throws Exception {
        String[] nina = register("wa-nina@test.com", "Nina Fernando", "CAREGIVER");
        mvc.perform(put("/api/caregivers/me/profile").header("Authorization", nina[0]).contentType(JSON)
                        .content("{\"fullName\":\"Nina Fernando\",\"jobType\":\"NURSING\",\"rates\":{\"DAILY\":9000}}"))
                .andExpect(status().isOk());
        String[] seeker = register("wa-seeker@test.com", "Sam Seeker", "CARE_SEEKER");
        String[] admin = register("wa-admin@test.com", "Admin Person", "CARE_SEEKER");
        jdbc.update("update users set is_admin = true where email = ?", "wa-admin@test.com");

        String res = mvc.perform(post("/api/bookings").header("Authorization", seeker[0]).contentType(JSON)
                        .content("{\"caregiverId\":%s,\"phone\":\"+94 77 123 4567\",\"rateType\":\"DAILY\",\"note\":\"Mornings only\"}".formatted(nina[1])))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = String.valueOf((Integer) JsonPath.read(res, "$.id"));

        assertEquals(1, dispatcher.dispatchDue());

        assertEquals(List.of(new FakeWhatsApp.Message("94771234567", "new_booking_details",
                List.of("#" + id, "Sam Seeker", "+94 77 123 4567", "wa-seeker@test.com",
                        "Nina Fernando", "Nursing", "LKR 9,000 per day", "Mornings only"))), WHATSAPP.sent);
        assertTrue(MAIL.sent.isEmpty(), "nothing is emailed when WhatsApp is the channel");
        mvc.perform(get("/api/admin/bookings").header("Authorization", admin[0]))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].alert.channel").value("WHATSAPP"))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].alert.status").value("SENT"));
    }
}
