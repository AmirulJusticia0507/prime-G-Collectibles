package com.figurestore.api;

import com.figurestore.api.model.Product;
import com.figurestore.api.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;

@SpringBootTest(properties = "app.payment-expiry.interval-ms=3600000")
@AutoConfigureMockMvc
class AdminDashboardTests {
    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;

    @Test
    void dashboardRequiresAdmin() throws Exception {
        mvc.perform(get("/admin/orders"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    void customerCannotAccessDashboard() throws Exception {
        mvc.perform(get("/admin/orders").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminFormLoginWorksRepeatedly() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(formLogin("/admin/login").user("admin").password("change-me"))
                    .andExpect(authenticated().withRoles("ADMIN"));
        }
    }

    @Test
    void adminCanLogoutBackToLogin() throws Exception {
        mvc.perform(post("/admin/logout").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login?logout"));
    }

    @Test
    void adminCanRenderDashboardAndUpdateProductSlot() throws Exception {
        Product product = new Product();
        product.setName("Dashboard Product");
        product.setFullPrice(BigDecimal.valueOf(1_000_000));
        product.setDpPrice(BigDecimal.valueOf(200_000));
        product.setStockSlot(2);
        product.setStatus("PO_OPEN");
        product = products.save(product);

        mvc.perform(get("/products/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("store/product-detail"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Dashboard Product")));

        mvc.perform(get("/admin/products").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/products"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Dashboard Product")));

        mvc.perform(post("/admin/products/{id}", product.getId())
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("name", product.getName())
                        .param("fullPrice", product.getFullPrice().toPlainString())
                        .param("dpPrice", product.getDpPrice().toPlainString())
                        .param("stockSlot", "7").param("status", "PO_CLOSED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"));

        Product updated = products.findById(product.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.getStockSlot()).isEqualTo(7);
        org.assertj.core.api.Assertions.assertThat(updated.getStatus()).isEqualTo("PO_CLOSED");

        mvc.perform(get("/admin/users").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(view().name("admin/users"));
    }
}
