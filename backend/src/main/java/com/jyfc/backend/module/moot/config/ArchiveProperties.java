package com.jyfc.backend.module.moot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jy.archive")
public class ArchiveProperties {
    private String dir = "./data/archive";

    public String getDir() { return dir; }
    public void setDir(String dir) { this.dir = dir; }
}
