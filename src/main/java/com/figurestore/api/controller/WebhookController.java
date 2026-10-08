package com.figurestore.api.controller;

import com.figurestore.api.dto.request.WebhookPayload;
import com.figurestore.api.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final PaymentService paymentService;

    public WebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/midtrans")
    public ResponseEntity<Void> handleMidtrans(@RequestBody WebhookPayload payload) {
        paymentService.processCallback(payload);
        return ResponseEntity.ok().build();
    }
}
