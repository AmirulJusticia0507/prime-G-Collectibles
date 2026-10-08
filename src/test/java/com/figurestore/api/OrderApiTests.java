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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.admin.username=admin",
        "app.admin.password=test-password",
        "app.payment-expiry.interval-ms=3600000"
})
@AutoConfigureMockMvc
@Transactional
class OrderApiTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired PaymentRepository payments;
    @Autowired OrderStatusHistoryRepository histories;
    @Autowired OrderService orderService;

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setFullName("Test Customer");
        user.setEmail("customer-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("not-used-in-phase-2");
        user.setRole("CUSTOMER");
        users.save(user);

        product = new Product();
        product.setName("PO Figure");
        product.setFullPrice(new BigDecimal("2000000"));
        product.setDpPrice(new BigDecimal("500000"));
        product.setStockSlot(5);
        product.setStatus("PO_OPEN");
        products.save(product);
    }

    @Test
    void createAndCancelPreOrderUpdatesPaymentStockAndHistory() throws Exception {
        String request = """
                {"userId":%d,"productId":%d,"quantity":2}
                """.formatted(user.getId(), product.getId());

        mvc.perform(post("/api/orders/pre-order")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").value(
                        org.hamcrest.Matchers.matchesPattern("PO-\\d{8}-[A-F0-9]{8}")))
                .andExpect(jsonPath("$.fulfillmentStatus").value("WAITING_DP"))
                .andExpect(jsonPath("$.payments[0].amount").value(1000000));

        com.figurestore.api.model.Order order = orders.findAll().stream()
                .filter(o -> o.getUser().getId().equals(user.getId()))
                .findFirst().orElseThrow();
        assertThat(products.findById(product.getId()).orElseThrow().getStockSlot()).isEqualTo(3);
        assertThat(payments.findByOrderId(order.getId())).singleElement()
                .satisfies(p -> {
                    assertThat(p.getPaymentStatus()).isEqualTo("PENDING");
                    assertThat(p.getExpiredAt()).isAfter(LocalDateTime.now().plusHours(23));
                });

        mvc.perform(post("/api/orders/{id}/cancel", order.getId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Berubah pikiran\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fulfillmentStatus").value("CANCELLED"));

        assertThat(products.findById(product.getId()).orElseThrow().getStockSlot()).isEqualTo(5);
        assertThat(histories.findByOrderIdOrderByChangedAtAsc(order.getId())).hasSize(2);
    }

    @Test
    void expiredDpCancelsOrderAndRestoresStock() {
        OrderResponse created = orderService.createPreOrder(
                new CreateOrderRequest(user.getId(), product.getId(), 1));
        Payment payment = payments.findByOrderId(created.id()).get(0);
        payment.setExpiredAt(LocalDateTime.now().minusMinutes(1));
        payments.saveAndFlush(payment);

        orderService.expirePayment(payment.getId(), LocalDateTime.now());

        assertThat(orders.findById(created.id()).orElseThrow().getFulfillmentStatus())
                .isEqualTo("CANCELLED");
        assertThat(payments.findById(payment.getId()).orElseThrow().getPaymentStatus())
                .isEqualTo("EXPIRED");
        assertThat(products.findById(product.getId()).orElseThrow().getStockSlot()).isEqualTo(5);
    }

    @Test
    void rejectsOrderWhenSlotsAreInsufficient() throws Exception {
        String request = """
                {"userId":%d,"productId":%d,"quantity":6}
                """.formatted(user.getId(), product.getId());

        mvc.perform(post("/api/orders/pre-order")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());

        assertThat(products.findById(product.getId()).orElseThrow().getStockSlot()).isEqualTo(5);
    }
}
