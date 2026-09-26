package com.jyfc.backend.module.news.service.importing;

import com.jyfc.backend.module.news.entity.News;
import com.jyfc.backend.module.news.repository.NewsRepository;
import org.apache.poi.ss.usermodel.*;
import com.jyfc.backend.module.marketing.service.importing.ExcelImportHandler;
import com.jyfc.backend.module.marketing.service.importing.ExcelImportResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class NewsExcelImportHandler implements ExcelImportHandler {
    private final NewsRepository newsRepository;

    public NewsExcelImportHandler(NewsRepository newsRepository) {
        this.newsRepository = newsRepository;
    }

    @Override
    public String getModule() {
        return "news";
    }

    @Override
    public Workbook buildTemplateWorkbook() {
        try {
            Workbook workbook = WorkbookFactory.create(false);
            Sheet sheet = workbook.createSheet("news");

            // Row 0: column headers matching entity fields
            Row header = sheet.createRow(0);
            String[] headers = {"title*", "categoryId", "authorId", "coverImage", "summary", "content*"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            // Row 1: example data
            Row example = sheet.createRow(1);
            example.createCell(0).setCellValue("示例新闻标题");
            example.createCell(1).setCellValue("1");
            example.createCell(2).setCellValue("1");
            example.createCell(3).setCellValue("https://example.com/cover.jpg");
            example.createCell(4).setCellValue("新闻摘要");
            example.createCell(5).setCellValue("新闻正文内容...");

            // Row 2: instructions
            Row instruction = sheet.createRow(2);
            instruction.createCell(0).setCellValue("必填");
            instruction.createCell(1).setCellValue("数字ID");
            instruction.createCell(2).setCellValue("数字ID");
            instruction.createCell(5).setCellValue("必填");

            return workbook;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Map<String, Object>> parsePreview(Workbook workbook) {
        if (workbook == null) return List.of();
        return new ArrayList<>();
    }

    @Override
    @Transactional
    public ExcelImportResult importWorkbook(Workbook workbook, Long operatorId) {
        if (workbook == null) throw new IllegalArgumentException("Workbook must not be null");
        Sheet sheet = workbook.getSheetAt(0);
        ExcelImportResult result = new ExcelImportResult();
        int total = 0;
        int success = 0;
        int failure = 0;

        // Content hash set for within-file dedup (P1-8)
        Set<String> seenHashes = new HashSet<>();

        int lastRow = sheet.getLastRowNum();
        for (int i = 3; i <= lastRow; i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            String title = getStringCell(row.getCell(0));
            if (title == null) continue;

            total++;

            // Content hash dedup — compute SHA-256 of key fields
            String content = getStringCell(row.getCell(4));
            String contentKey = title.trim() + "|" + (content != null ? content.trim() : "");
            String hashStr = sha256(contentKey);
            if (!seenHashes.add(hashStr)) {
                failure++;
                result.addError("第" + (i + 1) + "行: 文件内重复(标题+内容)");
                continue;
            }

            if (newsRepository.existsByTitle(title.trim())) {
                failure++;
                result.addError("第" + (i + 1) + "行: 标题已存在");
                continue;
            }

            News news = new News();
            news.setTitle(title.trim());
            news.setContent(content);
            news.setCoverImage(getStringCell(row.getCell(3)));
            news.setStatus(0); // 0=草稿，需 admin 二次发布
            newsRepository.save(news);

            success++;
        }

        result.setTotalCount(total);
        result.setSuccessCount(success);
        result.setFailureCount(failure);
        return result;
    }

    private static String sha256(String input) {
        if (input == null) input = "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getStringCell(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue();
        return null;
    }
}
