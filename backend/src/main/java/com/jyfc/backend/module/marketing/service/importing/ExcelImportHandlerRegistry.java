package com.jyfc.backend.module.marketing.service.importing;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** 模块名 → 导入处理器 的注册表，供模板生成、预览与异步导入共用。 */
@Component
public class ExcelImportHandlerRegistry {

    private final Map<String, ExcelImportHandler> handlers = new HashMap<>();

    public ExcelImportHandlerRegistry(ObjectProvider<ExcelImportHandler> handlerProvider) {
        handlerProvider.forEach(handler -> handlers.put(handler.getModule(), handler));
    }

    public ExcelImportHandler get(String module) {
        ExcelImportHandler handler = handlers.get(module);
        if (handler == null) {
            throw new IllegalArgumentException("Unsupported module: " + module);
        }
        return handler;
    }
}
