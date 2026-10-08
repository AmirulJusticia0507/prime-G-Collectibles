package com.figurestore.api;

import com.figurestore.api.dto.request.WebhookPayload;
import com.figurestore.api.model.Order;
import com.figurestore.api.model.Payment;
import com.figurestore.api.repository.OrderRepository;
import com.figurestore.api.repository.OrderStatusHistoryRepository;
import com.figurestore.api.repository.PaymentRepository;
import com.figurestore.api.service.PaymentService;
import com.figurestore.api.statemachine.OrderStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final String SERVER_KEY = "test-server-key";

    @Mock PaymentRepository payments;
    @Mock OrderRepository orders;
    @Mock OrderStatusHistoryRepository histories;

    PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(payments, orders, histories, new OrderStateMachine(), SERVER_KEY, false);
    }

    private WebhookPayload payload(String txStatus, String fraud, String txId) throws Exception {
        String orderId = "PAY-DP-ABC123";
        String statusCode = "200";
        String grossAmount = "300000.00";
        String raw = orderId + statusCode + grossAmount + SERVER_KEY;
        String signature = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-512").digest(raw.getBytes(StandardCharsets.UTF_8)));
        return new WebhookPayload(txId, orderId, statusCode, grossAmount, txStatus, fraud, signature);
    }

    private Payment pendingDpPayment() {
        Payment p = new Payment();
        p.setPaymentNumber("PAY-DP-ABC123");
        p.setPaymentType("DOWN_PAYMENT");
        p.setAmount(new BigDecimal("300000.00"));
        p.setPaymentStatus("PENDING");
        Order o = new Order();
        o.setFulfillmentStatus("WAITING_DP");
        p.setOrder(o);
        return p;
    }

    @Test
    void settlementMovesOrderToDpPaid() throws Exception {
        Payment p = pendingDpPayment();
        when(payments.existsByMidtransTransactionId("tx-1")).thenReturn(false);
        when(payments.findByPaymentNumber("PAY-DP-ABC123")).thenReturn(Optional.of(p));
        Order order = p.getOrder();
        when(orders.findByIdForUpdate(any())).thenReturn(Optional.of(order));

        service.processCallback(payload("settlement", null, "tx-1"));

        assertThat(p.getPaymentStatus()).isEqualTo("SUCCESS");
        assertThat(p.getPaidAt()).isNotNull();
        assertThat(order.getFulfillmentStatus()).isEqualTo("DP_PAID");
        verify(histories).save(any());
    }

    @Test
    void expiredCancelsOrder() throws Exception {
        Payment p = pendingDpPayment();
        when(payments.existsByMidtransTransactionId("tx-2")).thenReturn(false);
        when(payments.findByPaymentNumber("PAY-DP-ABC123")).thenReturn(Optional.of(p));
        Order order = p.getOrder();
        when(orders.findByIdForUpdate(any())).thenReturn(Optional.of(order));

        service.processCallback(payload("expire", null, "tx-2"));

        assertThat(p.getPaymentStatus()).isEqualTo("EXPIRED");
        assertThat(order.getFulfillmentStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void duplicateTransactionIsSkipped() throws Exception {
        when(payments.existsByMidtransTransactionId("tx-1")).thenReturn(true);

        service.processCallback(payload("settlement", null, "tx-1"));

        verify(payments, never()).findByPaymentNumber(any());
    }

    @Test
    void invalidSignatureRejected() {
        WebhookPayload bad = new WebhookPayload("tx-9", "PAY-DP-ABC123", "200", "300000.00", "settlement", null, "badsig");
        assertThatThrownBy(() -> service.processCallback(bad))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Signature");
    }

    @Test
    void captureWithFraudChallengeFails() throws Exception {
        assertThat(PaymentService.mapTransactionStatus("capture", "challenge")).isEqualTo("FAILED");
        assertThat(PaymentService.mapTransactionStatus("capture", "accept")).isEqualTo("SUCCESS");
        assertThat(PaymentService.mapTransactionStatus("pending", null)).isEqualTo("PENDING");
        assertThat(PaymentService.mapTransactionStatus("deny", null)).isEqualTo("FAILED");
    }
}
