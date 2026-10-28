package com.homecare.booking;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    long countBySeekerIdAndStatus(Long seekerId, BookingStatus status);

    long countBySeekerId(Long seekerId);

    long countByCaregiverId(Long caregiverId);

    long countBySeekerIdAndStatusIn(Long seekerId, Collection<BookingStatus> statuses);

    long countByCaregiverIdAndStatusIn(Long caregiverId, Collection<BookingStatus> statuses);

    boolean existsBySeekerIdAndCaregiverIdAndStatusIn(Long seekerId, Long caregiverId, Collection<BookingStatus> statuses);

    /** Rows of [Booking, caregiver full name, caregiver job type], newest first. */
    @Query("select b, c.fullName, p.jobType from Booking b, User c, CaregiverProfile p "
            + "where b.seekerId = :seekerId and c.id = b.caregiverId and p.userId = b.caregiverId order by b.createdAt desc")
    List<Object[]> forSeeker(@Param("seekerId") Long seekerId, Pageable pageable);

    /** Rows of [Booking, seeker User, caregiver User, caregiver job type], newest first. */
    @Query("select b, s, c, p.jobType from Booking b, User s, User c, CaregiverProfile p "
            + "where s.id = b.seekerId and c.id = b.caregiverId and p.userId = b.caregiverId order by b.createdAt desc")
    List<Object[]> forAdmin(Pageable pageable);

    @Query("select b, s, c, p.jobType from Booking b, User s, User c, CaregiverProfile p "
            + "where s.id = b.seekerId and c.id = b.caregiverId and p.userId = b.caregiverId and b.status = :status "
            + "order by b.createdAt desc")
    List<Object[]> forAdminByStatus(@Param("status") BookingStatus status, Pageable pageable);

    @Query("select b, s, c, p.jobType from Booking b, User s, User c, CaregiverProfile p "
            + "where b.id = :id and s.id = b.seekerId and c.id = b.caregiverId and p.userId = b.caregiverId")
    List<Object[]> forAdminById(@Param("id") Long id);
}
