package com.homecare.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(GoogleLoginTest.StubConfig.class)
class GoogleLoginTest {

    @Autowired MockMvc mvc;
    static volatile GoogleTokenVerifier.Identity next;

    @TestConfiguration
    static class StubConfig {
        @Bean @Primary
        GoogleTokenVerifier stubVerifier() { return token -> next; }
    }

    private void google(String json, int expected) throws Exception {
        mvc.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(expected));
    }

    @Test
    void newUserNeedsRoleThenIsCreatedAndLaterLoggedIn() throws Exception {
        next = new GoogleTokenVerifier.Identity("sub-1", "g1@test.com", "G One");

        google("{\"idToken\":\"t\"}", 428);

        mvc.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"t\",\"role\":\"CAREGIVER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("CAREGIVER"))
                .andExpect(jsonPath("$.user.fullName").value("G One"));

        google("{\"idToken\":\"t\",\"expectedRole\":\"CAREGIVER\"}", 200);
        google("{\"idToken\":\"t\",\"expectedRole\":\"CARE_SEEKER\"}", 401);
    }

    @Test
    void linksToExistingPasswordAccountByEmail() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Pat\",\"email\":\"pat@test.com\",\"password\":\"password123\",\"role\":\"CARE_SEEKER\"}"))
                .andExpect(status().isCreated());
        next = new GoogleTokenVerifier.Identity("sub-2", "Pat@test.com", "Pat G");

        mvc.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"t\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("CARE_SEEKER"))
                .andExpect(jsonPath("$.user.fullName").value("Pat"));
    }

    @Test
    void googleOnlyAccountCannotUsePasswordLogin() throws Exception {
        next = new GoogleTokenVerifier.Identity("sub-3", "g3@test.com", "G Three");
        google("{\"idToken\":\"t\",\"role\":\"CARE_SEEKER\"}", 200);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"g3@test.com\",\"password\":\"anything123\"}"))
                .andExpect(status().isUnauthorized());
    }
}
