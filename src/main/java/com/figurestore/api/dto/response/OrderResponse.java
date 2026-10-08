package com.figurestore.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderNumber,
        Long userId,
        BigDecimal totalAmount,
        String orderType,
        String fulfillmentStatus,
        LocalDateTime createdAt,
        List<Item> items,
        List<PaymentInfo> payments) {

    public record Item(Long productId, String productName, Integer quantity,
                       BigDecimal unitPrice, BigDecimal dpUnitPrice) {}

    public record PaymentInfo(Long id, String paymentNumber, String paymentType,
                              BigDecimal amount, String paymentStatus, LocalDateTime expiredAt) {}
}
