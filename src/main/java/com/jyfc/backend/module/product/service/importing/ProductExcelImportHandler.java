package com.jyfc.backend.module.product.service.importing;

import com.jyfc.backend.module.product.entity.Product;
import com.jyfc.backend.module.product.entity.Sku;
import com.jyfc.backend.module.product.repository.ProductRepository;
import com.jyfc.backend.module.product.repository.SkuRepository;
import org.apache.poi.ss.usermodel.*;
import com.jyfc.backend.module.marketing.service.importing.ExcelImportHandler;
import com.jyfc.backend.module.marketing.service.importing.ExcelImportResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ProductExcelImportHandler implements ExcelImportHandler {
    private final ProductRepository productRepository;
    private final SkuRepository skuRepository;

    public ProductExcelImportHandler(ProductRepository productRepository, SkuRepository skuRepository) {
        this.productRepository = productRepository;
        this.skuRepository = skuRepository;
    }

    @Override
    public String getModule() {
        return "product";
    }

    @Override
    public Workbook buildTemplateWorkbook() {
        try {
            Workbook workbook = WorkbookFactory.create(false);
            Sheet sheet = workbook.createSheet("products");

            // Row 0: column headers
            Row header = sheet.createRow(0);
            String[] headers = {"name*", "subtitle", "description", "mainImage", "detailImages", "price*", "stock"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            // Row 1: example data
            Row example = sheet.createRow(1);
            example.createCell(0).setCellValue("示例产品");
            example.createCell(1).setCellValue("副标题");
            example.createCell(2).setCellValue("产品描述");
            example.createCell(3).setCellValue("https://example.com/img.jpg");
            example.createCell(4).setCellValue("[\"https://example.com/detail1.jpg\"]");
            example.createCell(5).setCellValue("99.00");
            example.createCell(6).setCellValue("100");

            // Row 2: instruction
            Row instruction = sheet.createRow(2);
            instruction.createCell(0).setCellValue("必填");
            instruction.createCell(5).setCellValue("必填,数字");
            instruction.createCell(6).setCellValue("整数");

            return workbook;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Map<String, Object>> parsePreview(Workbook workbook) {
        return new ArrayList<>(); // Simplified
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

            String name = getStringCell(row.getCell(0));
            if (name == null) continue;

            total++;

            // Content hash dedup — compute SHA-256 of key fields
            String desc = getStringCell(row.getCell(2));
            String contentKey = name.trim() + "|" + (desc != null ? desc.trim() : "");
            String hashStr = sha256(contentKey);
            if (!seenHashes.add(hashStr)) {
                failure++;
                result.addError("第" + (i + 1) + "行: 文件内重复(名称+描述)");
                continue;
            }

            if (productRepository.existsByName(name.trim())) {
                failure++;
                result.addError("第" + (i + 1) + "行: 名称已存在");
                continue;
            }

            Product product = new Product();
            product.setName(name.trim());
            product.setDescription(desc);
            product.setMainImage(getStringCell(row.getCell(3)));
            product.setStatus(0); // 0=草稿，需 admin 二次上架
            product = productRepository.save(product);

            // 创建默认SKU
            String priceStr = getStringCell(row.getCell(5));
            if (priceStr != null) {
                Long productId = product.getId();
                if (productId == null) continue;
                Sku sku = new Sku();
                sku.setProductId(productId);
                sku.setSkuCode("SKU-" + System.nanoTime());
                sku.setSpecs("{}");
                sku.setPrice(new BigDecimal(priceStr));
                String stockStr = getStringCell(row.getCell(6));
                sku.setStock(stockStr != null ? (int)Double.parseDouble(stockStr) : 0);
                skuRepository.save(sku);
            }

            success++;
        }

        result.setTotalCount(total);
        result.setSuccessCount(success);
        result.setFailureCount(failure);
        return result;
    }

    private static String sha256(String input) {
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
        if (cell.getCellType() == CellType.NUMERIC) return String.valueOf(cell.getNumericCellValue());
        return null;
    }
}
