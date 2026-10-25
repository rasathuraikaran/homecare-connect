package com.homecare.profile;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ProfileDtos {
    private ProfileDtos() {}

    public record ProfileRequest(
            @NotBlank @Size(max = 150) String fullName,
            @Min(18) @Max(100) Integer age,
            @Min(0) @Max(60) Integer experienceYears,
            @Size(max = 255) String qualification,
            JobType jobType) {}

    public record ProfileResponse(String fullName, Integer age, Integer experienceYears,
                                  String qualification, JobType jobType, String photoUrl) {}

    /** Public listing card. Deliberately omits email and phone. */
    public record CaregiverCard(Long id, String fullName, Integer age, Integer experienceYears,
                                String qualification, JobType jobType, String photoUrl) {}
}
