package com.homecare.review;

import com.homecare.review.ReviewDtos.*;
import com.homecare.user.User;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/caregivers")
public class ReviewController {

    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    /** A caregiver reading their own reviews (caregiver role only, see SecurityConfig). */
    @GetMapping("/me/reviews")
    public ReviewsResponse mine(@AuthenticationPrincipal User caregiver) {
        return service.view(caregiver.getId(), null);
    }

    /** A care seeker reading a caregiver's reviews, including their own. */
    @GetMapping("/{caregiverId}/reviews")
    public ReviewsResponse list(@PathVariable Long caregiverId, @AuthenticationPrincipal User seeker) {
        return service.view(caregiverId, seeker);
    }

    /** Create or replace the signed-in seeker's review of this caregiver. */
    @PutMapping("/{caregiverId}/reviews")
    public ReviewsResponse save(@PathVariable Long caregiverId, @AuthenticationPrincipal User seeker,
                                @Valid @RequestBody ReviewRequest req) {
        return service.save(caregiverId, seeker, req);
    }
}
