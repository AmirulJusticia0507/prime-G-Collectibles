package com.figurestore.api.controller;

import com.figurestore.api.dto.request.ProductRequest;
import com.figurestore.api.dto.response.AdminOrderRow;
import com.figurestore.api.model.User;
import com.figurestore.api.repository.ProductRepository;
import com.figurestore.api.repository.UserRepository;
import com.figurestore.api.service.OrderService;
import com.figurestore.api.service.ProductService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Set;

@Controller
@RequestMapping("/admin")
public class AdminWebController {
    private static final int PAGE_SIZE = 10;
    private static final Set<String> ROLES = Set.of("ADMIN", "CUSTOMER");
    private final OrderService orderService;
    private final ProductRepository products;
    private final ProductService productService;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AdminWebController(OrderService orderService, ProductRepository products, ProductService productService,
                              UserRepository users, PasswordEncoder passwordEncoder) {
        this.orderService = orderService; this.products = products; this.productService = productService;
        this.users = users; this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/login") public String login() { return "admin/login"; }

    @GetMapping({"", "/orders"})
    public String orders(@RequestParam(required = false) String status,
                         @RequestParam(defaultValue = "0") int page, Model model) {
        var result = orderService.findPage(status,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by("createdAt").descending()))
                .map(AdminOrderRow::from);
        model.addAttribute("result", result); model.addAttribute("rows", result.getContent());
        model.addAttribute("status", status);
        model.addAttribute("statuses", Set.of("WAITING_DP", "DP_PAID", "WAITING_PELUNASAN", "FULL_PAID",
                "SHIPPED", "COMPLETED", "CANCELLED", "CANCELLED_DP_HANGUS"));
        return "admin/orders";
    }

    @PostMapping("/orders/{id}/pelunasan")
    public String triggerPelunasan(@PathVariable Long id, RedirectAttributes redirect) {
        try { orderService.triggerPelunasan(id); success(redirect, "Notifikasi pelunasan berhasil diproses"); }
        catch (RuntimeException ex) { error(redirect, ex); }
        return "redirect:/admin/orders";
    }

    @GetMapping("/products")
    public String products(@RequestParam(defaultValue = "") String query,
                           @RequestParam(defaultValue = "0") int page, Model model) {
        var pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by("name"));
        var result = query.isBlank() ? products.findAll(pageable)
                : products.findByNameContainingIgnoreCaseOrManufacturerContainingIgnoreCase(query, query, pageable);
        model.addAttribute("result", result); model.addAttribute("products", result.getContent());
        model.addAttribute("query", query); return "admin/products";
    }

    @PostMapping("/products")
    public String createProduct(ProductRequest request, RedirectAttributes redirect) {
        try { productService.create(request); success(redirect, "Produk berhasil ditambahkan"); }
        catch (RuntimeException ex) { error(redirect, ex); }
        return "redirect:/admin/products";
    }

    @PostMapping("/products/{id}")
    public String updateProduct(@PathVariable Long id, ProductRequest request, RedirectAttributes redirect) {
        try { productService.update(id, request); success(redirect, "Produk berhasil diperbarui"); }
        catch (RuntimeException ex) { error(redirect, ex); }
        return "redirect:/admin/products";
    }

    @PostMapping("/products/{id}/delete")
    public String deleteProduct(@PathVariable Long id, RedirectAttributes redirect) {
        try { productService.delete(id); success(redirect, "Produk berhasil dihapus"); }
        catch (RuntimeException ex) { error(redirect, ex); }
        return "redirect:/admin/products";
    }

    @GetMapping("/users")
    public String users(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("result", users.findAll(PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by("email"))));
        return "admin/users";
    }

    @PostMapping("/users")
    public String createUser(@RequestParam String fullName, @RequestParam String email,
                             @RequestParam(required = false) String phone, @RequestParam String password,
                             @RequestParam String role, RedirectAttributes redirect) {
        String normalizedEmail = email.trim().toLowerCase();
        if (fullName.isBlank() || normalizedEmail.isBlank() || password.length() < 8 || !ROLES.contains(role)
                || users.findByEmail(normalizedEmail).isPresent()) {
            redirect.addFlashAttribute("error", "Data user tidak valid, email duplikat, atau password kurang dari 8 karakter");
            return "redirect:/admin/users";
        }
        User user = new User(); user.setFullName(fullName.trim()); user.setEmail(normalizedEmail); user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(password)); user.setRole(role); users.save(user);
        success(redirect, "User berhasil ditambahkan"); return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/role")
    public String updateRole(@PathVariable Long id, @RequestParam String role, RedirectAttributes redirect) {
        if (!ROLES.contains(role)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role tidak valid");
        User user = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        user.setRole(role); users.save(user); success(redirect, "Role berhasil diperbarui");
        return "redirect:/admin/users";
    }

    private static void success(RedirectAttributes redirect, String message) { redirect.addFlashAttribute("message", message); }
    private static void error(RedirectAttributes redirect, RuntimeException ex) {
        String message = ex instanceof ResponseStatusException status && status.getReason() != null
                ? status.getReason() : "Operasi gagal diproses";
        redirect.addFlashAttribute("error", message);
    }
}
