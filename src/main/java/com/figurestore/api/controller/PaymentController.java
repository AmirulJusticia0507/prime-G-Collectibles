package com.figurestore.api.controller;

import com.figurestore.api.service.PaymentService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/{id}/snap")
    @PreAuthorize("hasRole('ADMIN') or @orderSecurity.canAccessPayment(#id, authentication)")
    public Map<String, String> initSnap(@PathVariable Long id) {
        return Map.of("snap_token", paymentService.initSnapToken(id));
    }
}
