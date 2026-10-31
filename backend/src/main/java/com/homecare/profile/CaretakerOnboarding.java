package com.homecare.profile;

import com.homecare.profile.ProfileDtos.CaretakerOnboardingDto;
import com.homecare.profile.ProfileDtos.WorkHistoryEntryDto;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;

/**
 * Caretaker-only onboarding record (Steps 2–10).
 * Separated from {@link CaregiverProfile} so Nursing and Physiotherapist roles remain decoupled.
 */
@Entity
@Table(name = "caretaker_onboarding")
public class CaretakerOnboarding {

    private static final String DELIM = "|";

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(length = 20)
    private String dob;

    @Column(length = 30)
    private String gender;

    @Column(name = "preferred_language", length = 40)
    private String preferredLanguage;

    @Column(name = "spoken_languages", length = 255)
    private String spokenLanguages;

    @Column(name = "emergency_contact_name", length = 150)
    private String emergencyContactName;

    @Column(name = "emergency_contact_relation", length = 80)
    private String emergencyContactRelation;

    @Column(name = "emergency_contact_phone", length = 30)
    private String emergencyContactPhone;

    @Column(name = "caretaker_category", length = 60)
    private String caretakerCategory;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "caretaker_skill_sources", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "skill_name", length = 100)
    @Column(name = "source", nullable = false, length = 100)
    private Map<String, String> skillSources = new LinkedHashMap<>();

    @Column(name = "is_new_caregiver", nullable = false)
    private boolean isNewCaregiver;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "caretaker_work_history", joinColumns = @JoinColumn(name = "user_id"))
    @OrderColumn(name = "entry_order")
    private List<CaretakerWorkEntry> workHistory = new ArrayList<>();

    @Column(name = "reference_name", length = 150)
    private String referenceName;

    @Column(name = "reference_phone", length = 30)
    private String referencePhone;

    @Column(name = "education_level", length = 80)
    private String educationLevel;

    @Column(name = "first_aid_certified", length = 60)
    private String firstAidCertified;

    @Column(name = "available_days", length = 100)
    private String availableDays;

    @Column(name = "preferred_shift", length = 80)
    private String preferredShift;

    @Column(name = "work_type", length = 40)
    private String workType;

    @Column(name = "living_arrangement", length = 40)
    private String livingArrangement;

    @Column(name = "preferred_locations", length = 200)
    private String preferredLocations;

    @Column(name = "start_when", length = 60)
    private String startWhen;

    @Column(name = "call_whatsapp_phone", length = 30)
    private String callWhatsAppPhone;

    @Column(name = "call_method", length = 60)
    private String callMethod;

    @Column(name = "call_available_times", length = 300)
    private String callAvailableTimes;

    @Column(name = "call_notes", length = 200)
    private String callNotes;

    @Column(name = "consent_accurate_info", nullable = false)
    private boolean consentAccurateInfo;

    @Column(name = "consent_background_check", nullable = false)
    private boolean consentBackgroundCheck;

    @Column(name = "consent_code_of_conduct", nullable = false)
    private boolean consentCodeOfConduct;

    @Column(name = "consent_terms_privacy", nullable = false)
    private boolean consentTermsPrivacy;

    @Column(nullable = false)
    private boolean consented;

    @Column(name = "background_step_saved", nullable = false)
    private boolean backgroundStepSaved;

    @Column(name = "skills_step_saved", nullable = false)
    private boolean skillsStepSaved;

    @Column(name = "experience_step_saved", nullable = false)
    private boolean experienceStepSaved;

    @Column(name = "identity_step_saved", nullable = false)
    private boolean identityStepSaved;

    @Column(name = "certificates_reviewed", nullable = false)
    private boolean certificatesReviewed;

    @Column(name = "availability_step_saved", nullable = false)
    private boolean availabilityStepSaved;

    @Column(name = "interview_scheduled", nullable = false)
    private boolean interviewScheduled;

    @Column(name = "application_submitted", nullable = false)
    private boolean applicationSubmitted;

    @Column(name = "last_step", length = 40)
    private String lastStep;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected CaretakerOnboarding() {}

    public CaretakerOnboarding(Long userId) {
        this.userId = userId;
    }

    public void apply(CaretakerOnboardingDto dto) {
        if (dto == null) return;
        this.dob = clean(dto.dob(), 20);
        this.gender = clean(dto.gender(), 30);
        this.preferredLanguage = clean(dto.preferredLanguage(), 40);
        this.spokenLanguages = joinList(dto.spokenLanguages(), 255);
        this.emergencyContactName = clean(dto.emergencyContactName(), 150);
        this.emergencyContactRelation = clean(dto.emergencyContactRelation(), 80);
        this.emergencyContactPhone = clean(dto.emergencyContactPhone(), 30);
        this.caretakerCategory = clean(dto.caretakerCategory(), 60);

        this.skillSources.clear();
        if (dto.skillSources() != null) {
            dto.skillSources().forEach((k, v) -> {
                String key = clean(k, 100);
                String val = clean(v, 100);
                if (key != null && val != null) {
                    this.skillSources.put(key, val);
                }
            });
        }

        if (dto.isNewCaregiver() != null) {
            this.isNewCaregiver = dto.isNewCaregiver();
        }

        this.workHistory.clear();
        if (dto.workHistory() != null) {
            for (WorkHistoryEntryDto w : dto.workHistory()) {
                if (w == null) continue;
                String org = clean(w.org(), 150);
                String title = clean(w.title(), 120);
                String duration = clean(w.duration(), 100);
                String reason = clean(w.reasonForLeaving(), 150);
                String resp = clean(w.responsibilities(), 255);
                if (org != null || title != null || duration != null || reason != null || resp != null) {
                    this.workHistory.add(new CaretakerWorkEntry(org, title, duration, reason, resp));
                }
            }
        }

        this.referenceName = clean(dto.referenceName(), 150);
        this.referencePhone = clean(dto.referencePhone(), 30);
        this.educationLevel = clean(dto.educationLevel(), 80);
        this.firstAidCertified = clean(dto.firstAidCertified(), 60);
        this.availableDays = joinList(dto.availableDays(), 100);
        this.preferredShift = clean(dto.preferredShift(), 80);
        this.workType = clean(dto.workType(), 40);
        this.livingArrangement = clean(dto.livingArrangement(), 40);
        this.preferredLocations = clean(dto.preferredLocations(), 200);
        this.startWhen = clean(dto.startWhen(), 60);
        this.callWhatsAppPhone = clean(dto.callWhatsAppPhone(), 30);
        this.callMethod = clean(dto.callMethod(), 60);
        this.callAvailableTimes = joinList(dto.callAvailableTimes(), 300);
        this.callNotes = clean(dto.callNotes(), 200);

        if (dto.consents() != null) {
            this.consentAccurateInfo = Boolean.TRUE.equals(dto.consents().get("accurateInfo"));
            this.consentBackgroundCheck = Boolean.TRUE.equals(dto.consents().get("backgroundCheck"));
            this.consentCodeOfConduct = Boolean.TRUE.equals(dto.consents().get("codeOfConduct"));
            this.consentTermsPrivacy = Boolean.TRUE.equals(dto.consents().get("termsPrivacy"));
        }
        if (dto.consented() != null) {
            this.consented = dto.consented();
        } else {
            this.consented = this.consentAccurateInfo && this.consentBackgroundCheck
                    && this.consentCodeOfConduct && this.consentTermsPrivacy;
        }

        if (dto.backgroundStepSaved() != null) this.backgroundStepSaved = dto.backgroundStepSaved();
        if (dto.skillsStepSaved() != null) this.skillsStepSaved = dto.skillsStepSaved();
        if (dto.experienceStepSaved() != null) this.experienceStepSaved = dto.experienceStepSaved();
        if (dto.identityStepSaved() != null) this.identityStepSaved = dto.identityStepSaved();
        if (dto.certificatesReviewed() != null) this.certificatesReviewed = dto.certificatesReviewed();
        if (dto.availabilityStepSaved() != null) this.availabilityStepSaved = dto.availabilityStepSaved();
        if (dto.interviewScheduled() != null) this.interviewScheduled = dto.interviewScheduled();
        if (dto.applicationSubmitted() != null) this.applicationSubmitted = dto.applicationSubmitted();
        if (dto.lastStep() != null) this.lastStep = clean(dto.lastStep(), 40);

        this.updatedAt = Instant.now();
    }

    public CaretakerOnboardingDto toDto() {
        Map<String, Boolean> consents = new LinkedHashMap<>();
        consents.put("accurateInfo", consentAccurateInfo);
        consents.put("backgroundCheck", consentBackgroundCheck);
        consents.put("codeOfConduct", consentCodeOfConduct);
        consents.put("termsPrivacy", consentTermsPrivacy);

        List<WorkHistoryEntryDto> history = workHistory.stream()
                .filter(Objects::nonNull)
                .map(w -> new WorkHistoryEntryDto(w.getOrg(), w.getTitle(), w.getDuration(), w.getReasonForLeaving(), w.getResponsibilities()))
                .toList();

        return new CaretakerOnboardingDto(
                dob,
                gender,
                preferredLanguage,
                splitList(spokenLanguages),
                emergencyContactName,
                emergencyContactRelation,
                emergencyContactPhone,
                caretakerCategory,
                new LinkedHashMap<>(skillSources),
                isNewCaregiver,
                history,
                referenceName,
                referencePhone,
                educationLevel,
                firstAidCertified,
                splitList(availableDays),
                preferredShift,
                workType,
                livingArrangement,
                preferredLocations,
                startWhen,
                callWhatsAppPhone,
                callMethod,
                splitList(callAvailableTimes),
                callNotes,
                consents,
                consented,
                backgroundStepSaved,
                skillsStepSaved,
                experienceStepSaved,
                identityStepSaved,
                certificatesReviewed,
                availabilityStepSaved,
                interviewScheduled,
                applicationSubmitted,
                lastStep
        );
    }

    public Long getUserId() { return userId; }

    private static String clean(String value, int maxLen) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() > maxLen ? trimmed.substring(0, maxLen) : trimmed;
    }

    private static String joinList(List<String> items, int maxLen) {
        if (items == null || items.isEmpty()) return null;
        String joined = String.join(DELIM, items.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
        if (joined.isEmpty()) return null;
        return joined.length() > maxLen ? joined.substring(0, maxLen) : joined;
    }

    private static List<String> splitList(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("\\|"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
