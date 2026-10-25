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
                        .content("{\"fullName\":\"Anu K\",\"age\":34,\"experienceYears\":6,\"qualification\":\"Diploma in Nursing\",\"jobType\":\"NURSING\"}"))
                .andExpect(jsonPath("$.jobType").value("NURSING"))
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
    void ratesAreSavedReplacedAndValidated() throws Exception {
        String auth = "Bearer " + token("cg-rates@test.com", "CAREGIVER");
        String url = "/api/caregivers/me/profile";

        mvc.perform(put(url).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"R\",\"rates\":{\"HOURLY\":1500,\"FULL_DAY_NIGHT\":12000.50}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rates.HOURLY").value(1500))
                .andExpect(jsonPath("$.rates.FULL_DAY_NIGHT").value(12000.50));

        // saving again replaces the whole set
        mvc.perform(put(url).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"R\",\"rates\":{\"DAILY\":6000}}"))
                .andExpect(jsonPath("$.rates.DAILY").value(6000))
                .andExpect(jsonPath("$.rates.HOURLY").doesNotExist());
        mvc.perform(get(url).header("Authorization", auth))
                .andExpect(jsonPath("$.rates.DAILY").value(6000))
                .andExpect(jsonPath("$.rates.FULL_DAY_NIGHT").doesNotExist());

        // no rates at all clears them
        mvc.perform(put(url).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"R\"}"))
                .andExpect(jsonPath("$.rates.DAILY").doesNotExist());

        for (String bad : new String[]{"{\"HOURLY\":0}", "{\"HOURLY\":-5}", "{\"HOURLY\":12345678}",
                "{\"HOURLY\":10.999}", "{\"WEEKLY\":100}", "{\"HOURLY\":null}"}) {
            mvc.perform(put(url).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fullName\":\"R\",\"rates\":" + bad + "}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void validationErrorsAreReadableAndListedTogether() throws Exception {
        String auth = "Bearer " + token("cg-errs@test.com", "CAREGIVER");
        mvc.perform(put("/api/caregivers/me/profile").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"age\":12,\"experienceYears\":99,\"rates\":{\"HOURLY\":0}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(4))
                .andExpect(jsonPath("$.errors[?(@ == 'Full name is required')]").isNotEmpty())
                .andExpect(jsonPath("$.errors[?(@ == 'Age must be between 18 and 100')]").isNotEmpty())
                .andExpect(jsonPath("$.errors[?(@ == 'Experience must be between 0 and 60 years')]").isNotEmpty())
                .andExpect(jsonPath("$.errors[?(@ == 'Per hour charge must be greater than zero')]").isNotEmpty());
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
        mvc.perform(put("/api/caregivers/me/profile").header("Authorization", cg)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"A\",\"jobType\":\"PILOT\"}"))
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
