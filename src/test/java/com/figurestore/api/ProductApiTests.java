package com.figurestore.api;

import com.figurestore.api.model.Product;
import com.figurestore.api.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.admin.username=admin",
        "app.admin.password=test-password"
})
@AutoConfigureMockMvc
@Transactional
class ProductApiTests {

    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;

    private static final String PRODUCT = """
            {
              "name": "Test Figure",
              "manufacturer": "Test Maker",
              "scaleType": "1/7",
              "releaseDate": "2027-01-01",
              "fullPrice": 2000000,
              "dpPrice": 500000,
              "stockSlot": 10,
              "status": "PO_OPEN"
            }
            """;

    @Test
    void catalogIsPublicButMutationRequiresAdmin() throws Exception {
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanCreateReadUpdateAndDeleteProduct() throws Exception {
        mvc.perform(post("/api/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Figure"));

        Product product = products.findAll().stream()
                .filter(p -> p.getName().equals("Test Figure"))
                .findFirst().orElseThrow();

        mvc.perform(get("/api/products/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product.getId()));

        String updated = PRODUCT.replace("Test Figure", "Updated Figure");
        mvc.perform(put("/api/products/{id}", product.getId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Figure"));

        mvc.perform(delete("/api/products/{id}", product.getId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void rejectsDpPriceAboveFullPrice() throws Exception {
        String invalid = PRODUCT.replace("500000", "2500000");

        mvc.perform(post("/api/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest());
    }
}
