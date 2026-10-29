package com.homecare.review;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AvailabilityAndReviewsTest {

    @Autowired MockMvc mvc;

    private String[] register(String email, String name, String role) throws Exception {
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"password123\",\"role\":\"%s\"}".formatted(name, email, role)))
                .andReturn().getResponse().getContentAsString();
        return new String[]{"Bearer " + JsonPath.read(res, "$.token"), String.valueOf((Integer) JsonPath.read(res, "$.user.id"))};
    }

    private String[] caregiver(String email, String name, String job) throws Exception {
        String[] cg = register(email, name, "CAREGIVER");
        mvc.perform(put("/api/caregivers/me/profile").header("Authorization", cg[0]).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"%s\",\"jobType\":\"%s\",\"address\":\"12 Temple Road, Jaffna\"}".formatted(name, job))).andExpect(status().isOk());
        return cg;
    }

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Test
    void unavailableCaregiversAreHiddenFromSeekers() throws Exception {
        String[] cg = caregiver("av-cg@test.com", "Avery Physio", "PHYSIOTHERAPIST");
        String seeker = register("av-seeker@test.com", "Sam Seeker", "CARE_SEEKER")[0];

        mvc.perform(get("/api/caregivers").param("job", "PHYSIOTHERAPIST").header("Authorization", seeker))
                .andExpect(jsonPath("$[?(@.fullName == 'Avery Physio')]").isNotEmpty());

        mvc.perform(put("/api/caregivers/me/availability").header("Authorization", cg[0]).contentType(JSON).content("{\"available\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        mvc.perform(get("/api/caregivers/me/profile").header("Authorization", cg[0]))
                .andExpect(jsonPath("$.available").value(false));
        mvc.perform(get("/api/caregivers").param("job", "PHYSIOTHERAPIST").header("Authorization", seeker))
                .andExpect(jsonPath("$[?(@.fullName == 'Avery Physio')]").isEmpty());

        // saving the profile again must not flip availability back on
        mvc.perform(put("/api/caregivers/me/profile").header("Authorization", cg[0]).contentType(JSON)
                .content("{\"fullName\":\"Avery Physio\",\"jobType\":\"PHYSIOTHERAPIST\",\"address\":\"12 Temple Road, Jaffna\",\"age\":30}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));

        mvc.perform(put("/api/caregivers/me/availability").header("Authorization", cg[0]).contentType(JSON).content("{\"available\":true}"));
        mvc.perform(get("/api/caregivers").param("job", "PHYSIOTHERAPIST").header("Authorization", seeker))
                .andExpect(jsonPath("$[?(@.fullName == 'Avery Physio')]").isNotEmpty());

        mvc.perform(put("/api/caregivers/me/availability").header("Authorization", seeker).contentType(JSON).content("{\"available\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/caregivers/me/availability").header("Authorization", cg[0]).contentType(JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void seekersReviewCaregiversAndRatingsAreAveraged() throws Exception {
        String[] cg = caregiver("rv-cg@test.com", "Rhea Reviewed", "NURSING");
        String id = cg[1];
        String s1 = register("rv-s1@test.com", "Priya Sharma", "CARE_SEEKER")[0];
        String s2 = register("rv-s2@test.com", "Tom", "CARE_SEEKER")[0];
        String url = "/api/caregivers/" + id + "/reviews";

        mvc.perform(get(url).header("Authorization", s1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.average").doesNotExist())
                .andExpect(jsonPath("$.myReview").doesNotExist());

        mvc.perform(put(url).header("Authorization", s1).contentType(JSON).content("{\"rating\":5,\"comment\":\"  Wonderful and kind  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.average").value(5.0))
                .andExpect(jsonPath("$.myReview.comment").value("Wonderful and kind"))
                .andExpect(jsonPath("$.reviews[0].reviewerName").value("Priya S."));

        mvc.perform(put(url).header("Authorization", s2).contentType(JSON).content("{\"rating\":4}"))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.average").value(4.5));

        // same seeker again replaces their review instead of adding another
        mvc.perform(put(url).header("Authorization", s2).contentType(JSON).content("{\"rating\":2,\"comment\":\"\"}"))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.average").value(3.5))
                .andExpect(jsonPath("$.myReview.comment").doesNotExist());

        // seeker sees ratings on the directory card
        mvc.perform(get("/api/caregivers").param("job", "NURSING").header("Authorization", s1))
                .andExpect(jsonPath("$[?(@.fullName == 'Rhea Reviewed')].rating").value(3.5))
                .andExpect(jsonPath("$[?(@.fullName == 'Rhea Reviewed')].reviewCount").value(2));

        // the caregiver reads their own reviews
        mvc.perform(get("/api/caregivers/me/reviews").header("Authorization", cg[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.myReview").doesNotExist());
    }

    @Test
    void reviewRulesAreEnforced() throws Exception {
        String[] cg = caregiver("rr-cg@test.com", "Ruth Rules", "CARETAKER");
        String seeker = register("rr-s@test.com", "Sue Seeker", "CARE_SEEKER")[0];
        String url = "/api/caregivers/" + cg[1] + "/reviews";

        for (String bad : new String[]{"{\"rating\":0}", "{\"rating\":6}", "{}", "{\"rating\":3,\"comment\":\"" + "x".repeat(1001) + "\"}"}) {
            mvc.perform(put(url).header("Authorization", seeker).contentType(JSON).content(bad)).andExpect(status().isBadRequest());
        }
        // caregivers cannot write reviews; anonymous users cannot read them
        mvc.perform(put(url).header("Authorization", cg[0]).contentType(JSON).content("{\"rating\":5}")).andExpect(status().isForbidden());
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        // unknown caregiver, and a seeker's id is not a caregiver
        mvc.perform(put("/api/caregivers/999999/reviews").header("Authorization", seeker).contentType(JSON).content("{\"rating\":5}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/caregivers/me/reviews").header("Authorization", seeker)).andExpect(status().isForbidden());
    }
}
