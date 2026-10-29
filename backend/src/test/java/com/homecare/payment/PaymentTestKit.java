package com.homecare.payment;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The set-up shared by the payment tests: people, a booking, and the steps up to a completed visit with a fee. */
final class PaymentTestKit {

    static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final AtomicInteger COUNTER = new AtomicInteger();

    record Person(String auth, String id, String email) {}

    /** An admin, a care seeker and a caregiver who has a profile, so a booking can be made. */
    record Scene(Person admin, Person seeker, Person caregiver) {}

    private final MockMvc mvc;
    private final JdbcTemplate jdbc;
    private final String tag;

    PaymentTestKit(MockMvc mvc, JdbcTemplate jdbc, String tag) {
        this.mvc = mvc;
        this.jdbc = jdbc;
        this.tag = tag;
    }

    Person register(String name, String role) throws Exception {
        String email = "%s-%s-%d@test.com".formatted(tag, role.toLowerCase().replace('_', '-'), COUNTER.incrementAndGet());
        String res = mvc.perform(post("/api/auth/register").contentType(JSON)
                        .content("{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"password123\",\"role\":\"%s\"}".formatted(name, email, role)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Person("Bearer " + JsonPath.read(res, "$.token"), String.valueOf((Integer) JsonPath.read(res, "$.user.id")), email);
    }

    Scene scene() throws Exception {
        Person caregiver = register("Nina Fernando", "CAREGIVER");
        mvc.perform(put("/api/caregivers/me/profile").header("Authorization", caregiver.auth()).contentType(JSON)
                        .content("{\"fullName\":\"Nina Fernando\",\"jobType\":\"NURSING\",\"address\":\"12 Temple Road, Jaffna\",\"rates\":{\"HOURLY\":1800,\"DAILY\":9000}}"))
                .andExpect(status().isOk());
        Person admin = register("Admin Person", "CARE_SEEKER");
        jdbc.update("update users set is_admin = true where email = ?", admin.email());   // the only way to become admin
        return new Scene(admin, register("Sam Seeker", "CARE_SEEKER"), caregiver);
    }

    /** A new request from the scene's seeker, still REQUESTED. @return its id */
    String book(Scene s) throws Exception {
        String res = mvc.perform(post("/api/bookings").header("Authorization", s.seeker().auth()).contentType(JSON)
                        .content("{\"caregiverId\":%s,\"address\":\"5 Lake Road, Colombo\",\"phone\":\"+94 77 123 4567\",\"rateType\":\"DAILY\"}".formatted(s.caregiver().id())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return String.valueOf((Integer) JsonPath.read(res, "$.id"));
    }

    ResultActions setStatus(Scene s, String bookingId, String json) throws Exception {
        return mvc.perform(patch("/api/admin/bookings/" + bookingId + "/status").header("Authorization", s.admin().auth()).contentType(JSON).content(json));
    }

    String confirmed(Scene s) throws Exception {
        String id = book(s);
        setStatus(s, id, "{\"status\":\"CONFIRMED\"}").andExpect(status().isOk());
        return id;
    }

    /** A visit marked completed with this fee. @return the request id */
    String completed(Scene s, String fee) throws Exception {
        String id = confirmed(s);
        setStatus(s, id, "{\"status\":\"COMPLETED\",\"fee\":" + fee + "}").andExpect(status().isOk());
        return id;
    }

    /** What the seeker is shown about the fee of one of their requests. */
    Map<String, Object> seekerView(Person seeker, String bookingId) throws Exception {
        return one(mvc.perform(get("/api/bookings/mine").header("Authorization", seeker.auth())).andReturn().getResponse().getContentAsString(), bookingId);
    }

    /** What the admin is shown. */
    Map<String, Object> adminView(Person admin, String bookingId) throws Exception {
        return one(mvc.perform(get("/api/admin/bookings").header("Authorization", admin.auth())).andReturn().getResponse().getContentAsString(), bookingId);
    }

    private static Map<String, Object> one(String body, String bookingId) {
        List<Map<String, Object>> found = JsonPath.read(body, "$[?(@.id == " + bookingId + ")].payment");
        if (found.size() != 1) throw new AssertionError("Expected one request " + bookingId + " but found " + found.size());
        return found.get(0);
    }
}
