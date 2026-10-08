package com.figurestore.api.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ProductRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 100)
    private String manufacturer;

    @Size(max = 50)
    private String scaleType;

    private LocalDate releaseDate;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal fullPrice;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal dpPrice;

    @NotNull
    @Min(0)
    private Integer stockSlot;

    @NotBlank
    @Pattern(regexp = "PO_OPEN|PO_CLOSED|READY_STOCK")
    private String status;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public String getScaleType() { return scaleType; }
    public void setScaleType(String scaleType) { this.scaleType = scaleType; }

    public LocalDate getReleaseDate() { return releaseDate; }
    public void setReleaseDate(LocalDate releaseDate) { this.releaseDate = releaseDate; }

    public BigDecimal getFullPrice() { return fullPrice; }
    public void setFullPrice(BigDecimal fullPrice) { this.fullPrice = fullPrice; }

    public BigDecimal getDpPrice() { return dpPrice; }
    public void setDpPrice(BigDecimal dpPrice) { this.dpPrice = dpPrice; }

    public Integer getStockSlot() { return stockSlot; }
    public void setStockSlot(Integer stockSlot) { this.stockSlot = stockSlot; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
