# FigureVault API - Backend & Architecture Specification (Revisi)

Dokumentasi resmi untuk backend e-commerce dan sistem manajemen *Pre-Order* (PO) / *Down Payment* (DP) toko *Action Figure* berbasis **Java Spring Boot** dan **PostgreSQL**.

Versi ini memperbaiki kekurangan pada versi awal: relasi data, state machine, idempotency webhook, dan kebijakan pembatalan/slot.

---

## 1. Frontend & UI Tech Stack (Tailwind CSS)

*   **Styling:** Tailwind CSS v3
*   **Interactive UI:** React.js / Vue.js 3 / Alpine.js
*   **Icons:** Lucide Icons / Heroicons

### Contoh Visual Badge Status DP (Tailwind Syntax)

```html
<span class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-amber-500/10 text-amber-500 border border-amber-500/20">
  <span class="w-1.5 h-1.5 rounded-full bg-amber-500 animate-pulse"></span>
  DP PAID (Menunggu Barang Rilis)
</span>

<div class="w-full bg-slate-800 rounded-full h-2.5 overflow-hidden border border-slate-700">
  <div class="bg-indigo-500 h-2.5 rounded-full" style="width: 30%"></div>
</div>
<p class="text-xs text-slate-400 mt-1">DP: Rp 300.000 / Total: Rp 1.000.000 (Sisa: Rp 700.000)</p>
```

---

## 2. Complete Workflow & Business Logic

```
[ User Select Figure ]
        |
        v
[ Create PO Order ] -----> State: WAITING_DP
        |                     |
        |                     +-- (timeout 24 jam) --> CANCELLED (slot dikembalikan)
        v
[ Pay DP (Payment Gateway) ] --> State: DP_PAID
        |
        v
[ Barang Tiba di Gudang (3-6 bulan) ]
        |
        v
[ Admin Triggers "Pelunasan Notification" ] --> State: WAITING_PELUNASAN
        |                                          |
        |                                          +-- (timeout 7 hari) --> CANCELLED_DP_HANGUS
        v
[ Pay Balance Payment ] --> State: FULL_PAID
        |
        v
[ Warehouse Ships Figure ] --> State: SHIPPED
        |
        v
                          --> State: COMPLETED
```

### State Machine (resmi)

| Dari | Event | Ke |
|---|---|---|
| WAITING_DP | DP berhasil | DP_PAID |
| WAITING_DP | Timeout / user batal | CANCELLED |
| DP_PAID | Admin rilis notifikasi pelunasan | WAITING_PELUNASAN |
| WAITING_PELUNASAN | Pelunasan berhasil | FULL_PAID |
| WAITING_PELUNASAN | Timeout / user batal | CANCELLED_DP_HANGUS |
| FULL_PAID | Barang dikirim | SHIPPED |
| SHIPPED | Barang diterima | COMPLETED |

Semua transisi status wajib dicatat di tabel `order_status_history` untuk audit.

### Kebijakan Bisnis
*   DP tidak refundable setelah lewat batas waktu pelunasan (`CANCELLED_DP_HANGUS`).
*   Slot pre-order yang batal/expired wajib dikembalikan ke `stock_slot`.
*   Satu order bisa berisi banyak item (lihat `order_items`).
*   Webhook Midtrans wajib diverifikasi signature SHA-512 dan idempotent (cek `payment_number` sebelum update).

---

## 3. Database Schema (PostgreSQL)

```sql
-- 1. Users
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    phone VARCHAR(20),
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER', -- ADMIN, CUSTOMER
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Product Catalog
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    manufacturer VARCHAR(100),
    scale_type VARCHAR(50),
    release_date DATE,
    full_price DECIMAL(12, 2) NOT NULL,
    dp_price DECIMAL(12, 2) NOT NULL,
    stock_slot INT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,  -- PO_OPEN, PO_CLOSED, READY_STOCK
    CONSTRAINT chk_price CHECK (dp_price > 0 AND full_price >= dp_price)
);

-- 3. Orders Header
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(50) UNIQUE NOT NULL, -- format: PO-YYYYMMDD-<uuid8>
    user_id BIGINT NOT NULL REFERENCES users(id),
    total_amount DECIMAL(12, 2) NOT NULL,
    order_type VARCHAR(20) NOT NULL,          -- PRE_ORDER, READY_STOCK
    fulfillment_status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. Order Items (1 order bisa banyak produk)
CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(12, 2) NOT NULL, -- harga saat order dibuat (snapshot)
    dp_unit_price DECIMAL(12, 2) NOT NULL
);

-- 5. Payments Ledger
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    payment_number VARCHAR(50) UNIQUE NOT NULL,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    payment_type VARCHAR(20) NOT NULL,      -- DOWN_PAYMENT, FINAL_PAYMENT
    amount DECIMAL(12, 2) NOT NULL,
    payment_status VARCHAR(20) NOT NULL,    -- PENDING, SUCCESS, EXPIRED, FAILED
    snap_token VARCHAR(255),
    midtrans_transaction_id VARCHAR(100),   -- untuk idempotency webhook
    paid_at TIMESTAMP,
    expired_at TIMESTAMP,                   -- batas waktu bayar
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 6. Audit trail status order
CREATE TABLE order_status_history (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    old_status VARCHAR(30),
    new_status VARCHAR(30) NOT NULL,
    note TEXT,
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 4. Clean Architecture Directory (Spring Boot)

```text
src/main/java/com/figurestore/api/
├── config/                  # Security, Web Client, & Payment Gateway Config
│   ├── SecurityConfig.java
│   └── MidtransConfig.java
├── controller/              # REST Endpoints
│   ├── ProductController.java
│   ├── OrderController.java
│   └── WebhookController.java
├── dto/                     # Data Transfer Objects
│   ├── request/
│   │   ├── CreateOrderRequest.java
│   │   └── WebhookPayload.java
│   └── response/
│       ├── OrderResponse.java
│       └── PaymentInitResponse.java
├── model/                   # JPA Entity Classes
│   ├── User.java
│   ├── Product.java
│   ├── Order.java
│   ├── OrderItem.java
│   ├── Payment.java
│   └── OrderStatusHistory.java
├── repository/              # Spring Data JPA Interfaces
├── service/                 # Core Business Logic
│   ├── OrderService.java
│   ├── PaymentService.java
│   └── impl/
├── statemachine/            # Transisi status order
│   └── OrderStateMachine.java
├── scheduler/               # Expire DP / pelunasan (cron job)
│   └── PaymentExpiryScheduler.java
└── enums/                   # OrderStatus, PaymentType, OrderType
```

---

## 5. Sample Core Implementation Code

### Order Service Logic (`OrderServiceImpl.java`)

```java
package com.figurestore.api.service.impl;

