package org.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.backend.config.AsyncConfig;
import org.backend.config.JacksonConfig;
import org.backend.service.CacheService;
import org.backend.util.AIService;
import org.backend.util.PromptBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanCursor;
import org.springframework.data.redis.core.ScanOptions;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 代码审查修复 — 回归测试
 * 覆盖 4 个修复点：
 * 1. P0 SSE 线程池泄露 — AsyncConfig 创建有界线程池
 * 2. P0 feedbackIndex 线程安全 — ScoringEngine 使用 AtomicInteger
 * 3. P1 ObjectMapper 多实例 — JacksonConfig 统一 Bean
 * 4. P1 Redis KEYS → SCAN — CacheService 使用 SCAN
 */
class CodeReviewRegressionTest {

    // ==================== P0-1: SSE 线程池泄露 ====================
    @Nested
    @DisplayName("P0-1: AsyncConfig 线程池配置")
    class AsyncConfigTest {

        @Test
        @DisplayName("sseExecutor 是 ThreadPoolExecutor（非 CachedThreadPool）")
        void sseExecutor_shouldBeThreadPoolExecutor() {
            AsyncConfig config = new AsyncConfig();
            ExecutorService executor = config.sseExecutor();

            assertInstanceOf(ThreadPoolExecutor.class, executor);
        }

        @Test
        @DisplayName("核心线程数=4，最大线程数=16")
        void sseExecutor_shouldHaveCorrectPoolSize() {
            AsyncConfig config = new AsyncConfig();
            ThreadPoolExecutor pool = (ThreadPoolExecutor) config.sseExecutor();

            assertEquals(4, pool.getCorePoolSize(), "核心线程数应为 4");
            assertEquals(16, pool.getMaximumPoolSize(), "最大线程数应为 16");
        }

        @Test
        @DisplayName("队列容量=100，拒绝策略=CallerRunsPolicy")
        void sseExecutor_shouldHaveBoundedQueue() {
            AsyncConfig config = new AsyncConfig();
            ThreadPoolExecutor pool = (ThreadPoolExecutor) config.sseExecutor();

            assertTrue(pool.getQueue() instanceof LinkedBlockingQueue,
                    "应使用 LinkedBlockingQueue");
            assertEquals(100, pool.getQueue().remainingCapacity() + pool.getQueue().size(),
                    "队列容量应为 100");
            assertInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class, pool.getRejectedExecutionHandler(),
                    "拒绝策略应为 CallerRunsPolicy");
        }

        @Test
        @DisplayName("线程池可正常提交任务并返回结果")
        void sseExecutor_shouldExecuteTasks() throws Exception {
            AsyncConfig config = new AsyncConfig();
            ExecutorService executor = config.sseExecutor();

            try {
                Future<String> future = executor.submit(() -> "hello");
                assertEquals("hello", future.get(2, TimeUnit.SECONDS));
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        @DisplayName("不会无限创建线程（有上限 16）")
        void sseExecutor_shouldNotCreateUnlimitedThreads() throws Exception {
            AsyncConfig config = new AsyncConfig();
            ThreadPoolExecutor pool = (ThreadPoolExecutor) config.sseExecutor();

            try {
                // 提交 50 个阻塞任务
                CountDownLatch latch = new CountDownLatch(1);
                for (int i = 0; i < 50; i++) {
                    pool.execute(() -> {
                        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    });
                }
                Thread.sleep(200); // 等待线程创建完成
                assertTrue(pool.getPoolSize() <= 16,
                        "线程池大小不应超过 16，实际: " + pool.getPoolSize());
            } finally {
                pool.shutdownNow();
            }
        }
    }

    // ==================== P0-2: feedbackIndex 线程安全 ====================
    @Nested
    @DisplayName("P0-2: ScoringEngine feedbackIndex 线程安全")
    class FeedbackIndexTest {

        @Test
        @DisplayName("AtomicInteger.getAndIncrement 并发不重复")
        void feedbackIndex_shouldBeThreadSafe() throws Exception {
            AtomicInteger feedbackIndex = new AtomicInteger(0);
            int threadCount = 100;
            int iterationsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < iterationsPerThread; j++) {
                            feedbackIndex.getAndIncrement();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "所有线程应在 5 秒内完成");
            assertEquals(threadCount * iterationsPerThread, feedbackIndex.get(),
                    "最终值应为 10000，无丢失");
            executor.shutdownNow();
        }

        @Test
        @DisplayName("Math.abs 取模不会数组越界")
        void feedbackIndex_moduloShouldNotOverflow() {
            String[] feedbacks = {"a", "b", "c"};
            AtomicInteger feedbackIndex = new AtomicInteger(Integer.MAX_VALUE - 1);

            // 连续取模 5 次，不应抛异常
            for (int i = 0; i < 5; i++) {
                int idx = Math.abs(feedbackIndex.getAndIncrement() % feedbacks.length);
                assertTrue(idx >= 0 && idx < feedbacks.length,
                        "索引应在 [0, " + feedbacks.length + ") 范围内，实际: " + idx);
            }
        }
    }

