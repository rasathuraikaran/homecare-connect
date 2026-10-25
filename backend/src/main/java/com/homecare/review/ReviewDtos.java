package com.homecare.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ReviewDtos {
    private ReviewDtos() {}

    public record ReviewRequest(
            @NotNull(message = "Choose a rating from 1 to 5")
            @Min(value = 1, message = "Rating must be between 1 and 5")
            @Max(value = 5, message = "Rating must be between 1 and 5") Integer rating,
            @Size(max = 1000, message = "Comment must be 1000 characters or fewer") String comment) {}

    /** reviewerName is shortened (e.g. "Priya S.") to protect the reviewer's privacy. */
    public record ReviewItem(Long id, String reviewerName, int rating, String comment, Instant createdAt) {}

    public record ReviewsResponse(Double average, long count, List<ReviewItem> reviews, ReviewItem myReview) {}
}
