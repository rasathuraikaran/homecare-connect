package com.homecare.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, Long> {

    List<PaymentReceipt> findByBookingIdIn(Collection<Long> bookingIds);

    long countByBookingId(Long bookingId);

    /** The files of every receipt for a person's requests, as seeker or as caregiver, so they can be removed with the account. */
    @Query("select r.fileName from PaymentReceipt r where r.bookingId in "
            + "(select b.id from Booking b where b.seekerId = :userId or b.caregiverId = :userId)")
    List<String> fileNamesForUser(@Param("userId") Long userId);
}
