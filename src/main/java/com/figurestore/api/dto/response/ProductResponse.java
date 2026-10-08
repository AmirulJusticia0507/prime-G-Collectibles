package com.figurestore.api.dto.response;

import com.figurestore.api.model.Product;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ProductResponse {

    private Long id;
    private String name;
    private String manufacturer;
    private String scaleType;
    private LocalDate releaseDate;
    private BigDecimal fullPrice;
    private BigDecimal dpPrice;
    private Integer stockSlot;
    private String status;

    public static ProductResponse from(Product p) {
        ProductResponse r = new ProductResponse();
        r.id = p.getId();
        r.name = p.getName();
        r.manufacturer = p.getManufacturer();
        r.scaleType = p.getScaleType();
        r.releaseDate = p.getReleaseDate();
        r.fullPrice = p.getFullPrice();
        r.dpPrice = p.getDpPrice();
        r.stockSlot = p.getStockSlot();
        r.status = p.getStatus();
        return r;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getManufacturer() { return manufacturer; }
    public String getScaleType() { return scaleType; }
    public LocalDate getReleaseDate() { return releaseDate; }
    public BigDecimal getFullPrice() { return fullPrice; }
    public BigDecimal getDpPrice() { return dpPrice; }
    public Integer getStockSlot() { return stockSlot; }
    public String getStatus() { return status; }
}
