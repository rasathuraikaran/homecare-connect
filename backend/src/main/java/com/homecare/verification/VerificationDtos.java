package com.homecare.verification;

import com.homecare.profile.JobType;
import com.homecare.profile.ProfileDtos.CaretakerOnboardingDto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class VerificationDtos {
    private VerificationDtos() {}

    /** One row of the caregiver's checklist. status is null until the file is uploaded. */
    public record DocumentView(DocumentType type, String label, String hint, boolean required,
                               DocumentStatus status, String rejectReason, Instant uploadedAt) {}

    public record VerificationView(VerificationStatus status, JobType job, List<DocumentView> documents) {}

    /** One row for the admin. id is null for a document that has not been uploaded. */
    public record AdminDocument(Long id, DocumentType type, String label, boolean required, DocumentStatus status,
                                int revision, String rejectReason, Instant uploadedAt) {}

    public record AdminCaregiver(Long userId, String name, String email, String address, JobType job, VerificationStatus status,
                                 List<AdminDocument> documents, CaretakerOnboardingDto caretakerOnboarding) {}

    public record Summary(int needsReview) {}

    /** One document a job can take, and whether the admin made it mandatory. */
    public record RequirementView(JobType job, DocumentType type, String label, String hint, boolean required) {}

    /** How many caregivers would disappear from, or appear in, the families' list if a document were made mandatory (or optional). */
    public record Impact(int willHide, int willShow) {}

    public record SetRequirementRequest(
            @NotNull(message = "Choose a job") JobType job,
            @NotNull(message = "Choose a document") DocumentType type,
            @NotNull(message = "Say whether it is mandatory") Boolean required) {}

    public record RequirementChange(List<RequirementView> requirements, int hidden, int shown) {}

    /** revision says which upload the admin looked at, so a file that was replaced since cannot be approved unseen. */
    public record ReviewRequest(
            @NotNull(message = "Choose approve or reject") DocumentStatus status,
            @Size(max = 300, message = "The reason must be 300 characters or fewer") String reason,
            @NotNull(message = "Say which version of the document you reviewed") Integer revision) {}
}
