package com.figurestore.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true) private String paymentNumber;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id") private Order order;
    @Column(nullable = false) private String paymentType;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(nullable = false) private String paymentStatus;
    private String snapToken;
    private String midtransTransactionId;
    private LocalDateTime paidAt;
    private LocalDateTime expiredAt;
    @Column(nullable = false, insertable = false, updatable = false) private LocalDateTime createdAt;

    public Long getId() { return id; }
    public String getPaymentNumber() { return paymentNumber; }
    public void setPaymentNumber(String paymentNumber) { this.paymentNumber = paymentNumber; }
    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }
    public String getPaymentType() { return paymentType; }
    public void setPaymentType(String paymentType) { this.paymentType = paymentType; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public String getSnapToken() { return snapToken; }
    public void setSnapToken(String snapToken) { this.snapToken = snapToken; }
    public String getMidtransTransactionId() { return midtransTransactionId; }
    public void setMidtransTransactionId(String value) { this.midtransTransactionId = value; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    public LocalDateTime getExpiredAt() { return expiredAt; }
    public void setExpiredAt(LocalDateTime expiredAt) { this.expiredAt = expiredAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
