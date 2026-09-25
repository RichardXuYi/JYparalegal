package com.jyfc.backend.module.trade.dto;

import com.jyfc.backend.module.trade.entity.OrderEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Order response DTO that contains order fields plus user display info.
 */
public class OrderResponseDTO {

    private Long id;
    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;
    private BigDecimal payAmount;
    private Integer status;
    private String receiverInfo;
    private String paymentMethod;
    private String shippingMethod;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // User display fields (queried separately, not in OrderEntity)
    private String username;
    private String userAvatar;

    // Order item summary (queried separately, for list display)
    private String productName;
    private Integer itemCount;

    public OrderResponseDTO() {}

    /** Build from OrderEntity + user info. */
    public OrderResponseDTO(OrderEntity entity, String username, String userAvatar) {
        this.id              = entity.getId();
        this.orderNo         = entity.getOrderNo();
        this.userId          = entity.getUserId();
        this.totalAmount     = entity.getTotalAmount();
        this.payAmount       = entity.getPayAmount();
        this.status          = entity.getStatus();
        this.receiverInfo    = entity.getReceiverInfo();
        this.paymentMethod   = entity.getPaymentMethod();
        this.shippingMethod  = entity.getShippingMethod();
        this.remark          = entity.getRemark();
        this.createdAt       = entity.getCreatedAt();
        this.updatedAt       = entity.getUpdatedAt();
        this.username        = username;
        this.userAvatar      = userAvatar;
    }

    // --- Getters & Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getPayAmount() { return payAmount; }
    public void setPayAmount(BigDecimal payAmount) { this.payAmount = payAmount; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getReceiverInfo() { return receiverInfo; }
    public void setReceiverInfo(String receiverInfo) { this.receiverInfo = receiverInfo; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getShippingMethod() { return shippingMethod; }
    public void setShippingMethod(String shippingMethod) { this.shippingMethod = shippingMethod; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getUserAvatar() { return userAvatar; }
    public void setUserAvatar(String userAvatar) { this.userAvatar = userAvatar; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }
}
