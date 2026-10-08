package com.figurestore.api;

import com.figurestore.api.dto.request.ProductRequest;
import com.figurestore.api.repository.ProductRepository;
import com.figurestore.api.service.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock ProductRepository products;
    @InjectMocks ProductService service;

    @Test
    void rejectsDpAboveFullPriceBeforeSaving() {
        ProductRequest request = new ProductRequest();
        request.setName("Invalid Price");
        request.setFullPrice(new BigDecimal("100000"));
        request.setDpPrice(new BigDecimal("200000"));
        request.setStockSlot(1);
        request.setStatus("PO_OPEN");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("dp_price");
    }
}
