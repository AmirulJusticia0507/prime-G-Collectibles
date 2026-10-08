# TODO List - FigureVault API

## Fase 1: Setup DB & Catalog
- [x] Buat project Spring Boot (Spring Web, JPA, PostgreSQL, Validation, Security)
- [x] Buat migrasi skema (Flyway/Liquibase) untuk tabel: users, products, orders, order_items, payments, order_status_history
- [x] Entity + Repository untuk Product
- [x] CRUD endpoint Product (admin) & list katalog (public)
- [x] Validasi `dp_price <= full_price` di level DB & aplikasi

## Fase 2: Logika PO & DP
- [x] Entity + Repository Order, OrderItem, Payment
- [x] Endpoint create PO order (potong slot, buat tagihan DP, set expired_at)
- [x] Generate order_number format `PO-YYYYMMDD-<uuid8>`
- [x] State machine transisi status order
- [x] Endpoint cancel order + kembalikan slot stok
- [x] Scheduler expire DP (24 jam) & pelunasan (7 hari)

## Fase 3: Payment Gateway (Midtrans)
- [ ] Integrasi Snap token saat create payment
- [ ] Webhook endpoint: verifikasi signature SHA-512
- [ ] Idempotency webhook via `midtrans_transaction_id`
- [ ] Update status payment & order dari callback
- [ ] Unit test untuk skenario callback sukses/expired/gagal

## Fase 4: Notifikasi Pelunasan
- [ ] Endpoint admin trigger notifikasi pelunasan (ubah status ke WAITING_PELUNASAN)
- [ ] Kirim notifikasi via Email
- [ ] (Opsional) WhatsApp via gateway
- [ ] Catat ke `order_status_history`

## Fase 5: Dashboard Admin (Tailwind)
- [ ] Setup Tailwind CSS (dengan Thymeleaf layout)
- [ ] Setup Alpine.js untuk interaksi ringan
- [ ] Halaman list order + filter status
- [ ] Halaman manajemen produk & slot PO
- [ ] Badge status & progress bar DP (lihat contoh di docs)

## Fase 6: Hardening
- [ ] Spring Security + JWT (role ADMIN/CUSTOMER)
- [ ] Audit trail ke `order_status_history` di setiap transisi
- [ ] Global exception handler (@RestControllerAdvice)
- [ ] Logging & request tracing
- [ ] Test coverage minimal service layer
