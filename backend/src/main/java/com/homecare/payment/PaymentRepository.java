package com.homecare.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(String orderId);

    List<Payment> findByBookingIdOrderByIdAsc(Long bookingId);

    List<Payment> findByBookingIdIn(Collection<Long> bookingIds);
}
