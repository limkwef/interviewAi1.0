package org.backend.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.backend.entity.AIResultVO;
import org.backend.entity.DiagnosisDataVO;
import org.backend.entity.InterviewReport;
import org.backend.entity.InterviewMessage;
import org.backend.entity.Question;
import org.backend.entity.ReportComment;
import org.backend.entity.ReportResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * AI 服务 — DeepSeek API 调用 + 面试对话编排
 *
 * 职责：调用 AI API、协调 PromptBuilder（构建提示词）和 ScoringEngine（解析/降级）
 * 不包含：Prompt 文本、评分规则、降级逻辑
 */
@Service
public class AIService {

    private static final Logger logger = LoggerFactory.getLogger(AIService.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${ai.api-key:}")
    private String apiKey;

    @Value("${ai.api-url:https://api.deepseek.com}")
    private String apiUrl;

    @Value("${ai.model:deepseek-chat}")
    private String model;

    @Value("${ai.max-tokens:2048}")
    private int maxTokens;

    @Value("${ai.temperature:0.7}")
    private double temperature;

    /** 单例 HttpClient，避免每次调用都创建新实例，线程池由 AsyncConfig.aiExecutor 管理 */
    private final HttpClient httpClient;

    public AIService(@Qualifier("aiExecutor") ExecutorService aiExecutor) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .executor(aiExecutor)
                .build();
    }

    @Autowired
    private PromptBuilder promptBuilder;

    @Autowired
    private ScoringEngine scoringEngine;

    // ======================== 简单熔断器 ========================

    /** 连续失败次数达到此阈值时打开熔断器 */
    private static final int CIRCUIT_BREAKER_THRESHOLD = 5;
    /** 熔断器打开后，等待此时间（毫秒）后进入半开状态 */
    private static final long CIRCUIT_BREAKER_COOLDOWN_MS = 30_000;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile boolean circuitBreakerOpen = false;
    private volatile long circuitBreakerOpenedAt = 0;

    /**
     * 检查熔断器状态，返回 true 表示可以调用，false 表示熔断中
     * 半开状态：冷却期过后允许一次试探调用
     */
    private boolean checkCircuitBreaker() {
        if (!circuitBreakerOpen) return true;
        long elapsed = System.currentTimeMillis() - circuitBreakerOpenedAt;
        if (elapsed >= CIRCUIT_BREAKER_COOLDOWN_MS) {
            // 半开：允许一次试探
            logger.warn("熔断器半开，允许一次试探调用");
            return true;
        }
        return false;
    }

    /** 记录调用成功，重置失败计数 */
    private void recordSuccess() {
        consecutiveFailures.set(0);
        if (circuitBreakerOpen) {
            circuitBreakerOpen = false;
            logger.info("熔断器关闭，AI 服务恢复正常");
        }
    }

