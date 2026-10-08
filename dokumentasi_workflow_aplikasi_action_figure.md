# 🧸 FigureVault API - Backend & Architecture Specification

Dokumentasi resmi untuk backend e-commerce dan sistem manajemen *Pre-Order* (PO) / *Down Payment* (DP) toko *Action Figure* berbasis **Java Spring Boot** dan **PostgreSQL**.

---

## 📸 1. Frontend & UI Tech Stack (Tailwind CSS)

> **Apakah bisa pakai Tailwind CSS?**
> **Sangat Bisa dan Sangat Direkomendasikan!** 
> Bootstrap menggunakan pendekatan *component-based* yang cenderung generik. **Tailwind CSS** adalah *utility-first framework* yang memungkinkan tampilan modern, presisi, cepat, dan mudah disesuaikan dengan tema anime/figure (seperti dark mode, glassmorphism, badge status neon).

### Recommended Modern Frontend Stack
*   **Styling:** [Tailwind CSS v3](https://tailwindcss.com/)
*   **Interactive UI:** React.js / Vue.js 3 / Alpine.js (untuk interaksi ringan tanpa *overkill* SPA)
*   **Icons:** Lucide Icons / Heroicons

### Contoh Visual Badge Status DP (Tailwind Syntax)
```html
<!-- Badge Status PO / DP di Frontend -->
<span class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-amber-500/10 text-amber-500 border border-amber-500/20">
  <span class="w-1.5 h-1.5 rounded-full bg-amber-500 animate-pulse"></span>
  DP PAID (Menunggu Barang Rilis)
</span>

<!-- Progress Bar DP vs Full Price -->
<div class="w-full bg-slate-800 rounded-full h-2.5 overflow-hidden border border-slate-700">
  <div class="bg-indigo-500 h-2.5 rounded-full" style="width: 30%"></div>
</div>
<p class="text-xs text-slate-400 mt-1">DP: Rp 300.000 / Total: Rp 1.000.000 (Sisa: Rp 700.000)</p>
```

---

## 🔄 2. Complete Workflow & Business Logic

Siklus transaksi *action figure* berbeda dari toko baju biasa karena adanya **Jeda Waktu Pre-Order (3-6 bulan)** dan **Dua Fase Pembayaran**.

```
[ User Select Figure ]
        │
        ▼
[ Create PO Order ] ───> State: WAITING_DP
        │
        ▼
[ Pay DP (Payment Gateway) ] ───> State: DP_PAID
        │
        ▼
[ Barang Tiba di Gudang (3 Bulan kemudian) ]
        │
        ▼
[ Admin Triggers "Pelunasan Notification" ] ───> State: WAITING_PELUNASAN
        │
        ▼
[ Pay Balance Payment ] ───> State: FULL_PAID
        │
        ▼
[ Warehouse Ships Figure ] ───> State: SHIPPED
```

---

## 🗄️ 3. Database Schema (PostgreSQL)

```sql
-- 1. Table Product Catalog
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    manufacturer VARCHAR(100), -- Good Smile Company, Alter, Bandai
    scale_type VARCHAR(50),      -- 1/7 Scale, Nendoroid, Figma
    release_date DATE,
    full_price DECIMAL(12, 2) NOT NULL,
    dp_price DECIMAL(12, 2) NOT NULL,
    stock_slot INT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL  -- PO_OPEN, PO_CLOSED, READY_STOCK
);

-- 2. Table Orders Header
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(50) UNIQUE NOT NULL,
    user_id BIGINT NOT NULL,
    total_amount DECIMAL(12, 2) NOT NULL,
    order_type VARCHAR(20) NOT NULL,         -- PRE_ORDER, READY_STOCK
    fulfillment_status VARCHAR(30) NOT NULL, -- WAITING_DP, DP_PAID, WAITING_PELUNASAN, FULL_PAID, SHIPPED, CANCELLED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Table Payments Ledger (Laporan DP & Pelunasan)
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    payment_number VARCHAR(50) UNIQUE NOT NULL,
    order_id BIGINT REFERENCES orders(id),
    payment_type VARCHAR(20) NOT NULL,      -- DOWN_PAYMENT, FINAL_PAYMENT
    amount DECIMAL(12, 2) NOT NULL,
    payment_status VARCHAR(20) NOT NULL,    -- PENDING, SUCCESS, EXPIRED
    snap_token VARCHAR(255),                -- Token Midtrans / Payment Link
    paid_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 📁 4. Clean Architecture Directory (Spring Boot)

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
│   ├── Product.java
│   ├── Order.java
│   └── Payment.java
├── repository/              # Spring Data JPA Interfaces
│   ├── ProductRepository.java
│   ├── OrderRepository.java
│   └── PaymentRepository.java
├── service/                 # Core Business Logic
│   ├── OrderService.java
│   ├── PaymentService.java
│   └── impl/
│       ├── OrderServiceImpl.java
│       └── PaymentServiceImpl.java
└── enums/                   # OrderStatus, PaymentType, OrderType
```

---

## 💻 5. Sample Core Implementation Code

### Order Service Logic (`OrderServiceImpl.java`)

```java
package com.figurestore.api.service.impl;

import com.figurestore.api.dto.request.CreateOrderRequest;
import com.figurestore.api.enums.OrderStatus;
import com.figurestore.api.enums.PaymentType;
import com.figurestore.api.model.Order;
import com.figurestore.api.model.Payment;
import com.figurestore.api.model.Product;
import com.figurestore.api.repository.OrderRepository;
import com.figurestore.api.repository.PaymentRepository;
import com.figurestore.api.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
                .orElseThrow(() -> new RuntimeException("Product tidak ditemukan"));

        if (product.getStockSlot() < request.getQuantity()) {
            throw new RuntimeException("Slot Pre-Order habis!");
        }

        // 1. Potong Slot Stok
        product.setStockSlot(product.getStockSlot() - request.getQuantity());
        productRepository.save(product);

        // 2. Buat Record Order Header
        Order order = new Order();
        order.setOrderNumber("PO-" + System.currentTimeMillis());
        order.setUserId(userId);
        order.setTotalAmount(product.getFullPrice().multiply(BigDecimal.valueOf(request.getQuantity())));
        order.setOrderType("PRE_ORDER");
        order.setFulfillmentStatus(OrderStatus.WAITING_DP.name());
        Order savedOrder = orderRepository.save(order);

        // 3. Buat Tagihan DP Pertama
        Payment dpPayment = new Payment();
        dpPayment.setPaymentNumber("PAY-DP-" + UUID.randomUUID().toString().substring(0, 8));
        dpPayment.setOrder(savedOrder);
        dpPayment.setPaymentType(PaymentType.DOWN_PAYMENT.name());
        dpPayment.setAmount(product.getDpPrice().multiply(BigDecimal.valueOf(request.getQuantity())));
        dpPayment.setPaymentStatus("PENDING");
        paymentRepository.save(dpPayment);

        return savedOrder;
    }
}
```

---

## 🚀 6. Setup & Getting Started

### Requirements
*   Java 17 LTS / Java 21 LTS
*   PostgreSQL 14+
*   Maven 3.8+
*   Node.js (jika build Tailwind terpisah)

### Run Local Engine

1. **Clone repository & config DB:**
   ```bash
   git clone https://github.com/yourusername/figure-store-backend.git
   cd figure-store-backend
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

## 🎯 7. Milestones & Roadmap MVP

- [x] **Phase 1:** Setup Skema DB & CRUD Catalog Figure
- [x] **Phase 2:** Logika Pemesanan PO (DP Handling)
- [ ] **Phase 3:** Integrasi Payment Gateway (Midtrans Webhook)
- [ ] **Phase 4:** Fitur Notifikasi Pelunasan (Email/WhatsApp via Turnkey Gateway)
- [ ] **Phase 5:** Dashboard Admin dengan Tailwind UI
```

---

Semoga dokumentasi dan panduan di atas membantu kamu memahami alur kerja penuh dari backend Java hingga UI Tailwind CSS! Jika ada bagian yang perlu disesuaikan dengan kebutuhan bisnismu, silakan beri tahu ya.