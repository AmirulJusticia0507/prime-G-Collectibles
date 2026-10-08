package com.figurestore.api;

import com.figurestore.api.dto.request.CreateOrderRequest;
import com.figurestore.api.dto.response.OrderResponse;
import com.figurestore.api.model.Payment;
import com.figurestore.api.model.Product;
import com.figurestore.api.model.User;
import com.figurestore.api.repository.*;
import com.figurestore.api.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.admin.username=admin",
        "app.admin.password=test-password",
        "app.notification.email-enabled=true",
        "app.payment-expiry.interval-ms=3600000"
})
@AutoConfigureMockMvc
@Transactional
class PelunasanNotificationTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired PaymentRepository payments;
    @Autowired OrderStatusHistoryRepository histories;
    @Autowired OrderService orderService;
    @MockitoBean JavaMailSender mailSender;

    private OrderResponse order;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setFullName("Email Customer");
        user.setEmail("email-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("not-used");
        user.setRole("CUSTOMER");
        users.save(user);

        Product product = new Product();
        product.setName("Ready for Final Payment");
        product.setFullPrice(new BigDecimal("2000000"));
        product.setDpPrice(new BigDecimal("500000"));
        product.setStockSlot(2);
        product.setStatus("PO_OPEN");
        products.save(product);

        order = orderService.createPreOrder(new CreateOrderRequest(user.getId(), product.getId(), 1));
        com.figurestore.api.model.Order entity = orders.findById(order.id()).orElseThrow();
        entity.setFulfillmentStatus("DP_PAID");
        Payment dp = payments.findByOrderId(order.id()).get(0);
        dp.setPaymentStatus("SUCCESS");
        dp.setPaidAt(LocalDateTime.now());
        orders.save(entity);
        payments.save(dp);
    }

    @Test
    void adminTriggersFinalPaymentAndEmail() throws Exception {
        mvc.perform(post("/api/orders/{id}/pelunasan-notification", order.id())
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailSent").value(true))
                .andExpect(jsonPath("$.order.fulfillmentStatus").value("WAITING_PELUNASAN"))
                .andExpect(jsonPath("$.order.payments[1].paymentType").value("FINAL_PAYMENT"))
                .andExpect(jsonPath("$.order.payments[1].amount").value(1500000));

        Payment finalPayment = payments.findByOrderId(order.id()).stream()
                .filter(p -> "FINAL_PAYMENT".equals(p.getPaymentType()))
                .findFirst().orElseThrow();
        assertThat(finalPayment.getExpiredAt()).isAfter(LocalDateTime.now().plusDays(6));
        assertThat(histories.findByOrderIdOrderByChangedAtAsc(order.id()).get(1).getNewStatus())
                .isEqualTo("WAITING_PELUNASAN");
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void rejectsNotificationBeforeDpIsPaid() throws Exception {
        com.figurestore.api.model.Order entity = orders.findById(order.id()).orElseThrow();
        entity.setFulfillmentStatus("WAITING_DP");

        mvc.perform(post("/api/orders/{id}/pelunasan-notification", order.id())
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isBadRequest());
    }
}
