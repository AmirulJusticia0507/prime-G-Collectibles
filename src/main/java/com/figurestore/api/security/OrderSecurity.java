package com.figurestore.api.security;

import com.figurestore.api.repository.OrderRepository;
import com.figurestore.api.repository.PaymentRepository;
import com.figurestore.api.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("orderSecurity")
public class OrderSecurity {
    private final UserRepository users;
    private final OrderRepository orders;
    private final PaymentRepository payments;

    public OrderSecurity(UserRepository users, OrderRepository orders, PaymentRepository payments) {
        this.users = users;
        this.orders = orders;
        this.payments = payments;
    }

    public boolean isUser(Long userId, Authentication authentication) {
        return authentication != null && users.existsByIdAndEmail(userId, authentication.getName());
    }

    public boolean canAccessOrder(Long orderId, Authentication authentication) {
        return authentication != null && orders.existsByIdAndUserEmail(orderId, authentication.getName());
    }

    public boolean canAccessPayment(Long paymentId, Authentication authentication) {
        return authentication != null && payments.existsByIdAndOrderUserEmail(paymentId, authentication.getName());
    }
}
