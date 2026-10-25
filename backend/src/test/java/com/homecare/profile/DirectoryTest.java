package com.homecare.profile;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DirectoryTest {

    @Autowired MockMvc mvc;

    private String register(String email, String role) throws Exception {
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"X\",\"email\":\"%s\",\"password\":\"password123\",\"role\":\"%s\"}".formatted(email, role)))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(res, "$.token");
    }

    private void caregiver(String email, String name, String job, int years) throws Exception {
        String auth = register(email, "CAREGIVER");
        mvc.perform(put("/api/caregivers/me/profile").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"%s\",\"jobType\":\"%s\",\"experienceYears\":%d}".formatted(name, job, years)))
                .andExpect(status().isOk());
    }

    @Test
    void seekerSeesOnlyMatchingCaregiversWithoutContactDetails() throws Exception {
        caregiver("dir-n1@test.com", "Nina Nurse", "NURSING", 3);
        caregiver("dir-n2@test.com", "Omar Nurse", "NURSING", 9);
        caregiver("dir-p1@test.com", "Pia Physio", "PHYSIOTHERAPIST", 4);
        String seeker = register("dir-seeker@test.com", "CARE_SEEKER");

        mvc.perform(get("/api/caregivers").param("job", "NURSING").header("Authorization", seeker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.jobType != 'NURSING')]").isEmpty())
                .andExpect(jsonPath("$[?(@.fullName == 'Nina Nurse')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.fullName == 'Pia Physio')]").isEmpty())
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].phone").doesNotExist());

        // most experienced first
        String body = mvc.perform(get("/api/caregivers").param("job", "NURSING").header("Authorization", seeker))
                .andReturn().getResponse().getContentAsString();
        java.util.List<String> names = JsonPath.read(body, "$[*].fullName");
        org.junit.jupiter.api.Assertions.assertTrue(names.indexOf("Omar Nurse") < names.indexOf("Nina Nurse"));

        mvc.perform(get("/api/caregivers/counts").header("Authorization", seeker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.NURSING").isNumber())
                .andExpect(jsonPath("$.CARETAKER").isNumber())
                .andExpect(jsonPath("$.PHYSIOTHERAPIST").isNumber());
    }

    @Test
    void accessRulesAndBadInput() throws Exception {
        String seeker = register("dir-seeker2@test.com", "CARE_SEEKER");
        String caregiver = register("dir-cg@test.com", "CAREGIVER");

        mvc.perform(get("/api/caregivers").param("job", "NURSING").header("Authorization", caregiver))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/caregivers").param("job", "NURSING")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/caregivers").param("job", "PILOT").header("Authorization", seeker))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/caregivers").header("Authorization", seeker)).andExpect(status().isBadRequest());
    }
}
