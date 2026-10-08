package com.figurestore.api.controller;

import com.figurestore.api.dto.response.OrderResponse;
import com.figurestore.api.repository.ProductRepository;
import com.figurestore.api.service.OrderService;
import com.figurestore.api.service.ProductService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/admin")
public class AdminWebController {

    private final OrderService orderService;
    private final ProductRepository products;
    private final ProductService productService;

    public AdminWebController(OrderService orderService, ProductRepository products, ProductService productService) {
        this.orderService = orderService;
        this.products = products;
        this.productService = productService;
    }

    @GetMapping("/login")
    public String login() {
        return "admin/login";
    }

    @GetMapping({"", "/orders"})
    public String orders(@RequestParam(required = false) String status, Model model) {
        List<OrderResponse> all = orderService.findAll();
        List<OrderRow> rows = all.stream()
                .map(OrderRow::from)
                .filter(r -> status == null || status.isBlank() || r.status.equals(status))
                .toList();
        model.addAttribute("rows", rows);
        model.addAttribute("status", status);
        model.addAttribute("statuses", all.stream().map(OrderResponse::fulfillmentStatus).distinct().sorted().toList());
        return "admin/orders";
    }

    @GetMapping("/products")
    public String products(Model model) {
        model.addAttribute("products", products.findAll());
        return "admin/products";
    }

    @PostMapping("/products/{id}/slot")
    public String updateSlot(@PathVariable Long id, @RequestParam Integer stockSlot,
                             @RequestParam String status, RedirectAttributes redirect) {
        if (stockSlot < 0 || !Set.of("PO_OPEN", "PO_CLOSED", "READY_STOCK").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slot atau status produk tidak valid");
        }
        var product = products.findById(id).orElseThrow();
        product.setStockSlot(stockSlot);
        product.setStatus(status);
        products.save(product);
        redirect.addFlashAttribute("message", "Slot produk berhasil diperbarui");
        return "redirect:/admin/products";
    }

    @PostMapping("/products/{id}/delete")
    public String deleteProduct(@PathVariable Long id, RedirectAttributes redirect) {
        productService.delete(id);
        redirect.addFlashAttribute("message", "Produk berhasil dihapus");
        return "redirect:/admin/products";
    }

    public record OrderRow(String orderNumber, Long userId, BigDecimal totalAmount, String status,
                           BigDecimal paidTotal, int progressPercent, String dpBadge) {
        static OrderRow from(OrderResponse o) {
            BigDecimal paid = o.payments().stream()
                    .filter(p -> "SUCCESS".equals(p.paymentStatus()))
                    .map(OrderResponse.PaymentInfo::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int percent = o.totalAmount().signum() == 0 ? 0 :
                    paid.multiply(BigDecimal.valueOf(100))
                            .divide(o.totalAmount(), 0, RoundingMode.HALF_UP).intValue();
            String badge = switch (o.fulfillmentStatus()) {
                case "WAITING_DP" -> "Menunggu DP";
                case "DP_PAID" -> "DP Paid";
                case "WAITING_PELUNASAN" -> "Menunggu Pelunasan";
                case "FULL_PAID" -> "Full Paid";
                case "SHIPPED" -> "Terkirim";
                case "COMPLETED" -> "Selesai";
                case "CANCELLED", "CANCELLED_DP_HANGUS" -> "Batal";
                default -> o.fulfillmentStatus();
            };
            return new OrderRow(o.orderNumber(), o.userId(), o.totalAmount(), o.fulfillmentStatus(), paid, Math.min(percent, 100), badge);
        }
    }
}