import com.figurestore.api.dto.request.CreateOrderRequest;
import com.figurestore.api.enums.OrderStatus;
import com.figurestore.api.enums.PaymentType;
import com.figurestore.api.model.*;
import com.figurestore.api.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class OrderServiceImpl {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    public OrderServiceImpl(ProductRepository productRepository,
                            OrderRepository orderRepository,
                            PaymentRepository paymentRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public Order createPreOrder(Long userId, CreateOrderRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product tidak ditemukan"));

        if (!"PO_OPEN".equals(product.getStatus())) {
            throw new IllegalStateException("Pre-Order untuk produk ini sudah ditutup");
        }
        if (product.getStockSlot() < request.getQuantity()) {
            throw new IllegalStateException("Slot Pre-Order habis!");
        }

        product.setStockSlot(product.getStockSlot() - request.getQuantity());
        productRepository.save(product);

        BigDecimal qty = BigDecimal.valueOf(request.getQuantity());

        Order order = new Order();
        order.setOrderNumber("PO-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setUserId(userId);
        order.setTotalAmount(product.getFullPrice().multiply(qty));
        order.setOrderType("PRE_ORDER");
        order.setFulfillmentStatus(OrderStatus.WAITING_DP.name());
        Order savedOrder = orderRepository.save(order);

        Payment dpPayment = new Payment();
        dpPayment.setPaymentNumber("PAY-DP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        dpPayment.setOrder(savedOrder);
        dpPayment.setPaymentType(PaymentType.DOWN_PAYMENT.name());
        dpPayment.setAmount(product.getDpPrice().multiply(qty));
        dpPayment.setPaymentStatus("PENDING");
        dpPayment.setExpiredAt(LocalDateTime.now().plusHours(24));
        paymentRepository.save(dpPayment);

        return savedOrder;
    }

    @Transactional
    public void cancelOrder(Order order, String note) {
        // kembalikan slot stok
        for (OrderItem item : order.getItems()) {
            Product p = item.getProduct();
            p.setStockSlot(p.getStockSlot() + item.getQuantity());
            productRepository.save(p);
        }
        order.setFulfillmentStatus(OrderStatus.CANCELLED.name());
        orderRepository.save(order);
    }
}
```

### Webhook Idempotency (`WebhookController`)

```java
@PostMapping("/midtrans")
public ResponseEntity<Void> handleWebhook(@RequestBody WebhookPayload payload) {
    // 1. Verifikasi signature SHA-512(order_id + status_code + gross_amount + server_key)
    if (!midtransService.isValidSignature(payload)) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    // 2. Idempotency: skip jika transaction_id sudah diproses
    if (paymentRepository.existsByMidtransTransactionId(payload.getTransactionId())) {
        return ResponseEntity.ok().build();
    }

    paymentService.processPaymentCallback(payload);
    return ResponseEntity.ok().build();
}
```

---

## 6. Setup & Getting Started

### Requirements
*   Java 17 LTS / Java 21 LTS
*   PostgreSQL 14+
*   Maven 3.8+
*   Node.js (jika build Tailwind terpisah)

### Run Local Engine

1. **Clone repository & config DB:**
   ```bash
   git clone <repo-url>
   cd prime-G-Collectibles
   ```

2. **Atur `src/main/resources/application.yml`:**
   ```yaml
   spring:
     datasource:
       url: jdbc:postgresql://localhost:5432/figure_store_db
       username: postgres
       password: yourpassword
     jpa:
       hibernate:
         ddl-auto: update
       show-sql: true
   ```

3. **Jalankan Aplikasi:**
   ```bash
   mvn spring-boot:run
   ```

---

## 7. Milestones & Roadmap MVP

- [x] **Phase 1:** Setup Skema DB & CRUD Catalog Figure
- [x] **Phase 2:** Logika Pemesanan PO (DP Handling)
- [ ] **Phase 3:** Integrasi Payment Gateway (Midtrans Webhook)
- [ ] **Phase 4:** Fitur Notifikasi Pelunasan (Email/WhatsApp)
- [ ] **Phase 5:** Dashboard Admin dengan Tailwind UI
- [ ] **Phase 6:** State machine + scheduler expiry + audit trail

---

## Changelog Revisi

*   Tambah tabel `users`, `order_items`, `order_status_history`.
*   `order_number` tidak lagi pakai `System.currentTimeMillis()`.
*   Tambah `expired_at` pada payments + scheduler pembatalan.
*   Webhook: verifikasi signature + idempotency via `midtrans_transaction_id`.
*   State machine & kebijakan pembatalan (slot kembali, DP hangus).
*   Validasi `dp_price <= full_price` dan status PO sebelum potong slot.
