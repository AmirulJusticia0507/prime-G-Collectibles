package com.figurestore.api.repository;

import com.figurestore.api.model.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    boolean existsByIdAndOrderUserEmail(Long id, String email);
    List<Payment> findByOrderId(Long orderId);
    java.util.Optional<Payment> findByPaymentNumber(String paymentNumber);
    boolean existsByMidtransTransactionId(String midtransTransactionId);
    List<Payment> findByPaymentStatusAndExpiredAtBefore(String status, LocalDateTime time);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(Long id);
}
