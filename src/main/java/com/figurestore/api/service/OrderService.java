package com.figurestore.api.service;

import com.figurestore.api.dto.request.CreateOrderRequest;
import com.figurestore.api.dto.response.OrderResponse;
import com.figurestore.api.dto.response.PelunasanNotificationResponse;
import com.figurestore.api.model.*;
import com.figurestore.api.repository.*;
import com.figurestore.api.statemachine.OrderStateMachine;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {
    private final UserRepository users;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final PaymentRepository payments;
    private final OrderStatusHistoryRepository histories;
    private final OrderStateMachine stateMachine;
    private final EmailNotificationService emailNotificationService;

    public OrderService(UserRepository users, ProductRepository products, OrderRepository orders,
                        OrderItemRepository items, PaymentRepository payments,
                        OrderStatusHistoryRepository histories, OrderStateMachine stateMachine,
                        EmailNotificationService emailNotificationService) {
        this.users = users;
        this.products = products;
        this.orders = orders;
        this.items = items;
        this.payments = payments;
        this.histories = histories;
        this.stateMachine = stateMachine;
        this.emailNotificationService = emailNotificationService;
    }

    @Transactional
    public OrderResponse createPreOrder(CreateOrderRequest request) {
        User user = users.findById(request.userId())
                .orElseThrow(() -> badRequest("User tidak ditemukan"));
        Product product = products.findByIdForUpdate(request.productId())
                .orElseThrow(() -> badRequest("Product tidak ditemukan"));

        if (!"PO_OPEN".equals(product.getStatus())) {
            throw badRequest("Pre-Order untuk produk ini sudah ditutup");
        }
        if (product.getStockSlot() < request.quantity()) {
            throw badRequest("Slot Pre-Order tidak mencukupi");
        }

        product.setStockSlot(product.getStockSlot() - request.quantity());
        BigDecimal quantity = BigDecimal.valueOf(request.quantity());

        Order order = new Order();
        order.setOrderNumber("PO-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + shortUuid());
        order.setUser(user);
        order.setTotalAmount(product.getFullPrice().multiply(quantity));
        order.setOrderType("PRE_ORDER");
        order.setFulfillmentStatus("WAITING_DP");
        orders.save(order);

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(request.quantity());
        item.setUnitPrice(product.getFullPrice());
        item.setDpUnitPrice(product.getDpPrice());
        items.save(item);

        Payment payment = new Payment();
        payment.setPaymentNumber("PAY-DP-" + shortUuid());
        payment.setOrder(order);
        payment.setPaymentType("DOWN_PAYMENT");
        payment.setAmount(product.getDpPrice().multiply(quantity));
        payment.setPaymentStatus("PENDING");
        payment.setExpiredAt(LocalDateTime.now().plusHours(24));
        payments.save(payment);

        recordHistory(order, null, "WAITING_DP", "Pre-order dibuat");
        return response(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(Long id) {
        return response(orders.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order tidak ditemukan")));
    }

    @Transactional
    public OrderResponse cancel(Long id, String note) {
        Order order = orders.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order tidak ditemukan"));
        String target = switch (order.getFulfillmentStatus()) {
            case "WAITING_DP" -> "CANCELLED";
            case "WAITING_PELUNASAN" -> "CANCELLED_DP_HANGUS";
            default -> throw badRequest("Order dengan status ini tidak dapat dibatalkan");
        };
        cancelAndRestore(order, target, note == null || note.isBlank() ? "Dibatalkan pengguna" : note);
        payments.findByOrderId(id).stream()
                .filter(p -> "PENDING".equals(p.getPaymentStatus()))
                .forEach(p -> p.setPaymentStatus("EXPIRED"));
        return response(order);
    }

    @Transactional
    public PelunasanNotificationResponse triggerPelunasan(Long id) {
        Order order = orders.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order tidak ditemukan"));
        if (!"DP_PAID".equals(order.getFulfillmentStatus())) {
            throw badRequest("Order harus berstatus DP_PAID");
        }
        stateMachine.validate(order.getFulfillmentStatus(), "WAITING_PELUNASAN");

        BigDecimal paidDp = payments.findByOrderId(id).stream()
                .filter(p -> "DOWN_PAYMENT".equals(p.getPaymentType()))
                .filter(p -> "SUCCESS".equals(p.getPaymentStatus()))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (paidDp.signum() == 0) {
            throw badRequest("DP belum berhasil dibayar");
        }
        BigDecimal remaining = order.getTotalAmount().subtract(paidDp);
        if (remaining.signum() <= 0) {
            throw badRequest("Order tidak memiliki sisa pelunasan");
        }

        Payment finalPayment = new Payment();
        finalPayment.setPaymentNumber("PAY-FINAL-" + shortUuid());
        finalPayment.setOrder(order);
        finalPayment.setPaymentType("FINAL_PAYMENT");
        finalPayment.setAmount(remaining);
        finalPayment.setPaymentStatus("PENDING");
        finalPayment.setExpiredAt(LocalDateTime.now().plusDays(7));
        payments.save(finalPayment);

        String old = order.getFulfillmentStatus();
        order.setFulfillmentStatus("WAITING_PELUNASAN");
        recordHistory(order, old, "WAITING_PELUNASAN", "Notifikasi pelunasan dikirim");
        boolean emailSent = emailNotificationService.sendPelunasan(order, finalPayment);
        return new PelunasanNotificationResponse(response(order), emailSent);
    }

    @Transactional
    public void expirePayment(Long paymentId, LocalDateTime now) {
        Payment payment = payments.findByIdForUpdate(paymentId).orElse(null);
        if (payment == null || !"PENDING".equals(payment.getPaymentStatus())
                || payment.getExpiredAt() == null || payment.getExpiredAt().isAfter(now)) {
            return;
        }

        Order order = orders.findByIdForUpdate(payment.getOrder().getId()).orElseThrow();
        String target = switch (order.getFulfillmentStatus()) {
            case "WAITING_DP" -> "CANCELLED";
            case "WAITING_PELUNASAN" -> "CANCELLED_DP_HANGUS";
            default -> null;
        };
        payment.setPaymentStatus("EXPIRED");
        if (target != null) {
            cancelAndRestore(order, target, "Batas waktu pembayaran habis");
        }
    }

    private void cancelAndRestore(Order order, String target, String note) {
        stateMachine.validate(order.getFulfillmentStatus(), target);
        for (OrderItem item : items.findByOrderId(order.getId())) {
            Product product = products.findByIdForUpdate(item.getProduct().getId()).orElseThrow();
            product.setStockSlot(product.getStockSlot() + item.getQuantity());
        }
        String old = order.getFulfillmentStatus();
        order.setFulfillmentStatus(target);
        recordHistory(order, old, target, note);
    }

    private void recordHistory(Order order, String oldStatus, String newStatus, String note) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setNote(note);
        histories.save(history);
    }

    private OrderResponse response(Order order) {
        List<OrderResponse.Item> orderItems = items.findByOrderId(order.getId()).stream()
                .map(i -> new OrderResponse.Item(i.getProduct().getId(), i.getProduct().getName(),
                        i.getQuantity(), i.getUnitPrice(), i.getDpUnitPrice()))
                .toList();
        List<OrderResponse.PaymentInfo> orderPayments = payments.findByOrderId(order.getId()).stream()
                .map(p -> new OrderResponse.PaymentInfo(p.getId(), p.getPaymentNumber(), p.getPaymentType(),
                        p.getAmount(), p.getPaymentStatus(), p.getExpiredAt()))
                .toList();
        return new OrderResponse(order.getId(), order.getOrderNumber(), order.getUser().getId(),
                order.getTotalAmount(), order.getOrderType(), order.getFulfillmentStatus(),
                order.getCreatedAt(), orderItems, orderPayments);
    }

    private static String shortUuid() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