    /** 记录调用失败，达到阈值时打开熔断器 */
    private void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= CIRCUIT_BREAKER_THRESHOLD) {
            if (!circuitBreakerOpen) {
                // 首次打开熔断器
                circuitBreakerOpen = true;
                circuitBreakerOpenedAt = System.currentTimeMillis();
                logger.error("熔断器打开！DeepSeek API 连续失败 {} 次，暂停调用 {} 秒",
                        failures, CIRCUIT_BREAKER_COOLDOWN_MS / 1000);
            } else {
                // 半开状态试探失败 → 重新计时冷却，延长熔断
                circuitBreakerOpenedAt = System.currentTimeMillis();
                logger.error("熔断器半开试探失败，重新计时 {} 秒冷却",
                        CIRCUIT_BREAKER_COOLDOWN_MS / 1000);
            }
        }
    }

    // ======================== 重试机制 ========================

    /**
     * 带重试的调用封装（内部使用，不改变外部调用方式）
     * 网络波动时自动重试 3 次，间隔 1s、2s，只有连续 3 次失败才返回 null
     */
    private <T> T withRetry(Supplier<T> call, String name) {
        // 熔断检查
        if (!checkCircuitBreaker()) {
            logger.warn("熔断器开启中，跳过 {} 调用", name);
            return null;
        }
        for (int i = 0; i < 3; i++) {
            try {
                T result = call.get();
                if (result != null) {
                    recordSuccess();
                    return result;
                }
                logger.warn("{} 第{}次返回null", name, i + 1);
            } catch (Exception e) {
                logger.warn("{} 第{}次失败: {}", name, i + 1, e.getMessage());
            }
            if (i < 2) {
                try { Thread.sleep(1000L * (i + 1)); } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        recordFailure();
        return null;
    }

    // ======================== DeepSeek API 调用 ========================

    public String chat(List<Map<String, String>> messages) {
        return withRetry(() -> doChat(messages), "chat");
    }

    private String doChat(List<Map<String, String>> messages) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("max_tokens", maxTokens);
            body.put("temperature", temperature);
            body.put("stream", false);

            String jsonBody = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                logTokenUsage(root, "chat");
                return root.path("choices").path(0).path("message").path("content").asText();
            } else {
                logByStatusCode(response.statusCode(), "chat");
                return null;
            }
        } catch (Exception e) {
            logger.error("DeepSeek API call failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 结构化输出调用（response_format: json_object）
     * 强制 AI 返回合法 JSON，自动解析为 Map
     * 返回 null 表示调用失败（调用方需要降级兜底）
     */
    public Map<String, Object> chatStructured(List<Map<String, String>> messages) {
        return withRetry(() -> doChatStructured(messages), "chatStructured");
    }

    private Map<String, Object> doChatStructured(List<Map<String, String>> messages) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("max_tokens", maxTokens);
            body.put("temperature", temperature);
            body.put("stream", false);
            body.put("response_format", Map.of("type", "json_object"));

            String jsonBody = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                logTokenUsage(root, "chatStructured");
                String content = root.path("choices").path(0).path("message").path("content").asText();
                return objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {});
            } else {
                logByStatusCode(response.statusCode(), "chatStructured");
                return null;
            }
        } catch (Exception e) {
            logger.error("DeepSeek structured API call failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 结构化输出调用（带降级兜底）
     * 优先使用 chatStructured，若返回 null 则降级为 chat + JSON 提取
     * 返回解析后的 Map，或 null 表示完全失败
     */
    public Map<String, Object> chatStructuredWithFallback(List<Map<String, String>> messages) {
        Map<String, Object> structured = chatStructured(messages);
        if (structured != null) return structured;

        String result = chat(messages);
        if (result != null) {
            try {
                String jsonStr = scoringEngine.extractJson(result);
                return objectMapper.readValue(jsonStr, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                logger.error("chatStructuredWithFallback: 降级解析JSON失败", e);
            }
        }
        return null;
    }

    public void chatStream(List<Map<String, String>> messages, Consumer<String> onChunk, Runnable onComplete) {
        // 熔断检查
        if (!checkCircuitBreaker()) {
            logger.warn("熔断器开启中，跳过 chatStream 调用");
            onComplete.run();
            return;
        }
        // completed 标志跨重试共享，避免重试时前一次异步回调仍触发 onComplete
        final AtomicBoolean completed = new AtomicBoolean(false);
        Runnable safeComplete = () -> {
            if (completed.compareAndSet(false, true)) {
                onComplete.run();
            }
        };
        doChatStreamWithRetry(messages, onChunk, safeComplete, 0);
    }

    /**
     * 带重试的流式调用
     * 网络波动或临时错误时自动重试最多 3 次，间隔 1s、2s
     */
    private void doChatStreamWithRetry(List<Map<String, String>> messages, Consumer<String> onChunk,
                                        Runnable safeComplete, int attempt) {

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("max_tokens", maxTokens);
            body.put("temperature", temperature);
            body.put("stream", true);

            String jsonBody = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(120))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                    .thenAccept(response -> {
                        if (response.statusCode() != 200) {
                            logByStatusCode(response.statusCode(), "chatStream");
                            handleStreamFailure(messages, onChunk, safeComplete, attempt);
                            return;
                        }
                        recordSuccess();
                        response.body().forEach(line -> {
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6).trim();
                                if ("[DONE]".equals(data)) { return; }
                                try {
                                    JsonNode node = objectMapper.readTree(data);
                                    String content = node.path("choices").path(0).path("delta").path("content").asText("");
                                    if (!content.isEmpty()) onChunk.accept(content);
                                } catch (Exception ignored) { }
                            }
                        });
                        safeComplete.run();
                    })
                    .exceptionally(ex -> {
                        logger.error("DeepSeek streaming failed (attempt {}): {}", attempt + 1, ex.getMessage());
                        handleStreamFailure(messages, onChunk, safeComplete, attempt);
                        return null;
                    });
        } catch (Exception e) {
            logger.error("DeepSeek stream setup failed (attempt {}): {}", attempt + 1, e.getMessage());
            handleStreamFailure(messages, onChunk, safeComplete, attempt);
        }
    }

    /**
     * 流式调用失败处理：重试或记录失败
     */
    private void handleStreamFailure(List<Map<String, String>> messages, Consumer<String> onChunk,
                                      Runnable safeComplete, int attempt) {
        if (attempt < 2) {
            long delay = 1000L * (attempt + 1);
            logger.warn("chatStream 第{}次失败，{}秒后重试...", attempt + 1, delay / 1000);
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                recordFailure();
                safeComplete.run();
                return;
            }
            doChatStreamWithRetry(messages, onChunk, safeComplete, attempt + 1);
        } else {
            logger.error("chatStream 重试3次全部失败");
            recordFailure();
            safeComplete.run();
        }
    }

    // ======================== 错误日志分级 ========================

    /**
     * 按 HTTP 状态码区分日志级别，方便快速定位问题
     */
    private void logByStatusCode(int statusCode, String method) {
        if (statusCode == 401) {
            logger.error("DeepSeek API 认证失败（{}），请检查 api-key 配置", method);
        } else if (statusCode == 429) {
            logger.warn("DeepSeek API 限流（{}），稍后重试", method);
        } else if (statusCode >= 500) {
            logger.error("DeepSeek 服务端错误（{}），status={}", method, statusCode);
        } else {
            logger.warn("DeepSeek API 返回异常状态码（{}）: {}", method, statusCode);
        }
    }

    // ======================== Token 消耗追踪 ========================

    private void logTokenUsage(JsonNode root, String method) {
        JsonNode usage = root.path("usage");
        if (!usage.isMissingNode()) {
            int promptTokens = usage.path("prompt_tokens").asInt(0);
            int completionTokens = usage.path("completion_tokens").asInt(0);
            int totalTokens = usage.path("total_tokens").asInt(0);
            logger.info("DeepSeek Token 消耗（{}）: prompt={}, completion={}, total={}",
                    method, promptTokens, completionTokens, totalTokens);
        }
    }

    // ======================== 面试开场白 ========================

    public String generateGreeting(String position, String round, String difficulty,
                                    int questionCount, List<Question> questions, int maxFollowUp) {
        String questionsText = promptBuilder.buildQuestionsListText(round, questions);
        List<Map<String, String>> messages = promptBuilder.buildGreetingMessages(
                position, round, difficulty, questionCount, questionsText, maxFollowUp);

        String result = chat(messages);
        if (result != null) return result;

        // 降级
        String firstQuestion = questions.isEmpty() ? "请介绍一下你自己" : questions.get(0).getTitle();
        return String.format("你好，欢迎参加本次%s%s模拟面试。本次面试共%d道题，请认真作答。准备好了吗？我们开始第一题：\n\n%s",
                promptBuilder.getDifficultyName(difficulty), promptBuilder.getPositionName(position),
                questionCount, firstQuestion);
    }

    // ======================== 评估回答并返回下一题 ========================

    public AIResultVO evaluateAndRespond(String position, String round, String difficulty,
                                                   int currentQuestion, int totalQuestions,
                                                   String userAnswer, String currentQuestionText,
                                                   List<Question> allQuestions,
                                                   List<Map<String, String>> history,
                                                   int maxFollowUp) {
        int remaining = totalQuestions - currentQuestion;
        String questionsText = promptBuilder.buildQuestionsListText(round, allQuestions);

        String nextQuestionText = "";
        if (!"hr".equals(round) && remaining > 0 && currentQuestion < allQuestions.size()) {
            Question q = allQuestions.get(currentQuestion);
            nextQuestionText = q.getTitle() + "\n" + (q.getContent() != null ? q.getContent() : "");
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content",
                promptBuilder.buildInterviewSystemPrompt(position, round, difficulty, totalQuestions, questionsText, maxFollowUp)));
        if (history != null && !history.isEmpty()) messages.addAll(history);

        String userPrompt = promptBuilder.buildUserPrompt(round, currentQuestion, currentQuestionText,
                userAnswer, nextQuestionText, remaining, history, maxFollowUp);
        messages.add(Map.of("role", "user", "content", userPrompt));

        AIResultVO result = new AIResultVO();

        // 优先使用结构化输出
        Map<String, Object> structured = chatStructured(messages);
        if (structured != null) {
            String rawDecision = (String) structured.getOrDefault("decision", "next_question");
            String evaluation = (String) structured.getOrDefault("evaluation", "");
            String nextQuestionStr = (String) structured.getOrDefault("nextQuestion", "");

            // 统一映射：AI 返回的 "next" → "next_question"，"follow_up" 和 "end" 保持不变
            String type = switch (rawDecision.toLowerCase()) {
                case "follow_up" -> "follow_up";
                case "end" -> "end";
                default -> "next_question";
            };

            String content = evaluation;
            if (!nextQuestionStr.isEmpty() && "next_question".equals(type)) {
                content += "\n\n" + nextQuestionStr;
            }

            if ("end".equals(type) && remaining > 0) type = "next_question";
            if (remaining <= 0) type = "end";

            result.setContent(content.isEmpty() ? scoringEngine.generateFeedback(difficulty) : content);
            result.setType(type);
            result.setNextQuestion("follow_up".equals(type) ? currentQuestion
                    : "next_question".equals(type) ? currentQuestion + 1 : currentQuestion);
            result.setRemainingQuestions("follow_up".equals(type) ? remaining
                    : "next_question".equals(type) ? remaining - 1 : 0);
            return result;
        }

        // 降级：普通 chat（兼容旧模型）
        String aiResponse = chat(messages);

        if (aiResponse != null) {
            String type = scoringEngine.parseDecisionType(aiResponse);
            aiResponse = scoringEngine.stripDecisionMarker(aiResponse);

            if ("end".equals(type) && remaining > 0) type = "next_question";
            if (remaining <= 0) type = "end";

            result.setContent(aiResponse);
            result.setType(type);
            result.setNextQuestion("follow_up".equals(type) ? currentQuestion
                    : "next_question".equals(type) ? currentQuestion + 1 : currentQuestion);
            result.setRemainingQuestions("follow_up".equals(type) ? remaining
                    : "next_question".equals(type) ? remaining - 1 : 0);
        } else {
            if (remaining <= 0) {
                result.setContent("感谢你的回答。本次面试的所有题目已经结束，我将为你生成面试评估报告。");
                result.setType("end");
                result.setNextQuestion(currentQuestion);
            } else {
                result.setContent(scoringEngine.generateFeedback(difficulty)
                        + "\n\n接下来是第" + (currentQuestion + 1) + "题：\n" + nextQuestionText);
                result.setType("next_question");
                result.setNextQuestion(currentQuestion + 1);
            }
            result.setRemainingQuestions(Math.max(0, remaining - 1));
        }
        return result;
    }

    /**
     * 简历面试专用：评估回答并返回下一话题（非流式路径）
     */
    public AIResultVO evaluateAndRespondForResume(String position, String round, String difficulty,
                                                   int currentQuestion, int totalQuestions,
                                                   String userAnswer,
                                                   List<Map<String, String>> history,
                                                   int maxFollowUp, String resumeContext) {
        int remaining = totalQuestions - currentQuestion;

        // 使用简历面试专用 system prompt
        String systemPrompt = promptBuilder.buildResumeInterviewSystemPrompt(
                position, round, difficulty, totalQuestions, maxFollowUp, resumeContext);

        // 使用简历面试专用 user prompt
        String userPrompt = promptBuilder.buildStreamUserPrompt(
                currentQuestion, "", userAnswer, "", remaining, history, maxFollowUp);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        if (history != null && !history.isEmpty()) messages.addAll(history);
        messages.add(Map.of("role", "user", "content", userPrompt));

        AIResultVO result = new AIResultVO();

        // 简历面试直接使用 chat + parseDecisionType（user prompt 要求【决策: xxx】格式，不是 JSON）
        String aiResponse = chat(messages);
        if (aiResponse != null) {
            String type = scoringEngine.parseDecisionType(aiResponse);
            aiResponse = scoringEngine.stripDecisionMarker(aiResponse);

            if ("end".equals(type) && remaining > 0) type = "next_question";
            if (remaining <= 0) type = "end";

            result.setContent(aiResponse);
            result.setType(type);
            result.setNextQuestion("follow_up".equals(type) ? currentQuestion
                    : "next_question".equals(type) ? currentQuestion + 1 : currentQuestion);
            result.setRemainingQuestions("follow_up".equals(type) ? remaining
                    : "next_question".equals(type) ? remaining - 1 : 0);
        } else {
            if (remaining <= 0) {
                result.setContent("感谢你的回答。本次面试的所有题目已经结束，我将为你生成面试评估报告。");
                result.setType("end");
                result.setNextQuestion(currentQuestion);
            } else {
                result.setContent(scoringEngine.generateFeedback(difficulty));
                result.setType("next_question");
                result.setNextQuestion(currentQuestion + 1);
                result.setRemainingQuestions(Math.max(0, remaining - 1));
            }
        }
        return result;
    }

    // ======================== 生成面试评分报告 ========================

    public ReportResultVO generateReport(String position, String round, String difficulty,
                                               List<Map<String, String>> conversation,
                                               List<Question> questions) {
        boolean hasUserAnswer = conversation.stream().anyMatch(m -> "user".equals(m.get("role")));
        if (!hasUserAnswer) {
            return scoringEngine.createEmptyReport("候选人未做任何回答，无法有效评估");
        }

        String conversationText = promptBuilder.buildConversationText(conversation);
        String scoringRules = promptBuilder.buildScoringRules(round, difficulty);
        String systemPrompt = promptBuilder.buildScoringSystemPrompt(position, round, scoringRules);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", "以下是面试对话记录：\n\n" + conversationText));

        // 优先使用结构化输出（response_format: json_object），AI 直接返回合法 JSON
        Map<String, Object> result = chatStructured(messages);
        if (result != null) {
            ReportResultVO report = parseStructuredReport(result);
            if (report != null) {
                // 系统公式验算：用难度加权算 totalScore，验证维度分
                return scoringEngine.verifyAndFixReport(report, questions, round);
            }
        }

        // 降级：普通 chat 调用（兼容旧模型）
        String aiResult = chat(messages);
        ReportResultVO parsed = scoringEngine.parseReportResult(aiResult);
        if (parsed != null) {
            return scoringEngine.verifyAndFixReport(parsed, questions, round);
        }

        // 最终降级：模板化评分
        return scoringEngine.generateFallbackReport(position, difficulty, conversation, questions, round);
    }

    /**
     * 解析结构化输出的报告（AI 可能不返回 totalScore 和 level）
     */
    private ReportResultVO parseStructuredReport(Map<String, Object> data) {
        try {
            ReportResultVO report = new ReportResultVO();
            // totalScore 和 level 由系统计算，AI 不返回
            report.setTechnicalScore(toInt(data.get("technicalScore")));
            report.setExpressionScore(toInt(data.get("expressionScore")));
            report.setLogicScore(toInt(data.get("logicScore")));
            report.setCompletenessScore(toInt(data.get("completenessScore")));
            report.setInnovationScore(toInt(data.get("innovationScore")));
            report.setSuggestion((String) data.get("suggestion"));

            // 解析 comments
            Object commentsObj = data.get("comments");
            if (commentsObj instanceof List) {
                List<ReportResultVO.Comment> comments = objectMapper.convertValue(commentsObj,
                        new com.fasterxml.jackson.core.type.TypeReference<List<ReportResultVO.Comment>>() {});
                // 补充 sortOrder
                for (int i = 0; i < comments.size(); i++) {
                    if (comments.get(i).getSortOrder() == null) {
                        comments.get(i).setSortOrder(i + 1);
                    }
                }
                report.setComments(comments);
            }

            return report;
        } catch (Exception e) {
            logger.error("解析结构化报告失败", e);
            return null;
        }
    }

    private Integer toInt(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try { return Integer.parseInt(obj.toString()); } catch (Exception e) { return null; }
    }

    // ======================== 生成 AI 深度诊断报告 ========================

    public DiagnosisDataVO generateDiagnosisReport(InterviewReport report,
                                                        List<ReportComment> comments,
                                                        List<InterviewMessage> messages,
                                                        String jobPosition,
                                                        Integer previousScore,
                                                        String interviewRound) {
        // 构建对话记录
        StringBuilder conversationBuilder = new StringBuilder();
        for (InterviewMessage msg : messages) {
            String role = "ai".equals(msg.getRole()) ? "【面试官】" : "【候选人】";
            conversationBuilder.append(role).append(msg.getContent()).append("\n\n");
        }

        // 构建题目信息
        StringBuilder questionsBuilder = new StringBuilder();
        if (comments != null) {
            for (int i = 0; i < comments.size(); i++) {
                ReportComment c = comments.get(i);
                questionsBuilder.append("第").append(i + 1).append("题：").append(c.getQuestionText())
                        .append("\n用户回答：").append(c.getUserAnswer())
                        .append("\n评分：").append(c.getScore())
                        .append("\n点评：").append(c.getComment()).append("\n\n");
            }
        }

        String prompt = promptBuilder.buildDiagnosisPrompt(jobPosition, interviewRound,
                report.getTotalScore(), report.getLevel(), previousScore,
                report.getTechnicalScore(), report.getExpressionScore(),
                report.getLogicScore(), report.getCompletenessScore(), report.getInnovationScore(),
                questionsBuilder.toString(), conversationBuilder.toString());

        List<Map<String, String>> msgList = new ArrayList<>();
        msgList.add(Map.of("role", "user", "content", prompt));

        // 优先使用结构化输出 — AI 直接返回合法 JSON
        Map<String, Object> structured = chatStructured(msgList);
        if (structured != null) {
            try {
                DiagnosisDataVO data = new DiagnosisDataVO();
                data.setKnowledgeAnalysis(objectMapper.convertValue(structured.get("knowledgeAnalysis"),
                        new TypeReference<List<Map<String, Object>>>() {}));
                data.setThinkingAnalysis(objectMapper.convertValue(structured.get("thinkingAnalysis"),
                        new TypeReference<Map<String, Object>>() {}));
                data.setMistakePatterns(objectMapper.convertValue(structured.get("mistakePatterns"),
                        new TypeReference<List<Map<String, Object>>>() {}));
                data.setLearningPlan(objectMapper.convertValue(structured.get("learningPlan"),
                        new TypeReference<Map<String, Object>>() {}));
                data.setDetailedComments(objectMapper.convertValue(structured.get("detailedComments"),
                        new TypeReference<List<Map<String, Object>>>() {}));
                return data;
            } catch (Exception e) {
                logger.error("解析结构化诊断报告失败", e);
            }
        }

        // 降级：普通 chat + regex 提取 JSON
        String result = chat(msgList);
        if (result != null) {
            try {
                String jsonStr = scoringEngine.extractJson(result);
                JsonNode root = objectMapper.readTree(jsonStr);
                DiagnosisDataVO data = new DiagnosisDataVO();
                data.setKnowledgeAnalysis(objectMapper.convertValue(root.path("knowledgeAnalysis"),
                        new TypeReference<List<Map<String, Object>>>() {}));
                data.setThinkingAnalysis(objectMapper.convertValue(root.path("thinkingAnalysis"),
                        new TypeReference<Map<String, Object>>() {}));
                data.setMistakePatterns(objectMapper.convertValue(root.path("mistakePatterns"),
                        new TypeReference<List<Map<String, Object>>>() {}));
                data.setLearningPlan(objectMapper.convertValue(root.path("learningPlan"),
                        new TypeReference<Map<String, Object>>() {}));
                data.setDetailedComments(objectMapper.convertValue(root.path("detailedComments"),
                        new TypeReference<List<Map<String, Object>>>() {}));
                return data;
            } catch (Exception e) {
                logger.error("解析诊断报告JSON失败", e);
            }
        }
        return scoringEngine.getDefaultDiagnosisData();
    }

    // ======================== 简历解析 ========================

    /**
     * 解析简历文本为结构化 ResumeData
     * 风格与 generateDiagnosisReport 一致：优先结构化输出，降级为普通 chat + JSON 提取
     *
     * @param rawText PDF/TXT 提取的纯文本
     * @return 解析后的 Map（可直接转 ResumeData），失败返回 null
     */
    public Map<String, Object> parseResume(String rawText) {
        if (rawText == null || rawText.isBlank()) return null;

        // 截断过长文本（DeepSeek 上下文窗口限制）
        String safeText = rawText.length() > 8000 ? rawText.substring(0, 8000) : rawText;

        String prompt = "你是一个专业的简历解析器。请从以下简历文本中提取结构化信息，严格按JSON格式返回。\n\n" +
                "要求：\n" +
                "1. 只返回JSON，不要包含其他文字\n" +
                "2. 如果某个字段在简历中找不到，设为 null\n" +
                "3. skills 中的 level 按以下标准判断：\n" +
                "   - 简历写了\"精通\"→ \"精通\"\n" +
                "   - 简历写了\"熟练\"或有3年以上经验 → \"熟练\"\n" +
                "   - 简历写了\"熟悉\"或有1-3年经验 → \"熟悉\"\n" +
                "   - 其他 → \"了解\"\n\n" +
                "JSON 结构：\n" +
                "{\n" +
                "  \"basicInfo\": {\"name\":\"姓名\",\"email\":\"邮箱\",\"phone\":\"电话\",\"targetPosition\":\"目标岗位\",\"location\":\"所在城市\",\"yearsOfExperience\":工作年限整数},\n" +
                "  \"education\": [{\"school\":\"学校\",\"degree\":\"学位\",\"major\":\"专业\",\"startDate\":\"起始时间\",\"endDate\":\"结束时间\"}],\n" +
                "  \"workExperience\": [{\"company\":\"公司\",\"position\":\"职位\",\"startDate\":\"起始\",\"endDate\":\"结束\",\"description\":\"职责描述\"}],\n" +
                "  \"projects\": [{\"name\":\"项目名\",\"role\":\"角色\",\"techStack\":[\"技术1\",\"技术2\"],\"description\":\"项目描述\"}],\n" +
                "  \"skills\": [{\"name\":\"技能名\",\"level\":\"精通/熟练/熟悉/了解\",\"years\":使用年限整数或null}],\n" +
                "  \"certifications\": [\"证书名称\"],\n" +
                "  \"selfEvaluation\": \"自我评价\"\n" +
                "}\n\n" +
                "---\n以下是简历文本：\n" + safeText;

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", prompt));

        // 优先使用结构化输出
        Map<String, Object> structured = chatStructured(messages);
        if (structured != null) {
            logger.info("简历解析成功（结构化输出）");
            return structured;
        }

        // 降级：普通 chat + JSON 提取
        String result = chat(messages);
        if (result != null) {
            try {
                String jsonStr = scoringEngine.extractJson(result);
                Map<String, Object> parsed = objectMapper.readValue(jsonStr,
                        new TypeReference<Map<String, Object>>() {});
                logger.info("简历解析成功（降级提取）");
                return parsed;
            } catch (Exception e) {
                logger.error("简历解析JSON提取失败", e);
            }
        }

        logger.warn("简历解析完全失败");
        return null;
    }
}
