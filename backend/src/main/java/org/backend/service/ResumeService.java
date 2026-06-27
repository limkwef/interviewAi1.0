package org.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.backend.entity.Resume;
import org.backend.entity.ResumeData;
import org.backend.exception.BusinessException;
import org.backend.mapper.ResumeMapper;
import org.backend.util.AIService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 简历业务服务
 */
@Service
public class ResumeService {

    private static final Logger logger = LoggerFactory.getLogger(ResumeService.class);

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final String[] ALLOWED_EXTENSIONS = {"pdf", "txt"};

    private final ResumeMapper resumeMapper;
    private final FileExtractService fileExtractService;
    private final AIService aiService;
    private final ObjectMapper objectMapper;

    @Value("${file.upload-dir:./uploads/resumes}")
    private String uploadDir;

    private String getAbsoluteUploadDir() {
        File dir = new File(uploadDir);
        if (!dir.isAbsolute()) {
            dir = new File(System.getProperty("user.dir"), uploadDir);
        }
        return dir.getAbsolutePath();
    }

    private final ExecutorService parseExecutor = Executors.newFixedThreadPool(2);

    public ResumeService(ResumeMapper resumeMapper, FileExtractService fileExtractService,
                         AIService aiService, ObjectMapper objectMapper) {
        this.resumeMapper = resumeMapper;
        this.fileExtractService = fileExtractService;
        this.aiService = aiService;
        this.objectMapper = objectMapper;
    }

    /**
     * 上传简历文件
     */
    public Resume uploadResume(Long userId, MultipartFile file) {
        // 1. 校验文件
        validateFile(file);

        String originalName = file.getOriginalFilename();
        String extension = getExtension(originalName);

        // 2. 存储文件
        String storedName = UUID.randomUUID() + "." + extension;
        String absUploadDir = getAbsoluteUploadDir();
        String userDir = absUploadDir + File.separator + userId;
        File dir = new File(userDir);
        if (!dir.exists()) dir.mkdirs();

        File dest = new File(dir, storedName);
        try {
            file.transferTo(dest);
        } catch (IOException e) {
            throw new BusinessException(500, "文件保存失败: " + e.getMessage());
        }

        // 3. 计算版本号
        int version = resumeMapper.countByUserIdAndSource(userId, "upload") + 1;

        // 4. 创建简历记录（状态=解析中）
        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setVersion(version);
        resume.setSource("upload");
        resume.setStatus(0); // 解析中
        resume.setFileName(originalName);
        resume.setFileUrl(dest.getAbsolutePath());
        resume.setFileSize(file.getSize());
        resume.setIsActive(0);
        resumeMapper.insert(resume);

        // 5. 异步提取文本 + AI 解析
        final Long resumeId = resume.getId();
        final String filePath = dest.getAbsolutePath();
        final String ext = extension;
        CompletableFuture.runAsync(() -> {
            try {
                // 从已保存的文件提取文本（而非原始临时文件）
                String rawText = fileExtractService.extractTextFromFile(filePath, ext);
                resumeMapper.updateRawText(resumeId, rawText);

                // AI 解析
                Map<String, Object> parsedMap = aiService.parseResume(rawText);
                if (parsedMap != null) {
                    String json = objectMapper.writeValueAsString(parsedMap);
                    resumeMapper.updateParsedData(resumeId, json);
                    logger.info("简历{}解析完成", resumeId);
                } else {
                    resumeMapper.updateStatus(resumeId, 2, "AI 解析失败，请重试");
                    logger.warn("简历{}AI解析失败", resumeId);
                }
            } catch (Exception e) {
                logger.error("简历{}解析异常", resumeId, e);
                resumeMapper.updateStatus(resumeId, 2, "解析异常: " + e.getMessage());
            }
        }, parseExecutor);

        return resumeMapper.findById(resumeId);
    }

    /**
     * 在线填写创建简历
     */
    @Transactional
    public Resume createFromTemplate(Long userId, ResumeData data) {
        String parsedJson;
        try {
            parsedJson = objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "数据序列化失败");
        }

        int version = resumeMapper.countByUserIdAndSource(userId, "template") + 1;

        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setVersion(version);
        resume.setSource("template");
        resume.setStatus(1); // 直接完成
        resume.setParsedData(parsedJson);
        resume.setIsActive(0);
        resumeMapper.insert(resume);

