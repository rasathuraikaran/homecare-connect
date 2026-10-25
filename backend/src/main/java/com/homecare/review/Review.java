package com.homecare.review;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "caregiver_id", nullable = false)
    private Long caregiverId;

    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    @Column(nullable = false)
    private int rating;

    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Review() {}

    public Review(Long caregiverId, Long reviewerId) {
        this.caregiverId = caregiverId;
        this.reviewerId = reviewerId;
    }

    public void update(int rating, String comment) {
        this.rating = rating;
        this.comment = comment;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getReviewerId() { return reviewerId; }
    public int getRating() { return rating; }
    public String getComment() { return comment; }
    public Instant getUpdatedAt() { return updatedAt; }
}
