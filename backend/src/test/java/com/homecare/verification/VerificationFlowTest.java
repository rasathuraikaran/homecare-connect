package com.homecare.verification;

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
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VerificationFlowTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] PDF = "%PDF-1.4\n%test document".getBytes(StandardCharsets.US_ASCII);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DocumentStorage storage;

    // ------------------------------------------------------------------ helpers

    private String[] register(String email, String name, String role) throws Exception {
        String res = mvc.perform(post("/api/auth/register").contentType(JSON)
                        .content("{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"password123\",\"role\":\"%s\"}".formatted(name, email, role)))
                .andReturn().getResponse().getContentAsString();
        return new String[]{"Bearer " + JsonPath.read(res, "$.token"), String.valueOf((Integer) JsonPath.read(res, "$.user.id"))};
    }

    private void setJob(String auth, String name, String job) throws Exception {
        mvc.perform(put("/api/caregivers/me/profile").header("Authorization", auth).contentType(JSON)
                .content("{\"fullName\":\"%s\",\"jobType\":\"%s\"}".formatted(name, job))).andExpect(status().isOk());
    }

    private String[] caregiver(String email, String name, String job) throws Exception {
        String[] cg = register(email, name, "CAREGIVER");
        setJob(cg[0], name, job);
        return cg;
    }

    private String[] admin(String email) throws Exception {
        String[] a = register(email, "Admin Person", "CARE_SEEKER");
        jdbc.update("update users set is_admin = true where email = ?", email);   // the only way to become admin
        return a;
    }

    private ResultActions upload(String auth, String type, byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/caregivers/me/documents/" + type)
                .file(new MockMultipartFile("file", "scan", "application/octet-stream", bytes))
                .header("Authorization", auth));
    }

    private long docId(String userId, String type) {
        return jdbc.queryForObject("select id from caregiver_documents where user_id = ? and doc_type = ?", Long.class, Long.parseLong(userId), type);
    }

    private int revision(long docId) {
        return jdbc.queryForObject("select revision from caregiver_documents where id = ?", Integer.class, docId);
    }

    private String fileName(String userId, String type) {
        return jdbc.queryForObject("select file_name from caregiver_documents where user_id = ? and doc_type = ?", String.class, Long.parseLong(userId), type);
    }

    private ResultActions review(String adminAuth, long docId, String status, String reason, int revision) throws Exception {
        return mvc.perform(patch("/api/admin/documents/" + docId).header("Authorization", adminAuth).contentType(JSON)
                .content("{\"status\":\"%s\",\"reason\":%s,\"revision\":%d}".formatted(status, reason == null ? "null" : "\"" + reason + "\"", revision)));
    }

    private void approve(String adminAuth, String userId, String type) throws Exception {
        long id = docId(userId, type);
        review(adminAuth, id, "APPROVED", null, revision(id)).andExpect(status().isOk());
    }

    private List<String> shown(String seekerAuth, String job) throws Exception {
        String body = mvc.perform(get("/api/caregivers").param("job", job).header("Authorization", seekerAuth))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[*].fullName");
    }

    private int count(String seekerAuth, String job) throws Exception {
        String body = mvc.perform(get("/api/caregivers/counts").header("Authorization", seekerAuth)).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$." + job)).intValue();
    }

    private ResultActions request(String seekerAuth, String caregiverId) throws Exception {
        return mvc.perform(post("/api/bookings").header("Authorization", seekerAuth).contentType(JSON)
                .content("{\"caregiverId\":%s,\"phone\":\"+94 77 123 4567\"}".formatted(caregiverId)));
    }

    // ------------------------------------------------------------------ what each job needs

    @Test
    void whatIsRequiredAndWhatIsOptionalDependsOnTheJob() throws Exception {
        String[] caretaker = caregiver("vf-need-c@test.com", "Cleo Caretaker", "CARETAKER");
        mvc.perform(get("/api/caregivers/me/verification").header("Authorization", caretaker[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INCOMPLETE"))
                .andExpect(jsonPath("$.documents.length()").value(2))
                .andExpect(jsonPath("$.documents[0].type").value("NIC"))
                .andExpect(jsonPath("$.documents[0].required").value(true))
                .andExpect(jsonPath("$.documents[1].type").value("GS_LETTER"))
                .andExpect(jsonPath("$.documents[1].required").value(false));

        String[] nurse = caregiver("vf-need-n@test.com", "Nora Nurse", "NURSING");
        mvc.perform(get("/api/caregivers/me/verification").header("Authorization", nurse[0]))
                .andExpect(jsonPath("$.documents[0].type").value("NURSING_CERTIFICATE"))
                .andExpect(jsonPath("$.documents[0].required").value(true))
                .andExpect(jsonPath("$.documents[1].type").value("NIC"))
                .andExpect(jsonPath("$.documents[1].required").value(false));

        String[] physio = caregiver("vf-need-p@test.com", "Pat Physio", "PHYSIOTHERAPIST");
        mvc.perform(get("/api/caregivers/me/verification").header("Authorization", physio[0]))
                .andExpect(jsonPath("$.documents[0].type").value("DEGREE_CERTIFICATE"))
                .andExpect(jsonPath("$.documents[0].required").value(true))
                .andExpect(jsonPath("$.documents[1].type").value("NIC"))
                .andExpect(jsonPath("$.documents[1].required").value(false));

        // no job chosen yet: nothing to upload, and uploading is refused with a reason
        String[] fresh = register("vf-need-f@test.com", "Fran Fresh", "CAREGIVER");
        mvc.perform(get("/api/caregivers/me/verification").header("Authorization", fresh[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INCOMPLETE"))
                .andExpect(jsonPath("$.documents").isEmpty());
        upload(fresh[0], "NIC", PNG).andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ uploading

    @Test
    void uploadsAreCheckedByTheirContentNotTheirLabel() throws Exception {
        String[] cg = caregiver("vf-up-c@test.com", "Uma Upload", "CARETAKER");

        upload(cg[0], "NIC", "just some text pretending to be a picture".getBytes(StandardCharsets.UTF_8)).andExpect(status().isBadRequest());
        upload(cg[0], "NIC", new byte[0]).andExpect(status().isBadRequest());
        byte[] tooBig = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, tooBig, 0, PNG.length);
        upload(cg[0], "NIC", tooBig).andExpect(status().isPayloadTooLarge());

        // a document the job does not use, and a type that does not exist
        upload(cg[0], "DEGREE_CERTIFICATE", PNG).andExpect(status().isBadRequest());
        upload(cg[0], "PASSPORT", PNG).andExpect(status().isBadRequest());

        // images and PDFs are fine, and the file waits for review
        upload(cg[0], "NIC", PDF).andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[0].status").value("PENDING"))
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));
        upload(cg[0], "GS_LETTER", PNG).andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[1].status").value("PENDING"));

        // only caregivers have documents
        String seeker = register("vf-up-s@test.com", "Sam Seeker", "CARE_SEEKER")[0];
        upload(seeker, "NIC", PNG).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/caregivers/me/documents/NIC").file(new MockMultipartFile("file", "x", "image/png", PNG)))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ who families can see

    @Test
    void aCaregiverIsShownOnlyOnceTheRequiredDocumentIsApproved() throws Exception {
        String[] cora = caregiver("vf-show-c@test.com", "Cora Caretaker", "CARETAKER");
        String seeker = register("vf-show-s@test.com", "Sam Seeker", "CARE_SEEKER")[0];
        String[] admin = admin("vf-show-admin@test.com");
        int before = count(seeker, "CARETAKER");

        // not verified: hidden from search and counts, and cannot be requested (same answer as for someone who does not exist)
        assertFalse(shown(seeker, "CARETAKER").contains("Cora Caretaker"));
        request(seeker, cora[1]).andExpect(status().isNotFound());

        // an upload alone changes nothing
        upload(cora[0], "NIC", PNG).andExpect(status().isOk());
        assertFalse(shown(seeker, "CARETAKER").contains("Cora Caretaker"));
        request(seeker, cora[1]).andExpect(status().isNotFound());

        // the optional GS letter being approved is not enough on its own either
        upload(cora[0], "GS_LETTER", PNG).andExpect(status().isOk());
        approve(admin[0], cora[1], "GS_LETTER");
        assertFalse(shown(seeker, "CARETAKER").contains("Cora Caretaker"));

        approve(admin[0], cora[1], "NIC");
        assertTrue(shown(seeker, "CARETAKER").contains("Cora Caretaker"));
        assertEquals(before + 1, count(seeker, "CARETAKER"));
        mvc.perform(get("/api/caregivers/me/verification").header("Authorization", cora[0])).andExpect(jsonPath("$.status").value("VERIFIED"));
        mvc.perform(get("/api/caregivers/me/profile").header("Authorization", cora[0])).andExpect(jsonPath("$.verified").value(true));
        request(seeker, cora[1]).andExpect(status().isCreated());
    }

    @Test
    void forNursesAndPhysiosOnlyTheCertificateDecidesAndTheNicIsOptional() throws Exception {
        String[] nora = caregiver("vf-opt-n@test.com", "Nora Nurse", "NURSING");
        String seeker = register("vf-opt-s@test.com", "Sam Seeker", "CARE_SEEKER")[0];
        String[] admin = admin("vf-opt-admin@test.com");

        // a NIC alone is not what a nurse is chosen for
        upload(nora[0], "NIC", PNG).andExpect(status().isOk());
        approve(admin[0], nora[1], "NIC");
        assertFalse(shown(seeker, "NURSING").contains("Nora Nurse"));

        upload(nora[0], "NURSING_CERTIFICATE", PDF).andExpect(status().isOk());
        approve(admin[0], nora[1], "NURSING_CERTIFICATE");
        assertTrue(shown(seeker, "NURSING").contains("Nora Nurse"));

        // an optional document that is rejected later does not hide a verified nurse
        upload(nora[0], "NIC", PNG).andExpect(status().isOk());   // replaces the approved one, back to pending
        long nic = docId(nora[1], "NIC");
        review(admin[0], nic, "REJECTED", "The photo is cut off", revision(nic)).andExpect(status().isOk());
        assertTrue(shown(seeker, "NURSING").contains("Nora Nurse"));
        mvc.perform(get("/api/caregivers/me/verification").header("Authorization", nora[0]))
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.documents[1].status").value("REJECTED"))
                .andExpect(jsonPath("$.documents[1].rejectReason").value("The photo is cut off"));

        // same for a physiotherapist with no NIC at all
        String[] pat = caregiver("vf-opt-p@test.com", "Pat Physio", "PHYSIOTHERAPIST");
        upload(pat[0], "DEGREE_CERTIFICATE", PNG).andExpect(status().isOk());
        approve(admin[0], pat[1], "DEGREE_CERTIFICATE");
        assertTrue(shown(seeker, "PHYSIOTHERAPIST").contains("Pat Physio"));
    }

    @Test
    void aRejectionExplainsItselfAndANewUploadStartsAgain() throws Exception {
        String[] pia = caregiver("vf-rej-p@test.com", "Pia Physio", "PHYSIOTHERAPIST");
        String seeker = register("vf-rej-s@test.com", "Sam Seeker", "CARE_SEEKER")[0];
        String[] admin = admin("vf-rej-admin@test.com");
        upload(pia[0], "DEGREE_CERTIFICATE", PNG).andExpect(status().isOk());
        long id = docId(pia[1], "DEGREE_CERTIFICATE");

        review(admin[0], id, "REJECTED", "   ", 1).andExpect(status().isBadRequest());   // the caregiver needs to know what to fix
        review(admin[0], id, "PENDING", null, 1).andExpect(status().isBadRequest());
        review(admin[0], id, "REJECTED", "The photo is blurry", 1).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTION_NEEDED"));
        mvc.perform(get("/api/caregivers/me/verification").header("Authorization", pia[0]))
                .andExpect(jsonPath("$.status").value("ACTION_NEEDED"))
                .andExpect(jsonPath("$.documents[0].rejectReason").value("The photo is blurry"));
        assertFalse(shown(seeker, "PHYSIOTHERAPIST").contains("Pia Physio"));

        // a new file clears the old verdict, and a review of the old file can no longer be applied to it
        upload(pia[0], "DEGREE_CERTIFICATE", PDF).andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[0].status").value("PENDING"))
                .andExpect(jsonPath("$.documents[0].rejectReason").doesNotExist());
        review(admin[0], id, "APPROVED", null, 1).andExpect(status().isConflict());
        assertFalse(shown(seeker, "PHYSIOTHERAPIST").contains("Pia Physio"));

        review(admin[0], id, "APPROVED", null, 2).andExpect(status().isOk());
        assertTrue(shown(seeker, "PHYSIOTHERAPIST").contains("Pia Physio"));

        // replacing an approved document puts the caregiver back in review, and out of search until it is approved again
        upload(pia[0], "DEGREE_CERTIFICATE", PNG).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_REVIEW"));
        assertFalse(shown(seeker, "PHYSIOTHERAPIST").contains("Pia Physio"));
    }

    @Test
    void changingJobChangesWhatIsRequired() throws Exception {
        String[] jo = caregiver("vf-job-c@test.com", "Jo Changer", "CARETAKER");
        String seeker = register("vf-job-s@test.com", "Sam Seeker", "CARE_SEEKER")[0];
        String[] admin = admin("vf-job-admin@test.com");
        upload(jo[0], "NIC", PNG).andExpect(status().isOk());
        approve(admin[0], jo[1], "NIC");
        assertTrue(shown(seeker, "CARETAKER").contains("Jo Changer"));

        // as a physiotherapist the NIC is only optional, and the degree is missing: hidden straight away
        setJob(jo[0], "Jo Changer", "PHYSIOTHERAPIST");
        mvc.perform(get("/api/caregivers/me/profile").header("Authorization", jo[0])).andExpect(jsonPath("$.verified").value(false));
        assertFalse(shown(seeker, "PHYSIOTHERAPIST").contains("Jo Changer"));
        mvc.perform(get("/api/caregivers/me/verification").header("Authorization", jo[0]))
                .andExpect(jsonPath("$.status").value("INCOMPLETE"))
                .andExpect(jsonPath("$.documents[1].type").value("NIC"))
                .andExpect(jsonPath("$.documents[1].status").value("APPROVED"));

        // going back, the approved NIC still counts
        setJob(jo[0], "Jo Changer", "CARETAKER");
        mvc.perform(get("/api/caregivers/me/profile").header("Authorization", jo[0])).andExpect(jsonPath("$.verified").value(true));
        assertTrue(shown(seeker, "CARETAKER").contains("Jo Changer"));

        // saving the profile again without changing the job leaves it as it was
        setJob(jo[0], "Jo Changer", "CARETAKER");
        mvc.perform(get("/api/caregivers/me/profile").header("Authorization", jo[0])).andExpect(jsonPath("$.verified").value(true));
    }

    // ------------------------------------------------------------------ the admin's rules

    @Test
    void anAdminCannotApproveTheirOwnDocuments() throws Exception {
        String[] both = caregiver("vf-own@test.com", "Olu Both", "CARETAKER");
        jdbc.update("update users set is_admin = true where email = ?", "vf-own@test.com");
        upload(both[0], "NIC", PNG).andExpect(status().isOk());
        long id = docId(both[1], "NIC");

        review(both[0], id, "APPROVED", null, 1).andExpect(status().isForbidden());
        mvc.perform(get("/api/caregivers/me/verification").header("Authorization", both[0])).andExpect(jsonPath("$.status").value("IN_REVIEW"));

        String[] other = admin("vf-own-admin@test.com");
        review(other[0], id, "APPROVED", null, 1).andExpect(status().isOk());
    }

    @Test
    void documentFilesAreForAdminsOnly() throws Exception {
        String[] owner = caregiver("vf-file-c@test.com", "Fay File", "CARETAKER");
        String[] stranger = caregiver("vf-file-x@test.com", "Xan Stranger", "CARETAKER");
        String seeker = register("vf-file-s@test.com", "Sam Seeker", "CARE_SEEKER")[0];
        String[] admin = admin("vf-file-admin@test.com");
        upload(owner[0], "NIC", PNG).andExpect(status().isOk());
        long id = docId(owner[1], "NIC");
        String url = "/api/admin/documents/" + id + "/file";

        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        mvc.perform(get(url).header("Authorization", seeker)).andExpect(status().isForbidden());
        mvc.perform(get(url).header("Authorization", stranger[0])).andExpect(status().isForbidden());
        mvc.perform(get(url).header("Authorization", owner[0])).andExpect(status().isForbidden());   // even the owner: it is the admin's copy

        byte[] served = mvc.perform(get(url).header("Authorization", admin[0]))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(PNG, served);
        mvc.perform(get("/api/admin/documents/999999/file").header("Authorization", admin[0])).andExpect(status().isNotFound());

        // and no public address serves them, whatever the name
        mvc.perform(get("/api/files/photos/" + fileName(owner[1], "NIC"))).andExpect(status().isNotFound());

        // the review endpoints are closed to everyone else too
        for (String auth : new String[]{seeker, owner[0]}) {
            mvc.perform(get("/api/admin/verifications").header("Authorization", auth)).andExpect(status().isForbidden());
            review(auth, id, "APPROVED", null, 1).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/admin/verifications")).andExpect(status().isUnauthorized());
    }

    @Test
    void theAdminSeesWhoIsWaitingAndWhatIsMissing() throws Exception {
        String[] wen = caregiver("vf-list-p@test.com", "Wen Waiting", "PHYSIOTHERAPIST");
        String[] admin = admin("vf-list-admin@test.com");
        upload(wen[0], "NIC", PNG).andExpect(status().isOk());   // only the optional one so far

        String waiting = mvc.perform(get("/api/admin/verifications").param("needsReview", "true").header("Authorization", admin[0]))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> names = JsonPath.read(waiting, "$[*].name");
        assertTrue(names.contains("Wen Waiting"));
        mvc.perform(get("/api/admin/verifications").param("needsReview", "true").header("Authorization", admin[0]))
                .andExpect(jsonPath("$[?(@.name == 'Wen Waiting')].email").value("vf-list-p@test.com"))
                .andExpect(jsonPath("$[?(@.name == 'Wen Waiting')].status").value("INCOMPLETE"))
                .andExpect(jsonPath("$[?(@.name == 'Wen Waiting')].documents[0].type").value("DEGREE_CERTIFICATE"));
        List<Object> degreeIds = JsonPath.read(waiting, "$[?(@.name == 'Wen Waiting')].documents[0].id");
        assertNull(degreeIds.get(0), "the degree certificate is not uploaded yet, so it has no id");
        mvc.perform(get("/api/admin/verifications/summary").header("Authorization", admin[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsReview").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        approve(admin[0], wen[1], "NIC");
        String after = mvc.perform(get("/api/admin/verifications").param("needsReview", "true").header("Authorization", admin[0]))
                .andReturn().getResponse().getContentAsString();
        assertFalse(((List<String>) JsonPath.read(after, "$[*].name")).contains("Wen Waiting"), "nothing left to review for her");
        String all = mvc.perform(get("/api/admin/verifications").header("Authorization", admin[0])).andReturn().getResponse().getContentAsString();
        assertTrue(((List<String>) JsonPath.read(all, "$[*].name")).contains("Wen Waiting"));
    }

    // ------------------------------------------------------------------ files on disk

    @Test
    void replacedAndRemovedFilesAreDeletedFromDisk() throws Exception {
        String[] cg = caregiver("vf-disk-c@test.com", "Dee Disk", "CARETAKER");
        String seeker = register("vf-disk-s@test.com", "Sam Seeker", "CARE_SEEKER")[0];
        String[] admin = admin("vf-disk-admin@test.com");

        upload(cg[0], "NIC", PNG).andExpect(status().isOk());
        String first = fileName(cg[1], "NIC");
        assertTrue(storage.exists(first));
        approve(admin[0], cg[1], "NIC");
        assertTrue(shown(seeker, "CARETAKER").contains("Dee Disk"));

        upload(cg[0], "NIC", PDF).andExpect(status().isOk());
        String second = fileName(cg[1], "NIC");
        assertNotEquals(first, second);
        assertFalse(storage.exists(first), "the old file is gone once the new one is saved");
        assertTrue(storage.exists(second));

        mvc.perform(delete("/api/caregivers/me/documents/NIC").header("Authorization", cg[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INCOMPLETE"))
                .andExpect(jsonPath("$.documents[0].status").doesNotExist());
        assertFalse(storage.exists(second));
        assertEquals(0, jdbc.queryForObject("select count(*) from caregiver_documents where user_id = ?", Integer.class, Long.parseLong(cg[1])));
        assertFalse(shown(seeker, "CARETAKER").contains("Dee Disk"));

        // removing something that was never uploaded is harmless
        mvc.perform(delete("/api/caregivers/me/documents/NIC").header("Authorization", cg[0])).andExpect(status().isOk());
    }

    @Test
    void storageOnlyAcceptsItsOwnGeneratedNames() {
        for (String bad : new String[]{"../../etc/passwd", "photo.jpg", "00000000-0000-0000-0000-000000000000.exe", "..", ""}) {
            assertFalse(storage.exists(bad), bad);
            assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> storage.load(bad));
            storage.delete(bad);   // must not throw or touch anything
        }
    }
}
