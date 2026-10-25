package com.homecare.profile;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CaregiverProfileRepository extends JpaRepository<CaregiverProfile, Long> {

    /** Rows of [CaregiverProfile, fullName], most experienced first. */
    @Query("select p, u.fullName from CaregiverProfile p, User u "
            + "where u.id = p.userId and p.jobType = :job and p.available = true "
            + "order by p.experienceYears desc nulls last, p.updatedAt desc")
    List<Object[]> findCards(@Param("job") JobType job, Pageable pageable);

    /** Rows of [JobType, count]. */
    @Query("select p.jobType, count(p) from CaregiverProfile p where p.jobType is not null and p.available = true group by p.jobType")
    List<Object[]> countByJobType();
}
