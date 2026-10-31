package com.homecare.admin;

import com.homecare.profile.JobType;
import com.homecare.profile.ProfileDtos.CaretakerOnboardingDto;
import com.homecare.profile.ProfileDtos.NursingOnboardingDto;
import com.homecare.profile.RateType;
import com.homecare.user.Role;
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
import java.time.Instant;
import java.util.Map;

public final class AdminUserDtos {
    private AdminUserDtos() {}

    /** One account in the admin's list. job, listed and address are only set for caregivers; listed says whether families can see them. */
    public record AdminUserRow(Long id, String name, String email, Role role, boolean admin, Instant createdAt,
                               JobType job, Boolean listed, String address) {}

    /** What deleting the account would remove, so the admin can see it before confirming. */
    public record DeletionPreview(Long id, String name, String email, Role role, boolean admin,
                                  long requests, long openRequests, long reviewsWritten, long reviewsReceived,
                                  long documents, boolean hasPhoto) {}

    /** Full account + caregiver profile + role-specific onboarding data for admin viewing and editing. */
    public record AdminUserProfile(
            Long id,
            String fullName,
            String email,
            String phone,
            Role role,
            Integer age,
            Integer experienceYears,
            String qualification,
            JobType jobType,
            Map<RateType, BigDecimal> rates,
            boolean available,
            String photoUrl,
            boolean listed,
            String address,
            CaretakerOnboardingDto caretakerOnboarding,
            NursingOnboardingDto nursingOnboarding
    ) {}

    /** Admin update payload to alter account details, caregiver profile, listed status, rates, and onboarding data. */
    public record AdminUserProfileUpdateRequest(
            @NotBlank(message = "Full name is required") @Size(max = 150) String fullName,
            @Size(max = 40) String phone,
            @Min(18) @Max(100) Integer age,
            @Min(0) @Max(60) Integer experienceYears,
            @Size(max = 255) String qualification,
            JobType jobType,
            @Size(max = 300) String address,
            Boolean available,
            Boolean listed,
            Map<RateType, @NotNull @DecimalMin("0.01") @DecimalMax("9999999.99") @Digits(integer = 7, fraction = 2) BigDecimal> rates,
            @Valid CaretakerOnboardingDto caretakerOnboarding,
            @Valid NursingOnboardingDto nursingOnboarding
    ) {}
}
