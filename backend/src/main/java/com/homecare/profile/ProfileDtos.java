package com.homecare.profile;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

public final class ProfileDtos {
    private ProfileDtos() {}

    public record ProfileRequest(
            @NotBlank @Size(max = 150) String fullName,
            @Min(18) @Max(100) Integer age,
            @Min(0) @Max(60) Integer experienceYears,
            @Size(max = 255) String qualification,
            JobType jobType,
            Map<RateType, @NotNull @DecimalMin("0.01") @DecimalMax("9999999.99") @Digits(integer = 7, fraction = 2) BigDecimal> rates) {}

    public record ProfileResponse(String fullName, Integer age, Integer experienceYears,
                                  String qualification, JobType jobType,
                                  Map<RateType, BigDecimal> rates, String photoUrl) {}

    /** Public listing card. Deliberately omits email and phone. */
    public record CaregiverCard(Long id, String fullName, Integer age, Integer experienceYears,
                                String qualification, JobType jobType,
                                  Map<RateType, BigDecimal> rates, String photoUrl) {}
}
