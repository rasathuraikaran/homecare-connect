package com.homecare.notification;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EmailTest {

    /** A mail server that never connects: it keeps what it was asked to send, or fails on request. */
    static class CapturingMailSender extends JavaMailSenderImpl {
        final List<MimeMessage> sent = new ArrayList<>();
        RuntimeException failWith;

        @Override
        protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) throws MailException {
            if (failWith != null) throw failWith;
            sent.addAll(List.of(mimeMessages));
        }
    }

    private final CapturingMailSender mail = new CapturingMailSender();
    private final SmtpEmailSender sender;

    EmailTest() {
        mail.setDefaultEncoding("UTF-8");   // as EmailConfig does
        sender = new SmtpEmailSender(mail, "alerts@homecare.test");
    }

    @Test
    void sendsAPlainTextEmailToEveryAdminAddress() throws Exception {
        String text = "Phone: +94 77 123 4567\nNote: Mornings only … கரன்";
        assertEquals(EmailSender.Outcome.SENT,
                sender.send("admin@example.com, second@example.com", "New booking request #12: Sam asked for Nina", text));

        MimeMessage sent = mail.sent.get(0);
        assertEquals("alerts@homecare.test", sent.getFrom()[0].toString());
        assertEquals(List.of("admin@example.com", "second@example.com"),
                Arrays.stream(sent.getRecipients(Message.RecipientType.TO)).map(Object::toString).toList());
        assertEquals("New booking request #12: Sam asked for Nina", sent.getSubject());
        assertEquals(text, sent.getContent());
    }

    @Test
    void aWrongPasswordIsNotRetriedAndTheServersTextIsNotRepeated() {
        mail.failWith = new MailAuthenticationException("535 5.7.8 secret.account@gmail.com credentials rejected");

        EmailSendException e = assertThrows(EmailSendException.class, () -> sender.send("admin@example.com", "s", "b"));
        assertFalse(e.isRetryable(), "a wrong password will not fix itself");
        assertTrue(e.getMessage().contains("EMAIL_PASSWORD"), "the admin is told which setting to check");
        assertFalse(e.getMessage().contains("secret.account"), "the account name from the server is not copied into the admin page");
    }

    @Test
    void anAddressProblemIsNotRetried() {
        mail.failWith = new MailParseException("Invalid address: not-set");
        assertFalse(assertThrows(EmailSendException.class, () -> sender.send("not-set", "s", "b")).isRetryable());
    }

    @Test
    void serverTroubleIsRetriedAndExplainedInOneLine() {
        mail.failWith = new MailSendException("Mail server connection failed",
                new MessagingException("Could not connect to SMTP host: smtp.example.test, port: 587"));

        EmailSendException e = assertThrows(EmailSendException.class, () -> sender.send("admin@example.com", "s", "b"));
        assertTrue(e.isRetryable());
        assertEquals("Could not send the email: Could not connect to SMTP host: smtp.example.test, port: 587", e.getMessage());
    }

    @Test
    void addressesAreMaskedInLogs() {
        assertEquals("s***@example.com", SmtpEmailSender.mask("sam@example.com"));
        assertEquals("a***@x.com, b***@y.com", SmtpEmailSender.mask("ann@x.com,  bob@y.com"));
        assertEquals("***", SmtpEmailSender.mask("not-set"));
    }

    @Test
    void testModeOnlyLogsTheEmail() {
        assertEquals(EmailSender.Outcome.DRY_RUN, new LoggingEmailSender().send("admin@example.com", "subject", "body"));
    }

    // ------------------------------------------------------------------ settings

    private static EmailProperties props(boolean enabled, String host, int port, String user, String password, String from, String to) {
        return new EmailProperties(enabled, host, port, user, password, from, to, "http://localhost:5173/admin");
    }

    @Test
    void settingsAreCheckedAtStartup() {
        EmailConfig config = new EmailConfig();

        assertInstanceOf(LoggingEmailSender.class, config.emailSender(props(false, "", 587, "", "", "", "")), "off means test mode");

        IllegalStateException missing = assertThrows(IllegalStateException.class,
                () -> config.emailSender(props(true, "", 587, "", "", "", "")));
        for (String setting : List.of("EMAIL_SMTP_HOST", "EMAIL_USERNAME", "EMAIL_PASSWORD", "EMAIL_ADMIN_TO")) {
            assertTrue(missing.getMessage().contains(setting), "should name " + setting + ": " + missing.getMessage());
        }

        assertTrue(assertThrows(IllegalStateException.class, () -> config.emailSender(
                props(true, "smtp.example.test", 587, "alerts@example.com", "pw", "", "not-an-address"))).getMessage().contains("EMAIL_ADMIN_TO"));
        assertTrue(assertThrows(IllegalStateException.class, () -> config.emailSender(
                props(true, "smtp.example.test", 587, "plain-username", "pw", "", "admin@example.com"))).getMessage().contains("EMAIL_FROM"));
        assertTrue(assertThrows(IllegalStateException.class, () -> config.emailSender(
                props(true, "smtp.example.test", 0, "alerts@example.com", "pw", "", "admin@example.com"))).getMessage().contains("EMAIL_SMTP_PORT"));

        assertInstanceOf(SmtpEmailSender.class, config.emailSender(
                props(true, "smtp.example.test", 587, "alerts@example.com", "pw", "", "admin@example.com, other@example.com")));
        assertInstanceOf(SmtpEmailSender.class, config.emailSender(
                props(true, "smtp.example.test", 465, "plain-username", "pw", "alerts@example.com", "admin@example.com")));
    }

    @Test
    void aSettingCannotSmuggleInAnotherHeaderOrRecipient() {
        EmailConfig config = new EmailConfig();
        assertThrows(IllegalStateException.class, () -> config.emailSender(
                props(true, "smtp.example.test", 587, "alerts@example.com", "pw", "", "admin@example.com\nBcc: evil@example.com")));
        assertThrows(IllegalStateException.class, () -> config.emailSender(
                props(true, "smtp.example.test", 587, "alerts@example.com", "pw", "alerts@example.com\nBcc: evil@example.com", "admin@example.com")));
    }

    @Test
    void addressListsAndTheSenderAreReadFromTheSettings() {
        EmailProperties p = props(true, "h", 587, "me@example.com", "pw", "", " a@x.com, ,b@y.com ");
        assertEquals(List.of("a@x.com", "b@y.com"), p.recipients());
        assertEquals("me@example.com", p.sender(), "the username is used when no sender is given");
        assertEquals("alerts@example.com", props(true, "h", 587, "me", "pw", " alerts@example.com ", "a@x.com").sender());
        assertEquals(List.of(), props(false, "", 587, "", "", "", "").recipients());
    }

    @Test
    void propertiesNeverPrintThePassword() {
        assertFalse(props(true, "h", 587, "u", "super-secret-password", "", "a@x.com").toString().contains("super-secret-password"));
    }

    // ------------------------------------------------------------------ the text of the email

    @Test
    void theEmailHasAClearSubjectEveryDetailAndALinkToTheAdminPage() {
        NewBookingAlert alert = new NewBookingAlert(12, "Sam\nSeeker", "+94 77 123 4567", "sam@example.com", "5 Lake Road, Colombo",
                "Nina Fernando", "Nursing", "LKR 9,000 per day", "Mornings only");
        List<String> params = alert.emailParams();

        assertEquals("New booking request #12: Sam Seeker asked for Nina Fernando", NewBookingAlert.emailSubject(params));
        String body = NewBookingAlert.emailBody(params, "https://care.example.com/admin ");
        for (String detail : List.of("#12", "Sam Seeker", "+94 77 123 4567", "sam@example.com", "5 Lake Road, Colombo", "Nina Fernando",
                "Nursing", "LKR 9,000 per day", "Mornings only")) {
            assertTrue(body.contains(detail), "missing in the email: " + detail);
        }
        assertTrue(body.endsWith("\n\nAdmin page: https://care.example.com/admin"), body);
        assertFalse(NewBookingAlert.emailBody(params, "").contains("Admin page:"), "no link when no address is set");
    }

    @Test
    void anEmailKeepsTheWholeNoteAddressAndEmailThatWhatsAppHasToShorten() {
        String longNote = "n".repeat(400);
        String longEmail = "e".repeat(150) + "@example.com";
        String longAddress = "a".repeat(250);
        NewBookingAlert alert = new NewBookingAlert(1, "Sam", "123", longEmail, longAddress, "Nina", "Nursing", "-", longNote);

        assertEquals(longNote, alert.emailParams().get(8));
        assertEquals(longEmail, alert.emailParams().get(3));
        assertEquals(longAddress, alert.emailParams().get(4));
        assertEquals(200, alert.params().get(8).length());
        assertEquals(100, alert.params().get(3).length());
        assertEquals(150, alert.params().get(4).length());
    }
}
