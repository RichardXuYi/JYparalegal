package com.jyfc.backend.module.marketing.dto;

import jakarta.validation.constraints.NotBlank;

public class HomepageConfigDTO {
    @NotBlank
    private String type;
    @NotBlank
    private String title;
    private Long refId;
    private String configJson;
    private Integer sortOrder;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getRefId() { return refId; }
    public void setRefId(Long refId) { this.refId = refId; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
