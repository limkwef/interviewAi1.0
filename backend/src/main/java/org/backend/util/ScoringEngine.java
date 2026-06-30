package org.backend.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.backend.vo.DiagnosisDataVO;
import org.backend.entity.Question;
import org.backend.vo.ReportResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 评分引擎 — 解析 AI 返回结果、降级评分、决策判断
 *
 * 职责：解析 AI 回复、生成降级评分、模板化反馈
 * 不包含：API 调用、Prompt 构建
 */
@Component
public class ScoringEngine {

    private static final Logger logger = LoggerFactory.getLogger(ScoringEngine.class);

    @Autowired
    private ObjectMapper objectMapper;

    /** 反馈轮询索引（避免连续相同反馈），使用 AtomicInteger 保证线程安全 */
    private final AtomicInteger feedbackIndex = new AtomicInteger(0);

    // ======================== 决策解析（正则容错版） ========================

    /** 从 AI 回复中解析决定标记（follow_up / next / end）
     *  支持格式（按优先级）：
     *    1. 【决策: follow_up】  — 各种括号 + 可选空格
     *    2. {"decision":"follow_up"} — JSON 格式
     *    3. 关键词降级（只看最后 200 字，避免误判） */
    public String parseDecisionType(String response) {
        if (response == null) return "next_question";

        // 1. 正则匹配：各种括号 + 可选空格 + 决策关键词
        Pattern p = Pattern.compile(
            "[\\[【（(]\\s*决策\\s*[:：]\\s*(follow_up|next|end|answer)\\s*[\\]】）)]",
            Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(response);
        if (m.find()) {
            return switch (m.group(1).toLowerCase()) {
                case "follow_up" -> "follow_up";
                case "end" -> "end";
                case "answer" -> "answer";
                default -> "next_question";
            };
        }

        // 2. 兼容 JSON 格式
        Pattern jp = Pattern.compile("\"decision\"\\s*:\\s*\"(follow_up|next|end|answer)\"", Pattern.CASE_INSENSITIVE);
        Matcher jm = jp.matcher(response);
        if (jm.find()) {
            return switch (jm.group(1).toLowerCase()) {
                case "follow_up" -> "follow_up";
                case "end" -> "end";
                case "answer" -> "answer";
                default -> "next_question";
            };
        }

        // 3. 兼容 type=xxx 格式
        Pattern tp = Pattern.compile("type\\s*=\\s*(follow_up|next|end|answer)", Pattern.CASE_INSENSITIVE);
        Matcher tm = tp.matcher(response);
        if (tm.find()) {
            return switch (tm.group(1).toLowerCase()) {
                case "follow_up" -> "follow_up";
                case "end" -> "end";
                case "answer" -> "answer";
                default -> "next_question";
            };
        }

        // 3. 关键词降级（只看最后 200 字，避免误判）
        String tail = response.length() > 200 ? response.substring(response.length() - 200) : response;
        if (tail.contains("面试结束") || tail.contains("所有题目")) return "end";
        if (tail.contains("下一题") || tail.contains("接下来是第")) return "next_question";
        return "follow_up";
    }

    /** 去掉 AI 回复末尾的决定标记
     *  同时支持 【决策: xxx】 和 {"decision":"xxx"} */
    public String stripDecisionMarker(String response) {
        if (response == null) return "";

        // 去掉新格式 【决策: xxx】（各种括号）
        Pattern p = Pattern.compile(
            "[\\[【（(]\\s*决策\\s*[:：]\\s*(?:follow_up|next|end)\\s*[\\]】）)]",
            Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(response);
        if (m.find()) {
            return response.substring(0, m.start()).trim();
        }

        // 去掉旧格式 {"decision":"xxx"}
        int idx = response.lastIndexOf("{\"decision\"");
        if (idx >= 0) return response.substring(0, idx).trim();

        return response.trim();
    }

    /** 从文本中提取 JSON（去掉前后的非 JSON 内容） */
    public String extractJson(String text) {
        if (text == null) return "{}";
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start >= 0 && end > start) return text.substring(start, end + 1);
        return text;
    }

    /**
     * 决策类型边界保护（统一规则，避免散落在多个调用点）：
     * <ul>
     *   <li>非最后一题：AI 想结束面试（end）时，降级为进入下一题（next_question），防止过早结束</li>
     *   <li>最后一题：AI 想进入下一题（next_question）时，强制结束面试（end），避免出现"进入下一题"后突然结束的体验</li>
     *   <li>无剩余题目：强制结束</li>
     * </ul>
     *
     * @param type           AI 解析出的决策类型（follow_up / next_question / end / answer）
     * @param currentQuestion 当前题号（0-based）
     * @param totalQuestions  总题数
     * @param remaining       剩余题数（totalQuestions - currentQuestion）
     * @return 边界保护后的决策类型
     */
    public String enforceDecisionBoundary(String type, int currentQuestion, int totalQuestions, int remaining) {
        if (type == null) type = "next_question";
        boolean isLastQuestion = totalQuestions > 0 && currentQuestion + 1 >= totalQuestions;
        // 非最后一题：end → next_question（防止 AI 过早结束面试）
        if ("end".equals(type) && remaining > 0 && !isLastQuestion) {
            return "next_question";
        }
        // 最后一题：next_question → end（没有下一题了，避免"进入下一题"后突然结束）
        if ("next_question".equals(type) && isLastQuestion) {
            return "end";
        }
        // 无剩余题目：强制结束
        if (remaining <= 0) {
            return "end";
        }
        return type;
    }

    // ======================== 系统公式算总分（核心） ========================

    /** 难度权重映射：easy=1, medium=2, hard=3 */
    private int getDifficultyWeight(String difficulty) {
        if (difficulty == null) return 2;
        return switch (difficulty.toLowerCase()) {
            case "easy" -> 1;
            case "hard" -> 3;
            default -> 2; // medium
        };
    }

    /**
     * 用系统公式验算总分（替代 AI 返回的 totalScore）
     *
     * 公式：totalScore = Σ(每题分数 × 难度权重) / Σ(难度权重)
     *
     * @param comments    AI 返回的每题评分
     * @param questions   题目列表（含 difficulty 字段）
     * @return 难度加权后的总分
     */
    public int computeWeightedTotalScore(List<ReportResultVO.Comment> comments,
                                          List<Question> questions) {
        if (comments == null || comments.isEmpty()) return 0;

        double weightedSum = 0;
        double totalWeight = 0;

        for (int i = 0; i < comments.size(); i++) {
            int score = comments.get(i).getScore() != null ? comments.get(i).getScore() : 0;
            // 匹配题目难度：按顺序匹配 comments 和 questions
            String diff = (questions != null && i < questions.size())
                    ? questions.get(i).getDifficulty() : null;
            int weight = getDifficultyWeight(diff);
            weightedSum += score * weight;
            totalWeight += weight;
        }

        if (totalWeight == 0) return 0;
        int result = (int) Math.round(weightedSum / totalWeight);
        return Math.min(100, Math.max(0, result));
    }

    /**
     * 用系统公式验算各维度分数
     *
     * 公式：维度分 = AI维度分（保留，但 clamp 到合理范围）
     * 如果 AI 维度分与加权总分偏差过大（>15分），则按权重比例从总分推算
     *
     * 权重：技术40% + 表达20% + 逻辑20% + 完整性10% + 创新性10%
     */
    public void verifyDimensionScores(ReportResultVO report, int weightedTotal, String round) {
        int tech = clamp(report.getTechnicalScore(), 0, 100);
        int expr = clamp(report.getExpressionScore(), 0, 100);
        int logic = clamp(report.getLogicScore(), 0, 100);
        int comp = clamp(report.getCompletenessScore(), 0, 100);
        int inno = clamp(report.getInnovationScore(), 0, 100);

        // 根据面试类型使用不同权重
        int formulaTotal;
        if ("hr".equals(round)) {
            // HR面试权重：表达30% + 团队协作25% + 学习能力20% + 职业规划15% + 责任感10%
            formulaTotal = (int) Math.round(expr * 0.3 + logic * 0.25 + comp * 0.2 + inno * 0.15 + tech * 0.1);
        } else if ("comprehensive".equals(round)) {
            // 综合面试权重：技术25% + 表达25% + 逻辑22% + 完整性15% + 创新性13%
            formulaTotal = (int) Math.round(tech * 0.25 + expr * 0.25 + logic * 0.22 + comp * 0.15 + inno * 0.13);
        } else {
            // 技术面试权重：技术40% + 表达20% + 逻辑20% + 完整性10% + 创新性10%
            formulaTotal = (int) Math.round(tech * 0.4 + expr * 0.2 + logic * 0.2 + comp * 0.1 + inno * 0.1);
        }

        // 如果 AI 维度分算出的总分与加权总分偏差 > 15，按比例修正各维度
        if (Math.abs(formulaTotal - weightedTotal) > 15 && formulaTotal > 0) {
            double ratio = (double) weightedTotal / formulaTotal;
            tech = clamp((int) Math.round(tech * ratio), 0, 100);
            expr = clamp((int) Math.round(expr * ratio), 0, 100);
            logic = clamp((int) Math.round(logic * ratio), 0, 100);
            comp = clamp((int) Math.round(comp * ratio), 0, 100);
            inno = clamp((int) Math.round(inno * ratio), 0, 100);
            logger.info("AI维度分偏差过大（公式总分{} vs 加权总分{}），已按比例修正", formulaTotal, weightedTotal);
        }

        report.setTechnicalScore(tech);
        report.setExpressionScore(expr);
        report.setLogicScore(logic);
        report.setCompletenessScore(comp);
        report.setInnovationScore(inno);
    }

    /**
     * 对 AI 返回的报告做系统级验算
     * 1. 用难度加权算 totalScore（替代 AI 的 totalScore）
     * 2. 验证各维度分，偏差过大则修正
     * 3. 用计算后的 totalScore 确定 level
     */
    public ReportResultVO verifyAndFixReport(ReportResultVO report, List<Question> questions, String round) {
        if (report == null) return null;

        // 1. 用难度加权算总分
        int weightedTotal = computeWeightedTotalScore(report.getComments(), questions);
        int aiTotal = report.getTotalScore() != null ? report.getTotalScore() : 0;

        logger.info("AI总分={}, 加权总分={}", aiTotal, weightedTotal);

        // 2. 验证各维度分（根据面试类型使用不同权重）
        verifyDimensionScores(report, weightedTotal, round);

        // 3. 用加权总分覆盖 AI 的 totalScore
        report.setTotalScore(weightedTotal);

        // 4. 用加权总分确定等级
        report.setLevel(computeLevel(weightedTotal));

        // 5. 重新计算 suggestion（基于加权总分）
        // suggestion 由 AI 生成，保留原样即可

        return report;
    }

    private int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    // ======================== 面试评分报告解析 ========================

    /** 解析 AI 返回的评分 JSON */
    public ReportResultVO parseReportResult(String aiResult) {
        if (aiResult == null) return null;
        try {
            String jsonStr = aiResult;
            int start = aiResult.indexOf("{");
            int end = aiResult.lastIndexOf("}");
            if (start >= 0 && end > start) jsonStr = aiResult.substring(start, end + 1);
            return objectMapper.readValue(jsonStr, ReportResultVO.class);
        } catch (Exception e) {
            return null;
        }
    }

    // ======================== 降级评分（确定性版） ========================

    /** 生成降级报告（AI 调用失败时使用，也用难度加权算总分） */
    public ReportResultVO generateFallbackReport(String position, String difficulty,
                                                       List<Map<String, String>> conversation,
                                                       List<Question> questions,
                                                       String round) {
        boolean hasAnswer = false;
        for (Map<String, String> m : conversation) {
            if ("user".equals(m.get("role"))) { hasAnswer = true; break; }
        }
        if (!hasAnswer) {
            return createEmptyReport("候选人未做任何回答，无法有效评估");
        }

        List<ReportResultVO.Comment> comments = new ArrayList<>();
        int userAnswerCount = 0;
        int emptyAnswerCount = 0;
        int questionIndex = 0;

        for (int i = 0; i < conversation.size(); i++) {
            Map<String, String> msg = conversation.get(i);
            if (!"user".equals(msg.get("role"))) continue;

            String answer = msg.getOrDefault("content", "");
            userAnswerCount++;

            String questionText = "";
            if (i > 0) {
                Map<String, String> prev = conversation.get(i - 1);
                questionText = prev.getOrDefault("content", "问题" + (questionIndex + 1));
            }
            if (questionText.length() > 100) questionText = questionText.substring(0, 100) + "...";

            boolean isEmptyAnswer = answer.length() < 5 || answer.toLowerCase().contains("不知道")
                    || answer.toLowerCase().contains("不懂");
            if (isEmptyAnswer) emptyAnswerCount++;

            int qScore = scoreAnswer(answer, isEmptyAnswer);
            String comment = generateComment(qScore);

            ReportResultVO.Comment commentVO = new ReportResultVO.Comment();
            commentVO.setQuestionText(questionText);
            commentVO.setUserAnswer(answer.length() > 200 ? answer.substring(0, 200) + "..." : answer);
            commentVO.setScore(qScore);
            commentVO.setComment(comment);
            commentVO.setSortOrder(comments.size() + 1);
            comments.add(commentVO);
            questionIndex++;
        }

        // 用难度加权算总分（与 AI 路径一致）
        int weightedTotal = computeWeightedTotalScore(comments, questions);
        double emptyRate = userAnswerCount > 0 ? (double) emptyAnswerCount / userAnswerCount : 0;
        // 放弃率惩罚：放弃越多，总分打折越狠
        int penalty = (int) (emptyRate * 30);
        int totalScore = Math.max(0, weightedTotal - penalty);

        // 各维度分按评分细则权重从总分推算
        int techBase, exprBase, logicBase, compBase, innoBase;
        if ("hr".equals(round)) {
            // HR面试权重：表达30% + 团队协作25% + 学习能力20% + 职业规划15% + 责任感10%
            techBase = (int) Math.round(totalScore * 0.8);   // 责任感权重最低
            exprBase = (int) Math.round(totalScore * 1.1);   // 表达权重最高
            logicBase = (int) Math.round(totalScore * 1.0);  // 团队协作
            compBase = (int) Math.round(totalScore * 0.95);  // 学习能力
            innoBase = (int) Math.round(totalScore * 0.9);   // 职业规划
        } else if ("comprehensive".equals(round)) {
            // 综合面试权重：技术25% + 表达25% + 逻辑22% + 完整性15% + 创新性13%
            techBase = (int) Math.round(totalScore * 1.0);
            exprBase = (int) Math.round(totalScore * 1.0);
            logicBase = (int) Math.round(totalScore * 0.95);
            compBase = (int) Math.round(totalScore * 0.9);
            innoBase = (int) Math.round(totalScore * 0.85);
        } else {
            // 技术面试权重：技术40% + 表达20% + 逻辑20% + 完整性10% + 创新性10%
            techBase = (int) Math.round(totalScore * 1.05);  // 技术权重最高
            exprBase = (int) Math.round(totalScore * 0.95);  // 表达权重次之
            logicBase = totalScore;                          // 逻辑权重次之
            compBase = (int) Math.round(totalScore * 0.9);   // 完整性权重较低
            innoBase = (int) Math.round(totalScore * 0.85);  // 创新性权重较低
        }

        ReportResultVO report = new ReportResultVO();
        report.setTotalScore(Math.min(100, totalScore));
        report.setLevel(computeLevel(totalScore));
        report.setTechnicalScore(clampScore(Math.min(100, techBase)));
        report.setExpressionScore(clampScore(exprBase));
        report.setLogicScore(clampScore(logicBase));
        report.setCompletenessScore(clampScore(compBase));
        report.setInnovationScore(clampScore(innoBase));
        report.setSuggestion(generateSuggestion(position, totalScore));
        report.setComments(comments);
        return report;
    }

    private int clampScore(int score) {
        return Math.min(90, Math.max(5, score));
    }

    private String computeLevel(int totalScore) {
        if (totalScore >= 90) return "优秀";
        if (totalScore >= 75) return "良好";
        if (totalScore >= 60) return "合格";
        return "待提升";
    }

    /**
     * 确定性评分：相同回答永远得相同分数
     * 放弃性回答 → 0 分
     * 过短 (< 20 字) → 25 分
     * 中等 (20-80 字) → 按关键词密度
     * 完整 (≥80 字) → 关键词 + 结构 + 深度
     */
    private int scoreAnswer(String answer, boolean isEmptyAnswer) {
        if (isEmptyAnswer) return 0;
        String trimmed = answer.trim();

        // 放弃性回答
        if (isGiveUp(trimmed)) return 0;

        // 过短
        if (trimmed.length() < 20) return 25;

        // 中等长度：按关键词密度
        if (trimmed.length() < 80) {
            return Math.min(60, 30 + countKeywords(trimmed) * 8);
        }

        // 完整回答：关键词 + 结构 + 深度标记
        int base = 25;
        base += Math.min(30, countKeywords(trimmed) * 5);
        base += hasStructure(trimmed) ? 15 : 5;
        base += hasDepthMarker(trimmed) ? 10 : 0;
        return Math.min(85, base);
    }

    private boolean isGiveUp(String answer) {
        String[] patterns = {"不知道", "不会", "没学过", "不清楚", "忘了", "不懂"};
        for (String p : patterns) {
            if (answer.contains(p)) return true;
        }
        return answer.length() < 5;
    }

    private int countKeywords(String answer) {
        String[] techKeywords = {"因为", "所以", "实现", "原理", "机制", "流程", "步骤",
                "数据", "结构", "线程", "内存", "缓存", "算法", "协议",
                "接口", "抽象", "继承", "多态", "事务", "索引", "查询",
                "框架", "配置", "部署", "测试", "异常", "日志"};
        int count = 0;
        for (String kw : techKeywords) {
            if (answer.contains(kw)) count++;
        }
        return count;
    }

    private boolean hasStructure(String answer) {
        return answer.contains("\n") || answer.contains("1.") || answer.contains("2.")
                || answer.contains("首先") || answer.contains("其次")
                || answer.contains("第一") || answer.contains("第二");
    }

    private boolean hasDepthMarker(String answer) {
        return answer.contains("源码") || answer.contains("底层") || answer.contains("原理")
                || answer.contains("对比") || answer.contains("优缺点") || answer.contains("trade-off")
                || answer.contains("实际") || answer.contains("项目") || answer.contains("生产");
    }

    // ======================== 模板化反馈文本（轮询版） ========================

    /**
     * 轮询反馈文本，避免连续两次重复
     */
    public String generateFeedback(String difficulty) {
        String[] feedbacks = {
                "回答得不错，你对这个知识点有较好的理解。",
                "基本回答正确，但还可以更深入一些。",
                "你的回答涵盖了主要要点，逻辑清晰。",
                "回答得比较好，可以看出你有一定的实践经验。",
                "思路正确，建议结合更多实际场景来说明。"
        };
        String fb = feedbacks[Math.abs(feedbackIndex.getAndIncrement() % feedbacks.length)];
        return fb;
    }

    public String generateComment(int score) {
        if (score >= 85) return "回答优秀，理解深入，表达清晰。";
        if (score >= 70) return "回答正确，覆盖了主要要点，建议补充更多细节。";
        if (score >= 60) return "基本正确，但关键点有遗漏，需要加强理解。";
        return "回答不够理想，建议重新学习相关知识点。";
    }

    public String generateSuggestion(String position, int score) {
        if (score >= 85) return "整体表现优秀！你对该岗位所需的技术知识有扎实的掌握。建议继续保持学习热情，关注行业前沿技术动态。";
        if (score >= 70) return "整体表现良好。建议加强对核心技术原理的深入理解，特别是底层实现细节。可以通过阅读源码和技术书籍来提升。";
        if (score >= 60) return "基本达到要求。建议系统复习基础知识，多做练习题，同时注重实际项目经验的积累。";
        return "需要加强学习。建议从基础开始系统学习，制定详细的学习计划，每天坚持学习和练习。";
    }

    // ======================== 空报告模板 ========================

    public ReportResultVO createEmptyReport(String message) {
        ReportResultVO report = new ReportResultVO();
        report.setTotalScore(5);
        report.setLevel("待提升");
        report.setTechnicalScore(0);
        report.setExpressionScore(0);
        report.setLogicScore(0);
        report.setCompletenessScore(0);
        report.setInnovationScore(0);
        report.setSuggestion(message);
        report.setComments(new ArrayList<>());
        return report;
    }

    // ======================== 诊断报告降级数据 ========================

    public DiagnosisDataVO getDefaultDiagnosisData() {
        DiagnosisDataVO data = new DiagnosisDataVO();
        data.setKnowledgeAnalysis(new ArrayList<>());
        data.setThinkingAnalysis(Map.of(
                "type", "待分析", "summary", "暂无数据",
                "strengths", List.of(), "weaknesses", List.of(), "suggestions", List.of()));
        data.setMistakePatterns(new ArrayList<>());
        data.setLearningPlan(Map.of("summary", "暂无数据", "phases", List.of(), "resources", List.of()));
        data.setDetailedComments(new ArrayList<>());
        return data;
    }
}
