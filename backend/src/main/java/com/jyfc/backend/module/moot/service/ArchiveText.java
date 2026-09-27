package com.jyfc.backend.module.moot.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/** 从上传字节抽出纯文本。无文字层与解析异常分开返回。 */
public final class ArchiveText {
    private ArchiveText() {}

    public record ExtractResult(String text, String error) {
        public static ExtractResult text(String value) {
            return new ExtractResult(value == null ? "" : value.trim(), null);
        }

        public static ExtractResult empty() {
            return new ExtractResult("", null);
        }

        public static ExtractResult failed(String message) {
            String detail = message == null || message.isBlank() ? "解析失败" : message.trim();
            return new ExtractResult("", detail);
        }
    }

    public static ExtractResult extract(String fileName, byte[] bytes) {
        String ext = extension(fileName);
        try {
            return switch (ext) {
                case "docx" -> ExtractResult.text(docx(bytes));
                case "pdf" -> ExtractResult.text(pdf(bytes));
                case "txt", "md", "csv" -> ExtractResult.text(new String(bytes, StandardCharsets.UTF_8));
                default -> ExtractResult.empty();
            };
        } catch (Exception ex) {
            return ExtractResult.failed(ex.getMessage());
        }
    }

    private static String docx(byte[] bytes) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                String line = p.getText();
                if (line != null && !line.isBlank()) sb.append(line.trim()).append('\n');
            }
            return sb.toString().trim();
        }
    }

    private static String pdf(byte[] bytes) throws Exception {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(doc).trim();
        }
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) return "";
        return fileName.substring(dot + 1).toLowerCase();
    }
}
