package com.jyfc.backend.module.marketing.service.importing;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 按模块名找到对应的 Excel 导入器。 */
@Component
public class ExcelImportHandlerRegistry {

    private final Map<String, ExcelImportHandler> handlers = new HashMap<>();

    public ExcelImportHandlerRegistry(List<ExcelImportHandler> found) {
        for (ExcelImportHandler handler : found) {
            handlers.put(handler.getModule(), handler);
        }
    }

    public ExcelImportHandler get(String module) {
        ExcelImportHandler handler = handlers.get(module);
        if (handler == null) {
            throw new IllegalArgumentException("不支持的导入类型");
        }
        return handler;
    }
}