        // 自动激活（第一条简历时）
        Resume activeResume = resumeMapper.findActiveByUserId(userId);
        if (activeResume == null) {
            resumeMapper.deactivateAll(userId);
            resumeMapper.activateById(resume.getId());
        }

        return resumeMapper.findById(resume.getId());
    }

    /**
     * 查询简历列表
     */
    public Map<String, Object> getResumeList(Long userId, int page, int size) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("offset", (page - 1) * size);
        params.put("size", size);

        List<Resume> records = resumeMapper.findByUserId(params);
        int total = resumeMapper.countByUserId(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", size);
        return result;
    }

    /**
     * 获取简历详情
     */
    public Resume getResumeDetail(Long userId, Long resumeId) {
        Resume resume = resumeMapper.findById(resumeId);
        if (resume == null) {
            throw new BusinessException(404, "简历不存在");
        }
        if (!resume.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问此简历");
        }
        return resume;
    }

    /**
     * 激活简历（设为当前生效）
     */
    @Transactional
    public void activateResume(Long userId, Long resumeId) {
        Resume resume = resumeMapper.findById(resumeId);
        if (resume == null) throw new BusinessException(404, "简历不存在");
        if (!resume.getUserId().equals(userId)) throw new BusinessException(403, "无权操作");
        if (resume.getStatus() != 1) throw new BusinessException(400, "简历未解析完成，无法激活");

        resumeMapper.deactivateAll(userId);
        resumeMapper.activateById(resumeId);
        logger.info("用户{}激活简历{}", userId, resumeId);
    }

    /**
     * 删除简历
     */
    @Transactional
    public void deleteResume(Long userId, Long resumeId) {
        Resume resume = resumeMapper.findById(resumeId);
        if (resume == null) throw new BusinessException(404, "简历不存在");
        if (!resume.getUserId().equals(userId)) throw new BusinessException(403, "无权操作");
        if (resume.getIsActive() == 1) throw new BusinessException(400, "当前简历正在使用中，请先切换到其他简历再删除");

        // 删除文件
        if (resume.getFileUrl() != null) {
            File file = new File(resume.getFileUrl());
            if (file.exists()) file.delete();
        }

        resumeMapper.deleteById(resumeId);
        logger.info("用户{}删除简历{}", userId, resumeId);
    }

    /**
     * 获取解析状态（供前端轮询）
     */
    public Map<String, Object> getParseStatus(Long userId, Long resumeId) {
        Resume resume = resumeMapper.findById(resumeId);
        if (resume == null) throw new BusinessException(404, "简历不存在");
        if (!resume.getUserId().equals(userId)) throw new BusinessException(403, "无权访问");

        Map<String, Object> result = new HashMap<>();
        result.put("status", resume.getStatus());
        result.put("errorMsg", resume.getErrorMsg());
        if (resume.getStatus() == 1) {
            result.put("parsedData", resume.getParsedData());
        }
        return result;
    }

    /**
     * 重新解析简历
     */
    public void reparseResume(Long userId, Long resumeId) {
        Resume resume = resumeMapper.findById(resumeId);
        if (resume == null) throw new BusinessException(404, "简历不存在");
        if (!resume.getUserId().equals(userId)) throw new BusinessException(403, "无权操作");
        if (resume.getRawText() == null || resume.getRawText().isBlank()) {
            throw new BusinessException(400, "无原始文本，无法重新解析");
        }

        resumeMapper.updateStatus(resumeId, 0, null); // 重置为解析中

        final String rawText = resume.getRawText();
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> parsedMap = aiService.parseResume(rawText);
                if (parsedMap != null) {
                    String json = objectMapper.writeValueAsString(parsedMap);
                    resumeMapper.updateParsedData(resumeId, json);
                    logger.info("简历{}重新解析完成", resumeId);
                } else {
                    resumeMapper.updateStatus(resumeId, 2, "AI 解析失败，请重试");
                }
            } catch (Exception e) {
                logger.error("简历{}重新解析异常", resumeId, e);
                resumeMapper.updateStatus(resumeId, 2, "解析异常: " + e.getMessage());
            }
        }, parseExecutor);
    }

    // ======================== 内部方法 ========================

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择要上传的文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(400, "文件大小不能超过 5MB");
        }
        String ext = getExtension(file.getOriginalFilename());
        boolean allowed = false;
        for (String e : ALLOWED_EXTENSIONS) {
            if (e.equalsIgnoreCase(ext)) { allowed = true; break; }
        }
        if (!allowed) {
            throw new BusinessException(400, "仅支持 PDF 和 TXT 格式");
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}
