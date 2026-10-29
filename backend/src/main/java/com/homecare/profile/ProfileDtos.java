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
            @NotBlank(message = "Full name is required") @Size(max = 150, message = "Full name must be 150 characters or fewer") String fullName,
            @Min(value = 18, message = "Age must be between 18 and 100") @Max(value = 100, message = "Age must be between 18 and 100") Integer age,
            @Min(value = 0, message = "Experience must be between 0 and 60 years") @Max(value = 60, message = "Experience must be between 0 and 60 years") Integer experienceYears,
            @Size(max = 255, message = "Qualifications must be 255 characters or fewer") String qualification,
            JobType jobType,
            @NotBlank(message = "Address is required") @Size(max = 300, message = "Address must be 300 characters or fewer") String address,
            Map<RateType, @NotNull(message = "must have an amount") @DecimalMin(value = "0.01", message = "must be greater than zero") @DecimalMax(value = "9999999.99", message = "is too large") @Digits(integer = 7, fraction = 2, message = "can have at most 2 decimal places") BigDecimal> rates) {}

    public record ProfileResponse(String fullName, Integer age, Integer experienceYears,
                                  String qualification, JobType jobType,
                                  Map<RateType, BigDecimal> rates, boolean available, String photoUrl, boolean listed, String address) {}

    public record AvailabilityRequest(@NotNull(message = "Say whether you are available") Boolean available) {}

    /** Public listing card. Deliberately omits email and phone. */
    public record CaregiverCard(Long id, String fullName, Integer age, Integer experienceYears,
                                String qualification, JobType jobType,
                                  Map<RateType, BigDecimal> rates, String photoUrl,
                                Double rating, long reviewCount, java.util.List<String> badges) {}
}
