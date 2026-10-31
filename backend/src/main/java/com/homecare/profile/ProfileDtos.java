package com.homecare.profile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class ProfileDtos {
    private ProfileDtos() {}

    public record WorkHistoryEntryDto(
            @Size(max = 150) String org,
            @Size(max = 120) String title,
            @Size(max = 100) String duration,
            @Size(max = 150) String reasonForLeaving,
            @Size(max = 255) String responsibilities
    ) {}

    /** Role-specific onboarding details for CARETAKER only. */
    public record CaretakerOnboardingDto(
            @Size(max = 20) String dob,
            @Size(max = 30) String gender,
            @Size(max = 40) String preferredLanguage,
            List<@Size(max = 40) String> spokenLanguages,
            @Size(max = 150) String emergencyContactName,
            @Size(max = 80) String emergencyContactRelation,
            @Size(max = 30) String emergencyContactPhone,
            @Size(max = 60) String caretakerCategory,
            Map<@Size(max = 100) String, @Size(max = 100) String> skillSources,
            Boolean isNewCaregiver,
            List<@Valid WorkHistoryEntryDto> workHistory,
            @Size(max = 150) String referenceName,
            @Size(max = 30) String referencePhone,
            @Size(max = 80) String educationLevel,
            @Size(max = 60) String firstAidCertified,
            List<@Size(max = 20) String> availableDays,
            @Size(max = 80) String preferredShift,
            @Size(max = 40) String workType,
            @Size(max = 40) String livingArrangement,
            @Size(max = 200) String preferredLocations,
            @Size(max = 60) String startWhen,
            @Size(max = 30) String callWhatsAppPhone,
            @Size(max = 60) String callMethod,
            List<@Size(max = 80) String> callAvailableTimes,
            @Size(max = 200) String callNotes,
            Map<String, Boolean> consents,
            Boolean consented,
            Boolean backgroundStepSaved,
            Boolean skillsStepSaved,
            Boolean experienceStepSaved,
            Boolean identityStepSaved,
            Boolean certificatesReviewed,
            Boolean availabilityStepSaved,
            Boolean interviewScheduled,
            Boolean applicationSubmitted,
            @Size(max = 40) String lastStep
    ) {}

    public record ProfileRequest(
            @NotBlank(message = "Full name is required") @Size(max = 150, message = "Full name must be 150 characters or fewer") String fullName,
            @Min(value = 18, message = "Age must be between 18 and 100") @Max(value = 100, message = "Age must be between 18 and 100") Integer age,
            @Min(value = 0, message = "Experience must be between 0 and 60 years") @Max(value = 60, message = "Experience must be between 0 and 60 years") Integer experienceYears,
            @Size(max = 255, message = "Qualifications must be 255 characters or fewer") String qualification,
            JobType jobType,
            @NotBlank(message = "Address is required") @Size(max = 300, message = "Address must be 300 characters or fewer") String address,
            Map<RateType, @NotNull(message = "must have an amount") @DecimalMin(value = "0.01", message = "must be greater than zero") @DecimalMax(value = "9999999.99", message = "is too large") @Digits(integer = 7, fraction = 2, message = "can have at most 2 decimal places") BigDecimal> rates,
            @Valid CaretakerOnboardingDto caretakerOnboarding) {

        public ProfileRequest(String fullName, Integer age, Integer experienceYears, String qualification,
                              JobType jobType, String address, Map<RateType, BigDecimal> rates) {
            this(fullName, age, experienceYears, qualification, jobType, address, rates, null);
        }
    }

    public record ProfileResponse(String fullName, Integer age, Integer experienceYears,
                                  String qualification, JobType jobType,
                                  Map<RateType, BigDecimal> rates, boolean available, String photoUrl, boolean listed, String address,
                                  CaretakerOnboardingDto caretakerOnboarding) {}

    public record AvailabilityRequest(@NotNull(message = "Say whether you are available") Boolean available) {}

    /** Public listing card. Deliberately omits email and phone. */
    public record CaregiverCard(Long id, String fullName, Integer age, Integer experienceYears,
                                String qualification, JobType jobType,
                                  Map<RateType, BigDecimal> rates, String photoUrl,
                                Double rating, long reviewCount, java.util.List<String> badges) {}
}
