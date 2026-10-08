package com.figurestore.api.dto.response;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record AdminOrderRow(Long id, String orderNumber, Long userId, BigDecimal totalAmount,
                            String status, BigDecimal paidTotal, int progressPercent,
                            String badge, String badgeClass, boolean canTriggerPelunasan) {
    public static AdminOrderRow from(OrderResponse order) {
        BigDecimal paid = order.payments().stream()
                .filter(payment -> "SUCCESS".equals(payment.paymentStatus()))
                .map(OrderResponse.PaymentInfo::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        int percent = order.totalAmount().signum() == 0 ? 0 : paid.multiply(BigDecimal.valueOf(100))
                .divide(order.totalAmount(), 0, RoundingMode.HALF_UP).intValue();
        String status = order.fulfillmentStatus();
        String badge = switch (status) {
            case "WAITING_DP" -> "Menunggu DP";
            case "DP_PAID" -> "DP Paid";
            case "WAITING_PELUNASAN" -> "Menunggu Pelunasan";
            case "FULL_PAID" -> "Full Paid";
            case "SHIPPED" -> "Terkirim";
            case "COMPLETED" -> "Selesai";
            case "CANCELLED", "CANCELLED_DP_HANGUS" -> "Batal";
            default -> status;
        };
        String color = switch (status) {
            case "WAITING_DP" -> "text-amber-400 border-amber-500/20 bg-amber-500/10";
            case "DP_PAID" -> "text-sky-400 border-sky-500/20 bg-sky-500/10";
            case "WAITING_PELUNASAN" -> "text-violet-400 border-violet-500/20 bg-violet-500/10";
            case "FULL_PAID", "COMPLETED" -> "text-emerald-400 border-emerald-500/20 bg-emerald-500/10";
            default -> "text-slate-400 border-slate-500/20 bg-slate-500/10";
        };
        return new AdminOrderRow(order.id(), order.orderNumber(), order.userId(), order.totalAmount(), status,
                paid, Math.min(percent, 100), badge, color, "DP_PAID".equals(status));
    }
}
