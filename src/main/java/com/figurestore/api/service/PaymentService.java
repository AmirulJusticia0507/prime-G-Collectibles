package com.figurestore.api.service;

import com.figurestore.api.dto.request.WebhookPayload;
import com.figurestore.api.model.Order;
import com.figurestore.api.model.OrderStatusHistory;
import com.figurestore.api.model.Payment;
import com.figurestore.api.repository.OrderRepository;
import com.figurestore.api.repository.OrderStatusHistoryRepository;
import com.figurestore.api.repository.PaymentRepository;
import com.figurestore.api.statemachine.OrderStateMachine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final OrderStatusHistoryRepository histories;
    private final OrderStateMachine stateMachine;
    private final RestTemplate restTemplate = new RestTemplate();
    private final String serverKey;
    private final boolean production;

    public PaymentService(PaymentRepository payments, OrderRepository orders,
                          OrderStatusHistoryRepository histories, OrderStateMachine stateMachine,
                          @Value("${midtrans.server-key}") String serverKey,
                          @Value("${midtrans.is-production}") boolean production) {
        this.payments = payments;
        this.orders = orders;
        this.histories = histories;
        this.stateMachine = stateMachine;
        this.serverKey = serverKey;
        this.production = production;
    }

    public String initSnapToken(Long paymentId) {
        Payment payment = payments.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment tidak ditemukan"));
        if (!"PENDING".equals(payment.getPaymentStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment sudah tidak PENDING");
        }
        String baseUrl = production ? "https://app.midtrans.com" : "https://app.sandbox.midtrans.com";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Basic " + Base64.getEncoder()
                .encodeToString((serverKey + ":").getBytes(StandardCharsets.UTF_8)));
        Map<String, Object> body = Map.of(
                "transaction_details", Map.of(
                        "order_id", payment.getPaymentNumber(),
                        "gross_amount", payment.getAmount().longValue()));
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/snap/v1/transactions", new HttpEntity<>(body, headers), Map.class);
            String snapToken = String.valueOf(response.getBody().get("token"));
            payment.setSnapToken(snapToken);
            payments.save(payment);
            return snapToken;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gagal membuat transaksi Midtrans: " + e.getMessage());
        }
    }

    @Transactional
    public void processCallback(WebhookPayload payload) {
        if (!isValidSignature(payload)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Signature tidak valid");
        }
        if (payments.existsByMidtransTransactionId(payload.transactionId())) {
            return;
        }

        Payment payment = payments.findByPaymentNumber(payload.orderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment tidak ditemukan"));

        payment.setMidtransTransactionId(payload.transactionId());
        String mapped = mapTransactionStatus(payload.transactionStatus(), payload.fraudStatus());
        payment.setPaymentStatus(mapped);

        if ("SUCCESS".equals(mapped)) {
            payment.setPaidAt(LocalDateTime.now());
            Order order = orders.findByIdForUpdate(payment.getOrder().getId()).orElseThrow();
            String target = switch (payment.getPaymentType()) {
                case "DOWN_PAYMENT" -> "DP_PAID";
                case "FINAL_PAYMENT" -> "FULL_PAID";
                default -> null;
            };
            if (target != null) {
                stateMachine.validate(order.getFulfillmentStatus(), target);
                String old = order.getFulfillmentStatus();
                order.setFulfillmentStatus(target);
                recordHistory(order, old, target, "Pembayaran " + payment.getPaymentType() + " berhasil");
            }
        } else if ("EXPIRED".equals(mapped) || "FAILED".equals(mapped)) {
            Order order = orders.findByIdForUpdate(payment.getOrder().getId()).orElseThrow();
            String target = switch (order.getFulfillmentStatus()) {
                case "WAITING_DP" -> "CANCELLED";
                case "WAITING_PELUNASAN" -> "CANCELLED_DP_HANGUS";
                default -> null;
            };
            if (target != null) {
                stateMachine.validate(order.getFulfillmentStatus(), target);
                String old = order.getFulfillmentStatus();
                order.setFulfillmentStatus(target);
                recordHistory(order, old, target, "Pembayaran " + mapped.toLowerCase());
            }
        }
        payments.save(payment);
    }

    public boolean isValidSignature(WebhookPayload payload) {
        String raw = payload.orderId() + payload.statusCode() + payload.grossAmount() + serverKey;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            String computed = HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
            return computed.equalsIgnoreCase(payload.signatureKey());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String mapTransactionStatus(String transactionStatus, String fraudStatus) {
        return switch (transactionStatus) {
            case "capture" -> ("accept".equals(fraudStatus) || fraudStatus == null) ? "SUCCESS" : "FAILED";
            case "settlement" -> "SUCCESS";
            case "pending" -> "PENDING";
            case "expire" -> "EXPIRED";
            case "cancel", "deny", "failure" -> "FAILED";
            default -> "PENDING";
        };
    }

    private void recordHistory(Order order, String oldStatus, String newStatus, String note) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setNote(note);
        histories.save(history);
    }
}
