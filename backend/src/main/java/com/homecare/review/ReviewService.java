package com.homecare.review;

import com.homecare.profile.CaregiverProfileRepository;
import com.homecare.review.ReviewDtos.*;
import com.homecare.user.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class ReviewService {

    private static final int RECENT = 20;

    private final ReviewRepository reviews;
    private final CaregiverProfileRepository profiles;

    public ReviewService(ReviewRepository reviews, CaregiverProfileRepository profiles) {
        this.reviews = reviews;
        this.profiles = profiles;
    }

    /** What a caregiver sees about themselves, or a seeker sees about a caregiver (viewer = seeker). */
    @Transactional(readOnly = true)
    public ReviewsResponse view(Long caregiverId, User viewerSeeker) {
        requireCaregiver(caregiverId);
        ReviewItem mine = viewerSeeker == null ? null
                : reviews.findByCaregiverIdAndReviewerId(caregiverId, viewerSeeker.getId())
                        .map(r -> toItem(r, viewerSeeker.getFullName())).orElse(null);
        return build(caregiverId, mine);
    }

    @Transactional
    public ReviewsResponse save(Long caregiverId, User seeker, ReviewRequest req) {
        requireCaregiver(caregiverId);
        Review review = reviews.findByCaregiverIdAndReviewerId(caregiverId, seeker.getId())
                .orElseGet(() -> new Review(caregiverId, seeker.getId()));
        String comment = req.comment() == null || req.comment().isBlank() ? null : req.comment().trim();
        review.update(req.rating(), comment);
        Review saved = reviews.saveAndFlush(review);
        return build(caregiverId, toItem(saved, seeker.getFullName()));
    }

    private ReviewsResponse build(Long caregiverId, ReviewItem mine) {
        Object[] summary = reviews.summary(caregiverId).get(0);
        List<ReviewItem> items = reviews.recent(caregiverId, PageRequest.of(0, RECENT)).stream()
                .map(row -> toItem((Review) row[0], (String) row[1]))
                .toList();
        return new ReviewsResponse(round((Double) summary[0]), (Long) summary[1], items, mine);
    }

    private void requireCaregiver(Long caregiverId) {
        if (!profiles.existsById(caregiverId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Caregiver not found");
        }
    }

    static Double round(Double average) {
        return average == null ? null : BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    static ReviewItem toItem(Review r, String reviewerFullName) {
        return new ReviewItem(r.getId(), shortName(reviewerFullName), r.getRating(), r.getComment(), r.getUpdatedAt());
    }

    /** "Priya Sharma" becomes "Priya S."; a single name is kept as is. */
    static String shortName(String fullName) {
        String[] parts = fullName == null ? new String[0] : fullName.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return "A family member";
        return parts.length == 1 ? parts[0] : parts[0] + " " + Character.toUpperCase(parts[parts.length - 1].charAt(0)) + ".";
    }
}
