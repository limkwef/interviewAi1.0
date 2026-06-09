package org.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.backend.entity.*;
import org.backend.mapper.*;
import org.backend.util.AIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AIDiagnosisService {

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

    /**
     * 面试结束后生成 AI 深度诊断报告
     */
    @Transactional
    public DiagnosisReport generateDiagnosisReport(Long sessionId) {
        // 1. 获取基础数据
        InterviewSession session = interviewSessionMapper.findById(sessionId);
        InterviewReport report = interviewReportMapper.findBySessionId(sessionId);
        if (report == null) {
            throw new RuntimeException("未找到评分报告，请先生成评分报告");
        }

        // 获取上一次的诊断报告（用于计算分数变化）
        DiagnosisReport previousDiagnosis = diagnosisReportMapper.findLatestByUserId(session.getUserId());
        Integer previousScore = previousDiagnosis != null ? previousDiagnosis.getTotalScore() : 0;

        // 获取逐题点评
        List<ReportComment> comments = reportCommentMapper.findByReportId(report.getId());

        // 获取面试对话记录
        List<InterviewMessage> messages = interviewMessageMapper.findBySessionId(sessionId);

        // 2. 使用 DeepSeek 生成诊断报告
        DiagnosisDataVO diagnosisData = aiService.generateDiagnosisReport(
                report, comments, messages, session.getPosition(),
                previousScore, session.getRound()
        );

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
            throw new RuntimeException("JSON 序列化失败", e);
        }

        // 4. 存入数据库
        diagnosisReportMapper.insert(diagnosis);

        return diagnosis;
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
