package com.jyfc.backend.module.sitecontent.dto;

import jakarta.validation.constraints.NotBlank;

public class SiteContentDTO {
    @NotBlank
    private String section;

    @NotBlank
    private String contentKey;

    @NotBlank
    private String contentValue;

    private Integer sortOrder;

    private Integer status;

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public String getContentKey() { return contentKey; }
    public void setContentKey(String contentKey) { this.contentKey = contentKey; }
    public String getContentValue() { return contentValue; }
    public void setContentValue(String contentValue) { this.contentValue = contentValue; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
