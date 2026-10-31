package com.homecare.profile;

import com.homecare.profile.ProfileDtos.NursingOnboardingDto;
import com.homecare.profile.ProfileDtos.NursingServiceDto;
import com.homecare.profile.ProfileDtos.NursingWorkHistoryDto;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Nursing-only onboarding record (Steps 2–10).
 * Separated from {@link CaregiverProfile} and {@link CaretakerOnboarding}.
 */
@Entity
@Table(name = "nursing_onboarding")
public class NursingOnboarding {

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

    @Column(name = "nursing_qualification_type", length = 100)
    private String nursingQualificationType;

    @Column(name = "training_institute", length = 160)
    private String trainingInstitute;

    @Column(name = "graduation_year", length = 10)
    private String graduationYear;

    @Column(name = "registration_body", length = 80)
    private String registrationBody;

    @Column(name = "registration_number", length = 80)
    private String registrationNumber;

    @Column(name = "registration_valid_until", length = 20)
    private String registrationValidUntil;

    @Column(name = "clinical_areas", length = 300)
    private String clinicalAreas;

    @Column(name = "is_new_home_nurse", nullable = false)
    private boolean isNewHomeNurse;

    @Column(name = "current_work_status", length = 80)
    private String currentWorkStatus;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "nursing_work_history", joinColumns = @JoinColumn(name = "user_id"))
    @OrderColumn(name = "entry_order")
    private List<NursingWorkEntry> workHistory = new ArrayList<>();

    @Column(name = "reference_name", length = 150)
    private String referenceName;

    @Column(name = "reference_phone", length = 30)
    private String referencePhone;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "nursing_service_rates", joinColumns = @JoinColumn(name = "user_id"))
    @OrderColumn(name = "service_order")
    private List<NursingServiceOffering> services = new ArrayList<>();

    @Column(name = "offers_long_duration_care", nullable = false)
    private boolean offersLongDurationCare;

    @Column(name = "additional_training", length = 300)
    private String additionalTraining;

    @Column(name = "available_days", length = 100)
    private String availableDays;

    @Column(name = "preferred_shift", length = 80)
    private String preferredShift;

    @Column(name = "min_visit_fee", precision = 10, scale = 2)
    private BigDecimal minVisitFee;

    @Column(name = "max_travel_distance", length = 40)
    private String maxTravelDistance;

    @Column(name = "charges_extra_for_travel", nullable = false)
    private boolean chargesExtraForTravel;

    @Column(name = "travel_allowance", length = 100)
    private String travelAllowance;

    @Column(name = "available_for_urgent", nullable = false)
    private boolean availableForUrgent = true;

    @Column(name = "provides_night_services", nullable = false)
    private boolean providesNightServices;

    @Column(name = "night_extra_charge", length = 100)
    private String nightExtraCharge;

    @Column(name = "multiple_services_per_visit", nullable = false)
    private boolean multipleServicesPerVisit = true;

    @Column(name = "provides_medical_consumables", nullable = false)
    private boolean providesMedicalConsumables;

    @Column(name = "available_for_recurring", nullable = false)
    private boolean availableForRecurring = true;

    @Column(name = "preferred_locations", length = 200)
    private String preferredLocations;

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

    @Column(name = "consent_clinical_scope", nullable = false)
    private boolean consentClinicalScope;

    @Column(name = "consent_background_check", nullable = false)
    private boolean consentBackgroundCheck;

    @Column(name = "consent_terms_privacy", nullable = false)
    private boolean consentTermsPrivacy;

    @Column(nullable = false)
    private boolean consented;

    @Column(name = "credentials_step_saved", nullable = false)
    private boolean credentialsStepSaved;

    @Column(name = "experience_step_saved", nullable = false)
    private boolean experienceStepSaved;

    @Column(name = "services_step_saved", nullable = false)
    private boolean servicesStepSaved;

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

    protected NursingOnboarding() {}

    public NursingOnboarding(Long userId) {
        this.userId = userId;
    }

    public void apply(NursingOnboardingDto dto) {
        if (dto == null) return;
        this.dob = clean(dto.dob(), 20);
        this.gender = clean(dto.gender(), 30);
        this.preferredLanguage = clean(dto.preferredLanguage(), 40);
        this.spokenLanguages = joinList(dto.spokenLanguages(), 255);
        this.emergencyContactName = clean(dto.emergencyContactName(), 150);
        this.emergencyContactRelation = clean(dto.emergencyContactRelation(), 80);
        this.emergencyContactPhone = clean(dto.emergencyContactPhone(), 30);

        this.nursingQualificationType = clean(dto.nursingQualificationType(), 100);
        this.trainingInstitute = clean(dto.trainingInstitute(), 160);
        this.graduationYear = clean(dto.graduationYear(), 10);
        this.registrationBody = clean(dto.registrationBody(), 80);
        this.registrationNumber = clean(dto.registrationNumber(), 80);
        this.registrationValidUntil = clean(dto.registrationValidUntil(), 20);
        this.clinicalAreas = joinList(dto.clinicalAreas(), 300);

        if (dto.isNewHomeNurse() != null) {
            this.isNewHomeNurse = dto.isNewHomeNurse();
        }
        this.currentWorkStatus = clean(dto.currentWorkStatus(), 80);

        this.workHistory.clear();
        if (dto.workHistory() != null) {
            for (NursingWorkHistoryDto w : dto.workHistory()) {
                if (w == null) continue;
                String org = clean(w.org(), 150);
                String title = clean(w.title(), 120);
                String dept = clean(w.department(), 100);
                String dur = clean(w.duration(), 100);
                String resp = clean(w.responsibilities(), 255);
                if (org != null || title != null || dept != null || dur != null || resp != null) {
                    this.workHistory.add(new NursingWorkEntry(org, title, dept, dur, resp));
                }
            }
        }

        this.referenceName = clean(dto.referenceName(), 150);
        this.referencePhone = clean(dto.referencePhone(), 30);

        this.services.clear();
        if (dto.services() != null) {
            for (NursingServiceDto s : dto.services()) {
                if (s == null) continue;
                String code = clean(s.code(), 80);
                String name = clean(s.name(), 120);
                if (code == null || name == null || s.expectedRate() == null || s.expectedRate().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                this.services.add(new NursingServiceOffering(
                        code,
                        name,
                        clean(s.category(), 80),
                        s.expectedRate(),
                        clean(s.duration(), 40),
                        clean(s.experienceLevel(), 80),
                        clean(s.timesPerformed(), 40),
                        clean(s.lastPerformed(), 60),
                        Boolean.TRUE.equals(s.bringsEquipment()),
                        Boolean.TRUE.equals(s.providesConsumables())
                ));
            }
        }

        if (dto.offersLongDurationCare() != null) {
            this.offersLongDurationCare = dto.offersLongDurationCare();
        }
        this.additionalTraining = clean(dto.additionalTraining(), 300);
        this.availableDays = joinList(dto.availableDays(), 100);
        this.preferredShift = clean(dto.preferredShift(), 80);
        this.minVisitFee = dto.minVisitFee() != null && dto.minVisitFee().compareTo(BigDecimal.ZERO) > 0 ? dto.minVisitFee() : null;
        this.maxTravelDistance = clean(dto.maxTravelDistance(), 40);
        if (dto.chargesExtraForTravel() != null) this.chargesExtraForTravel = dto.chargesExtraForTravel();
        this.travelAllowance = clean(dto.travelAllowance(), 100);
        if (dto.availableForUrgent() != null) this.availableForUrgent = dto.availableForUrgent();
        if (dto.providesNightServices() != null) this.providesNightServices = dto.providesNightServices();
        this.nightExtraCharge = clean(dto.nightExtraCharge(), 100);
        if (dto.multipleServicesPerVisit() != null) this.multipleServicesPerVisit = dto.multipleServicesPerVisit();
        if (dto.providesMedicalConsumables() != null) this.providesMedicalConsumables = dto.providesMedicalConsumables();
        if (dto.availableForRecurring() != null) this.availableForRecurring = dto.availableForRecurring();
        this.preferredLocations = clean(dto.preferredLocations(), 200);

        this.callWhatsAppPhone = clean(dto.callWhatsAppPhone(), 30);
        this.callMethod = clean(dto.callMethod(), 60);
        this.callAvailableTimes = joinList(dto.callAvailableTimes(), 300);
        this.callNotes = clean(dto.callNotes(), 200);

        if (dto.consents() != null) {
            this.consentAccurateInfo = Boolean.TRUE.equals(dto.consents().get("accurateInfo"));
            this.consentClinicalScope = Boolean.TRUE.equals(dto.consents().get("clinicalScope"));
            this.consentBackgroundCheck = Boolean.TRUE.equals(dto.consents().get("backgroundCheck"));
            this.consentTermsPrivacy = Boolean.TRUE.equals(dto.consents().get("termsPrivacy"));
        }
        if (dto.consented() != null) {
            this.consented = dto.consented();
        } else {
            this.consented = this.consentAccurateInfo && this.consentClinicalScope
                    && this.consentBackgroundCheck && this.consentTermsPrivacy;
        }

        if (dto.credentialsStepSaved() != null) this.credentialsStepSaved = dto.credentialsStepSaved();
        if (dto.experienceStepSaved() != null) this.experienceStepSaved = dto.experienceStepSaved();
        if (dto.servicesStepSaved() != null) this.servicesStepSaved = dto.servicesStepSaved();
        if (dto.identityStepSaved() != null) this.identityStepSaved = dto.identityStepSaved();
        if (dto.certificatesReviewed() != null) this.certificatesReviewed = dto.certificatesReviewed();
        if (dto.availabilityStepSaved() != null) this.availabilityStepSaved = dto.availabilityStepSaved();
        if (dto.interviewScheduled() != null) this.interviewScheduled = dto.interviewScheduled();
        if (dto.applicationSubmitted() != null) this.applicationSubmitted = dto.applicationSubmitted();
        if (dto.lastStep() != null) this.lastStep = clean(dto.lastStep(), 40);

        this.updatedAt = Instant.now();
    }

    public NursingOnboardingDto toDto() {
        Map<String, Boolean> consents = new LinkedHashMap<>();
        consents.put("accurateInfo", consentAccurateInfo);
        consents.put("clinicalScope", consentClinicalScope);
        consents.put("backgroundCheck", consentBackgroundCheck);
        consents.put("termsPrivacy", consentTermsPrivacy);

        List<NursingWorkHistoryDto> history = workHistory.stream()
                .filter(Objects::nonNull)
                .map(w -> new NursingWorkHistoryDto(w.getOrg(), w.getTitle(), w.getDepartment(), w.getDuration(), w.getResponsibilities()))
                .toList();

        List<NursingServiceDto> serviceDtos = services.stream()
                .filter(Objects::nonNull)
                .map(s -> new NursingServiceDto(
                        s.getCode(),
                        s.getName(),
                        s.getCategory(),
                        s.getExpectedRate(),
                        s.getDuration(),
                        s.getExperienceLevel(),
                        s.getTimesPerformed(),
                        s.getLastPerformed(),
                        s.isBringsEquipment(),
                        s.isProvidesConsumables()
                ))
                .toList();

        return new NursingOnboardingDto(
                dob,
                gender,
                preferredLanguage,
                splitList(spokenLanguages),
                emergencyContactName,
                emergencyContactRelation,
                emergencyContactPhone,
                nursingQualificationType,
                trainingInstitute,
                graduationYear,
                registrationBody,
                registrationNumber,
                registrationValidUntil,
                splitList(clinicalAreas),
                isNewHomeNurse,
                currentWorkStatus,
                history,
                referenceName,
                referencePhone,
                serviceDtos,
                offersLongDurationCare,
                additionalTraining,
                splitList(availableDays),
                preferredShift,
                minVisitFee,
                maxTravelDistance,
                chargesExtraForTravel,
                travelAllowance,
                availableForUrgent,
                providesNightServices,
                nightExtraCharge,
                multipleServicesPerVisit,
                providesMedicalConsumables,
                availableForRecurring,
                preferredLocations,
                callWhatsAppPhone,
                callMethod,
                splitList(callAvailableTimes),
                callNotes,
                consents,
                consented,
                credentialsStepSaved,
                experienceStepSaved,
                servicesStepSaved,
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
