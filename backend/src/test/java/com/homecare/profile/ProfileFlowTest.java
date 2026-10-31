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

        // the name and the address are required, everything else is optional
        mvc.perform(put("/api/caregivers/me/profile").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Anu K\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@ == 'Address is required')]").isNotEmpty());
        mvc.perform(put("/api/caregivers/me/profile").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"  Anu K  \",\"address\":\"  12 Temple Road, Jaffna  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Anu K"))
                .andExpect(jsonPath("$.address").value("12 Temple Road, Jaffna"))
                .andExpect(jsonPath("$.age").doesNotExist());

        mvc.perform(put("/api/caregivers/me/profile").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Anu K\",\"age\":34,\"experienceYears\":6,\"qualification\":\"Diploma in Nursing\",\"jobType\":\"NURSING\",\"address\":\"12 Temple Road, Jaffna\"}"))
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
                        .content("{\"fullName\":\"R\",\"address\":\"12 Temple Road, Jaffna\",\"rates\":{\"HOURLY\":1500,\"FULL_DAY_NIGHT\":12000.50}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rates.HOURLY").value(1500))
                .andExpect(jsonPath("$.rates.FULL_DAY_NIGHT").value(12000.50));

        // saving again replaces the whole set
        mvc.perform(put(url).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"R\",\"address\":\"12 Temple Road, Jaffna\",\"rates\":{\"DAILY\":6000}}"))
                .andExpect(jsonPath("$.rates.DAILY").value(6000))
                .andExpect(jsonPath("$.rates.HOURLY").doesNotExist());
        mvc.perform(get(url).header("Authorization", auth))
                .andExpect(jsonPath("$.rates.DAILY").value(6000))
                .andExpect(jsonPath("$.rates.FULL_DAY_NIGHT").doesNotExist());

        // no rates at all clears them
        mvc.perform(put(url).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"R\",\"address\":\"12 Temple Road, Jaffna\"}"))
                .andExpect(jsonPath("$.rates.DAILY").doesNotExist());

        for (String bad : new String[]{"{\"HOURLY\":0}", "{\"HOURLY\":-5}", "{\"HOURLY\":12345678}",
                "{\"HOURLY\":10.999}", "{\"WEEKLY\":100}", "{\"HOURLY\":null}"}) {
            mvc.perform(put(url).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fullName\":\"R\",\"address\":\"12 Temple Road, Jaffna\",\"rates\":" + bad + "}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void validationErrorsAreReadableAndListedTogether() throws Exception {
        String auth = "Bearer " + token("cg-errs@test.com", "CAREGIVER");
        mvc.perform(put("/api/caregivers/me/profile").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"address\":\"12 Temple Road, Jaffna\",\"age\":12,\"experienceYears\":99,\"rates\":{\"HOURLY\":0}}"))
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
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"A\",\"address\":\"12 Temple Road, Jaffna\",\"age\":12}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/caregivers/me/profile").header("Authorization", cg)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"A\",\"address\":\"12 Temple Road, Jaffna\",\"jobType\":\"PILOT\"}"))
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

    @Test
    void caretakerOnboardingPersistsAllStepsAndRemainsRoleSpecific() throws Exception {
        String auth = "Bearer " + token("cg-caretaker@test.com", "CAREGIVER");
        String payload = """
                {
                  "fullName": "Sivaram Rasathurai",
                  "age": 30,
                  "experienceYears": 3,
                  "qualification": "Elderly care, Feeding assistance, Companionship",
                  "jobType": "CARETAKER",
                  "address": "12 Temple Road, Jaffna",
                  "rates": { "HOURLY": 1000 },
                  "caretakerOnboarding": {
                    "dob": "1996-05-12",
                    "gender": "Male",
                    "preferredLanguage": "Tamil",
                    "spokenLanguages": ["Tamil", "English"],
                    "emergencyContactName": "K. Rasathurai",
                    "emergencyContactRelation": "Family member",
                    "emergencyContactPhone": "0771234567",
                    "caretakerCategory": "ELDERLY_CAREGIVER",
                    "skillSources": {
                      "Elderly care": "Learned on previous job",
                      "Feeding assistance": "Family caregiving experience"
                    },
                    "isNewCaregiver": false,
                    "workHistory": [
                      {
                        "org": "St. Mary's Elders Home",
                        "title": "Senior Caretaker",
                        "duration": "2023 - 2026",
                        "reasonForLeaving": "Relocated",
                        "responsibilities": "Assisted with mobility and daily meals"
                      }
                    ],
                    "referenceName": "Dr. K. Selvarajah",
                    "referencePhone": "0779876543",
                    "educationLevel": "G.C.E. A/L",
                    "firstAidCertified": "Yes, certified",
                    "availableDays": ["Mon", "Tue", "Wed", "Thu", "Fri"],
                    "preferredShift": "Flexible / Any shift",
                    "workType": "Full-time",
                    "livingArrangement": "Live-out",
                    "preferredLocations": "Jaffna, Nallur",
                    "startWhen": "Immediately",
                    "callWhatsAppPhone": "0771112233",
                    "callMethod": "WhatsApp Call",
                    "callAvailableTimes": ["Morning (8:00 AM – 12:00 PM)", "Evening (4:00 PM – 8:00 PM)"],
                    "callNotes": "Usually free after 9 AM",
                    "consents": {
                      "accurateInfo": true,
                      "backgroundCheck": true,
                      "codeOfConduct": true,
                      "termsPrivacy": true
                    },
                    "consented": true,
                    "backgroundStepSaved": true,
                    "skillsStepSaved": true,
                    "experienceStepSaved": true,
                    "identityStepSaved": true,
                    "certificatesReviewed": true,
                    "availabilityStepSaved": true,
                    "interviewScheduled": true,
                    "applicationSubmitted": true,
                    "lastStep": "review"
                  }
                }
                """;

        mvc.perform(put("/api/caregivers/me/profile").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobType").value("CARETAKER"))
                .andExpect(jsonPath("$.caretakerOnboarding.caretakerCategory").value("ELDERLY_CAREGIVER"))
                .andExpect(jsonPath("$.caretakerOnboarding.spokenLanguages[1]").value("English"))
                .andExpect(jsonPath("$.caretakerOnboarding.workHistory[0].org").value("St. Mary's Elders Home"))
                .andExpect(jsonPath("$.caretakerOnboarding.callWhatsAppPhone").value("0771112233"))
                .andExpect(jsonPath("$.caretakerOnboarding.callMethod").value("WhatsApp Call"))
                .andExpect(jsonPath("$.caretakerOnboarding.callAvailableTimes.length()").value(2))
                .andExpect(jsonPath("$.caretakerOnboarding.applicationSubmitted").value(true));

        mvc.perform(get("/api/caregivers/me/profile").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caretakerOnboarding.emergencyContactName").value("K. Rasathurai"))
                .andExpect(jsonPath("$.caretakerOnboarding.skillSources['Elderly care']").value("Learned on previous job"))
                .andExpect(jsonPath("$.caretakerOnboarding.consents.accurateInfo").value(true));

        // Nursing profiles do not expose caretakerOnboarding
        String nurseAuth = "Bearer " + token("cg-nurse@test.com", "CAREGIVER");
        mvc.perform(put("/api/caregivers/me/profile").header("Authorization", nurseAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Nurse A\",\"age\":28,\"jobType\":\"NURSING\",\"address\":\"Colombo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobType").value("NURSING"))
                .andExpect(jsonPath("$.caretakerOnboarding").doesNotExist());
    }

    @Test
    void nursingOnboardingPersistsServicesRatesAndTravelRules() throws Exception {
        String auth = "Bearer " + token("cg-nurse-full@test.com", "CAREGIVER");
        String payload = """
                {
                  "fullName": "Nirmala Perera",
                  "age": 32,
                  "experienceYears": 7,
                  "qualification": "Urinary catheter replacement, Wound dressing, Blood pressure checking",
                  "jobType": "NURSING",
                  "address": "45 Hospital Road, Jaffna",
                  "rates": { "SESSION": 1500 },
                  "nursingOnboarding": {
                    "dob": "1994-03-20",
                    "gender": "Female",
                    "preferredLanguage": "Tamil",
                    "spokenLanguages": ["Tamil", "English", "Sinhala"],
                    "emergencyContactName": "S. Perera",
                    "emergencyContactRelation": "Spouse / Partner",
                    "emergencyContactPhone": "0772223344",
                    "nursingQualificationType": "Diploma in Nursing (3-Year General Nursing)",
                    "trainingInstitute": "School of Nursing, Jaffna",
                    "graduationYear": "2018",
                    "registrationBody": "Sri Lanka Nursing Council (SLNC)",
                    "registrationNumber": "SLNC-48291",
                    "registrationValidUntil": "2028-12-31",
                    "clinicalAreas": ["General Medical / Surgical Ward", "ICU / Critical Care"],
                    "isNewHomeNurse": false,
                    "currentWorkStatus": "Government hospital nurse (part-time home visits)",
                    "workHistory": [
                      {
                        "org": "Teaching Hospital Jaffna",
                        "title": "Staff Nurse",
                        "department": "Surgical Ward",
                        "duration": "2019 - Present",
                        "responsibilities": "Post-op wound care, catheterization, and IV medications"
                      }
                    ],
                    "referenceName": "Dr. M. Ratnam",
                    "referencePhone": "0778889900",
                    "services": [
                      {
                        "code": "CATHETER_REPLACEMENT",
                        "name": "Urinary catheter replacement",
                        "category": "Catheter care",
                        "expectedRate": 2000,
                        "duration": "30 minutes",
                        "experienceLevel": "Extensive experience",
                        "timesPerformed": "50+",
                        "lastPerformed": "Within 3 months",
                        "bringsEquipment": true,
                        "providesConsumables": true
                      },
                      {
                        "code": "WOUND_DRESSING",
                        "name": "Wound dressing",
                        "category": "Wound care",
                        "expectedRate": 1500,
                        "duration": "30 minutes",
                        "experienceLevel": "Performed regularly",
                        "timesPerformed": "50+",
                        "lastPerformed": "Within 3 months",
                        "bringsEquipment": true,
                        "providesConsumables": false
                      }
                    ],
                    "offersLongDurationCare": false,
                    "additionalTraining": "Advanced Wound Management Certificate",
                    "availableDays": ["Mon", "Wed", "Fri", "Sat", "Sun"],
                    "preferredShift": "Evening (2:00 PM – 8:00 PM)",
                    "minVisitFee": 1500,
                    "maxTravelDistance": "20 km",
                    "chargesExtraForTravel": true,
                    "travelAllowance": "LKR 80 per km beyond 5 km",
                    "availableForUrgent": true,
                    "providesNightServices": true,
                    "nightExtraCharge": "LKR 1,000 extra",
                    "multipleServicesPerVisit": true,
                    "providesMedicalConsumables": true,
                    "availableForRecurring": true,
                    "preferredLocations": "Jaffna, Nallur, Kopay",
                    "callWhatsAppPhone": "0775556677",
                    "callMethod": "WhatsApp Call",
                    "callAvailableTimes": ["Evening (4:00 PM – 8:00 PM)"],
                    "callNotes": "Free after hospital shift at 4 PM",
                    "consents": {
                      "accurateInfo": true,
                      "clinicalScope": true,
                      "backgroundCheck": true,
                      "termsPrivacy": true
                    },
                    "consented": true,
                    "credentialsStepSaved": true,
                    "experienceStepSaved": true,
                    "servicesStepSaved": true,
                    "identityStepSaved": true,
                    "certificatesReviewed": true,
                    "availabilityStepSaved": true,
                    "interviewScheduled": true,
                    "applicationSubmitted": true,
                    "lastStep": "review"
                  }
                }
                """;

        mvc.perform(put("/api/caregivers/me/profile").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobType").value("NURSING"))
                .andExpect(jsonPath("$.caretakerOnboarding").doesNotExist())
                .andExpect(jsonPath("$.nursingOnboarding.registrationNumber").value("SLNC-48291"))
                .andExpect(jsonPath("$.nursingOnboarding.services.length()").value(2))
                .andExpect(jsonPath("$.nursingOnboarding.services[0].name").value("Urinary catheter replacement"))
                .andExpect(jsonPath("$.nursingOnboarding.services[0].expectedRate").value(2000))
                .andExpect(jsonPath("$.nursingOnboarding.services[0].timesPerformed").value("50+"))
                .andExpect(jsonPath("$.nursingOnboarding.minVisitFee").value(1500))
                .andExpect(jsonPath("$.nursingOnboarding.maxTravelDistance").value("20 km"))
                .andExpect(jsonPath("$.nursingOnboarding.providesNightServices").value(true))
                .andExpect(jsonPath("$.nursingOnboarding.applicationSubmitted").value(true));

        mvc.perform(get("/api/caregivers/me/profile").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nursingOnboarding.trainingInstitute").value("School of Nursing, Jaffna"))
                .andExpect(jsonPath("$.nursingOnboarding.services[1].name").value("Wound dressing"))
                .andExpect(jsonPath("$.nursingOnboarding.consents.clinicalScope").value(true));
    }
}
