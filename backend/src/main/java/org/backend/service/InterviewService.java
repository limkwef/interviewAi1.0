package org.backend.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.backend.entity.AIResultVO;
import org.backend.entity.EndInterviewVO;
import org.backend.entity.InterviewCreateVO;
import org.backend.entity.InterviewHistoryVO;
import org.backend.entity.InterviewMessage;
import org.backend.entity.InterviewReport;
import org.backend.entity.PollStatusVO;
import org.backend.entity.InterviewSession;
import org.backend.entity.Question;
import org.backend.entity.ReportComment;
import org.backend.entity.ReportResultVO;
import org.backend.entity.SendMessageVO;
import org.backend.entity.SessionInfoVO;
import org.backend.entity.StreamMetaVO;
import org.backend.entity.WrongAnswerDTO;
import org.backend.exception.BusinessException;
import org.backend.mapper.InterviewMessageMapper;
import org.backend.mapper.InterviewReportMapper;
import org.backend.mapper.InterviewSessionMapper;
import org.backend.mapper.QuestionMapper;
import org.backend.mapper.ReportCommentMapper;
import org.backend.util.AIService;
import org.backend.util.PromptBuilder;
import org.backend.util.ScoringEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class InterviewService {

    private static final Logger logger = LoggerFactory.getLogger(InterviewService.class);
    private final ObjectMapper objectMapper;

    /** 每道题最多允许 AI 追问的次数，超过则自动跳题 */
    private static final int MAX_FOLLOW_UP_PER_QUESTION = 3;

    private final InterviewSessionMapper sessionMapper;
    private final InterviewMessageMapper messageMapper;
    private final InterviewReportMapper reportMapper;
    private final ReportCommentMapper commentMapper;
    private final QuestionMapper questionMapper;
    private final AIService aiService;
    private final PromptBuilder promptBuilder;
    private final ScoringEngine scoringEngine;
    private final MistakeService mistakeService;
    private final AIDiagnosisService diagnosisService;
    private final InterviewContextService contextService;
    private final TransactionTemplate transactionTemplate;
    private final CacheService cacheService;

    /** 流式消息增量持久化的间隔字符数 */
    private static final int STREAM_PERSIST_INTERVAL = 200;

    public InterviewService(InterviewSessionMapper sessionMapper,
                            InterviewMessageMapper messageMapper,
                            InterviewReportMapper reportMapper,
                            ReportCommentMapper commentMapper,
                            QuestionMapper questionMapper,
                            AIService aiService,
                            PromptBuilder promptBuilder,
                            ScoringEngine scoringEngine,
                            MistakeService mistakeService,
                            AIDiagnosisService diagnosisService,
                            InterviewContextService contextService,
                            ObjectMapper objectMapper,
                            TransactionTemplate transactionTemplate,
                            CacheService cacheService) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.reportMapper = reportMapper;
        this.commentMapper = commentMapper;
        this.questionMapper = questionMapper;
        this.aiService = aiService;
        this.promptBuilder = promptBuilder;
        this.scoringEngine = scoringEngine;
        this.mistakeService = mistakeService;
        this.diagnosisService = diagnosisService;
        this.contextService = contextService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.cacheService = cacheService;
    }

    // ======================== 创建面试 ========================

    @Transactional
    public InterviewCreateVO createInterview(Long userId, String position, String round, String difficulty, int questionCount) {
        // 防止并发重复创建面试
        String lockKey = "interview:create:" + userId;
        if (!cacheService.tryLock(lockKey, 10)) {
            throw new BusinessException(429, "操作太频繁，请稍后重试");
        }
        try {
            return doCreateInterview(userId, position, round, difficulty, questionCount);
        } finally {
            cacheService.unlock(lockKey);
        }
    }

    private InterviewCreateVO doCreateInterview(Long userId, String position, String round, String difficulty, int questionCount) {
        // 1. 分层抽取 + 去重（按难度比例 + 排除近期做过的题）
        List<Question> questions = selectBalancedQuestions(position, questionCount, userId, difficulty);
        if (questions.isEmpty()) {
            throw new BusinessException(400, "题库为空，请联系管理员添加题目");
        }
        int actualCount = Math.min(questions.size(), questionCount);

        // 2. 提取题目 ID 列表，存为 JSON
        List<Long> idList = new ArrayList<>();
        for (int i = 0; i < actualCount; i++) {
            idList.add(questions.get(i).getId());
        }
        String questionIdsJson;
        try {
            questionIdsJson = objectMapper.writeValueAsString(idList);
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "序列化题目列表失败");
        }

        // 3. 创建面试会话
        InterviewSession session = new InterviewSession();
        session.setUserId(userId);
        session.setPosition(position);
        session.setRound(round);
        session.setDifficulty(difficulty);
        session.setQuestionCount(actualCount);
        session.setCurrentQuestion(1);
        session.setQuestionIds(questionIdsJson);
        session.setStatus("in_progress");
        sessionMapper.insert(session);

        // 4. AI 生成开场白 + 第一题
        String greeting = aiService.generateGreeting(position, round, difficulty, actualCount,
                new ArrayList<>(questions.subList(0, actualCount)));

        // 5. 保存第一条 AI 消息
        InterviewMessage firstMessage = new InterviewMessage();
        firstMessage.setSessionId(session.getId());
        firstMessage.setRole("ai");
        firstMessage.setContent(greeting);
        firstMessage.setMessageType("question");
        firstMessage.setQuestionIndex(1);
        messageMapper.insert(firstMessage);

        // 6. 初始化 Redis 上下文
        String systemPrompt = promptBuilder.buildInterviewSystemPrompt(
                position, round, difficulty, actualCount,
                promptBuilder.buildQuestionsListText("technical", new ArrayList<>(questions.subList(0, actualCount))));
        contextService.initContext(session.getId(), systemPrompt);
        contextService.addMessage(session.getId(), "assistant", greeting);

        logger.info("用户{}创建面试会话{}，从题库抽取{}道题", userId, session.getId(), actualCount);

        // 6. 组装返回数据
        InterviewCreateVO data = new InterviewCreateVO();
        data.setId(session.getId());
        data.setPosition(position);
        data.setRound(round);
        data.setDifficulty(difficulty);
        data.setQuestionCount(actualCount);
        data.setCurrentQuestion(1);
        data.setStatus("in_progress");
        data.setCreatedAt(session.getCreatedAt());

        InterviewCreateVO.FirstMessage firstMsg = new InterviewCreateVO.FirstMessage();
        firstMsg.setId(firstMessage.getId());
        firstMsg.setRole("ai");
        firstMsg.setContent(greeting);
        firstMsg.setType("question");
        firstMsg.setCreatedAt(firstMessage.getCreatedAt());
        data.setFirstMessage(firstMsg);

        return data;
    }

    // ======================== 发送消息（普通） ========================

    public SendMessageVO sendMessage(Long sessionId, Long userId, String content) {
        SendMessageContext ctx = prepareSendMessage(sessionId, userId, content);
        InterviewSession session = ctx.getSession();
        Question currentQuestion = ctx.getCurrentQuestion();

        // AI 评估并返回下一题（支持追问）
        AIResultVO aiResult = aiService.evaluateAndRespond(
                session.getPosition(), session.getRound(), session.getDifficulty(),
                session.getCurrentQuestion(), session.getQuestionCount(), content,
                currentQuestion != null ? currentQuestion.getTitle() : "",
                ctx.getAllQuestions(), ctx.getHistory());

        String aiContent = aiResult.getContent();
        String type = aiResult.getType();
        int nextQuestion = aiResult.getNextQuestion();

        // 追问次数限制：同一题最多追问 MAX_FOLLOW_UP_PER_QUESTION 次
        if ("follow_up".equals(type)) {
            int followUpCount = contextService.getFollowUpCount(sessionId, session.getCurrentQuestion());
            if (followUpCount >= MAX_FOLLOW_UP_PER_QUESTION) {
                logger.info("题目{}已追问{}次，强制进入下一题", session.getCurrentQuestion(), followUpCount);
                type = "next_question";
                nextQuestion = session.getCurrentQuestion() + 1;
            } else {
                contextService.incrementFollowUp(sessionId, session.getCurrentQuestion());
            }
        }

        // 保存 AI 消息
        InterviewMessage aiMessage = new InterviewMessage();
        aiMessage.setSessionId(sessionId);
        aiMessage.setRole("ai");
        aiMessage.setContent(aiContent);
        aiMessage.setMessageType(type);
        aiMessage.setQuestionIndex(nextQuestion);
        messageMapper.insert(aiMessage);

        // 保存到 Redis 上下文
        contextService.addMessage(sessionId, "user", content);
        contextService.addMessage(sessionId, "assistant", aiContent);

        // follow_up 不更新 currentQuestion，next_question 才跳题
        if ("next_question".equals(type)) {
            sessionMapper.updateCurrentQuestion(sessionId, nextQuestion);
        }

        SendMessageVO data = new SendMessageVO();
        data.setId(aiMessage.getId());
        data.setRole("ai");
        data.setContent(aiContent);
        data.setType(type);
        data.setNextQuestion(nextQuestion);
        data.setRemainingQuestions(aiResult.getRemainingQuestions());
        data.setCreatedAt(aiMessage.getCreatedAt());
        return data;
    }

    // ======================== 发送消息（流式 SSE） ========================

    public Map<String, Object> sendMessageStream(Long sessionId, Long userId, String content, SseEmitter emitter) {
        SendMessageContext ctx = prepareSendMessage(sessionId, userId, content);
        InterviewSession session = ctx.getSession();
        Question currentQuestion = ctx.getCurrentQuestion();
        String currentQuestionText = currentQuestion != null ? currentQuestion.getTitle() : "";

        List<Question> allQuestions = ctx.getAllQuestions();
        String questionsText = buildQuestionsListText(allQuestions);

        List<Map<String, String>> history = ctx.getHistory();

        int remaining = session.getQuestionCount() - session.getCurrentQuestion();

        // 获取下一题内容
        String nextQuestionText = "";
        if (remaining > 0 && session.getCurrentQuestion() < allQuestions.size()) {
            Question q = allQuestions.get(session.getCurrentQuestion());
            nextQuestionText = q.getTitle() + "\n" + (q.getContent() != null ? q.getContent() : "");
        }

        List<Map<String, String>> aiMessages = new ArrayList<>();
        aiMessages.add(Map.of("role", "system", "content",
                buildStreamSystemPrompt(session.getPosition(), session.getRound(), session.getDifficulty(),
                        session.getQuestionCount(), questionsText)));

        // 插入历史对话
        if (history != null && !history.isEmpty()) {
            aiMessages.addAll(history);
        }

        // 当前用户消息（流式专用 Prompt，使用【决策: xxx】标记）
        String userPrompt = promptBuilder.buildStreamUserPrompt(
                session.getCurrentQuestion(), currentQuestionText,
                content, nextQuestionText, remaining, history);

        aiMessages.add(Map.of("role", "user", "content", userPrompt));

        // 先插入一条空 AI 消息（供轮询降级时前端读取中间内容）
        InterviewMessage aiMessage = new InterviewMessage();
        aiMessage.setSessionId(sessionId);
        aiMessage.setRole("ai");
        aiMessage.setContent("");
        aiMessage.setMessageType("streaming"); // 标记为流式生成中
        aiMessage.setQuestionIndex(session.getCurrentQuestion());
        messageMapper.insert(aiMessage);

        // 在 Redis 中标记该会话正在流式生成
        cacheService.set("interview:streaming:" + sessionId, aiMessage.getId(), 300);

        StringBuilder fullContent = new StringBuilder();
        final int[] persistCounter = {0};

        aiService.chatStream(aiMessages, chunk -> {
            try {
                fullContent.append(chunk);
                emitter.send(SseEmitter.event().name("chunk").data(chunk));

                // 增量持久化：每累积 STREAM_PERSIST_INTERVAL 字符更新一次 DB
                persistCounter[0] += chunk.length();
                if (persistCounter[0] >= STREAM_PERSIST_INTERVAL) {
                    messageMapper.updateContent(aiMessage.getId(), fullContent.toString());
                    persistCounter[0] = 0;
                }
            } catch (Exception e) {
                logger.error("SSE send chunk error", e);
            }
        }, () -> {
            try {
                String rawAiContent = fullContent.toString();

                // 解析决定标记
                String type = parseDecisionType(rawAiContent);
                String aiContent = stripDecisionMarker(rawAiContent);

                // 边界保护
                if ("end".equals(type) && remaining > 0) type = "next_question";
                if (remaining <= 0) type = "end";

                // 追问次数限制：同一题最多追问 MAX_FOLLOW_UP_PER_QUESTION 次
                if ("follow_up".equals(type)) {
                    int followUpCount = contextService.getFollowUpCount(sessionId, session.getCurrentQuestion());
                    if (followUpCount >= MAX_FOLLOW_UP_PER_QUESTION) {
                        logger.info("题目{}已追问{}次，强制进入下一题", session.getCurrentQuestion(), followUpCount);
                        type = "next_question";
                    } else {
                        contextService.incrementFollowUp(sessionId, session.getCurrentQuestion());
                    }
                }

                int nextQuestion;
                int remainingAfter;
                if ("follow_up".equals(type)) {
                    nextQuestion = session.getCurrentQuestion();
                    remainingAfter = remaining;
                } else if ("next_question".equals(type)) {
                    nextQuestion = session.getCurrentQuestion() + 1;
                    remainingAfter = remaining - 1;
                } else {
                    nextQuestion = session.getCurrentQuestion();
                    remainingAfter = 0;
                }

                // 最终更新消息：内容 + 类型
                aiMessage.setContent(aiContent);
                aiMessage.setMessageType(type);
                aiMessage.setQuestionIndex(nextQuestion);
                messageMapper.updateContent(aiMessage.getId(), aiContent);
                // 更新 messageType 和 questionIndex 需要额外 SQL 或复用 updateContent
                messageMapper.updateMessageTypeAndQuestionIndex(aiMessage.getId(), type, nextQuestion);

                // 保存到 Redis 上下文
                contextService.addMessage(sessionId, "user", content);
                contextService.addMessage(sessionId, "assistant", aiContent);

                if ("next_question".equals(type)) {
                    sessionMapper.updateCurrentQuestion(sessionId, nextQuestion);
                }

                // 清除流式生成标记
                cacheService.del("interview:streaming:" + sessionId);

                StreamMetaVO meta = new StreamMetaVO();
                meta.setType(type);
                meta.setNextQuestion(nextQuestion);
                meta.setRemainingQuestions(Math.max(0, remainingAfter));
                meta.setMessageId(aiMessage.getId());
                emitter.send(SseEmitter.event().name("meta").data(meta));
            } catch (Exception e) {
                logger.error("SSE send meta error", e);
            }
        });

        return Map.of("status", "streaming");
    }

    // ======================== 结束面试 ========================

    public EndInterviewVO endInterview(Long sessionId, Long userId) {
        InterviewSession session = sessionMapper.findById(sessionId);
        if (session == null) {
            throw new BusinessException(404, "面试会话不存在");
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问此面试会话");
        }
        if ("completed".equals(session.getStatus())) {
            throw new BusinessException(400, "面试已结束");
        }

        // 1. 更新状态（小事务）
        transactionTemplate.executeWithoutResult(status -> {
            sessionMapper.updateStatus(sessionId, "completed");
        });

        // 2. 获取对话上下文（无事务，纯读取）
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

        // 清理 Redis 上下文
        contextService.clear(sessionId);

        // 获取题目列表
        List<Question> questions = getSessionQuestions(session);

        // 3. AI 调用（无事务，网络 I/O 可能耗时 10-60s）
        ReportResultVO reportData = aiService.generateReport(
                session.getPosition(), session.getRound(), session.getDifficulty(), conversation, questions);

        // 4. 保存报告（小事务）
        return transactionTemplate.execute(status -> saveReportAndRelated(sessionId, userId, session, reportData));
    }

    private EndInterviewVO saveReportAndRelated(Long sessionId, Long userId, InterviewSession session, ReportResultVO reportData) {
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

        // 自动导入错题本：评分 < 60 的题目
        try {
            importWrongAnswers(session, comments, userId);
        } catch (Exception e) {
            logger.error("导入错题本失败", e);
        }

        // 自动生成 AI 深度诊断报告（异步，不阻塞返回）
        try {
            diagnosisService.generateDiagnosisReport(sessionId);
        } catch (Exception e) {
            logger.error("生成诊断报告失败", e);
        }

        logger.info("用户{}结束面试{}，报告{}", userId, sessionId, report.getId());

        EndInterviewVO data = new EndInterviewVO();
        data.setInterviewId(sessionId);
        data.setReportId(report.getId());
        data.setTotalScore(report.getTotalScore());
        data.setLevel(report.getLevel());
        Map<String, Integer> dimensions = new HashMap<>();
        dimensions.put("technical", report.getTechnicalScore());
        dimensions.put("expression", report.getExpressionScore());
        dimensions.put("logic", report.getLogicScore());
        dimensions.put("completeness", report.getCompletenessScore());
        dimensions.put("innovation", report.getInnovationScore());
        data.setDimensions(dimensions);
        data.setComments(comments);
        data.setSuggestion(report.getSuggestion());
        return data;
    }

    // ======================== 查询方法 ========================

    public InterviewHistoryVO getHistory(Long userId, Integer page, Integer pageSize) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("offset", (page - 1) * pageSize);
        params.put("size", pageSize);

        List<InterviewSession> records = sessionMapper.findByUserId(params);
        int total = sessionMapper.countByUserId(params);

        List<InterviewHistoryVO.Item> list = new ArrayList<>();
        for (InterviewSession s : records) {
            InterviewHistoryVO.Item item = new InterviewHistoryVO.Item();
            item.setId(s.getId());
            item.setPosition(s.getPosition());
            item.setRound(s.getRound());
            item.setDifficulty(s.getDifficulty());
            item.setStatus(s.getStatus());
            item.setCreatedAt(s.getCreatedAt());
            // findByUserId 已经 LEFT JOIN report，直接取 reportCreatedAt 计算耗时
            if ("completed".equals(s.getStatus()) && s.getCreatedAt() != null && s.getReportCreatedAt() != null) {
                item.setTotalDurationSeconds((int) java.time.Duration.between(s.getCreatedAt(), s.getReportCreatedAt()).getSeconds());
            }
            list.add(item);
        }

        InterviewHistoryVO data = new InterviewHistoryVO();
        data.setRecords(list);
        data.setTotal(total);
        data.setPage(page);
        data.setPageSize(pageSize);
        return data;
    }

    public List<InterviewMessage> getSessionMessages(Long sessionId, Long userId) {
        InterviewSession session = sessionMapper.findById(sessionId);
        if (session == null) {
            throw new BusinessException(404, "面试会话不存在");
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问此面试会话");
        }
        return messageMapper.findBySessionId(sessionId);
    }

    /**
     * 轮询降级接口：获取会话最新 AI 消息的生成状态
     * 前端 SSE 断开后，通过此接口轮询获取 AI 回复的中间内容
     */
    public PollStatusVO pollStreamStatus(Long sessionId, Long userId) {
        InterviewSession session = sessionMapper.findById(sessionId);
        if (session == null) {
            throw new BusinessException(404, "面试会话不存在");
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问此面试会话");
        }

        InterviewMessage latest = messageMapper.findLatestBySessionId(sessionId);
        PollStatusVO vo = new PollStatusVO();

        if (latest == null || !"ai".equals(latest.getRole())) {
            // 没有 AI 消息，说明还没开始生成
            vo.setStatus("pending");
            return vo;
        }

        vo.setMessageId(latest.getId());
        vo.setRole(latest.getRole());
        vo.setContent(latest.getContent());
        vo.setNextQuestion(latest.getQuestionIndex());

        if ("streaming".equals(latest.getMessageType())) {
            // 仍在生成中
            vo.setStatus("streaming");
            vo.setType(null);
        } else {
            // 生成完成
            vo.setStatus("completed");
            vo.setType(latest.getMessageType());
            int remaining = session.getQuestionCount() - session.getCurrentQuestion();
            vo.setRemainingQuestions(Math.max(0, remaining));
        }

        return vo;
    }

    public SessionInfoVO getSessionInfo(Long sessionId, Long userId) {
        InterviewSession session = sessionMapper.findById(sessionId);
        if (session == null) {
            throw new BusinessException(404, "面试会话不存在");
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问此面试会话");
        }
        SessionInfoVO info = new SessionInfoVO();
        info.setId(session.getId());
        info.setPosition(session.getPosition());
        info.setRound(session.getRound());
        info.setDifficulty(session.getDifficulty());
        info.setQuestionCount(session.getQuestionCount());
        info.setCurrentQuestion(session.getCurrentQuestion());
        info.setStatus(session.getStatus());
        info.setCreatedAt(session.getCreatedAt());
        return info;
    }

    public void abandonInterview(Long sessionId, Long userId) {
        InterviewSession session = sessionMapper.findById(sessionId);
        if (session == null) {
            throw new BusinessException(404, "面试会话不存在");
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问此面试会话");
        }
        int userMsgCount = messageMapper.countUserMessages(sessionId);
        if (userMsgCount > 0) {
            throw new BusinessException(400, "已有回答记录，无法放弃面试");
        }
        messageMapper.deleteBySessionId(sessionId);
        sessionMapper.deleteById(sessionId);
        // 清理 Redis 上下文
        contextService.clear(sessionId);
        logger.info("用户{}放弃面试会话{}", userId, sessionId);
    }

    /**
     * 面试结束后自动导入错题本：评分 < 60 的题目标记为错题
     */
    private void importWrongAnswers(InterviewSession session,
                                     List<ReportResultVO.Comment> comments,
                                     Long userId) {
        if (comments == null || comments.isEmpty()) return;

        // 解析 question_ids 列表
        List<Question> questions = getSessionQuestions(session);
        List<WrongAnswerDTO> wrongAnswers = new ArrayList<>();

        for (int i = 0; i < comments.size(); i++) {
            ReportResultVO.Comment c = comments.get(i);
            int score = c.getScore();
            // 评分低于 60 分视为答错
            if (score < 60) {
                Long questionId = null;
                if (i < questions.size()) {
                    questionId = questions.get(i).getId();
                }
                if (questionId == null) continue;

                WrongAnswerDTO wa = new WrongAnswerDTO();
                wa.setQuestionId(questionId);
                wa.setUserAnswer(c.getUserAnswer());
                wa.setAiComment(c.getComment());
                wa.setCategory(questions.get(i).getCategory());
                wrongAnswers.add(wa);
            }
        }

        if (!wrongAnswers.isEmpty()) {
            mistakeService.batchImport(userId, session.getId(), wrongAnswers);
            logger.info("面试{}自动导入{}条错题", session.getId(), wrongAnswers.size());
        }
    }

    // ======================== 内部方法 ========================

    /**
     * 发送消息前的公共上下文
     */
    @lombok.Data
    private static class SendMessageContext {
        private InterviewSession session;
        private Question currentQuestion;
        private List<Question> allQuestions;
        private List<Map<String, String>> history;
        private Long userId;
    }

    /**
     * 发送消息前的公共准备逻辑（抽取自 sendMessage / sendMessageStream 的重复代码）
     */
    private SendMessageContext prepareSendMessage(Long sessionId, Long userId, String content) {
        InterviewSession session = validateSession(sessionId, userId);
        Question currentQuestion = getCurrentQuestion(session);
        List<Question> allQuestions = getSessionQuestions(session);

        // 保存用户消息
        InterviewMessage userMessage = new InterviewMessage();
        userMessage.setSessionId(sessionId);
        userMessage.setRole("user");
        userMessage.setContent(content);
        userMessage.setMessageType("answer");
        userMessage.setQuestionIndex(session.getCurrentQuestion());
        messageMapper.insert(userMessage);

        List<Map<String, String>> history = loadContext(sessionId, session);

        SendMessageContext ctx = new SendMessageContext();
        ctx.setSession(session);
        ctx.setCurrentQuestion(currentQuestion);
        ctx.setAllQuestions(allQuestions);
        ctx.setHistory(history);
        ctx.setUserId(userId);
        return ctx;
    }

    /**
     * 校验面试会话（抽取公共校验逻辑）
     */
    private InterviewSession validateSession(Long sessionId, Long userId) {
        InterviewSession session = sessionMapper.findById(sessionId);
        if (session == null) throw new BusinessException(404, "面试会话不存在");
        if (!session.getUserId().equals(userId)) throw new BusinessException(403, "无权访问此面试会话");
        if ("completed".equals(session.getStatus())) throw new BusinessException(400, "面试已结束");
        return session;
    }

    /**
     * 加载对话上下文（两级降级）
     * 第 1 层：Redis（快）
     * 第 2 层：MySQL（Redis 挂了自动降级）
     */
    private List<Map<String, String>> loadContext(Long sessionId, InterviewSession session) {
        // 第 1 层：Redis
        List<Map<String, String>> context = contextService.getContext(sessionId);
        if (!context.isEmpty()) return context;

        // 第 2 层：从 MySQL 重建
        logger.warn("Redis 未命中，从 MySQL 重建会话 {} 的上下文", sessionId);
        return buildConversationHistory(sessionId);
    }

    /**
     * 获取面试会话中当前正在回答的题目
     */
    private Question getCurrentQuestion(InterviewSession session) {
        List<Question> questions = getSessionQuestions(session);
        int index = session.getCurrentQuestion() - 1; // 1-based → 0-based
        if (index >= 0 && index < questions.size()) {
            return questions.get(index);
        }
        return null;
    }

    /**
     * 从会话的 question_ids 解析出完整的 Question 列表
     */
    private List<Question> getSessionQuestions(InterviewSession session) {
        String questionIdsJson = session.getQuestionIds();
        if (questionIdsJson == null || questionIdsJson.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<Long> ids = objectMapper.readValue(questionIdsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class));
            if (ids.isEmpty()) {
                return new ArrayList<>();
            }
            List<Question> questions = questionMapper.findByIds(ids);
            // 按 ids 的原始顺序排列（SQL IN 不保证顺序）
            Map<Long, Question> questionMap = new HashMap<>();
            for (Question q : questions) {
                questionMap.put(q.getId(), q);
            }
            List<Question> result = new ArrayList<>();
            for (Long id : ids) {
                Question q = questionMap.get(id);
                if (q != null) {
                    result.add(q);
                }
            }
            return result;
        } catch (Exception e) {
            logger.error("解析 questionIds 失败: {}", questionIdsJson, e);
            return new ArrayList<>();
        }
    }

    /**
     * 构建对话历史列表，用于传给 AI 作为上下文
     */
    private List<Map<String, String>> buildConversationHistory(Long sessionId) {
        List<InterviewMessage> dbMessages = messageMapper.findBySessionId(sessionId);
        List<Map<String, String>> history = new ArrayList<>();
        for (InterviewMessage msg : dbMessages) {
            Map<String, String> entry = new HashMap<>();
            entry.put("role", "ai".equals(msg.getRole()) ? "assistant" : "user");
            entry.put("content", msg.getContent());
            entry.put("type", msg.getMessageType() != null ? msg.getMessageType() : "");
            history.add(entry);
        }
        return history;
    }

    /**
     * 解析 AI 回复中的决定标记（委托 ScoringEngine）
     */
    private String parseDecisionType(String response) {
        return scoringEngine.parseDecisionType(response);
    }

    /**
     * 去除 AI 回复末尾的决定标记（委托 ScoringEngine）
     */
    private String stripDecisionMarker(String response) {
        return scoringEngine.stripDecisionMarker(response);
    }

    /**
     * 获取历史中最后一条消息的类型（委托 ScoringEngine 中的 PromptBuilder 逻辑）
     */
    private String getLastHistoryType(List<Map<String, String>> history) {
        return promptBuilder.getLastType(history);
    }

    /**
     * 构建题目列表文本（委托 PromptBuilder）
     */
    private String buildQuestionsListText(List<Question> questions) {
        return promptBuilder.buildQuestionsListText("technical", questions);
    }

    private String buildStreamSystemPrompt(String position, String round, String difficulty, int questionCount, String questionsText) {
        return promptBuilder.buildInterviewSystemPrompt(position, round, difficulty, questionCount, questionsText);
    }

    private String getPositionName(String position) {
        return promptBuilder.getPositionName(position);
    }

    private String getDifficultyName(String difficulty) {
        return promptBuilder.getDifficultyName(difficulty);
    }

    private String getRoundName(String round) {
        return promptBuilder.getRoundName(round);
    }

    // ======================== 分层抽题 + 去重 ========================

    /**
     * 按难度分层 + 排除近期题 的智能抽题策略
     * 1. 根据会话难度计算 easy/medium/hard 分配比例
     * 2. 每层从题库中定向抽取，同时过滤掉用户近期做过的题
     * 3. 某层不够时从相邻层补足
     * 4. 整体不够时取消方向限制兜底
     * 5. 抽取结果写入 Redis（供后续面试去重）
     */
    private List<Question> selectBalancedQuestions(String direction, int total, Long userId, String sessionDifficulty) {
        // 1. 计算各难度分配
        Map<String, Integer> dist = calculateDifficultyDistribution(sessionDifficulty, total);

        // 2. 获取用户近期做过的题（去重）
        Set<Long> recentIds = contextService.getRecentQuestionIds(userId);

        // 3. 按难度分层抽取，逐层过滤近期题
        List<Question> result = new ArrayList<>();
        Map<String, Integer> deficit = new HashMap<>();  // 记录各层缺口

        for (String diff : List.of("easy", "medium", "hard")) {
            int need = dist.getOrDefault(diff, 0);
            if (need <= 0) continue;
            // 多取一些作为去重缓冲（最多多取20条）
            List<Question> layerQuestions = questionMapper.findRandomByDirectionAndDifficulty(
                    direction, diff, need + Math.min(recentIds.size(), 20));
            layerQuestions.removeIf(q -> recentIds.contains(q.getId()));
            int take = Math.min(need, layerQuestions.size());
            result.addAll(new ArrayList<>(layerQuestions.subList(0, take)));
            if (take < need) {
                deficit.put(diff, need - take);
            }
        }

        // 4. 某层因去重不够的，从其他层补足（尽量从高分值层补）
        if (!deficit.isEmpty()) {
            int totalDeficit = deficit.values().stream().mapToInt(Integer::intValue).sum();
            Set<Long> usedIds = result.stream().map(Question::getId).collect(Collectors.toSet());
            usedIds.addAll(recentIds);

            for (String diff : List.of("medium", "easy", "hard")) {
                if (totalDeficit <= 0) break;
                int need = totalDeficit + 3; // 稍微多取
                List<Question> extra = questionMapper.findRandomByDirectionAndDifficulty(direction, diff, need);
                extra.removeIf(q -> usedIds.contains(q.getId()));
                int take = Math.min(totalDeficit, extra.size());
                result.addAll(new ArrayList<>(extra.subList(0, take)));
                totalDeficit -= take;
            }
        }

        // 5. 仍然不够总题数 → 取消方向限制，从全部题目中补足
        if (result.size() < total) {
            int need = total - result.size();
            Set<Long> usedIds = result.stream().map(Question::getId).collect(Collectors.toSet());
            usedIds.addAll(recentIds);
            List<Long> excludeList = new ArrayList<>(usedIds);
            if (excludeList.size() > 100) excludeList = new ArrayList<>(excludeList.subList(0, 100));

            // 优先尝试排除查询（保留方向限制）
            List<Question> supplement = questionMapper.findRandomWithExclusion(direction, need + 5, excludeList);
            if (supplement.size() < need) {
                // 仍不够 → 不限方向
                supplement = questionMapper.findRandomByDirection(null, need + 10);
                supplement.removeIf(q -> usedIds.contains(q.getId()));
            }
            int take = Math.min(need, supplement.size());
            result.addAll(new ArrayList<>(supplement.subList(0, take)));
        }

        // 6. 记入 Redis（供后续面试去重）
        contextService.addRecentQuestionIds(userId,
                result.stream().map(Question::getId).collect(Collectors.toList()));

        // 7. 打乱顺序，让难易题交错出现
        Collections.shuffle(result);
        return result;
    }

    /**
     * 根据会话难度计算 easy/medium/hard 三层分配数量
     * 确保总和 = total 且每层 >= 0
     */
    private Map<String, Integer> calculateDifficultyDistribution(String sessionDifficulty, int total) {
        int easy, medium, hard;
        switch (sessionDifficulty != null ? sessionDifficulty : "medium") {
            case "easy":
                easy = (int) Math.round(total * 0.6);
                medium = (int) Math.round(total * 0.3);
                hard = total - easy - medium;
                break;
            case "hard":
                easy = (int) Math.round(total * 0.1);
                medium = (int) Math.round(total * 0.3);
                hard = total - easy - medium;
                break;
            default: // medium
                easy = (int) Math.round(total * 0.2);
                medium = (int) Math.round(total * 0.6);
                hard = total - easy - medium;
                break;
        }
        // 边界修正：确保每层 >= 0 且总和 = total
        if (hard < 0) { medium += hard; hard = 0; }
        if (medium < 0) { easy += medium; medium = 0; }
        if (easy < 0) easy = 0;
        int sum = easy + medium + hard;
        if (sum < total) easy += (total - sum);

        Map<String, Integer> dist = new HashMap<>();
        dist.put("easy", easy);
        dist.put("medium", medium);
        dist.put("hard", hard);
        return dist;
    }

}
