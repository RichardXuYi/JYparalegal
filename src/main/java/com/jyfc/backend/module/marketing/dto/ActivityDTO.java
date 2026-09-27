package com.jyfc.backend.module.marketing.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.Set;

public class ActivityDTO {
    @NotBlank
    private String name;
    private String description;
    private String coverImage;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Set<Long> couponIds;
    private Set<Long> discountIds;
    private Integer sortOrder;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCoverImage() { return coverImage; }
    public void setCoverImage(String coverImage) { this.coverImage = coverImage; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public Set<Long> getCouponIds() { return couponIds; }
    public void setCouponIds(Set<Long> couponIds) { this.couponIds = couponIds; }
    public Set<Long> getDiscountIds() { return discountIds; }
    public void setDiscountIds(Set<Long> discountIds) { this.discountIds = discountIds; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
