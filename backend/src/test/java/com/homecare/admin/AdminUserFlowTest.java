package com.homecare.admin;

import com.homecare.verification.DocumentStorage;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserFlowTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DocumentStorage documentFiles;

    // ------------------------------------------------------------------ helpers

    private String[] register(String email, String name, String role) throws Exception {
        String res = mvc.perform(post("/api/auth/register").contentType(JSON)
                        .content("{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"password123\",\"role\":\"%s\"}".formatted(name, email, role)))
                .andReturn().getResponse().getContentAsString();
        return new String[]{"Bearer " + JsonPath.read(res, "$.token"), String.valueOf((Integer) JsonPath.read(res, "$.user.id"))};
    }

    private String[] admin(String email) throws Exception {
        String[] a = register(email, "Admin Person", "CARE_SEEKER");
        jdbc.update("update users set is_admin = true where email = ?", email);   // the only way to become admin
        return a;
    }

    /** A verified caretaker with a charge, a photo and an NIC on file. */
    private String[] fullCaregiver(String email, String name) throws Exception {
        String[] cg = register(email, name, "CAREGIVER");
        mvc.perform(put("/api/caregivers/me/profile").header("Authorization", cg[0]).contentType(JSON)
                .content("{\"fullName\":\"%s\",\"jobType\":\"CARETAKER\",\"address\":\"12 Temple Road, Jaffna\",\"rates\":{\"HOURLY\":1200}}".formatted(name))).andExpect(status().isOk());
        mvc.perform(multipart("/api/caregivers/me/photo").file(new MockMultipartFile("file", "p.png", "image/png", PNG))
                .header("Authorization", cg[0])).andExpect(status().isOk());
        mvc.perform(multipart("/api/caregivers/me/documents/NIC").file(new MockMultipartFile("file", "nic.png", "image/png", PNG))
                .header("Authorization", cg[0])).andExpect(status().isOk());
        return cg;
    }

    private void request(String seekerAuth, String caregiverId) throws Exception {
        mvc.perform(post("/api/bookings").header("Authorization", seekerAuth).contentType(JSON)
                .content("{\"caregiverId\":%s,\"address\":\"5 Lake Road, Colombo\",\"phone\":\"+94 77 123 4567\"}".formatted(caregiverId))).andExpect(status().isCreated());
    }

    private void review(String seekerAuth, String caregiverId) throws Exception {
        mvc.perform(put("/api/caregivers/" + caregiverId + "/reviews").header("Authorization", seekerAuth).contentType(JSON)
                .content("{\"rating\":5,\"comment\":\"Kind\"}")).andExpect(status().isOk());
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private int reviewCount(String viewerAuth, String caregiverId) throws Exception {
        String body = mvc.perform(get("/api/caregivers/" + caregiverId + "/reviews").header("Authorization", viewerAuth))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.count");
    }

    // ------------------------------------------------------------------ who may do this

    @Test
    void onlyAdminsCanListPreviewOrDeleteAccounts() throws Exception {
        String[] cg = register("au-who-cg@test.com", "Cleo Caregiver", "CAREGIVER");
        String[] seeker = register("au-who-s@test.com", "Sam Seeker", "CARE_SEEKER");
        String[] victim = register("au-who-victim@test.com", "Vic Victim", "CARE_SEEKER");

        for (String auth : new String[]{cg[0], seeker[0]}) {
            mvc.perform(get("/api/admin/users").header("Authorization", auth)).andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/users/" + victim[1] + "/deletion-preview").header("Authorization", auth)).andExpect(status().isForbidden());
            mvc.perform(delete("/api/admin/users/" + victim[1]).header("Authorization", auth)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/admin/users/" + victim[1])).andExpect(status().isUnauthorized());
        assertEquals(1, count("select count(*) from users where id = ?", Long.parseLong(victim[1])), "nobody but an admin can remove an account");
    }

    // ------------------------------------------------------------------ finding people

    @Test
    void accountsCanBeFilteredByRoleAndSearchedByNameOrEmail() throws Exception {
        String[] admin = admin("au-find-admin@test.com");
        register("au-find-zed@test.com", "Zed Zimmerman", "CAREGIVER");
        register("au-find-yan@test.com", "Yan Yates", "CARE_SEEKER");
        mvc.perform(put("/api/caregivers/me/profile").header("Authorization", register("au-find-job@test.com", "Jo Job", "CAREGIVER")[0]).contentType(JSON)
                .content("{\"fullName\":\"Jo Job\",\"jobType\":\"NURSING\",\"address\":\"12 Temple Road, Jaffna\"}")).andExpect(status().isOk());

        // by name, in any letter case
        mvc.perform(get("/api/admin/users").param("q", "ZIMMERMAN").header("Authorization", admin[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("au-find-zed@test.com"))
                .andExpect(jsonPath("$[0].role").value("CAREGIVER"))
                .andExpect(jsonPath("$[0].admin").value(false));
        // by email
        mvc.perform(get("/api/admin/users").param("q", "au-find-yan").header("Authorization", admin[0]))
                .andExpect(jsonPath("$[0].name").value("Yan Yates"))
                .andExpect(jsonPath("$[0].role").value("CARE_SEEKER"))
                .andExpect(jsonPath("$[0].job").doesNotExist());
        // by role
        String caregivers = mvc.perform(get("/api/admin/users").param("role", "CAREGIVER").param("q", "au-find").header("Authorization", admin[0]))
                .andReturn().getResponse().getContentAsString();
        assertEquals(List.of("CAREGIVER", "CAREGIVER"), JsonPath.read(caregivers, "$[*].role"));
        // a caregiver's job, address and whether families can see them come along, and admins are marked
        mvc.perform(get("/api/admin/users").param("q", "au-find-job").header("Authorization", admin[0]))
                .andExpect(jsonPath("$[0].job").value("NURSING"))
                .andExpect(jsonPath("$[0].listed").value(true))   // nothing is mandatory, so families can see them
                .andExpect(jsonPath("$[0].address").value("12 Temple Road, Jaffna"));
        mvc.perform(get("/api/admin/users").param("q", "au-find-admin").header("Authorization", admin[0]))
                .andExpect(jsonPath("$[0].admin").value(true));
        // a typed % or _ is searched for as itself, not as "anything"
        mvc.perform(get("/api/admin/users").param("q", "%").header("Authorization", admin[0])).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/admin/users").param("q", "au_find").header("Authorization", admin[0])).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/admin/users").param("role", "PILOT").header("Authorization", admin[0])).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ the preview

    @Test
    void thePreviewCountsWhatDeletingWouldRemove() throws Exception {
        String[] admin = admin("au-prev-admin@test.com");
        String[] cg = fullCaregiver("au-prev-cg@test.com", "Pia Preview");
        String[] seeker = register("au-prev-s@test.com", "Sam Seeker", "CARE_SEEKER");
        request(seeker[0], cg[1]);
        review(seeker[0], cg[1]);

        mvc.perform(get("/api/admin/users/" + cg[1] + "/deletion-preview").header("Authorization", admin[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pia Preview"))
                .andExpect(jsonPath("$.role").value("CAREGIVER"))
                .andExpect(jsonPath("$.requests").value(1))
                .andExpect(jsonPath("$.openRequests").value(1))
                .andExpect(jsonPath("$.reviewsReceived").value(1))
                .andExpect(jsonPath("$.reviewsWritten").value(0))
                .andExpect(jsonPath("$.documents").value(1))
                .andExpect(jsonPath("$.hasPhoto").value(true));
        mvc.perform(get("/api/admin/users/" + seeker[1] + "/deletion-preview").header("Authorization", admin[0]))
                .andExpect(jsonPath("$.role").value("CARE_SEEKER"))
                .andExpect(jsonPath("$.requests").value(1))
                .andExpect(jsonPath("$.openRequests").value(1))
                .andExpect(jsonPath("$.reviewsWritten").value(1))
                .andExpect(jsonPath("$.documents").value(0))
                .andExpect(jsonPath("$.hasPhoto").value(false));
        mvc.perform(get("/api/admin/users/999999/deletion-preview").header("Authorization", admin[0])).andExpect(status().isNotFound());
        assertEquals(1, count("select count(*) from users where id = ?", Long.parseLong(cg[1])), "a preview deletes nothing");
    }

    // ------------------------------------------------------------------ deleting

    @Test
    void deletingACaregiverRemovesTheirDataAndFilesButNobodyElses() throws Exception {
        String[] admin = admin("au-delcg-admin@test.com");
        String[] cg = fullCaregiver("au-delcg-cg@test.com", "Dora Deleted");
        String[] other = fullCaregiver("au-delcg-other@test.com", "Otto Other");
        String[] seeker = register("au-delcg-s@test.com", "Sam Seeker", "CARE_SEEKER");
        request(seeker[0], cg[1]);
        review(seeker[0], cg[1]);
        request(seeker[0], other[1]);
        review(seeker[0], other[1]);
        long id = Long.parseLong(cg[1]);

        String photoUrl = JsonPath.read(mvc.perform(get("/api/caregivers/me/profile").header("Authorization", cg[0]))
                .andReturn().getResponse().getContentAsString(), "$.photoUrl");
        String documentFile = jdbc.queryForObject("select file_name from caregiver_documents where user_id = ?", String.class, id);
        mvc.perform(get(photoUrl)).andExpect(status().isOk());
        assertNotNull(documentFiles.load(documentFile));

        mvc.perform(delete("/api/admin/users/" + cg[1]).header("Authorization", admin[0])).andExpect(status().isNoContent());

        // everything that hung off the account is gone
        assertEquals(0, count("select count(*) from users where id = ?", id));
        assertEquals(0, count("select count(*) from caregiver_profiles where user_id = ?", id));
        assertEquals(0, count("select count(*) from caregiver_rates where user_id = ?", id));
        assertEquals(0, count("select count(*) from caregiver_documents where user_id = ?", id));
        assertEquals(0, count("select count(*) from reviews where caregiver_id = ?", id));
        assertEquals(0, count("select count(*) from bookings where caregiver_id = ?", id));
        assertEquals(0, count("select count(*) from notifications where booking_id not in (select id from bookings)"), "no alert is left pointing at a deleted request");
        // and so are the files on disk
        mvc.perform(get(photoUrl)).andExpect(status().isNotFound());
        assertThrows(ResponseStatusException.class, () -> documentFiles.load(documentFile));

        // other people are untouched
        mvc.perform(get("/api/auth/me").header("Authorization", seeker[0])).andExpect(status().isOk());
        assertEquals(1, count("select count(*) from bookings where caregiver_id = ?", Long.parseLong(other[1])));
        assertEquals(1, reviewCount(seeker[0], other[1]));
        mvc.perform(get("/api/caregivers/me/verification").header("Authorization", other[0])).andExpect(status().isOk());

        // the deleted person's sign-in stops working at once, and the email can be used again
        mvc.perform(get("/api/caregivers/me/profile").header("Authorization", cg[0])).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(JSON)
                .content("{\"email\":\"au-delcg-cg@test.com\",\"password\":\"password123\",\"role\":\"CAREGIVER\"}")).andExpect(status().is4xxClientError());
        register("au-delcg-cg@test.com", "Dora Again", "CAREGIVER");
        assertEquals(1, count("select count(*) from users where email = 'au-delcg-cg@test.com'"));

        // it is gone from the admin's list too
        mvc.perform(get("/api/admin/users/" + cg[1] + "/deletion-preview").header("Authorization", admin[0])).andExpect(status().isNotFound());
        mvc.perform(delete("/api/admin/users/" + cg[1]).header("Authorization", admin[0])).andExpect(status().isNotFound());
    }

    @Test
    void deletingASeekerRemovesTheirRequestsAndReviewsAndTheCaregiversRatingFollows() throws Exception {
        String[] admin = admin("au-dels-admin@test.com");
        String[] cg = fullCaregiver("au-dels-cg@test.com", "Cara Caregiver");
        String[] seeker = register("au-dels-s@test.com", "Sue Seeker", "CARE_SEEKER");
        String[] bystander = register("au-dels-b@test.com", "Bo Bystander", "CARE_SEEKER");
        request(seeker[0], cg[1]);
        review(seeker[0], cg[1]);
        review(bystander[0], cg[1]);
        assertEquals(2, reviewCount(bystander[0], cg[1]));

        mvc.perform(delete("/api/admin/users/" + seeker[1]).header("Authorization", admin[0])).andExpect(status().isNoContent());

        assertEquals(0, count("select count(*) from bookings where seeker_id = ?", Long.parseLong(seeker[1])));
        assertEquals(0, count("select count(*) from reviews where reviewer_id = ?", Long.parseLong(seeker[1])));
        assertEquals(1, reviewCount(bystander[0], cg[1]), "only the bystander's review is left");
        mvc.perform(get("/api/caregivers/me/profile").header("Authorization", cg[0])).andExpect(status().isOk());   // the caregiver is fine
        mvc.perform(get("/api/auth/me").header("Authorization", seeker[0])).andExpect(status().isUnauthorized());
    }

    @Test
    void adminsCannotBeDeletedEvenByThemselvesOrAnotherAdmin() throws Exception {
        String[] boss = admin("au-adm-boss@test.com");
        String[] other = admin("au-adm-other@test.com");

        mvc.perform(delete("/api/admin/users/" + boss[1]).header("Authorization", boss[0]))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("You cannot delete your own account."));
        mvc.perform(delete("/api/admin/users/" + other[1]).header("Authorization", boss[0]))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Admins cannot be deleted here")));
        assertEquals(2, count("select count(*) from users where id in (?, ?)", Long.parseLong(boss[1]), Long.parseLong(other[1])));

        // once the admin rights are taken away (the make-admin --remove command does exactly this) the account can go
        jdbc.update("update users set is_admin = false where id = ?", Long.parseLong(other[1]));
        mvc.perform(delete("/api/admin/users/" + other[1]).header("Authorization", boss[0])).andExpect(status().isNoContent());
        assertEquals(0, count("select count(*) from users where id = ?", Long.parseLong(other[1])));
    }
}
