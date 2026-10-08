package com.figurestore.api.service;

import com.figurestore.api.dto.request.ProductRequest;
import com.figurestore.api.dto.response.ProductResponse;
import com.figurestore.api.model.Product;
import com.figurestore.api.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<ProductResponse> findAll() {
        return productRepository.findAll().stream().map(ProductResponse::from).toList();
    }

    public ProductResponse findById(Long id) {
        return ProductResponse.from(getProduct(id));
    }

    public ProductResponse create(ProductRequest req) {
        validatePrice(req);
        Product p = new Product();
        apply(p, req);
        return ProductResponse.from(productRepository.save(p));
    }

    public ProductResponse update(Long id, ProductRequest req) {
        validatePrice(req);
        Product p = getProduct(id);
        apply(p, req);
        return ProductResponse.from(productRepository.save(p));
    }

    public void delete(Long id) {
        productRepository.delete(getProduct(id));
    }

    private Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product tidak ditemukan"));
    }

    private void validatePrice(ProductRequest req) {
        if (req.getFullPrice() != null && req.getDpPrice() != null
                && req.getDpPrice().compareTo(req.getFullPrice()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dp_price tidak boleh lebih besar dari full_price");
        }
    }

    private void apply(Product p, ProductRequest req) {
        p.setName(req.getName());
        p.setManufacturer(req.getManufacturer());
        p.setScaleType(req.getScaleType());
        p.setReleaseDate(req.getReleaseDate());
        p.setFullPrice(req.getFullPrice());
        p.setDpPrice(req.getDpPrice());
        p.setStockSlot(req.getStockSlot());
        p.setStatus(req.getStatus());
    }
}
