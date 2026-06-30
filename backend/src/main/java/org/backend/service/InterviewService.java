package org.backend.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.backend.vo.AIResultVO;
import org.backend.util.PositionConstants;
import org.backend.vo.EndInterviewVO;
import org.backend.vo.InterviewCreateVO;
import org.backend.vo.InterviewHistoryVO;
import org.backend.entity.InterviewMessage;
import org.backend.entity.InterviewReport;
import org.backend.vo.PollStatusVO;
import org.backend.entity.InterviewSession;
import org.backend.entity.Question;
import org.backend.entity.ReportComment;
import org.backend.vo.ReportResultVO;
import org.backend.entity.Resume;
import org.backend.vo.SendMessageVO;
import org.backend.vo.SessionInfoVO;
import org.backend.vo.StreamMetaVO;
import org.backend.dto.WrongAnswerDTO;
import org.backend.exception.BusinessException;
import org.backend.mapper.InterviewMessageMapper;
import org.backend.mapper.InterviewReportMapper;
import org.backend.mapper.InterviewSessionMapper;
import org.backend.mapper.QuestionMapper;
import org.backend.mapper.ReportCommentMapper;
import org.backend.mapper.ResumeMapper;
import org.backend.util.AIService;
import org.backend.util.PromptBuilder;
import org.backend.util.ScoringEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class InterviewService {

    private static final Logger logger = LoggerFactory.getLogger(InterviewService.class);
    private final ObjectMapper objectMapper;

    /** 每道题默认最多允许 AI 追问的次数，超过则自动跳题 */
    private static final int DEFAULT_MAX_FOLLOW_UP = 4;

    private final InterviewSessionMapper sessionMapper;
    private final InterviewMessageMapper messageMapper;
    private final InterviewReportMapper reportMapper;
    private final ReportCommentMapper commentMapper;
    private final QuestionMapper questionMapper;
    private final ResumeMapper resumeMapper;
    private final AIService aiService;
    private final PromptBuilder promptBuilder;
    private final ScoringEngine scoringEngine;
    private final MistakeService mistakeService;
    private final AIDiagnosisService diagnosisService;
    private final InterviewContextService contextService;
    private final TransactionTemplate transactionTemplate;
    private final CacheService cacheService;
    private final QuestionSelector questionSelector;
    private final InterviewEvaluateService evaluateService;

    /** 流式消息增量持久化的间隔字符数 */
    private static final int STREAM_PERSIST_INTERVAL = 200;

    /** 评估状态常量 */
    private static final String STATUS_EVALUATING = "evaluating";
    private static final String STATUS_EVALUATE_FAILED = "evaluate_failed";

    /** 评估任务专用线程池 */
    private final ExecutorService evaluateExecutor = Executors.newFixedThreadPool(2);

    public InterviewService(InterviewSessionMapper sessionMapper,
                            InterviewMessageMapper messageMapper,
                            InterviewReportMapper reportMapper,
                            ReportCommentMapper commentMapper,
                            QuestionMapper questionMapper,
                            ResumeMapper resumeMapper,
                            AIService aiService,
                            PromptBuilder promptBuilder,
                            ScoringEngine scoringEngine,
                            MistakeService mistakeService,
                            AIDiagnosisService diagnosisService,
                            InterviewContextService contextService,
                            ObjectMapper objectMapper,
                            TransactionTemplate transactionTemplate,
                            CacheService cacheService,
                            QuestionSelector questionSelector,
                            InterviewEvaluateService evaluateService) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.reportMapper = reportMapper;
        this.commentMapper = commentMapper;
        this.questionMapper = questionMapper;
        this.resumeMapper = resumeMapper;
        this.aiService = aiService;
        this.promptBuilder = promptBuilder;
        this.scoringEngine = scoringEngine;
        this.mistakeService = mistakeService;
        this.diagnosisService = diagnosisService;
        this.contextService = contextService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.cacheService = cacheService;
        this.questionSelector = questionSelector;
        this.evaluateService = evaluateService;
    }

    @PreDestroy
    public void destroy() {
        evaluateExecutor.shutdown();
        try {
            if (!evaluateExecutor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
                evaluateExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            evaluateExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ======================== 创建面试 ========================

    public InterviewCreateVO createInterview(Long userId, String position, String round, String difficulty, int questionCount, int maxFollowUp, Long resumeId, String interviewType) {
        // 防止并发重复创建面试
        String lockKey = "interview:create:" + userId;
        if (!cacheService.tryLock(lockKey, 10)) {
            throw new BusinessException(429, "操作太频繁，请稍后重试");
        }
        try {
            return doCreateInterview(userId, position, round, difficulty, questionCount, maxFollowUp, resumeId, interviewType);
        } finally {
            cacheService.unlock(lockKey);
        }
    }

    private InterviewCreateVO doCreateInterview(Long userId, String position, String round, String difficulty, int questionCount, int maxFollowUp, Long resumeId, String interviewType) {
        // 0. 如果未指定简历，自动携带用户的活跃简历
        if (resumeId == null) {
            Resume activeResume = resumeMapper.findActiveByUserId(userId);
            if (activeResume != null && activeResume.getStatus() == 1) {
                resumeId = activeResume.getId();
                logger.info("用户{}自动携带活跃简历{}", userId, resumeId);
            }
        }

        // 简历面试必须有简历
        if ("resume".equals(interviewType) && resumeId == null) {
            throw new BusinessException(400, "简历面试需要先上传并激活一份简历");
        }

        // 1. 统一岗位名为英文 code（"Java后端" → "java_backend"）
        position = PositionConstants.normalize(position);

        // 2. 根据面试类型选择不同的创建逻辑
        if ("resume".equals(interviewType)) {
            return doCreateResumeInterview(userId, position, round, difficulty, questionCount, maxFollowUp, resumeId);
        } else {
            return doCreateNormalInterview(userId, position, round, difficulty, questionCount, maxFollowUp, resumeId);
        }
    }

    /**
     * 普通面试：从题库抽题
     */
    private InterviewCreateVO doCreateNormalInterview(Long userId, String position, String round, String difficulty, int questionCount, int maxFollowUp, Long resumeId) {
        // 1. 分层抽取 + 去重（按难度比例 + 排除近期做过的题）
        Set<Long> recentIds = contextService.getRecentQuestionIds(userId);
        List<Question> questions = questionSelector.selectBalancedQuestions(position, questionCount, recentIds, difficulty, round);
        if (questions.isEmpty()) {
            throw new BusinessException(400, "题库为空，请联系管理员添加题目");
        }
        int actualCount = Math.min(questions.size(), questionCount);

        // 记入 Redis（供后续面试去重）
        contextService.addRecentQuestionIds(userId,
                questions.stream().map(Question::getId).collect(java.util.stream.Collectors.toList()));

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
        session.setMaxFollowUp(maxFollowUp);
        session.setCurrentQuestion(0);
        session.setQuestionIds(questionIdsJson);
        session.setResumeId(resumeId);
        session.setInterviewType("normal");
        session.setStatus("in_progress");
        sessionMapper.insert(session);

        // 4. AI 生成开场白 + 第一题
        String greeting = aiService.generateGreeting(position, round, difficulty, actualCount,
                new ArrayList<>(questions.subList(0, actualCount)), maxFollowUp);

        // 5. 保存第一条 AI 消息
        InterviewMessage firstMessage = new InterviewMessage();
        firstMessage.setSessionId(session.getId());
        firstMessage.setRole("ai");
        firstMessage.setContent(greeting);
        firstMessage.setMessageType("question");
        firstMessage.setQuestionIndex(0);
        messageMapper.insert(firstMessage);

        // 6. 初始化 Redis 上下文 + 缓存会话
        String questionsText = promptBuilder.buildQuestionsListText("technical", new ArrayList<>(questions.subList(0, actualCount)));
        String systemPrompt = promptBuilder.buildInterviewSystemPrompt(
                position, round, difficulty, actualCount, questionsText, maxFollowUp);

        // 如果绑定了简历，将简历信息注入 system prompt
        String resumeContext = buildResumeContext(resumeId);
        if (resumeContext != null) {
            systemPrompt += resumeContext;
        }

        contextService.initContext(session.getId(), systemPrompt);
        contextService.addMessage(session.getId(), "assistant", greeting);
        contextService.cacheSession(session);
        contextService.cacheQuestions(session.getId(), new ArrayList<>(questions.subList(0, actualCount)));

        logger.info("用户{}创建普通面试会话{}，从题库抽取{}道题", userId, session.getId(), actualCount);

        // 7. 组装返回数据
        InterviewCreateVO data = new InterviewCreateVO();
        data.setId(session.getId());
        data.setPosition(position);
        data.setRound(round);
        data.setDifficulty(difficulty);
        data.setQuestionCount(actualCount);
        data.setMaxFollowUp(maxFollowUp);
        data.setCurrentQuestion(session.getCurrentQuestion());
        data.setInterviewType("normal");
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

    /**
     * 简历面试：基于简历内容，AI 动态生成问题
     */
    private InterviewCreateVO doCreateResumeInterview(Long userId, String position, String round, String difficulty, int questionCount, int maxFollowUp, Long resumeId) {
        // 1. 获取简历数据
        Resume resume = resumeMapper.findById(resumeId);
        if (resume == null) {
            throw new BusinessException(404, "简历不存在");
        }
        if (resume.getStatus() != 1) {
            throw new BusinessException(400, "简历尚未解析完成，请稍后再试");
        }

        // 2. 构建简历上下文
        String resumeContext = buildResumeContext(resumeId);
        if (resumeContext == null) {
            throw new BusinessException(400, "简历数据为空，无法进行简历面试");
        }

        // 3. 创建面试会话（简历面试不从题库抽题）
        InterviewSession session = new InterviewSession();
        session.setUserId(userId);
        session.setPosition(position);
        session.setRound(round);
        session.setDifficulty(difficulty);
        session.setQuestionCount(questionCount);
        session.setMaxFollowUp(maxFollowUp);
        session.setCurrentQuestion(0);
        session.setQuestionIds("[]");  // 简历面试没有题库题目
        session.setResumeId(resumeId);
        session.setInterviewType("resume");
        session.setStatus("in_progress");
        sessionMapper.insert(session);

        // 4. 构建简历面试的 system prompt
        String systemPrompt = promptBuilder.buildResumeInterviewSystemPrompt(
                position, round, difficulty, questionCount, maxFollowUp, resumeContext);

        // 5. AI 生成开场白（基于简历）
        String greeting = generateResumeGreeting(position, round, difficulty, questionCount, resumeContext);

        // 6. 保存第一条 AI 消息
        InterviewMessage firstMessage = new InterviewMessage();
        firstMessage.setSessionId(session.getId());
        firstMessage.setRole("ai");
        firstMessage.setContent(greeting);
        firstMessage.setMessageType("question");
        firstMessage.setQuestionIndex(0);
        messageMapper.insert(firstMessage);

        // 7. 初始化 Redis 上下文
        contextService.initContext(session.getId(), systemPrompt);
        contextService.addMessage(session.getId(), "assistant", greeting);
        contextService.cacheSession(session);

        logger.info("用户{}创建简历面试会话{}，基于简历{}", userId, session.getId(), resumeId);

        // 8. 组装返回数据
        InterviewCreateVO data = new InterviewCreateVO();
        data.setId(session.getId());
        data.setPosition(position);
        data.setRound(round);
        data.setDifficulty(difficulty);
        data.setQuestionCount(questionCount);
        data.setMaxFollowUp(maxFollowUp);
        data.setCurrentQuestion(0);
        data.setInterviewType("resume");
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

    /**
     * 生成简历面试的开场白
     */
    private String generateResumeGreeting(String position, String round, String difficulty, int questionCount, String resumeContext) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content",
                "你是一位专业面试官，请根据以下简历信息生成一段简短的开场白（2-3句话），" +
                "欢迎候选人参加面试，并说明你会根据简历内容进行针对性提问。" +
                "不要提问，只做开场介绍。\n\n" + resumeContext));

        return aiService.chat(messages);
    }

    // ======================== 发送消息（普通） ========================

    public SendMessageVO sendMessage(Long sessionId, Long userId, String content) {
        // 获取发送锁，防止并发发送
        String sendLockKey = "interview:msg:" + sessionId;
        if (!cacheService.tryLock(sendLockKey, 30)) {
            throw new BusinessException(429, "正在生成回复，请稍后再试");
        }
        try {
            return doSendMessage(sessionId, userId, content);
        } finally {
            cacheService.unlock(sendLockKey);
        }
    }

    private SendMessageVO doSendMessage(Long sessionId, Long userId, String content) {
        SendMessageContext ctx = prepareSendMessage(sessionId, userId, content);
        InterviewSession session = ctx.getSession();
        Question currentQuestion = ctx.getCurrentQuestion();

        // 面试已结束检查
        int remaining = session.getQuestionCount() - session.getCurrentQuestion();
        if (remaining <= 0) {
            throw new BusinessException(400, "面试已结束，无法继续发送消息");
        }

        // AI 评估并返回下一题（支持追问）
        AIResultVO aiResult;
        if ("resume".equals(session.getInterviewType())) {
            // 简历面试：使用简历专用的评估方法
            String resumeContext = buildResumeContext(session.getResumeId());
            aiResult = aiService.evaluateAndRespondForResume(
                    session.getPosition(), session.getRound(), session.getDifficulty(),
                    session.getCurrentQuestion(), session.getQuestionCount(), content,
                    ctx.getHistory(), session.getMaxFollowUp(),
                    resumeContext != null ? resumeContext : "");
        } else {
            aiResult = aiService.evaluateAndRespond(
                    session.getPosition(), session.getRound(), session.getDifficulty(),
                    session.getCurrentQuestion(), session.getQuestionCount(), content,
                    currentQuestion != null ? currentQuestion.getTitle() : "",
                    ctx.getAllQuestions(), ctx.getHistory(),
                    session.getMaxFollowUp());
        }

        String aiContent = aiResult.getContent();
        String type = aiResult.getType();
        int nextQuestion = aiResult.getNextQuestion();

        // 公共后处理：追问限制 → 追问递增 → Redis 上下文 → 跳题 → 自动结束
        // 必须在保存消息之前调用，否则 getFollowUpCount 会把当前消息也算进去
        type = applyPostAIResponse(sessionId, session, type, aiContent, nextQuestion);

        // 后处理可能改变了 type（如 follow_up → next_question），需要重新计算 nextQuestion 和 remainingQuestions
        int remainingAfter;
        if ("next_question".equals(type)) {
            nextQuestion = session.getCurrentQuestion() + 1;
            remainingAfter = remaining - 1;
        } else if ("follow_up".equals(type) || "answer".equals(type)) {
            nextQuestion = session.getCurrentQuestion();
            remainingAfter = remaining;
        } else {
            // end 或其他类型
            nextQuestion = session.getCurrentQuestion();
            remainingAfter = 0;
        }

        // 保存 AI 消息（使用后处理后的 type）
        InterviewMessage aiMessage = new InterviewMessage();
        aiMessage.setSessionId(sessionId);
        aiMessage.setRole("ai");
        aiMessage.setContent(aiContent);
        aiMessage.setMessageType(type);
        aiMessage.setQuestionIndex(type.equals("next_question") ? nextQuestion : session.getCurrentQuestion());
        messageMapper.insert(aiMessage);

        SendMessageVO data = new SendMessageVO();
        data.setId(aiMessage.getId());
        data.setRole("ai");
        data.setContent(aiContent);
        data.setType(type);
        data.setNextQuestion(nextQuestion);
        data.setRemainingQuestions(Math.max(0, remainingAfter));
        data.setCreatedAt(aiMessage.getCreatedAt());
        return data;
    }

    // ======================== 发送消息（流式 SSE） ========================

    public Map<String, Object> sendMessageStream(Long sessionId, Long userId, String content, SseEmitter emitter) {
        // 获取发送锁，防止并发发送
        String sendLockKey = "interview:msg:" + sessionId;
        if (!cacheService.tryLock(sendLockKey, 30)) {
            throw new BusinessException(429, "正在生成回复，请稍后再试");
        }
        // 注意：锁在 emitter 的 onCompletion/onTimeout/onError 中释放

        SendMessageContext ctx = prepareSendMessage(sessionId, userId, content);
        InterviewSession session = ctx.getSession();
        Question currentQuestion = ctx.getCurrentQuestion();
        String currentQuestionText = currentQuestion != null ? currentQuestion.getTitle() : "";

        List<Question> allQuestions = ctx.getAllQuestions();
        String questionsText = buildQuestionsListText(allQuestions);

        List<Map<String, String>> history = ctx.getHistory();

        int remaining = session.getQuestionCount() - session.getCurrentQuestion();

        // 面试已结束检查：所有题目已答完
        if (remaining <= 0) {
            cacheService.unlock(sendLockKey);
            throw new BusinessException(400, "面试已结束，无法继续发送消息");
        }

        // 获取下一题内容（当前题的下一题）
        String nextQuestionText = "";
        int nextIndex = session.getCurrentQuestion() + 1;
        if (remaining > 0 && nextIndex < allQuestions.size()) {
            Question q = allQuestions.get(nextIndex);
            nextQuestionText = q.getTitle() + "\n" + (q.getContent() != null ? q.getContent() : "");
        }

        List<Map<String, String>> aiMessages = new ArrayList<>();
        String streamSystemPrompt;

        // 简历面试使用专用 prompt
        if ("resume".equals(session.getInterviewType())) {
            String resumeCtx = buildResumeContext(session.getResumeId());
            streamSystemPrompt = promptBuilder.buildResumeInterviewSystemPrompt(
                    session.getPosition(), session.getRound(), session.getDifficulty(),
                    session.getQuestionCount(), session.getMaxFollowUp(),
                    resumeCtx != null ? resumeCtx : "");
        } else {
            streamSystemPrompt = buildStreamSystemPrompt(session.getPosition(), session.getRound(), session.getDifficulty(),
                    session.getQuestionCount(), questionsText, session.getMaxFollowUp());

            // 如果绑定了简历，注入简历上下文
            if (session.getResumeId() != null) {
                String resumeCtx = buildResumeContext(session.getResumeId());
                if (resumeCtx != null) streamSystemPrompt += resumeCtx;
            }
        }

        aiMessages.add(Map.of("role", "system", "content", streamSystemPrompt));

        // 插入历史对话
        if (history != null && !history.isEmpty()) {
            aiMessages.addAll(history);
        }

        // 当前用户消息（流式专用 Prompt，使用【决策: xxx】标记）
        String userPrompt = promptBuilder.buildStreamUserPrompt(
                session.getCurrentQuestion(), currentQuestionText,
                content, nextQuestionText, remaining, history,
                session.getMaxFollowUp(), session.getQuestionCount());

        // 追问次数预检：如果已超限，覆盖 prompt 强制跳题，防止 AI 生成追问内容
        if (session.getMaxFollowUp() <= 0) {
            // 不追问模式：直接强制要求 AI 不得追问
            logger.info("不追问模式，强制要求 AI 不得追问，sessionId={}", sessionId);
            userPrompt += "\n\n⚠️ 【强制指令】本轮面试设置为【不追问模式】，无论候选人回答如何，你都必须选择【决策: next】或【决策: end】。禁止生成任何追问内容。";
        } else {
            int followUpCount = contextService.getFollowUpCount(sessionId, session.getCurrentQuestion(), messageMapper);
            if (followUpCount >= session.getMaxFollowUp()) {
                logger.info("题目{}已追问{}次，预检超限，强制跳题", session.getCurrentQuestion(), followUpCount);
                userPrompt += "\n\n⚠️ 【强制指令】你已追问" + followUpCount + "次（上限" + session.getMaxFollowUp() + "次）。本轮必须选择【决策: next】或【决策: end】，禁止追问。回复内容中不能出现追问句式。";
            }
        }

        aiMessages.add(Map.of("role", "user", "content", userPrompt));

        // 先插入一条空 AI 消息（供轮询降级时前端读取中间内容）
        InterviewMessage aiMessage = new InterviewMessage();
        aiMessage.setSessionId(sessionId);
        aiMessage.setRole("ai");
        aiMessage.setContent("");
        aiMessage.setMessageType("streaming"); // 标记为流式生成中，后续根据实际类型更新
        aiMessage.setQuestionIndex(session.getCurrentQuestion());
        messageMapper.insert(aiMessage);

        // 在 Redis 中标记该会话正在流式生成（30 秒超时，而非 300 秒）
        String streamLockKey = "interview:streaming:" + sessionId;
        cacheService.set(streamLockKey, aiMessage.getId(), 30);

        // 取消标记：客户端断开时设为 true
        AtomicBoolean isCancelled = new AtomicBoolean(false);

        // 注册 emitter 回调：确保锁一定会被释放（同时释放流式锁和发送锁）
        Runnable releaseLock = () -> {
            isCancelled.set(true);
            cacheService.del(streamLockKey);
            cacheService.unlock(sendLockKey);
            logger.debug("SSE 流式锁和发送锁已释放，sessionId={}", sessionId);
        };

        emitter.onCompletion(releaseLock);
        emitter.onTimeout(releaseLock);
        emitter.onError(e -> {
            releaseLock.run();
            logger.warn("SSE 连接错误，sessionId={}", sessionId, e);
        });

        StringBuilder fullContent = new StringBuilder();
        final int[] persistCounter = {0};

        aiService.chatStream(aiMessages, chunk -> {
            try {
                // 客户端已断开，丢弃后续内容
                if (isCancelled.get()) return;

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
                isCancelled.set(true);
            }
        }, () -> {
            try {
                // 客户端已断开，仍然完成消息保存（避免消息丢失）
                String rawAiContent = fullContent.toString();

                // 解析决定标记
                String type = parseDecisionType(rawAiContent);
                String aiContent = stripDecisionMarker(rawAiContent);

                // 边界保护：非最后一题 end→next_question 防过早结束；最后一题 next_question→end 防"进入下一题"后突然结束
                type = scoringEngine.enforceDecisionBoundary(type,
                        session.getCurrentQuestion(), session.getQuestionCount(), remaining);

                // 追问次数限制（流式场景：超限时替换 AI 内容为兜底文案）
                String enforcedType = enforceFollowUpLimit(sessionId, session.getCurrentQuestion(),
                        session.getMaxFollowUp(), type, remaining);
                if (!enforcedType.equals(type)) {
                    type = enforcedType;
                    aiContent = "好的，你的回答我们已经了解了。" + (remaining > 1 ? "我们进入下一题。" : "本次面试到此结束。感谢你的时间！");
                }

                int nextQuestion;
                int remainingAfter;
                if ("follow_up".equals(type)) {
                    nextQuestion = session.getCurrentQuestion();
                    remainingAfter = remaining;
                } else if ("answer".equals(type)) {
                    // answer 类型：保持当前状态，不进入下一题，不递增追问次数
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

                // 公共后处理：追问限制 → 追问递增 → Redis 上下文 → 跳题 → 自动结束
                // 必须在更新消息之前调用，否则 getFollowUpCount 会把当前消息也算进去
                type = applyPostAIResponse(sessionId, session, type, aiContent, nextQuestion);

                // 后处理可能改变了 type（如 follow_up → next_question），需要重新计算 nextQuestion
                if ("next_question".equals(type)) {
                    nextQuestion = session.getCurrentQuestion() + 1;
                    remainingAfter = remaining - 1;
                } else if ("follow_up".equals(type) || "answer".equals(type)) {
                    nextQuestion = session.getCurrentQuestion();
                    remainingAfter = remaining;
                }

                // 使用后处理后的 type 更新消息
                aiMessage.setMessageType(type);
                aiMessage.setQuestionIndex(nextQuestion);
                messageMapper.updateContent(aiMessage.getId(), aiContent);
                messageMapper.updateMessageTypeAndQuestionIndex(aiMessage.getId(), type, nextQuestion);

                // 清除流式生成标记
                cacheService.del(streamLockKey);

                // 客户端仍然连接时才发送 meta 事件
                if (!isCancelled.get()) {
                    StreamMetaVO meta = new StreamMetaVO();
                    meta.setType(type);
                    meta.setContent(aiContent);
                    meta.setNextQuestion(nextQuestion);
                    meta.setRemainingQuestions(Math.max(0, remainingAfter));
                    meta.setMessageId(aiMessage.getId());
                    emitter.send(SseEmitter.event().name("meta").data(meta));
                    emitter.complete();
                }
            } catch (Exception e) {
                logger.error("SSE completion error", e);
                // 确保锁被释放
                cacheService.del(streamLockKey);
                cacheService.unlock(sendLockKey);
                if (!isCancelled.get()) {
                    try {
                        emitter.completeWithError(e);
                    } catch (Exception ignored) {
                        // emitter may already be completed
                    }
                }
            }
        });

        return Map.of("status", "streaming");
    }

    // ======================== 结束面试 ========================

    public EndInterviewVO endInterview(Long sessionId, Long userId) {
        InterviewSession session = getSession(sessionId);
        if (session == null) {
            throw new BusinessException(404, "面试会话不存在");
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问此面试会话");
        }
        // 已经在评估中，直接返回
        if (STATUS_EVALUATING.equals(session.getEvaluateStatus())) {
            throw new BusinessException(400, "面试正在评估中");
        }

        // 已经评估完成（有报告），直接返回
        if ("completed".equals(session.getStatus()) && session.getEvaluateStatus() == null
                && reportMapper.findBySessionId(sessionId) != null) {
            throw new BusinessException(400, "面试已结束");
        }

        // 1. 更新状态为 evaluating（立即返回）
        transactionTemplate.executeWithoutResult(status -> {
            sessionMapper.updateStatus(sessionId, "completed");
            session.setEvaluateStatus(STATUS_EVALUATING);
            session.setEvaluateRetryCount(0);
            sessionMapper.updateEvaluateStatus(session);
        });
        // 同步缓存
        contextService.updateCachedSession(sessionId, "status", "completed");
        contextService.updateCachedSession(sessionId, "evaluateStatus", STATUS_EVALUATING);
        contextService.updateCachedSession(sessionId, "evaluateRetryCount", 0);

        // 2. 异步执行 AI 评估
        CompletableFuture.runAsync(() -> {
            try {
                evaluateService.doEvaluate(sessionId, userId, session);
                // 评估成功，清除 evaluate_status
                session.setEvaluateStatus(null);
                session.setEvaluateError(null);
                sessionMapper.updateEvaluateStatus(session);
                contextService.updateCachedSession(sessionId, "evaluateStatus", null);
                contextService.updateCachedSession(sessionId, "evaluateError", null);
            } catch (Exception e) {
                logger.error("AI 评估失败，sessionId={}", sessionId, e);
                session.setEvaluateStatus(STATUS_EVALUATE_FAILED);
                session.setEvaluateError(e.getMessage());
                session.setEvaluateRetryCount(session.getEvaluateRetryCount() + 1);
                sessionMapper.updateEvaluateStatus(session);
                contextService.updateCachedSession(sessionId, "evaluateStatus", STATUS_EVALUATE_FAILED);
                contextService.updateCachedSession(sessionId, "evaluateError", e.getMessage());
                contextService.updateCachedSession(sessionId, "evaluateRetryCount", session.getEvaluateRetryCount());
            }
        }, evaluateExecutor);

        // 3. 立即返回 evaluating 状态
        EndInterviewVO data = new EndInterviewVO();
        data.setInterviewId(sessionId);
        return data;
    }


    /**
     * 查询报告生成状态（供前端轮询）
     */
    public Map<String, Object> getReportStatus(Long sessionId, Long userId) {
        InterviewSession session = getSession(sessionId);
        if (session == null) {
            throw new BusinessException(404, "面试会话不存在");
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问此面试会话");
        }

        Map<String, Object> result = new HashMap<>();

        // 优先用 evaluate_status 判断
        if (STATUS_EVALUATING.equals(session.getEvaluateStatus())) {
            result.put("status", "evaluating");
        } else if (STATUS_EVALUATE_FAILED.equals(session.getEvaluateStatus())) {
            result.put("status", "evaluate_failed");
            result.put("error", session.getEvaluateError());
            result.put("retryCount", session.getEvaluateRetryCount());
        } else if ("completed".equals(session.getStatus())) {
            result.put("status", "completed");
            // 直接用 report_id，无需二次查 report 表
            if (session.getReportId() != null) {
                result.put("reportId", session.getReportId());
            } else {
                // 兜底：report_id 未回写时查 report 表
                InterviewReport report = reportMapper.findBySessionId(sessionId);
                if (report != null) {
                    result.put("reportId", report.getId());
                }
            }
        } else {
            result.put("status", session.getStatus());
        }

        return result;
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
        InterviewSession session = getSession(sessionId);
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
        InterviewSession session = getSession(sessionId);
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
        InterviewSession session = getSession(sessionId);
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
        info.setMaxFollowUp(session.getMaxFollowUp());
        info.setCurrentQuestion(session.getCurrentQuestion());
        info.setStatus(session.getStatus());
        info.setCreatedAt(session.getCreatedAt());
        return info;
    }

    @Transactional
    public void abandonInterview(Long sessionId, Long userId) {
        InterviewSession session = getSession(sessionId);
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
        int totalMsgCount = messageMapper.countBySessionId(sessionId);
        if (totalMsgCount > 0) {
            logger.info("会话{}有{}条消息（用户{}条），允许放弃", sessionId, totalMsgCount, userMsgCount);
        }
        messageMapper.deleteBySessionId(sessionId);
        sessionMapper.deleteById(sessionId);
        // 清理所有 Redis 缓存（上下文 + 题目 + 会话 + 追问计数）
        contextService.clearAllInterviewCache(sessionId);
        logger.info("用户{}放弃面试会话{}", userId, sessionId);
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
     * AI 回复后的公共状态处理（sendMessage 和 sendMessageStream 共用）
     * 包含：追问次数限制 → 追问递增 → Redis 上下文 → 跳题更新 → 自动结束
     *
     * @return 最终的 type（可能被强制修改为 next_question）
     */
    private String applyPostAIResponse(Long sessionId, InterviewSession session,
                                        String type, String aiContent, int nextQuestion) {
        // 1. 追问次数限制
        type = enforceFollowUpLimit(sessionId, session.getCurrentQuestion(),
                session.getMaxFollowUp(), type, remaining(session));

        // 2. 如果是追问，递增追问次数
        if ("follow_up".equals(type)) {
            contextService.incrementFollowUp(sessionId, session.getCurrentQuestion());
        }

        // 3. 保存到 Redis 上下文
        contextService.addMessage(sessionId, "assistant", aiContent);

        // 4. follow_up/answer 不更新 currentQuestion，next_question 才跳题
        if ("next_question".equals(type)) {
            sessionMapper.updateCurrentQuestion(sessionId, nextQuestion);
            contextService.updateCachedSession(sessionId, "currentQuestion", nextQuestion);
        }

        // 5. 面试自动结束
        if (shouldAutoEnd(type, nextQuestion, session.getQuestionCount())) {
            sessionMapper.updateStatus(sessionId, "completed");
            contextService.updateCachedSession(sessionId, "status", "completed");
            logger.info("面试自动结束：sessionId={}, type={}, nextQuestion={}", sessionId, type, nextQuestion);
        }

        return type;
    }

    /**
     * 追问次数限制：超过 maxFollowUp 次则强制跳题
     */
    private String enforceFollowUpLimit(Long sessionId, int questionIndex, int maxFollowUp,
                                         String type, int remaining) {
        if (!"follow_up".equals(type)) return type;
        int followUpCount = contextService.getFollowUpCount(sessionId, questionIndex, messageMapper);
        logger.info("追问限制检查：sessionId={}, questionIndex={}, followUpCount={}, maxFollowUp={}",
                sessionId, questionIndex, followUpCount, maxFollowUp);
        if (followUpCount >= maxFollowUp) {
            logger.info("题目{}已追问{}次，强制进入下一题", questionIndex, followUpCount);
            return "next_question";
        }
        return type;
    }

    /**
     * 判断是否应该自动结束面试
     */
    private boolean shouldAutoEnd(String type, int nextQuestion, int totalQuestions) {
        if ("end".equals(type)) return true;
        return nextQuestion >= totalQuestions && !"follow_up".equals(type) && !"answer".equals(type);
    }

    /**
     * 计算剩余题数
     */
    private int remaining(InterviewSession session) {
        return session.getQuestionCount() - session.getCurrentQuestion();
    }

    /**
     * 发送消息前的公共准备逻辑（抽取自 sendMessage / sendMessageStream 的重复代码）
     */
    private SendMessageContext prepareSendMessage(Long sessionId, Long userId, String content) {
        InterviewSession session = validateSession(sessionId, userId);
        List<Question> allQuestions = getSessionQuestions(session);
        
        // 从题目列表中获取当前题目（0-based）
        int index = session.getCurrentQuestion();
        Question currentQuestion = (index >= 0 && index < allQuestions.size()) ? allQuestions.get(index) : null;

        // 保存用户消息到 MySQL
        InterviewMessage userMessage = new InterviewMessage();
        userMessage.setSessionId(sessionId);
        userMessage.setRole("user");
        userMessage.setContent(content);
        userMessage.setMessageType("answer");
        userMessage.setQuestionIndex(session.getCurrentQuestion());
        messageMapper.insert(userMessage);

        // 保存用户消息到 Redis 上下文（统一在此处保存，避免重复）
        contextService.addMessage(sessionId, "user", content);

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
        InterviewSession session = getSession(sessionId);
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
     * 获取面试会话（带 Redis 缓存，避免重复查 MySQL）
     */
    private InterviewSession getSession(Long sessionId) {
        // 1. 优先从 Redis 缓存读取
        InterviewSession cached = contextService.getCachedSession(sessionId);
        if (cached != null) {
            return cached;
        }
        // 2. 缓存未命中，从 MySQL 查询并缓存
        InterviewSession session = sessionMapper.findById(sessionId);
        if (session != null) {
            contextService.cacheSession(session);
        }
        return session;
    }

    /**
     * 从会话的 question_ids 解析出完整的 Question 列表（带 Redis 缓存）
     */
    private List<Question> getSessionQuestions(InterviewSession session) {
        // 1. 优先从 Redis 缓存读取
        List<Question> cached = contextService.getCachedQuestions(session.getId());
        if (cached != null) {
            return cached;
        }

        // 2. 缓存未命中，从 MySQL 查询
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

            // 3. 写入 Redis 缓存
            contextService.cacheQuestions(session.getId(), result);

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
            if (msg.getQuestionIndex() != null) {
                entry.put("questionIndex", String.valueOf(msg.getQuestionIndex()));
            }
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
     * 构建题目列表文本（委托 PromptBuilder）
     */
    private String buildQuestionsListText(List<Question> questions) {
        return promptBuilder.buildQuestionsListText("technical", questions);
    }

    private String buildStreamSystemPrompt(String position, String round, String difficulty, int questionCount, String questionsText, int maxFollowUp) {
        return promptBuilder.buildInterviewSystemPrompt(position, round, difficulty, questionCount, questionsText, maxFollowUp);
    }

    // ======================== 简历上下文构建 ========================

    /**
     * 构建简历上下文文本，注入到面试 system prompt 中
     */
    private String buildResumeContext(Long resumeId) {
        if (resumeId == null) return null;
        try {
            Resume resume = resumeMapper.findById(resumeId);
            if (resume == null || resume.getStatus() != 1 || resume.getParsedData() == null) {
                logger.warn("简历{}不存在或未解析完成，跳过简历上下文", resumeId);
                return null;
            }
            return "\n\n=== 面试者简历信息 ===\n" +
                    resume.getParsedData() + "\n\n" +
                    "根据以上简历信息：\n" +
                    "1. 优先针对简历中的技能标签出题和追问\n" +
                    "2. 对简历标注\"精通\"的技能深入提问，标注\"了解\"的适当追问\n" +
                    "3. 项目经验中的技术栈是重点考察方向\n" +
                    "4. 可以结合候选人的项目经验进行场景化提问\n";
        } catch (Exception e) {
            logger.error("构建简历上下文失败，resumeId={}", resumeId, e);
            return null;
        }
    }

}
