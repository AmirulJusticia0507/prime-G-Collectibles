package com.figurestore.api.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WebhookPayload(
        @JsonProperty("transaction_id") String transactionId,
        @JsonProperty("order_id") String orderId,
        @JsonProperty("status_code") String statusCode,
        @JsonProperty("gross_amount") String grossAmount,
        @JsonProperty("transaction_status") String transactionStatus,
        @JsonProperty("fraud_status") String fraudStatus,
        @JsonProperty("signature_key") String signatureKey
) {}
