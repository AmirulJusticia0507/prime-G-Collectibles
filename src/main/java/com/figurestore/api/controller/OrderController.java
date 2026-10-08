package com.figurestore.api.controller;

import com.figurestore.api.dto.request.CancelOrderRequest;
import com.figurestore.api.dto.request.CreateOrderRequest;
import com.figurestore.api.dto.response.OrderResponse;
import com.figurestore.api.dto.response.PelunasanNotificationResponse;
import com.figurestore.api.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/pre-order")
    @PreAuthorize("hasRole('ADMIN') or @orderSecurity.isUser(#request.userId(), authentication)")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createPreOrder(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.createPreOrder(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @orderSecurity.canAccessOrder(#id, authentication)")
    public OrderResponse get(@PathVariable Long id) {
        return orderService.findById(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN') or @orderSecurity.canAccessOrder(#id, authentication)")
    public OrderResponse cancel(@PathVariable Long id,
                                @RequestBody(required = false) CancelOrderRequest request) {
        return orderService.cancel(id, request == null ? null : request.note());
    }

    @PostMapping("/{id}/pelunasan-notification")
    @PreAuthorize("hasRole('ADMIN')")
    public PelunasanNotificationResponse triggerPelunasan(@PathVariable Long id) {
        return orderService.triggerPelunasan(id);
    }
}