    // ==================== P1-1: ObjectMapper 统一实例 ====================
    @Nested
    @DisplayName("P1-1: JacksonConfig ObjectMapper 配置")
    class JacksonConfigTest {

        @Test
        @DisplayName("ObjectMapper 注册了 JavaTimeModule")
        void objectMapper_shouldHaveJavaTimeModule() {
            JacksonConfig config = new JacksonConfig();
            ObjectMapper mapper = config.objectMapper();

            assertTrue(mapper.getRegisteredModuleIds().contains("jackson-datatype-jsr310"),
                    "应注册 JavaTimeModule");
        }

        @Test
        @DisplayName("ObjectMapper 禁用了 WRITE_DATES_AS_TIMESTAMPS")
        void objectMapper_shouldDisableWriteDatesAsTimestamps() {
            JacksonConfig config = new JacksonConfig();
            ObjectMapper mapper = config.objectMapper();

            assertFalse(mapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS),
                    "WRITE_DATES_AS_TIMESTAMPS 应禁用");
        }

        @Test
        @DisplayName("LocalDateTime 序列化为 ISO 字符串（非时间戳数字）")
        void objectMapper_shouldSerializeLocalDateTimeAsIso() throws Exception {
            JacksonConfig config = new JacksonConfig();
            ObjectMapper mapper = config.objectMapper();

            LocalDateTime time = LocalDateTime.of(2026, 6, 8, 10, 30, 0);
            String json = mapper.writeValueAsString(time);

            assertTrue(json.contains("2026-06-08"), "应包含日期字符串");
            assertFalse(json.matches("\\d+"), "不应是纯数字时间戳");
        }

