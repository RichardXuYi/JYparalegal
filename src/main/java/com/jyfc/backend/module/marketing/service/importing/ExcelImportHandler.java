package com.jyfc.backend.module.marketing.service.importing;

import org.apache.poi.ss.usermodel.Workbook;

import java.util.List;
import java.util.Map;

public interface ExcelImportHandler {
    String getModule();

    Workbook buildTemplateWorkbook();

    List<Map<String, Object>> parsePreview(Workbook workbook);

    ExcelImportResult importWorkbook(Workbook workbook, Long operatorId);
}

