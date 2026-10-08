# FigureVault API (prime-G-Collectibles)

Backend & arsitektur e-commerce action figure dengan alur Pre-Order (PO), Down Payment (DP), dan Pelunasan.

## Tech Stack

* **Backend:** Java 17/21, Spring Boot, Spring Data JPA
* **Database:** PostgreSQL 14+
* **Payment Gateway:** Midtrans
* **Frontend:** Thymeleaf + Tailwind CSS v3 + Alpine.js (server-rendered, satu project dengan Spring Boot)

## Struktur Project

```
├── docs/
│   └── dokumentasi_workflow_aplikasi_action_figure_revisi.md  # Spesifikasi lengkap
├── dokumentasi_workflow_aplikasi_action_figure.md             # Dok asli (arsip)
└── TODO.md                                                    # Daftar kerja
```

## Dokumentasi

* [Spesifikasi Revisi](docs/dokumentasi_workflow_aplikasi_action_figure_revisi.md) — workflow, state machine, skema DB, struktur kode, setup.

## Roadmap Singkat

1. Setup skema DB & CRUD catalog
2. Logika PO + DP
3. Midtrans webhook
4. Notifikasi pelunasan
5. Dashboard admin (Tailwind)
6. State machine + scheduler expiry + audit trail

Detail progres ada di [TODO.md](TODO.md).
