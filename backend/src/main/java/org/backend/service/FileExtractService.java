package org.backend.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 文件文本提取服务 — 从上传文件中提取纯文本
 */
@Service
public class FileExtractService {

    private static final Logger logger = LoggerFactory.getLogger(FileExtractService.class);

    /**
     * 从上传文件中提取纯文本
     *
     * @param file      上传的文件
     * @param extension 文件扩展名（pdf / txt）
     * @return 提取的纯文本
     */
    public String extractText(MultipartFile file, String extension) {
        return switch (extension.toLowerCase()) {
            case "pdf" -> extractFromPdf(file);
            case "txt" -> extractFromTxt(file);
            default -> throw new IllegalArgumentException("不支持的文件类型: " + extension);
        };
    }

    private String extractFromPdf(MultipartFile file) {
        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            logger.info("PDF 文本提取成功，长度={} 字符", text.length());
            return text;
        } catch (IOException e) {
            logger.error("PDF 文本提取失败", e);
            throw new RuntimeException("PDF 文件解析失败: " + e.getMessage(), e);
        }
    }

    private String extractFromTxt(MultipartFile file) {
        try {
            String text = new String(file.getBytes(), StandardCharsets.UTF_8);
            logger.info("TXT 文本提取成功，长度={} 字符", text.length());
            return text;
        } catch (IOException e) {
            logger.error("TXT 文本提取失败", e);
            throw new RuntimeException("TXT 文件读取失败: " + e.getMessage(), e);
        }
    }

    // ======================== 从文件路径读取（异步场景） ========================

    /**
     * 从已保存的文件路径提取纯文本（用于异步解析，避免临时文件被删除）
     */
    public String extractTextFromFile(String filePath, String extension) {
        return switch (extension.toLowerCase()) {
            case "pdf" -> extractFromPdfFile(filePath);
            case "txt" -> extractFromTxtFile(filePath);
            default -> throw new IllegalArgumentException("不支持的文件类型: " + extension);
        };
    }

    private String extractFromPdfFile(String filePath) {
        try (PDDocument doc = Loader.loadPDF(new File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            logger.info("PDF 文本提取成功（文件路径），长度={} 字符", text.length());
            return text;
        } catch (IOException e) {
            logger.error("PDF 文本提取失败", e);
            throw new RuntimeException("PDF 文件解析失败: " + e.getMessage(), e);
        }
    }

    private String extractFromTxtFile(String filePath) {
        try {
            String text = java.nio.file.Files.readString(java.nio.file.Path.of(filePath), StandardCharsets.UTF_8);
            logger.info("TXT 文本提取成功（文件路径），长度={} 字符", text.length());
            return text;
        } catch (IOException e) {
            logger.error("TXT 文本提取失败", e);
            throw new RuntimeException("TXT 文件读取失败: " + e.getMessage(), e);
        }
    }
}
