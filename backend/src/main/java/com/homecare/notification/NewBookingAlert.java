package com.homecare.notification;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Everything the admin needs to follow up a new booking request. The values go into the template's
 * variables in the order below; {@link #TEMPLATE_BODY} is the exact text to create in Meta's template manager.
 */
public record NewBookingAlert(long bookingId, String seekerName, String seekerPhone, String seekerEmail,
                              String caregiverName, String job, String charge, String note) {

    /** Keep README.md in sync: this is the text to submit to Meta for WhatsApp, and the email and the test-mode log show it filled in. */
    public static final String TEMPLATE_BODY = """
            New booking request {{1}}

            Care seeker: {{2}}
            Phone: {{3}}
            Email: {{4}}

            Caregiver: {{5}} ({{6}})
            Charge: {{7}}
            Note: {{8}}

            Please call the care seeker, then update the request on the HomeCare Connect admin page.""";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\d+)}}");

    /** Values for {{1}} to {{8}}, cleaned and kept short so WhatsApp accepts them. */
    public List<String> params() {
        return values(100, 200);
    }

    /** The same values for an email, which has room for a full email address and the longest note allowed. */
    public List<String> emailParams() {
        return values(254, 500);
    }

    private List<String> values(int emailMax, int noteMax) {
        return List.of(
                "#" + bookingId,
                NotificationService.cleanParam(seekerName),
                NotificationService.cleanParam(seekerPhone),
                NotificationService.cleanParam(seekerEmail, emailMax),   // a cut-off email would be misleading, so allow more room
                NotificationService.cleanParam(caregiverName),
                NotificationService.cleanParam(job),
                NotificationService.cleanParam(charge),
                NotificationService.cleanParam(note, noteMax));
    }

    public static String emailSubject(List<String> params) {
        if (params.size() < 5) return "New booking request";
        return "New booking request " + params.get(0) + ": " + params.get(1) + " asked for " + params.get(4);
    }

    /** The same text as the WhatsApp message, with a link to the admin page underneath. */
    public static String emailBody(List<String> params, String adminUrl) {
        String body = preview(params);
        return adminUrl == null || adminUrl.isBlank() ? body : body + "\n\nAdmin page: " + adminUrl.trim();
    }

    /** The message as the admin will read it. Fills every placeholder in one pass, so a value that looks like {{3}} stays as typed. */
    public static String preview(List<String> params) {
        Matcher m = PLACEHOLDER.matcher(TEMPLATE_BODY);
        return m.replaceAll(r -> {
            int i = Integer.parseInt(r.group(1)) - 1;
            return Matcher.quoteReplacement(i >= 0 && i < params.size() ? params.get(i) : r.group());
        });
    }
}
