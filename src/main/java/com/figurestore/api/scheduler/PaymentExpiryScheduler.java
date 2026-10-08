package com.figurestore.api.scheduler;

import com.figurestore.api.repository.PaymentRepository;
import com.figurestore.api.service.OrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
public class PaymentExpiryScheduler {
    private final PaymentRepository payments;
    private final OrderService orderService;

    public PaymentExpiryScheduler(PaymentRepository payments, OrderService orderService) {
        this.payments = payments;
        this.orderService = orderService;
    }

    @Scheduled(fixedDelayString = "${app.payment-expiry.interval-ms:60000}")
    public void expirePayments() {
        LocalDateTime now = LocalDateTime.now();
        payments.findByPaymentStatusAndExpiredAtBefore("PENDING", now)
                .forEach(payment -> orderService.expirePayment(payment.getId(), now));
    }
}
