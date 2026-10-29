package com.homecare.notification;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.http.HttpStatus;

class WhatsAppTest {

    private static final String TOKEN = "super-secret-token";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final WhatsAppCloudSender sender = new WhatsAppCloudSender(
            new WhatsAppProperties(true, TOKEN, "555000", "v99.0", "94771234567", "new_booking_alert", "en",
                    "https://graph.example.test"), builder);

    @Test
    void sendsTheTemplateWithTheRightAddressHeaderAndBody() {
        server.expect(requestTo("https://graph.example.test/v99.0/555000/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andExpect(jsonPath("$.messaging_product").value("whatsapp"))
                .andExpect(jsonPath("$.to").value("94771234567"))
                .andExpect(jsonPath("$.type").value("template"))
                .andExpect(jsonPath("$.template.name").value("new_booking_alert"))
                .andExpect(jsonPath("$.template.language.code").value("en"))
                .andExpect(jsonPath("$.template.components[0].type").value("body"))
                .andExpect(jsonPath("$.template.components[0].parameters[0].text").value("#12"))
                .andExpect(jsonPath("$.template.components[0].parameters[1].text").value("Nina Fernando"))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.1\"}]}", MediaType.APPLICATION_JSON));

        assertEquals(WhatsAppSender.Outcome.SENT,
                sender.send("94771234567", "new_booking_alert", "en", List.of("#12", "Nina Fernando")));
        server.verify();
    }

    @Test
    void aWrongRequestIsNotRetriedAndShowsMetasReason() {
        server.expect(requestTo("https://graph.example.test/v99.0/555000/messages"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Template name does not exist\",\"code\":132001}}"));

        WhatsAppSendException e = assertThrows(WhatsAppSendException.class,
                () -> sender.send("94771234567", "new_booking_alert", "en", List.of("#1", "X")));
        assertFalse(e.isRetryable());
        assertTrue(e.getMessage().contains("HTTP 400"));
        assertTrue(e.getMessage().contains("Template name does not exist"));
        assertFalse(e.getMessage().contains(TOKEN), "the token must never appear in an error message");
    }

    @Test
    void serverErrorsAndRateLimitsAreRetried() {
        server.expect(requestTo("https://graph.example.test/v99.0/555000/messages")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("https://graph.example.test/v99.0/555000/messages")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo("https://graph.example.test/v99.0/555000/messages")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertTrue(assertThrows(WhatsAppSendException.class, () -> sender.send("1", "t", "en", List.of())).isRetryable());
        assertTrue(assertThrows(WhatsAppSendException.class, () -> sender.send("1", "t", "en", List.of())).isRetryable());
        assertFalse(assertThrows(WhatsAppSendException.class, () -> sender.send("1", "t", "en", List.of())).isRetryable(),
                "an expired or wrong token will not fix itself, so do not keep retrying");
    }

    @Test
    void phoneNumbersAreMaskedInLogs() {
        assertEquals("*******4567", WhatsAppCloudSender.mask("94771234567".substring(0, 7) + "4567"));
        assertEquals("****", WhatsAppCloudSender.mask("123"));
    }

    @Test
    void propertiesNeverPrintTheToken() {
        String text = new WhatsAppProperties(true, TOKEN, "1", "v1", "9477", "t", "en", "u").toString();
        assertFalse(text.contains(TOKEN));
    }

    @Test
    void templateVariablesAreCleanedForWhatsApp() {
        assertEquals("Nina Fernando", NotificationService.cleanParam("  Nina \n\t  Fernando  "));
        assertEquals("-", NotificationService.cleanParam("   "));
        assertEquals("-", NotificationService.cleanParam(null));
        assertEquals(60, NotificationService.cleanParam("x".repeat(200)).length());
    }

    @Test
    void theAlertFillsEveryVariableOfTheTemplate() {
        NewBookingAlert alert = new NewBookingAlert(12, "Sam Seeker", "+94 77 123 4567", "sam@example.com", "5 Lake Road, Colombo",
                "Nina Fernando", "Nursing", "LKR 9,000 per day", "Mornings only,\nplease call after 6pm");
        List<String> params = alert.params();

        long placeholders = NewBookingAlert.TEMPLATE_BODY.chars().filter(c -> c == '{').count() / 2;
        assertEquals(placeholders, params.size(), "README and Meta's template must have exactly this many variables");

        String message = NewBookingAlert.preview(params);
        assertFalse(message.contains("{{"), "no variable may be left unfilled");
        for (String detail : List.of("#12", "Sam Seeker", "+94 77 123 4567", "sam@example.com", "5 Lake Road, Colombo", "Nina Fernando",
                "Nursing", "LKR 9,000 per day", "Mornings only, please call after 6pm")) {
            assertTrue(message.contains(detail), "missing in the message: " + detail);
        }
        assertTrue(params.stream().noneMatch(p -> p.contains("\n")), "WhatsApp variables may not hold line breaks");
    }

    @Test
    void aValueThatLooksLikeAVariableIsShownAsTyped() {
        NewBookingAlert alert = new NewBookingAlert(1, "Sam {{3}}", "123", "a@b.c", "1 Road", "Nina", "Nursing", "-", "$1 \\ {{9}}");
        String message = NewBookingAlert.preview(alert.params());
        assertTrue(message.contains("Care seeker: Sam {{3}}"));
        assertTrue(message.contains("Note: $1 \\ {{9}}"));
    }

    @Test
    void longEmailsAndNotesGetMoreRoomThanNames() {
        assertEquals(100, NotificationService.cleanParam("x".repeat(300), 100).length());
        assertEquals("short", NotificationService.cleanParam("short", 200));
    }

    @Test
    void settingsAreCheckedAtStartup() {
        WhatsAppConfig config = new WhatsAppConfig();
        RestClient.Builder b = RestClient.builder();

        assertInstanceOf(LoggingWhatsAppSender.class, config.whatsAppSender(
                new WhatsAppProperties(false, "", "", "", "", "t", "en", "https://x"), b), "off means test mode");

        IllegalStateException missing = assertThrows(IllegalStateException.class, () -> config.whatsAppSender(
                new WhatsAppProperties(true, "", "", "", "", "t", "en", "https://x"), b));
        assertTrue(missing.getMessage().contains("WHATSAPP_TOKEN"));
        assertTrue(missing.getMessage().contains("WHATSAPP_ADMIN_TO"));

        assertThrows(IllegalStateException.class, () -> config.whatsAppSender(
                new WhatsAppProperties(true, "tok", "1", "v1", "+94 77 123 4567", "t", "en", "https://x"), b),
                "the admin number must be digits only");

        assertInstanceOf(WhatsAppCloudSender.class, config.whatsAppSender(
                new WhatsAppProperties(true, "tok", "1", "v1", "94771234567", "t", "en", "https://x"), b));
    }
}
