package com.homecare.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("select n.id from Notification n where n.status = com.homecare.notification.NotificationStatus.PENDING "
            + "and n.nextAttemptAt <= :now order by n.id")
    List<Long> findDueIds(@Param("now") Instant now, Pageable pageable);

    List<Notification> findByBookingIdInOrderByIdAsc(Collection<Long> bookingIds);
}