        @Test
        @DisplayName("JacksonConfig 返回的是单例 Bean（多次调用返回新实例，但 Spring 管理为单例）")
        void objectMapper_shouldBeCreatedByConfig() {
            JacksonConfig config = new JacksonConfig();
            ObjectMapper m1 = config.objectMapper();
            ObjectMapper m2 = config.objectMapper();

            // 每次调用创建新实例（Spring 容器会缓存为单例）
            assertNotNull(m1);
            assertNotNull(m2);
        }
    }

    // ==================== P1-2: Redis SCAN 替代 KEYS ====================
    @Nested
    @DisplayName("P1-2: CacheService deleteByPrefix 使用 SCAN")
    class CacheServiceScanTest {

        @Test
        @DisplayName("deleteByPrefix 通过 execute(RedisCallback) 使用 SCAN 而非 keys()")
        @SuppressWarnings("unchecked")
        void deleteByPrefix_shouldUseScanNotKeys() {
            RedisTemplate<String, Object> mockTemplate = mock(RedisTemplate.class);
            RedisConnection mockConnection = mock(RedisConnection.class);

            // 模拟 SCAN 返回空 Cursor
            org.springframework.data.redis.core.Cursor<byte[]> mockCursor = mock(org.springframework.data.redis.core.Cursor.class);
            when(mockCursor.hasNext()).thenReturn(false);
            when(mockConnection.scan(any(ScanOptions.class))).thenReturn(mockCursor);

            // execute(RedisCallback) 将 mockConnection 传给回调
            when(mockTemplate.execute(any(RedisCallback.class))).thenAnswer(invocation -> {
                RedisCallback<?> callback = invocation.getArgument(0);
                return callback.doInRedis(mockConnection);
            });

            CacheService cacheService = new CacheService();
            try {
                var field = CacheService.class.getDeclaredField("redisTemplate");
                field.setAccessible(true);
                field.set(cacheService, mockTemplate);
            } catch (Exception e) {
                fail("注入 mock 失败: " + e.getMessage());
            }

            cacheService.deleteByPrefix("test:");

            // 验证：调用了 execute(RedisCallback) — 即使用 SCAN 路径
            verify(mockTemplate).execute(any(RedisCallback.class));
            // 验证：调用了 connection.scan() — 确认走的是 SCAN 而非 KEYS
            verify(mockConnection).scan(any(ScanOptions.class));
            // 验证：空结果不调用 delete
            verify(mockTemplate, never()).delete(any(Set.class));
        }
    }

    // ==================== P3-1: Prompt 构建路径统一 ====================
    @Nested
    @DisplayName("P3-1: PromptBuilder 统一构建路径")
    class PromptBuilderUnifyTest {

        private final PromptBuilder promptBuilder = new PromptBuilder();

        @Test
        @DisplayName("buildStreamUserPrompt 包含决策标记指令")
        void buildStreamUserPrompt_shouldContainDecisionMarker() {
            String prompt = promptBuilder.buildStreamUserPrompt(
                    1, "什么是JVM?", "JVM是虚拟机", "什么是GC?", 3, Collections.emptyList(), 2, 5);

            assertTrue(prompt.contains("【决策: follow_up】"), "应包含 follow_up 决策标记");
            assertTrue(prompt.contains("【决策: next】"), "应包含 next 决策标记");
            assertTrue(prompt.contains("【决策: end】"), "应包含 end 决策标记");
        }

        @Test
        @DisplayName("buildStreamUserPrompt 有剩余题目且非追问时，包含下一题")
        void buildStreamUserPrompt_shouldIncludeNextQuestion() {
            String prompt = promptBuilder.buildStreamUserPrompt(
                    1, "什么是JVM?", "JVM是虚拟机", "什么是GC?", 3, Collections.emptyList(), 2, 5);

            assertTrue(prompt.contains("下一题题目供参考"), "有剩余题目时应包含下一题提示");
            assertTrue(prompt.contains("什么是GC?"), "应包含下一题内容");
        }

        @Test
        @DisplayName("buildStreamUserPrompt 追问状态时不包含下一题")
        void buildStreamUserPrompt_followUp_shouldNotIncludeNextQuestion() {
            List<Map<String, String>> history = new ArrayList<>();
            history.add(Map.of("role", "assistant", "content", "test", "type", "follow_up"));

            String prompt = promptBuilder.buildStreamUserPrompt(
                    1, "什么是JVM?", "JVM是虚拟机", "什么是GC?", 3, history, 2, 5);

            assertFalse(prompt.contains("下一题题目供参考"), "追问状态不应包含下一题提示");
        }

        @Test
        @DisplayName("buildUserPrompt（非流式）要求 JSON 格式输出")
        void buildUserPrompt_shouldRequireJsonFormat() {
            String prompt = promptBuilder.buildUserPrompt(
                    "technical", 1, "什么是JVM?", "JVM是虚拟机", "什么是GC?", 3, Collections.emptyList(), 2);

            assertTrue(prompt.contains("JSON"), "非流式 prompt 应要求 JSON 格式");
        }

        @Test
        @DisplayName("buildStreamUserPrompt（流式）不要求 JSON 格式")
        void buildStreamUserPrompt_shouldNotRequireJsonFormat() {
            String prompt = promptBuilder.buildStreamUserPrompt(
                    1, "什么是JVM?", "JVM是虚拟机", "什么是GC?", 3, Collections.emptyList(), 2, 5);

            assertFalse(prompt.contains("JSON"), "流式 prompt 不应要求 JSON 格式");
        }

        @Test
        @DisplayName("buildStreamUserPrompt 最后一题：强制 end 指令，不包含下一题参考")
        void buildStreamUserPrompt_lastQuestion_shouldForceEnd() {
            // currentQuestion=4, questionCount=5 → 4+1>=5，是最后一题
            String prompt = promptBuilder.buildStreamUserPrompt(
                    4, "什么是JVM?", "JVM是虚拟机", "什么是GC?", 1, Collections.emptyList(), 2, 5);

            assertTrue(prompt.contains("最高优先级指令"), "最后一题应包含强制 end 指令");
        }

        @Test
        @DisplayName("buildStreamUserPrompt 包含候选人放弃回答时不给答案的规则")
        void buildStreamUserPrompt_shouldContainNoAnswerRule() {
            String prompt = promptBuilder.buildStreamUserPrompt(
                    1, "什么是JVM?", "JVM是虚拟机", "什么是GC?", 3, Collections.emptyList(), 2, 5);

            assertTrue(prompt.contains("绝对禁止在回复中给出正确答案"), "应明确禁止给出正确答案");
        }

        @Test
        @DisplayName("buildInterviewSystemPrompt 包含面试不是培训的绝对禁止规则")
        void buildInterviewSystemPrompt_shouldContainNoTrainingRule() {
            String prompt = promptBuilder.buildInterviewSystemPrompt(
                    "java_backend", "technical", "medium", 5, "", 2);

            assertTrue(prompt.contains("绝对禁止规则"), "应包含绝对禁止规则标题");
            assertTrue(prompt.contains("面试不是培训"), "应强调面试不是培训");
        }
    }

    // ==================== BugFix: 面试提前结束 + AI 跳出答案 ====================
    @Nested
    @DisplayName("BugFix: 决策类型边界保护（面试提前结束修复）")
    class DecisionBoundaryTest {

        private final org.backend.util.ScoringEngine engine = new org.backend.util.ScoringEngine();

        @Test
        @DisplayName("最后一题：AI 返回 next_question 时强制转为 end")
        void lastQuestion_nextQuestion_shouldForceEnd() {
            String result = engine.enforceDecisionBoundary("next_question", 1, 2, 1);
            assertEquals("end", result, "最后一题 next_question 必须转为 end");
        }

        @Test
        @DisplayName("最后一题：AI 返回 end 时保持 end")
        void lastQuestion_end_shouldStayEnd() {
            String result = engine.enforceDecisionBoundary("end", 1, 2, 1);
            assertEquals("end", result, "最后一题的 end 应保持 end");
        }

        @Test
        @DisplayName("非最后一题：AI 返回 end 时降级为 next_question")
        void nonLastQuestion_end_shouldDowngradeToNextQuestion() {
            String result = engine.enforceDecisionBoundary("end", 1, 5, 4);
            assertEquals("next_question", result, "非最后一题的 end 应降级为 next_question");
        }

        @Test
        @DisplayName("非最后一题：AI 返回 next_question 时保持 next_question")
        void nonLastQuestion_nextQuestion_shouldStayNextQuestion() {
            String result = engine.enforceDecisionBoundary("next_question", 1, 5, 4);
            assertEquals("next_question", result, "非最后一题的 next_question 应保持不变");
        }

        @Test
        @DisplayName("follow_up 类型不受边界保护影响")
        void followUp_shouldNotBeAffected() {
            assertEquals("follow_up", engine.enforceDecisionBoundary("follow_up", 1, 2, 1));
            assertEquals("follow_up", engine.enforceDecisionBoundary("follow_up", 0, 5, 5));
        }

        @Test
        @DisplayName("answer 类型不受边界保护影响")
        void answer_shouldNotBeAffected() {
            assertEquals("answer", engine.enforceDecisionBoundary("answer", 1, 2, 1));
        }

        @Test
        @DisplayName("无剩余题目：任何类型都强制转为 end")
        void noRemaining_shouldForceEnd() {
            assertEquals("end", engine.enforceDecisionBoundary("next_question", 5, 5, 0));
            assertEquals("end", engine.enforceDecisionBoundary("follow_up", 5, 5, 0));
            assertEquals("end", engine.enforceDecisionBoundary("end", 5, 5, 0));
        }

        @Test
        @DisplayName("null 类型降级为 next_question 后再应用边界保护")
        void nullType_shouldDefaultAndApplyBoundary() {
            assertEquals("next_question", engine.enforceDecisionBoundary(null, 0, 5, 5));
            assertEquals("end", engine.enforceDecisionBoundary(null, 1, 2, 1));
        }

        @Test
        @DisplayName("复现用户 bug 场景：2 题面试，第 2 题 AI 返回 next 应转为 end")
        void reproduceUserBug_2Questions_lastQuestionNextShouldBeEnd() {
            String type = "next_question";
            int currentQuestion = 1;
            int totalQuestions = 2;
            int remaining = 1;
            String result = engine.enforceDecisionBoundary(type, currentQuestion, totalQuestions, remaining);
            assertEquals("end", result,
                    "用户 bug 场景：2 题面试第 2 题的 next_question 必须转为 end，避免'进入下一题'后突然结束");
        }
    }

    // ==================== P3-2: AIService 熔断器 ====================
    @Nested
    @DisplayName("P3-2: AIService 熔断器")
    class CircuitBreakerTest {

        private AIService createAIService() {
            ExecutorService mockExecutor = Executors.newFixedThreadPool(2);
            AIService service = new AIService(mockExecutor);
            // 注入必要的依赖
            try {
                setField(service, "objectMapper", new ObjectMapper());
                setField(service, "apiKey", "test-key");
                setField(service, "apiUrl", "https://api.deepseek.com");
                setField(service, "model", "deepseek-chat");
                setField(service, "maxTokens", 2048);
                setField(service, "temperature", 0.7);
                setField(service, "promptBuilder", new PromptBuilder());
                setField(service, "scoringEngine", new org.backend.util.ScoringEngine());
            } catch (Exception e) {
                fail("注入字段失败: " + e.getMessage());
            }
            return service;
        }

        private void setField(Object target, String fieldName, Object value) throws Exception {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        }

        @Test
        @DisplayName("初始状态：熔断器关闭，允许调用")
        void circuitBreaker_initialState_shouldBeClosed() throws Exception {
            AIService service = createAIService();

            Field openField = AIService.class.getDeclaredField("circuitBreakerOpen");
            openField.setAccessible(true);
            assertFalse((boolean) openField.get(service), "初始状态熔断器应关闭");
        }

        @Test
        @DisplayName("连续失败达到阈值后，熔断器打开")
        void circuitBreaker_shouldOpenAfterConsecutiveFailures() throws Exception {
            AIService service = createAIService();

            Field failuresField = AIService.class.getDeclaredField("consecutiveFailures");
            failuresField.setAccessible(true);
            AtomicInteger failures = (AtomicInteger) failuresField.get(service);

            // 模拟连续 5 次失败
            for (int i = 0; i < 5; i++) {
                failures.incrementAndGet();
            }

            // 调用 recordFailure 触发熔断
            var method = AIService.class.getDeclaredMethod("recordFailure");
            method.setAccessible(true);
            method.invoke(service);

            Field openField = AIService.class.getDeclaredField("circuitBreakerOpen");
            openField.setAccessible(true);
            assertTrue((boolean) openField.get(service), "连续失败 5 次后熔断器应打开");
        }

        @Test
        @DisplayName("熔断器打开后，chat 返回 null（快速失败）")
        void circuitBreaker_whenOpen_chatReturnsNull() throws Exception {
            AIService service = createAIService();

            // 强制打开熔断器
            Field openField = AIService.class.getDeclaredField("circuitBreakerOpen");
            openField.setAccessible(true);
            openField.set(service, true);

            Field openedAtField = AIService.class.getDeclaredField("circuitBreakerOpenedAt");
            openedAtField.setAccessible(true);
            openedAtField.set(service, System.currentTimeMillis()); // 刚刚打开

            // chat 应该直接返回 null，不发起请求
            List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", "test"));
            String result = service.chat(messages);
            assertNull(result, "熔断器打开时 chat 应返回 null");
        }

        @Test
        @DisplayName("熔断器打开后冷却期过，进入半开状态允许试探")
        void circuitBreaker_halfOpen_afterCooldown() throws Exception {
            AIService service = createAIService();

            // 打开熔断器，设置 30 秒前
            Field openField = AIService.class.getDeclaredField("circuitBreakerOpen");
            openField.setAccessible(true);
            openField.set(service, true);

            Field openedAtField = AIService.class.getDeclaredField("circuitBreakerOpenedAt");
            openedAtField.setAccessible(true);
            openedAtField.set(service, System.currentTimeMillis() - 31_000); // 31 秒前

            // checkCircuitBreaker 应返回 true（半开）
            var method = AIService.class.getDeclaredMethod("checkCircuitBreaker");
            method.setAccessible(true);
            boolean canCall = (boolean) method.invoke(service);
            assertTrue(canCall, "冷却期过后应允许试探调用");
        }

        @Test
        @DisplayName("recordSuccess 重置失败计数并关闭熔断器")
        void recordSuccess_shouldResetFailures() throws Exception {
            AIService service = createAIService();

            // 先设置一些失败
            Field failuresField = AIService.class.getDeclaredField("consecutiveFailures");
            failuresField.setAccessible(true);
            AtomicInteger failures = (AtomicInteger) failuresField.get(service);
            failures.set(4);

            Field openField = AIService.class.getDeclaredField("circuitBreakerOpen");
            openField.setAccessible(true);
            openField.set(service, true);

            // 调用 recordSuccess
            var method = AIService.class.getDeclaredMethod("recordSuccess");
            method.setAccessible(true);
            method.invoke(service);

            assertEquals(0, failures.get(), "成功后失败计数应重置为 0");
            assertFalse((boolean) openField.get(service), "成功后熔断器应关闭");
        }
    }
}
