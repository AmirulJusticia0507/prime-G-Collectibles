package com.figurestore.api.controller;

import com.figurestore.api.service.ProductService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class StorefrontController {
    private final ProductService products;

    public StorefrontController(ProductService products) {
        this.products = products;
    }

    @GetMapping("/products/{id}")
    public String product(@PathVariable Long id, Model model) {
        model.addAttribute("product", products.findById(id));
        return "store/product-detail";
    }
}
