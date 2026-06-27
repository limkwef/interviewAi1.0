package org.backend.service;

import org.backend.entity.*;
import org.backend.mapper.*;
import org.backend.util.AIService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

/**
 * 面试评估服务：AI 评估 + 报告生成 + 错题导入
 * 从 InterviewService 中拆出，职责单一。
 */
@Service
public class InterviewEvaluateService {

    private static final Logger logger = LoggerFactory.getLogger(InterviewEvaluateService.class);

    private final InterviewSessionMapper sessionMapper;
    private final InterviewReportMapper reportMapper;
    private final ReportCommentMapper commentMapper;
    private final InterviewMessageMapper messageMapper;
    private final QuestionMapper questionMapper;
    private final AIService aiService;
    private final MistakeService mistakeService;
    private final AIDiagnosisService diagnosisService;
    private final InterviewContextService contextService;
    private final TransactionTemplate transactionTemplate;

    public InterviewEvaluateService(InterviewSessionMapper sessionMapper,
                                     InterviewReportMapper reportMapper,
                                     ReportCommentMapper commentMapper,
                                     InterviewMessageMapper messageMapper,
                                     QuestionMapper questionMapper,
                                     AIService aiService,
                                     MistakeService mistakeService,
                                     AIDiagnosisService diagnosisService,
                                     InterviewContextService contextService,
                                     TransactionTemplate transactionTemplate) {
        this.sessionMapper = sessionMapper;
        this.reportMapper = reportMapper;
        this.commentMapper = commentMapper;
        this.messageMapper = messageMapper;
        this.questionMapper = questionMapper;
        this.aiService = aiService;
        this.mistakeService = mistakeService;
        this.diagnosisService = diagnosisService;
        this.contextService = contextService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 执行 AI 评估（异步调用）
     */
    public void doEvaluate(Long sessionId, Long userId, InterviewSession session) {
        List<Map<String, String>> conversation = contextService.getContext(sessionId);
        if (conversation.isEmpty()) {
            List<InterviewMessage> messages = messageMapper.findBySessionId(sessionId);
            for (InterviewMessage msg : messages) {
                Map<String, String> entry = new HashMap<>();
                entry.put("role", msg.getRole());
                entry.put("content", msg.getContent());
                conversation.add(entry);
            }
        }

        // 只清理 Redis 上下文，不重复持久化到 MySQL（消息在面试过程中已实时保存）
        contextService.clear(sessionId);

        if (!hasMeaningfulAnswer(conversation)) {
            logger.info("面试{}无有效回答，跳过AI评估，不生成报告", sessionId);
            session.setEvaluateStatus(null);
            session.setEvaluateError(null);
            sessionMapper.updateEvaluateStatus(session);
            contextService.updateCachedSession(sessionId, "evaluateStatus", null);
            contextService.updateCachedSession(sessionId, "evaluateError", null);
            return;
        }

        List<Question> questions = getSessionQuestions(session);
        ReportResultVO reportData = aiService.generateReport(
                session.getPosition(), session.getRound(), session.getDifficulty(), conversation, questions);

        transactionTemplate.executeWithoutResult(status -> saveReportAndRelated(sessionId, userId, session, reportData));
    }

    private void saveReportAndRelated(Long sessionId, Long userId, InterviewSession session, ReportResultVO reportData) {
        InterviewReport report = new InterviewReport();
        report.setSessionId(sessionId);
        report.setUserId(userId);
        report.setTotalScore(reportData.getTotalScore());
        report.setLevel(reportData.getLevel());
        report.setTechnicalScore(reportData.getTechnicalScore());
        report.setExpressionScore(reportData.getExpressionScore());
        report.setLogicScore(reportData.getLogicScore());
        report.setCompletenessScore(reportData.getCompletenessScore());
        report.setInnovationScore(reportData.getInnovationScore());
        report.setSuggestion(reportData.getSuggestion());
        reportMapper.insert(report);

        session.setReportId(report.getId());
        sessionMapper.updateEvaluateStatus(session);

        List<ReportResultVO.Comment> comments = reportData.getComments();
        if (comments != null) {
            for (ReportResultVO.Comment c : comments) {
                ReportComment comment = new ReportComment();
                comment.setReportId(report.getId());
                comment.setQuestionText(c.getQuestionText());
                comment.setUserAnswer(c.getUserAnswer());
                comment.setScore(c.getScore());
                comment.setComment(c.getComment());
                comment.setSortOrder(c.getSortOrder());
                commentMapper.insert(comment);
            }
        }

        try {
            importWrongAnswers(session, comments, userId);
        } catch (Exception e) {
            logger.error("导入错题本失败", e);
        }

        try {
            diagnosisService.generateDiagnosisReport(sessionId);
        } catch (Exception e) {
            logger.error("生成诊断报告失败", e);
        }

        logger.info("用户{}结束面试{}，报告{}", userId, sessionId, report.getId());
    }

    private boolean hasMeaningfulAnswer(List<Map<String, String>> conversation) {
        for (Map<String, String> msg : conversation) {
            if (!"user".equals(msg.get("role"))) continue;
            String content = msg.getOrDefault("content", "").trim();
            if (content.isEmpty()) continue;
            if (content.length() >= 15) return true;
            if (content.length() < 3) continue;
            String lower = content.toLowerCase();
            boolean isGiveUp = false;
            for (String pattern : new String[]{"不知道", "不会", "没学过", "不清楚", "忘了", "不懂", "跳过", "下一题"}) {
                if (lower.contains(pattern)) { isGiveUp = true; break; }
            }
            if (!isGiveUp) return true;
        }
        return false;
    }

    private void importWrongAnswers(InterviewSession session, List<ReportResultVO.Comment> comments, Long userId) {
        if (comments == null || comments.isEmpty()) return;
        List<Question> questions = getSessionQuestions(session);
        if (questions.isEmpty()) return;
        List<WrongAnswerDTO> wrongAnswers = new ArrayList<>();

        for (int i = 0; i < comments.size(); i++) {
            ReportResultVO.Comment c = comments.get(i);
            if (c.getScore() < 60) {
                // 使用 sortOrder 映射到题目索引（0-based），如果 sortOrder 为空则使用循环索引
                int questionIdx = (c.getSortOrder() != null) ? c.getSortOrder() : i;
                // 确保索引在有效范围内
                if (questionIdx < 0 || questionIdx >= questions.size()) {
                    questionIdx = Math.min(i, questions.size() - 1);
                }
                Question question = questions.get(questionIdx);
                WrongAnswerDTO wa = new WrongAnswerDTO();
                wa.setQuestionId(question.getId());
                wa.setUserAnswer(c.getUserAnswer());
                wa.setAiComment(c.getComment());
                wa.setCategory(question.getCategory());
                wrongAnswers.add(wa);
            }
        }

        if (!wrongAnswers.isEmpty()) {
            mistakeService.batchImport(userId, session.getId(), wrongAnswers);
            logger.info("面试{}自动导入{}条错题", session.getId(), wrongAnswers.size());
        }
    }

    private List<Question> getSessionQuestions(InterviewSession session) {
        String questionIdsJson = session.getQuestionIds();
        if (questionIdsJson == null || questionIdsJson.isEmpty() || "[]".equals(questionIdsJson)) {
            return new ArrayList<>();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            List<Long> ids = om.readValue(questionIdsJson,
                    om.getTypeFactory().constructCollectionType(List.class, Long.class));
            if (ids.isEmpty()) return new ArrayList<>();
            List<Question> questions = questionMapper.findByIds(ids);
            Map<Long, Question> questionMap = new HashMap<>();
            for (Question q : questions) questionMap.put(q.getId(), q);
            List<Question> result = new ArrayList<>();
            for (Long id : ids) {
                Question q = questionMap.get(id);
                if (q != null) result.add(q);
            }
            return result;
        } catch (Exception e) {
            logger.error("解析 questionIds 失败: {}", questionIdsJson, e);
            return new ArrayList<>();
        }
    }
}
