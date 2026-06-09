package org.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.backend.entity.CompetitiveAnalysis;
import org.backend.entity.DimensionVO;
import org.backend.entity.ImprovementPredictionVO;
import org.backend.entity.InterviewReport;
import org.backend.entity.InterviewSession;
import org.backend.entity.User;
import org.backend.mapper.InterviewReportMapper;
import org.backend.mapper.InterviewSessionMapper;
import org.backend.mapper.MistakeMapper;
import org.backend.mapper.UserMapper;
import org.backend.util.AIService;
import org.backend.util.PositionConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class CompetitiveAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(CompetitiveAnalysisService.class);
    private static final String CACHE_KEY_PREFIX = "competitive:analysis:";
    private static final long CACHE_TTL_SECONDS = 3600; // 1小时

    @Autowired
    private CacheService cacheService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private InterviewReportMapper interviewReportMapper;

    @Autowired
    private InterviewSessionMapper sessionMapper;

    @Autowired
    private MistakeMapper mistakeMapper;

    @Autowired
    private AIService aiService;

    @Autowired
    private ObjectMapper objectMapper;

    /** 等级分数线 */
    private static final int PASS_SCORE = 60;    // 及格
    private static final int GOOD_SCORE = 75;    // 良好
    private static final int EXCELLENT_SCORE = 90; // 优秀

    /**
     * 等级递进目标：根据当前分数返回下一等级的分数线
     */
    private int calculateTierTarget(int currentScore) {
        if (currentScore < PASS_SCORE) return PASS_SCORE;
        if (currentScore < GOOD_SCORE) return GOOD_SCORE;
        if (currentScore < EXCELLENT_SCORE) return EXCELLENT_SCORE;
        return EXCELLENT_SCORE;
    }

    /**
     * 同岗位分数线数据（从真实数据计算）
     */
    private static class PositionBenchmark {
        int p75;
        int median;
        int totalUsers;
        int userRank;
        int percentile;
    }

    /**
     * 计算同岗位分数线对标数据（按 position）
     */
    private PositionBenchmark calculatePositionBenchmark(String position, int userScore) {
        List<Map<String, Object>> distribution = interviewReportMapper.findPositionScoreDistribution(position);
        return computeBenchmark(distribution, userScore);
    }

    /**
     * 计算同岗位同轮次分数线对标数据（按 position + round）
     */
    private PositionBenchmark calculatePositionRoundBenchmark(String position, String round, int userScore) {
        List<Map<String, Object>> distribution = interviewReportMapper.findPositionRoundScoreDistribution(position, round);
        return computeBenchmark(distribution, userScore);
    }

    /**
     * 从分数分布数据计算 benchmark
     */
    private PositionBenchmark computeBenchmark(List<Map<String, Object>> distribution, int userScore) {
        PositionBenchmark benchmark = new PositionBenchmark();

        if (distribution == null || distribution.isEmpty()) {
            benchmark.p75 = 80;
            benchmark.median = 70;
            benchmark.totalUsers = 0;
            benchmark.userRank = 1;
            benchmark.percentile = 100;
            return benchmark;
        }

        List<Integer> scores = new ArrayList<>();
        for (Map<String, Object> row : distribution) {
            Object scoreObj = row.get("bestScore");
            if (scoreObj instanceof Number) {
                scores.add(((Number) scoreObj).intValue());
            }
        }

        if (scores.isEmpty()) {
            benchmark.p75 = 80;
            benchmark.median = 70;
            benchmark.totalUsers = 0;
            benchmark.userRank = 1;
            benchmark.percentile = 100;
            return benchmark;
        }

        Collections.sort(scores);

        benchmark.totalUsers = scores.size();

        int p75Index = (int) Math.ceil(scores.size() * 0.25) - 1;
        p75Index = Math.max(0, Math.min(p75Index, scores.size() - 1));
        benchmark.p75 = scores.get(p75Index);

        int medianIndex = scores.size() / 2;
        benchmark.median = scores.get(medianIndex);

        int rank = 0;
        for (int score : scores) {
            if (score >= userScore) rank++;
        }
        benchmark.userRank = Math.max(1, rank);

        benchmark.percentile = (int) Math.round((1.0 - (double) benchmark.userRank / benchmark.totalUsers) * 100);
        benchmark.percentile = Math.max(0, Math.min(100, benchmark.percentile));

        return benchmark;
    }

    /**
     * 获取竞争力分析（有缓存直接返回，无缓存则生成）
     */
    public CompetitiveAnalysis getAnalysis(Long userId) {
        String cacheKey = CACHE_KEY_PREFIX + userId;
        CompetitiveAnalysis cached = cacheService.get(cacheKey, CompetitiveAnalysis.class);
        if (cached != null) {
            return cached;
        }
        return generateAnalysis(userId);
    }

    /**
     * 强制重新生成分析
     */
    public CompetitiveAnalysis refreshAnalysis(Long userId) {
        String cacheKey = CACHE_KEY_PREFIX + userId;
        cacheService.del(cacheKey);
        return generateAnalysis(userId);
    }

    /**
     * 基于某次面试生成竞争力分析（用于诊断页内嵌）
     *
     * 与 generateAnalysis() 的区别：
     * - 不取全部报告加权平均，直接用本次面试的分数
     * - 同行数据按 (position + round) 双条件筛选
     * - 只返回数据，不缓存
     */
    public CompetitiveAnalysis getAnalysisForSession(Long userId, Long sessionId) {
        // 1. 取这场面试的信息
        InterviewSession session = sessionMapper.findById(sessionId);
        if (session == null) {
            throw new RuntimeException("面试会话不存在");
        }

        InterviewReport report = interviewReportMapper.findBySessionId(sessionId);
        if (report == null) {
            throw new RuntimeException("未找到评分报告，请先完成评分");
        }

        // 2. 按 position + round 查同行分数分布
        String position = session.getPosition() != null ? session.getPosition() : "java_backend";
        String round = session.getRound() != null ? session.getRound() : "technical";
        int currentScore = safeInt(report.getTotalScore());

        PositionBenchmark benchmark = calculatePositionRoundBenchmark(position, round, currentScore);

        // 3. 等级递进目标
        int tierTarget = calculateTierTarget(currentScore);

        // 4. 构建结果（不需要 AI 分析，直接用本地数据）
        CompetitiveAnalysis analysis = new CompetitiveAnalysis();
        analysis.setUserId(userId);
        analysis.setTargetPosition(position);
        analysis.setCurrentScore(currentScore);
        analysis.setTechnicalScore(safeInt(report.getTechnicalScore()));
        analysis.setExpressionScore(safeInt(report.getExpressionScore()));
        analysis.setLogicScore(safeInt(report.getLogicScore()));
        analysis.setCompletenessScore(safeInt(report.getCompletenessScore()));
        analysis.setInnovationScore(safeInt(report.getInnovationScore()));
        analysis.setCreatedAt(LocalDateTime.now());
        analysis.setInterviewCount(1);
        analysis.setConfidence(false); // 单场面试，置信度低

        // 等级递进目标
        analysis.setTargetScore(tierTarget);
        analysis.setGap(Math.max(0, tierTarget - currentScore));

        // 同行对比数据
        analysis.setPeerTotalCount(benchmark.totalUsers);
        analysis.setPeerRank(benchmark.userRank);
        analysis.setPeerPercentile(benchmark.percentile);

        // 构建维度分析（按比例从等级递进目标推算）
        buildDimensions(analysis, tierTarget);

        return analysis;
    }

    /**
     * 构建维度分析
     */
    private void buildDimensions(CompetitiveAnalysis analysis, int tierTarget) {
        String[] dimNames = {"技术能力", "表达能力", "逻辑思维", "完整性", "创新性"};
        int[] userScores = {
            analysis.getTechnicalScore() != null ? analysis.getTechnicalScore() : 0,
            analysis.getExpressionScore() != null ? analysis.getExpressionScore() : 0,
            analysis.getLogicScore() != null ? analysis.getLogicScore() : 0,
            analysis.getCompletenessScore() != null ? analysis.getCompletenessScore() : 0,
            analysis.getInnovationScore() != null ? analysis.getInnovationScore() : 0
        };
        double[] ratios = {1.02, 0.96, 1.0, 0.94, 0.91};
        int[] targets = new int[5];
        for (int i = 0; i < 5; i++) {
            targets[i] = (int) Math.round(tierTarget * ratios[i]);
        }

        List<DimensionVO> dimensions = new ArrayList<>();
        for (int i = 0; i < dimNames.length; i++) {
            int gap = Math.max(0, targets[i] - userScores[i]);
            DimensionVO dim = new DimensionVO();
            dim.setName(dimNames[i]);
            dim.setUserScore(userScores[i]);
            dim.setTargetScore(targets[i]);
            dim.setGap(gap);
            dim.setUrgency(gap > 15 ? "high" : gap > 8 ? "medium" : "low");
            dim.setAnalysis(dimNames[i] + "当前" + userScores[i] + "分，目标" + targets[i] + "分");
            dim.setSuggestions(Collections.emptyList());
            dimensions.add(dim);
        }
        analysis.setDimensions(dimensions);

        // 设置各维度目标
        analysis.setTechnicalTarget(targets[0]);
        analysis.setExpressionTarget(targets[1]);
        analysis.setLogicTarget(targets[2]);
        analysis.setCompletenessTarget(targets[3]);
        analysis.setInnovationTarget(targets[4]);

        // 薄弱环节
        List<String> weaknesses = new ArrayList<>();
        for (int i = 0; i < dimNames.length; i++) {
            if (targets[i] - userScores[i] > 10) {
                weaknesses.add(dimNames[i] + "有待提升（差距" + (targets[i] - userScores[i]) + "分）");
            }
        }
        analysis.setWeaknesses(weaknesses.isEmpty() ? List.of("整体表现良好") : weaknesses);

        // 竞争优势
        List<String> advantages = new ArrayList<>();
        for (int i = 0; i < dimNames.length; i++) {
            if (userScores[i] >= targets[i]) {
                advantages.add(dimNames[i] + "已达标");
            }
        }
        analysis.setCompetitiveAdvantage(advantages.isEmpty() ? List.of("有提升潜力") : advantages);

        analysis.setImprovementPrediction(Collections.emptyList());
        analysis.setSummary(getTierDescription(analysis.getCurrentScore()));
    }

    /**
     * 生成竞争力分析
     */
    private CompetitiveAnalysis generateAnalysis(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("offset", 0);
        params.put("size", 100);
        List<InterviewReport> reports = interviewReportMapper.findByUserId(params);
        if (reports.isEmpty()) {
            throw new RuntimeException("暂无面试记录，请先完成至少一次模拟面试");
        }

        Map<String, Object> stats = interviewReportMapper.findStats(userId);
        List<Map<String, Object>> categoryStats = mistakeMapper.findCategoryStats(userId);
        List<Map<String, Object>> tagStats = mistakeMapper.findTagStats(userId);

        // 1. 加权评分
        int[] weightedScores = calculateWeightedScores(reports);
        int avgTotal = weightedScores[0];
        int avgTechnical = weightedScores[1];
        int avgExpression = weightedScores[2];
        int avgLogic = weightedScores[3];
        int avgCompleteness = weightedScores[4];
        int avgInnovation = weightedScores[5];

        // 2. 趋势判断
        Map<String, Object> trendResult = analyzeTrend(reports);

        // 3. 等级递进目标
        String targetPosition = user.getTargetPosition() != null ? user.getTargetPosition() : "java_backend";
        int tierTarget = calculateTierTarget(avgTotal);

        // 4. 同岗位同行对比数据
        PositionBenchmark benchmark = calculatePositionBenchmark(targetPosition, avgTotal);

        // 5. 调用 AI 生成分析
        CompetitiveAnalysis analysis = generateWithAI(user, avgTotal, avgTechnical, avgExpression,
                avgLogic, avgCompleteness, avgInnovation, categoryStats, tagStats,
                reports.size(), trendResult, benchmark, tierTarget);

        // 6. 填充基础数据
        analysis.setUserId(userId);
        analysis.setCurrentScore(avgTotal);
        analysis.setTechnicalScore(avgTechnical);
        analysis.setExpressionScore(avgExpression);
        analysis.setLogicScore(avgLogic);
        analysis.setCompletenessScore(avgCompleteness);
        analysis.setInnovationScore(avgInnovation);
        analysis.setCreatedAt(LocalDateTime.now());
        analysis.setInterviewCount(reports.size());
        analysis.setConfidence(reports.size() >= 3);

        analysis.setTargetScore(tierTarget);
        analysis.setGap(Math.max(0, tierTarget - avgTotal));

        analysis.setPeerTotalCount(benchmark.totalUsers);
        analysis.setPeerRank(benchmark.userRank);
        analysis.setPeerPercentile(benchmark.percentile);

        if (trendResult != null) {
            analysis.setTrendDirection((String) trendResult.get("direction"));
            analysis.setTrendSlope((Double) trendResult.get("slope"));
        }

        // 7. 维度按差距排序
        sortDimensionsByGap(analysis);

        // 8. 缓存结果
        String cacheKey = CACHE_KEY_PREFIX + userId;
        cacheService.set(cacheKey, analysis, CACHE_TTL_SECONDS);

        return analysis;
    }

    /**
     * 加权评分计算
     */
    private int[] calculateWeightedScores(List<InterviewReport> reports) {
        List<InterviewReport> sorted = new ArrayList<>(reports);
        sorted.sort(Comparator.comparing(InterviewReport::getCreatedAt));

        if (sorted.size() == 1) {
            InterviewReport r = sorted.get(0);
            return new int[]{
                safeInt(r.getTotalScore()),
                safeInt(r.getTechnicalScore()),
                safeInt(r.getExpressionScore()),
                safeInt(r.getLogicScore()),
                safeInt(r.getCompletenessScore()),
                safeInt(r.getInnovationScore())
            };
        }

        int n = sorted.size();
        double totalWeight = 0;
        double[] weighted = new double[6];

        for (int i = 0; i < n; i++) {
            double weight;
            if (i == n - 1) weight = 0.5;
            else if (i >= n - 3) weight = 0.3 / Math.min(2, n - 1);
            else weight = 0.2 / Math.max(1, n - 3);

            InterviewReport r = sorted.get(i);
            weighted[0] += safeInt(r.getTotalScore()) * weight;
            weighted[1] += safeInt(r.getTechnicalScore()) * weight;
            weighted[2] += safeInt(r.getExpressionScore()) * weight;
            weighted[3] += safeInt(r.getLogicScore()) * weight;
            weighted[4] += safeInt(r.getCompletenessScore()) * weight;
            weighted[5] += safeInt(r.getInnovationScore()) * weight;
            totalWeight += weight;
        }

        return new int[]{
            (int) Math.round(weighted[0] / totalWeight),
            (int) Math.round(weighted[1] / totalWeight),
            (int) Math.round(weighted[2] / totalWeight),
            (int) Math.round(weighted[3] / totalWeight),
            (int) Math.round(weighted[4] / totalWeight),
            (int) Math.round(weighted[5] / totalWeight)
        };
    }

    /**
     * 趋势判断
     */
    private Map<String, Object> analyzeTrend(List<InterviewReport> reports) {
        if (reports.size() < 2) return null;

        List<InterviewReport> sorted = new ArrayList<>(reports);
        sorted.sort(Comparator.comparing(InterviewReport::getCreatedAt));

        List<InterviewReport> recent = sorted.subList(
            Math.max(0, sorted.size() - 5), sorted.size());
        int n = recent.size();

        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            int score = safeInt(recent.get(i).getTotalScore());
            sumX += i;
            sumY += score;
            sumXY += i * score;
            sumX2 += i * i;
        }
        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);

        String direction;
        if (slope > 2) direction = "rapid_up";
        else if (slope > 0.5) direction = "slow_up";
        else if (slope > -0.5) direction = "stable";
        else if (slope > -2) direction = "slow_down";
        else direction = "rapid_down";

        Map<String, Object> result = new HashMap<>();
        result.put("direction", direction);
        result.put("slope", Math.round(slope * 100.0) / 100.0);
        return result;
    }

    /**
     * 维度按差距从大到小排序
     */
    private void sortDimensionsByGap(CompetitiveAnalysis analysis) {
        List<DimensionVO> dims = analysis.getDimensions();
        if (dims == null || dims.isEmpty()) return;

        dims.sort((a, b) -> Integer.compare(
            b.getGap() != null ? b.getGap() : 0,
            a.getGap() != null ? a.getGap() : 0));

        if (dims.get(0).getGap() != null && dims.get(0).getGap() > 0) {
            dims.get(0).setUrgency("high");
        }
        analysis.setDimensions(dims);
    }

    /**
     * 调用 DeepSeek 生成竞争力分析
     */
    private CompetitiveAnalysis generateWithAI(User user, int currentScore,
                                                int technicalScore, int expressionScore,
                                                int logicScore, int completenessScore,
                                                int innovationScore,
                                                List<Map<String, Object>> categoryStats,
                                                List<Map<String, Object>> tagStats,
                                                int interviewCount,
                                                Map<String, Object> trendResult,
                                                PositionBenchmark benchmark,
                                                int tierTarget) {
        String targetPosition = user.getTargetPosition() != null ? user.getTargetPosition() : "java_backend";
        String positionName = getPositionName(targetPosition);
        boolean confidence = interviewCount >= 3;

        // 构建薄弱知识点描述
        StringBuilder weakBuilder = new StringBuilder();
        if (tagStats != null && !tagStats.isEmpty()) {
            for (Map<String, Object> stat : tagStats) {
                String tagName = (String) stat.getOrDefault("tag_name", "未知");
                Object countObj = stat.getOrDefault("wrong_count", 0);
                int count = countObj instanceof Number ? ((Number) countObj).intValue() : 0;
                String sampleQuestion = (String) stat.getOrDefault("sample_question", "");
                weakBuilder.append("- ").append(tagName).append("错了").append(count).append("道");
                if (sampleQuestion != null && !sampleQuestion.isEmpty()) {
                    weakBuilder.append("，典型题目：").append(sampleQuestion);
                }
                weakBuilder.append("\n");
            }
        } else if (categoryStats != null && !categoryStats.isEmpty()) {
            for (Map<String, Object> stat : categoryStats) {
                String tag = (String) stat.getOrDefault("category", "未知");
                Object countObj = stat.getOrDefault("count", 0);
                int count = countObj instanceof Number ? ((Number) countObj).intValue() : 0;
                weakBuilder.append("- ").append(tag).append(": ").append(count).append("道错题\n");
            }
        } else {
            weakBuilder.append("暂无错题数据");
        }

        // 构建趋势描述
        String trendText;
        if (trendResult != null) {
            String dir = (String) trendResult.get("direction");
            double slope = (Double) trendResult.get("slope");
            String dirDesc = switch (dir) {
                case "rapid_up" -> "快速上升";
                case "slow_up" -> "缓慢上升";
                case "stable" -> "基本稳定";
                case "slow_down" -> "缓慢下降";
                case "rapid_down" -> "快速下降";
                default -> "未知";
            };
            trendText = "\n- 趋势方向：" + dirDesc + "\n- 斜率：" + slope + "分/次";
        } else {
            trendText = "\n- 数据不足，无法判断趋势";
        }

        // 构建同行对比描述
        String peerText;
        if (benchmark.totalUsers > 0) {
            peerText = "\n- 同岗位总人数：" + benchmark.totalUsers + "人" +
                    "\n- 用户排名：第" + benchmark.userRank + "名" +
                    "\n- 超过 " + benchmark.percentile + "% 的同岗位候选人" +
                    "\n- 同岗位中位数：" + benchmark.median + "分";
        } else {
            peerText = "\n- 暂无同岗位对比数据";
        }

        // 等级递进目标描述
        String tierDesc;
        if (currentScore < PASS_SCORE) {
            tierDesc = "目标分：" + tierTarget + "分（达到及格水平），差距：" + (tierTarget - currentScore) + "分";
        } else if (currentScore < GOOD_SCORE) {
            tierDesc = "目标分：" + tierTarget + "分（达到良好水平），差距：" + (tierTarget - currentScore) + "分";
        } else if (currentScore < EXCELLENT_SCORE) {
            tierDesc = "目标分：" + tierTarget + "分（达到优秀水平），差距：" + (tierTarget - currentScore) + "分";
        } else {
            tierDesc = "已达标：当前已是优秀水平，继续保持";
        }

        String prompt = "你是一位资深的IT招聘分析师。请基于以下面试数据，分析该候选人的竞争力。\n\n" +
                "【用户信息】\n" +
                "- 目标岗位：" + positionName + "\n" +
                "- 已面试次数：" + interviewCount + "次\n" +
                "- 置信度：" + (confidence ? "数据充足" : "数据较少，仅供参考") + "\n\n" +
                "【当前评分】（加权平均，越近期权重越高）\n" +
                "- 综合评分：" + currentScore + "/100\n" +
                "- 技术能力：" + technicalScore + "/100\n" +
                "- 表达能力：" + expressionScore + "/100\n" +
                "- 逻辑思维：" + logicScore + "/100\n" +
                "- 完整性：" + completenessScore + "/100\n" +
                "- 创新性：" + innovationScore + "/100\n\n" +
                "【成绩趋势】" + trendText + "\n\n" +
                "【等级递进目标】" + tierDesc + "\n\n" +
                "【同行对比】" + peerText + "\n\n" +
                "【薄弱知识点】（错题本统计，按错误次数排序）\n" + weakBuilder + "\n" +
                "请按照以下JSON格式返回（不用返回 targetScore 和 gap，由系统计算）：\n" +
                "{\n" +
                "  \"dimensions\": [\n" +
                "    {\n" +
                "      \"name\": \"技术能力|表达能力|逻辑思维|完整性|创新性\",\n" +
                "      \"userScore\": 整数,\n" +
                "      \"targetScore\": 整数（基于等级递进目标推算的该维度目标）,\n" +
                "      \"gap\": 整数（targetScore - userScore）,\n" +
                "      \"urgency\": \"high|medium|low\",\n" +
                "      \"analysis\": \"分析文字（说明具体差距和提升方向）\",\n" +
                "      \"suggestions\": [\"建议1\", \"建议2\"]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"competitiveAdvantage\": [\"优势1\"],\n" +
                "  \"weaknesses\": [\"短板1\"],\n" +
                "  \"improvementPrediction\": [\n" +
                "    {\"focus\": \"提升方向\", \"estimatedScore\": 整数, \"effort\": \"预估耗时\"}\n" +
                "  ],\n" +
                "  \"summary\": \"综合评估结论（说明当前等级和距离下一等级还需多少分）\"\n" +
                "}";

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", prompt));

        Map<String, Object> structured = aiService.chatStructuredWithFallback(messages);
        if (structured != null) {
            return parseAIResult(structured, targetPosition);
        }

        return generateFallbackAnalysis(user, currentScore, technicalScore, expressionScore,
                logicScore, completenessScore, innovationScore, categoryStats, tagStats, benchmark);
    }

    /**
     * 解析 AI 返回的结果
     */
    private CompetitiveAnalysis parseAIResult(Map<String, Object> data, String targetPosition) {
        CompetitiveAnalysis analysis = new CompetitiveAnalysis();
        analysis.setTargetPosition(targetPosition);
        analysis.setDimensions(objectMapper.convertValue(data.get("dimensions"),
                new TypeReference<List<DimensionVO>>() {}));
        analysis.setCompetitiveAdvantage(objectMapper.convertValue(data.get("competitiveAdvantage"),
                new TypeReference<List<String>>() {}));
        analysis.setWeaknesses(objectMapper.convertValue(data.get("weaknesses"),
                new TypeReference<List<String>>() {}));
        analysis.setImprovementPrediction(objectMapper.convertValue(data.get("improvementPrediction"),
                new TypeReference<List<ImprovementPredictionVO>>() {}));
        analysis.setSummary((String) data.get("summary"));

        List<DimensionVO> dims = analysis.getDimensions();
        if (dims != null) {
            for (DimensionVO dim : dims) {
                String name = dim.getName();
                Integer target = dim.getTargetScore();
                if (name == null || target == null) continue;
                switch (name) {
                    case "技术能力" -> analysis.setTechnicalTarget(target);
                    case "表达能力" -> analysis.setExpressionTarget(target);
                    case "逻辑思维" -> analysis.setLogicTarget(target);
                    case "完整性" -> analysis.setCompletenessTarget(target);
                    case "创新性" -> analysis.setInnovationTarget(target);
                }
            }
        }

        return analysis;
    }

    /**
     * 模板化降级分析
     */
    private CompetitiveAnalysis generateFallbackAnalysis(User user, int currentScore,
                                                          int technicalScore, int expressionScore,
                                                          int logicScore, int completenessScore,
                                                          int innovationScore,
                                                          List<Map<String, Object>> categoryStats,
                                                          List<Map<String, Object>> tagStats,
                                                          PositionBenchmark benchmark) {
        String targetPosition = user.getTargetPosition() != null ? user.getTargetPosition() : "java_backend";
        CompetitiveAnalysis analysis = new CompetitiveAnalysis();
        analysis.setTargetPosition(targetPosition);

        int overallTarget = calculateTierTarget(currentScore);

        int techTarget = (int) Math.round(overallTarget * 1.02);
        int exprTarget = (int) Math.round(overallTarget * 0.96);
        int logicTarget = overallTarget;
        int compTarget = (int) Math.round(overallTarget * 0.94);
        int innoTarget = (int) Math.round(overallTarget * 0.91);

        boolean hasTechMistakes = false;
        if (tagStats != null) {
            hasTechMistakes = tagStats.stream().anyMatch(s -> {
                String name = (String) s.getOrDefault("tag_name", "");
                return name != null && (name.contains("多线程") || name.contains("JVM") ||
                        name.contains("Spring") || name.contains("MySQL") || name.contains("Redis"));
            });
        }
        if (!hasTechMistakes && categoryStats != null) {
            hasTechMistakes = categoryStats.stream().anyMatch(s -> {
                String cat = (String) s.getOrDefault("category", "");
                return "java_basic".equals(cat) || "spring".equals(cat) || "database".equals(cat);
            });
        }
        if (hasTechMistakes) {
            techTarget = Math.min(techTarget + 5, 95);
        }

        analysis.setTargetScore(overallTarget);
        analysis.setGap(Math.max(0, overallTarget - currentScore));
        analysis.setTechnicalTarget(techTarget);
        analysis.setExpressionTarget(exprTarget);
        analysis.setLogicTarget(logicTarget);
        analysis.setCompletenessTarget(compTarget);
        analysis.setInnovationTarget(innoTarget);

        analysis.setPeerTotalCount(benchmark.totalUsers);
        analysis.setPeerRank(benchmark.userRank);
        analysis.setPeerPercentile(benchmark.percentile);

        String[] dimNames = {"技术能力", "表达能力", "逻辑思维", "完整性", "创新性"};
        int[] userScores = {technicalScore, expressionScore, logicScore, completenessScore, innovationScore};
        int[] targets = {techTarget, exprTarget, logicTarget, compTarget, innoTarget};

        List<DimensionVO> dimensions = new ArrayList<>();
        for (int i = 0; i < dimNames.length; i++) {
            int gap = Math.max(0, targets[i] - userScores[i]);
            DimensionVO dim = new DimensionVO();
            dim.setName(dimNames[i]);
            dim.setUserScore(userScores[i]);
            dim.setTargetScore(targets[i]);
            dim.setGap(gap);
            dim.setUrgency(gap > 15 ? "high" : gap > 8 ? "medium" : "low");
            dim.setAnalysis(dimNames[i] + "当前得分为" + userScores[i] + "分，目标为" + targets[i] + "分");
            dim.setSuggestions(Collections.emptyList());
            dimensions.add(dim);
        }
        analysis.setDimensions(dimensions);

        List<String> weaknesses = new ArrayList<>();
        for (int i = 0; i < dimNames.length; i++) {
            if (targets[i] - userScores[i] > 10) {
                weaknesses.add(dimNames[i] + "有待提升（差距" + (targets[i] - userScores[i]) + "分）");
            }
        }
        analysis.setWeaknesses(weaknesses.isEmpty() ? List.of("整体表现良好，继续努力") : weaknesses);

        List<String> advantages = new ArrayList<>();
        for (int i = 0; i < dimNames.length; i++) {
            if (userScores[i] >= targets[i]) {
                advantages.add(dimNames[i] + "已达标");
            }
        }
        if (benchmark.percentile >= 70) {
            advantages.add("超过 " + benchmark.percentile + "% 的同岗位候选人");
        }
        analysis.setCompetitiveAdvantage(advantages.isEmpty() ? List.of("有提升潜力") : advantages);

        List<ImprovementPredictionVO> predictions = new ArrayList<>();
        if (!weaknesses.isEmpty()) {
            int nextScore = Math.min(currentScore + 8, overallTarget);
            ImprovementPredictionVO pred = new ImprovementPredictionVO();
            pred.setFocus("补齐" + weaknesses.get(0).split("（")[0] + "短板");
            pred.setEstimatedScore(nextScore);
            pred.setEffort("2-3周");
            predictions.add(pred);
        }
        analysis.setImprovementPrediction(predictions);
        analysis.setSummary("基于最近面试数据的分析。" +
                getTierDescription(currentScore) +
                "建议针对薄弱维度重点练习，提升整体竞争力。");

        return analysis;
    }

    private String getPositionName(String position) {
        return PositionConstants.getFullName(position);
    }

    private String getTierDescription(int currentScore) {
        String level;
        if (currentScore >= EXCELLENT_SCORE) level = "优秀";
        else if (currentScore >= GOOD_SCORE) level = "良好";
        else if (currentScore >= PASS_SCORE) level = "合格";
        else level = "待提升";

        int nextTarget = calculateTierTarget(currentScore);
        if (currentScore >= EXCELLENT_SCORE) {
            return "当前等级：" + level + "，已达标，保持即可。";
        }
        return "当前等级：" + level + "，距离" + (nextTarget >= EXCELLENT_SCORE ? "优秀" : nextTarget >= GOOD_SCORE ? "良好" : "及格") + "还差" + (nextTarget - currentScore) + "分。";
    }

    private Integer toInt(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try { return Integer.parseInt(obj.toString()); } catch (Exception e) { return null; }
    }

    private int safeInt(Integer val) {
        return val != null ? val : 0;
    }
}
