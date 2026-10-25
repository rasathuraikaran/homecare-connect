package com.homecare.profile;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileFlowTest {

    @Autowired MockMvc mvc;

    private String token(String email, String role) throws Exception {
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Care Giver\",\"email\":\"%s\",\"password\":\"password123\",\"role\":\"%s\"}".formatted(email, role)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.token");
    }

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Test
    void caregiverCompletesProfileWithOptionalFieldsAndPhoto() throws Exception {
        String t = token("cg1@test.com", "CAREGIVER");
        String auth = "Bearer " + t;

        mvc.perform(get("/api/auth/me").header("Authorization", auth))
                .andExpect(jsonPath("$.profileCompleted").value(false));

        // only the name is required
        mvc.perform(put("/api/caregivers/me/profile").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"  Anu K  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Anu K"))
                .andExpect(jsonPath("$.age").doesNotExist());

        mvc.perform(put("/api/caregivers/me/profile").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Anu K\",\"age\":34,\"experienceYears\":6,\"qualification\":\"Diploma in Nursing\"}"))
                .andExpect(jsonPath("$.age").value(34))
                .andExpect(jsonPath("$.qualification").value("Diploma in Nursing"));

        String photo = mvc.perform(multipart("/api/caregivers/me/photo")
                        .file(new MockMultipartFile("file", "x.png", "image/png", PNG)).header("Authorization", auth))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(photo, "$.photoUrl");

        mvc.perform(get(url)).andExpect(status().isOk());   // public, no token
        mvc.perform(get("/api/auth/me").header("Authorization", auth))
                .andExpect(jsonPath("$.profileCompleted").value(true))
                .andExpect(jsonPath("$.photoUrl").value(url))
                .andExpect(jsonPath("$.fullName").value("Anu K"));
    }

    @Test
    void validationAndAccessRules() throws Exception {
        String cg = "Bearer " + token("cg2@test.com", "CAREGIVER");
        String seeker = "Bearer " + token("seeker@test.com", "CARE_SEEKER");

        mvc.perform(put("/api/caregivers/me/profile").header("Authorization", cg)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/caregivers/me/profile").header("Authorization", cg)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"A\",\"age\":12}"))
                .andExpect(status().isBadRequest());

        // not an image, even if it claims to be
        mvc.perform(multipart("/api/caregivers/me/photo")
                        .file(new MockMultipartFile("file", "x.png", "image/png", "<script>".getBytes())).header("Authorization", cg))
                .andExpect(status().isBadRequest());

        // care seekers and anonymous users cannot use caregiver endpoints
        mvc.perform(put("/api/caregivers/me/profile").header("Authorization", seeker)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"X\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/caregivers/me/profile")).andExpect(status().isUnauthorized());

        // path traversal / unknown names never touch the disk
        mvc.perform(get("/api/files/photos/..%2F..%2Fetc%2Fpasswd")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/files/photos/nope.png")).andExpect(status().isNotFound());
    }
}
