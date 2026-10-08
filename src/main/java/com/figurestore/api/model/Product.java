package com.figurestore.api.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    private String name;

    @Size(max = 100)
    private String manufacturer;

    @Size(max = 50)
    private String scaleType;

    private LocalDate releaseDate;

    @NotNull
    @DecimalMin("0.01")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal fullPrice;

    @NotNull
    @DecimalMin("0.01")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal dpPrice;

    @NotNull
    @Min(0)
    @Column(nullable = false)
    private Integer stockSlot = 0;

    @NotBlank
    @Size(max = 30)
    @Column(nullable = false, length = 30)
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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
