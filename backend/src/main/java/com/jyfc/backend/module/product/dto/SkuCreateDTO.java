package com.jyfc.backend.module.product.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class SkuCreateDTO {
    @Size(max = 200)
    private String title;
    private String skuCode;
    @NotNull
    private Double currentPrice;
    @NotNull
    private Integer stock;
    private String status;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
    public Double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(Double currentPrice) { this.currentPrice = currentPrice; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}