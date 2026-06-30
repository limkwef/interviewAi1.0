package org.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.backend.entity.*;
import org.backend.vo.DiagnosisDataVO;
import org.backend.exception.BusinessException;
import org.backend.mapper.*;
import org.backend.util.AIService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AIDiagnosisService {

    private static final Logger logger = LoggerFactory.getLogger(AIDiagnosisService.class);

    @Autowired
    private DiagnosisReportMapper diagnosisReportMapper;

    @Autowired
    private InterviewReportMapper interviewReportMapper;

    @Autowired
    private ReportCommentMapper reportCommentMapper;

    @Autowired
    private InterviewMessageMapper interviewMessageMapper;

    @Autowired
    private InterviewSessionMapper interviewSessionMapper;

    @Autowired
    private AIService aiService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    /**
     * 面试结束后生成 AI 深度诊断报告
     *
     * 注意：不使用 @Transactional 注解，因为方法内包含 DeepSeek API 外部 HTTP 调用（耗时 10-60 秒），
     * 如果包裹在事务中会长时间占用数据库连接，导致连接池耗尽。
     * 仅在最终写入数据库时使用事务。
     */
    @CacheEvict(cacheNames = "diagnosis", allEntries = true)
    public DiagnosisReport generateDiagnosisReport(Long sessionId) {
        // 0. 幂等：已生成过诊断报告且数据有效则直接返回
        DiagnosisReport existing = diagnosisReportMapper.findBySessionId(sessionId);
        if (existing != null && existing.getTotalScore() != null && existing.getTotalScore() > 0
                && existing.getKnowledgeAnalysis() != null && !existing.getKnowledgeAnalysis().isEmpty()) {
            logger.info("面试{}的诊断报告已存在且有效，直接返回", sessionId);
            return existing;
        }
        // 如果存在但数据无效（如之前生成失败留下的空记录），删除后重新生成
        if (existing != null) {
            logger.warn("面试{}的诊断报告数据无效（totalScore={}），删除后重新生成", sessionId, existing.getTotalScore());
            diagnosisReportMapper.deleteById(existing.getId());
        }

        // 1. 获取基础数据（无事务，纯读取）
        InterviewSession session = interviewSessionMapper.findById(sessionId);
        if (session == null) {
            throw new BusinessException(404, "面试会话不存在");
        }
        InterviewReport report = interviewReportMapper.findBySessionId(sessionId);
        if (report == null) {
            throw new BusinessException(400, "未找到评分报告，请先生成评分报告");
        }

        // 获取上一次的诊断报告（用于计算分数变化）
        DiagnosisReport previousDiagnosis = diagnosisReportMapper.findLatestByUserId(session.getUserId());
        Integer previousScore = previousDiagnosis != null ? previousDiagnosis.getTotalScore() : 0;

        // 获取逐题点评
        List<ReportComment> comments = reportCommentMapper.findByReportId(report.getId());

        // 获取面试对话记录
        List<InterviewMessage> messages = interviewMessageMapper.findBySessionId(sessionId);

        // 2. 使用 DeepSeek 生成诊断报告（外部 HTTP 调用，在事务外执行）
        DiagnosisDataVO diagnosisData;
        try {
            diagnosisData = aiService.generateDiagnosisReport(
                    report, comments, messages, session.getPosition(),
                    previousScore, session.getRound()
            );
        } catch (Exception e) {
            logger.error("AI 诊断报告生成失败，sessionId={}", sessionId, e);
            throw new BusinessException(500, "AI 诊断报告生成失败：" + e.getMessage());
        }

        if (diagnosisData == null) {
            throw new BusinessException(500, "AI 服务返回为空，无法生成诊断报告");
        }

        // 3. 组装 DiagnosisReport
        DiagnosisReport diagnosis = new DiagnosisReport();
        diagnosis.setUserId(session.getUserId());
        diagnosis.setSessionId(sessionId);
        diagnosis.setReportId(report.getId());
        diagnosis.setTotalScore(report.getTotalScore());
        diagnosis.setLevel(report.getLevel());
        diagnosis.setPreviousScore(previousScore);
        diagnosis.setScoreChange(report.getTotalScore() - previousScore);
        diagnosis.setCreatedAt(LocalDateTime.now());

        // JSON 字段
        try {
            diagnosis.setKnowledgeAnalysis(toJson(diagnosisData.getKnowledgeAnalysis()));
            diagnosis.setThinkingAnalysis(toJson(diagnosisData.getThinkingAnalysis()));
            diagnosis.setMistakePatterns(toJson(diagnosisData.getMistakePatterns()));
            diagnosis.setLearningPlan(toJson(diagnosisData.getLearningPlan()));
            diagnosis.setDetailedComments(toJson(diagnosisData.getDetailedComments()));
        } catch (Exception e) {
            logger.error("诊断报告 JSON 序列化失败，sessionId={}", sessionId, e);
            throw new BusinessException(500, "诊断报告数据处理失败");
        }

        // 4. 存入数据库（仅写操作使用事务）
        DiagnosisReport finalDiagnosis = diagnosis;
        transactionTemplate.executeWithoutResult(status -> {
            // 再次检查幂等（防止并发重复生成）
            DiagnosisReport concurrent = diagnosisReportMapper.findBySessionId(sessionId);
            if (concurrent != null) {
                logger.info("并发检测：面试{}的诊断报告已被其他线程生成，跳过", sessionId);
                // 用已存在的覆盖，后续直接返回
                finalDiagnosis.setId(concurrent.getId());
                return;
            }
            diagnosisReportMapper.insert(finalDiagnosis);
        });

        // 如果是并发场景下已存在的，重新查询返回
        if (diagnosis.getId() == null || diagnosis.getId() == 0L) {
            return diagnosisReportMapper.findBySessionId(sessionId);
        }

        return diagnosis;
    }

    /**
     * 校验当前用户是否有权操作该面试会话
     */
    public void validateSessionOwnership(Long sessionId, Long userId) {
        InterviewSession session = interviewSessionMapper.findById(sessionId);
        if (session == null) {
            throw new BusinessException(404, "面试会话不存在");
        }
        if (session.getUserId() == null) {
            logger.error("面试会话{}的 userId 为空，数据异常", sessionId);
            throw new BusinessException(500, "面试会话数据异常，请联系管理员");
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作此面试会话");
        }
    }

    /**
     * 根据 ID 查询诊断报告
     */
    @Cacheable(cacheNames = "diagnosis", key = "'detail:' + #id", unless = "#result == null")
    public DiagnosisReport findById(Long id) {
        return diagnosisReportMapper.findById(id);
    }

    /**
     * 根据面试ID查询诊断报告
     */
    @Cacheable(cacheNames = "diagnosis", key = "'session:' + #sessionId", unless = "#result == null")
    public DiagnosisReport findBySessionId(Long sessionId) {
        return diagnosisReportMapper.findBySessionId(sessionId);
    }

    /**
     * 根据报告ID查询诊断报告
     */
    public DiagnosisReport findByReportId(Long reportId) {
        return diagnosisReportMapper.findByReportId(reportId);
    }

    /**
     * 获取用户的诊断报告历史
     */
    public Map<String, Object> getDiagnosisHistory(Long userId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<DiagnosisReport> reports = diagnosisReportMapper.findByUserId(userId, offset, pageSize);
        int total = diagnosisReportMapper.countByUserId(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("records", reports);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    /**
     * 获取最新的诊断报告
     */
    public DiagnosisReport getLatestDiagnosis(Long userId) {
        return diagnosisReportMapper.findLatestByUserId(userId);
    }

    private String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }
}
