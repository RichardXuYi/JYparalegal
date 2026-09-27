package com.jyfc.backend.module.product.dto;

import java.util.ArrayList;
import java.util.List;

public class PromotionDTO {
    private Long activityId;
    private String activityName;
    private List<Object> discounts = new ArrayList<>();
    private List<Object> coupons = new ArrayList<>();

    public Long getActivityId() { return activityId; }
    public void setActivityId(Long activityId) { this.activityId = activityId; }
    public String getActivityName() { return activityName; }
    public void setActivityName(String activityName) { this.activityName = activityName; }
    public List<Object> getDiscounts() { return discounts; }
    public void setDiscounts(List<Object> discounts) { this.discounts = discounts; }
    public List<Object> getCoupons() { return coupons; }
    public void setCoupons(List<Object> coupons) { this.coupons = coupons; }
}
