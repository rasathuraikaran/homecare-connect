package com.homecare.review;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByCaregiverIdAndReviewerId(Long caregiverId, Long reviewerId);

    /** Rows of [Review, reviewer full name], newest first. */
    @Query("select r, u.fullName from Review r, User u where u.id = r.reviewerId and r.caregiverId = :id order by r.updatedAt desc")
    List<Object[]> recent(@Param("id") Long caregiverId, Pageable pageable);

    /** One row of [average rating (null if none), count]. */
    @Query("select avg(r.rating), count(r) from Review r where r.caregiverId = :id")
    List<Object[]> summary(@Param("id") Long caregiverId);

    /** Rows of [caregiverId, average, count] for the caregivers that have reviews. */
    @Query("select r.caregiverId, avg(r.rating), count(r) from Review r where r.caregiverId in :ids group by r.caregiverId")
    List<Object[]> summaries(@Param("ids") Collection<Long> caregiverIds);
}
