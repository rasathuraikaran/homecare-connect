package com.homecare.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("test")
class AuthFlowTest {

    @Autowired MockMvc mvc;

    private String register(String email, String role) throws Exception {
        String body = """
            {"fullName":"Test User","email":"%s","password":"password123","role":"%s"}""".formatted(email, role);
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void registerThenLoginThenMe() throws Exception {
        String res = register("a@test.com", "CAREGIVER");
        String token = com.jayway.jsonpath.JsonPath.read(res, "$.token");

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"A@test.com\",\"password\":\"password123\",\"role\":\"CAREGIVER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("CAREGIVER"));

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("a@test.com"));
    }

    @Test
    void duplicateEmailRejected() throws Exception {
        register("dup@test.com", "CARE_SEEKER");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"X\",\"email\":\"dup@test.com\",\"password\":\"password123\",\"role\":\"CARE_SEEKER\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void wrongPasswordOrWrongRoleRejected() throws Exception {
        register("b@test.com", "CARE_SEEKER");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"b@test.com\",\"password\":\"nope-nope-1\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"b@test.com\",\"password\":\"password123\",\"role\":\"CAREGIVER\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("care seeker")));
        // wrong password with the wrong role must not reveal the real role
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"b@test.com\",\"password\":\"wrong-pass-1\",\"role\":\"CAREGIVER\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void meRequiresToken() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
}
