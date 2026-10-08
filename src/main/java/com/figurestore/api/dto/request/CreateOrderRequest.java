package com.figurestore.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(
        @NotNull Long userId,
        @NotNull Long productId,
        @NotNull @Min(1) Integer quantity) {}
