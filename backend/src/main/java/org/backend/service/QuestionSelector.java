package org.backend.service;

import org.backend.entity.Question;
import org.backend.mapper.QuestionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 题目抽取策略：分层抽题 + 去重 + 应用层随机
 * 从 InterviewService 中拆出，职责单一。
 */
@Component
public class QuestionSelector {

    private static final Logger logger = LoggerFactory.getLogger(QuestionSelector.class);

    private final QuestionMapper questionMapper;

    public QuestionSelector(QuestionMapper questionMapper) {
        this.questionMapper = questionMapper;
    }

    /**
     * 按难度分层 + 排除近期题 的智能抽题策略
     *
     * @param direction 岗位方向（如 java_backend）
     * @param total 总题数
     * @param recentIds 用户近期做过的题 ID（去重用）
     * @param sessionDifficulty 难度
     * @param round 面试轮次（hr/comprehensive/technical）
     */
    public List<Question> selectBalancedQuestions(String direction, int total, Set<Long> recentIds,
                                                   String sessionDifficulty, String round) {
        boolean isHR = "hr".equals(round);
        String category = isHR ? "behavioral" : null;
        logger.info("selectBalancedQuestions: direction={}, round={}, isHR={}, category={}, total={}",
                direction, round, isHR, category, total);

        Map<String, Integer> dist = calculateDifficultyDistribution(sessionDifficulty, total);

        List<Question> result = new ArrayList<>();
        Map<String, Integer> deficit = new HashMap<>();

        // 1. 按难度分层抽取，逐层过滤近期题
        for (String diff : List.of("easy", "medium", "hard")) {
            int need = dist.getOrDefault(diff, 0);
            if (need <= 0) continue;
            List<Question> layerQuestions;
            if (isHR) {
                layerQuestions = shuffleAndFetch(
                        questionMapper.findIdsByCategoryAndDifficulty(category, diff), recentIds, need);
            } else {
                layerQuestions = shuffleAndFetch(
                        questionMapper.findIdsByDirectionAndDifficulty(direction, diff), recentIds, need);
            }
            int take = Math.min(need, layerQuestions.size());
            result.addAll(new ArrayList<>(layerQuestions.subList(0, take)));
            if (take < need) deficit.put(diff, need - take);
        }

        // 2. 某层因去重不够的，从其他层补足
        if (!deficit.isEmpty()) {
            int totalDeficit = deficit.values().stream().mapToInt(Integer::intValue).sum();
            Set<Long> usedIds = result.stream().map(Question::getId).collect(Collectors.toSet());
            usedIds.addAll(recentIds);

            for (String diff : List.of("medium", "easy", "hard")) {
                if (totalDeficit <= 0) break;
                List<Question> extra;
                if (isHR) {
                    extra = shuffleAndFetch(questionMapper.findIdsByCategoryAndDifficulty(category, diff),
                            usedIds, totalDeficit + 3);
                } else {
                    extra = shuffleAndFetch(questionMapper.findIdsByDirectionAndDifficulty(direction, diff),
                            usedIds, totalDeficit + 3);
                }
                int take = Math.min(totalDeficit, extra.size());
                result.addAll(new ArrayList<>(extra.subList(0, take)));
                totalDeficit -= take;
            }
        }

        // 3. 仍然不够 → 放宽去重限制（允许重复，但不跨方向）
        if (result.size() < total) {
            int need = total - result.size();
            Set<Long> usedIds = result.stream().map(Question::getId).collect(Collectors.toSet());

            List<Question> supplement;
            if (isHR) {
                supplement = shuffleAndFetch(questionMapper.findIdsByCategory(category),
                        usedIds, need + 5);
            } else {
                // 只在同方向内补题，不跨方向（避免前端面试出后端题）
                supplement = shuffleAndFetch(questionMapper.findIdsByDirection(direction),
                        usedIds, need + 5);
            }
            int take = Math.min(need, supplement.size());
            result.addAll(new ArrayList<>(supplement.subList(0, take)));
        }

        Collections.shuffle(result);
        return result;
    }

    /**
     * 应用层随机抽取：从候选 ID 中排除已用 ID，shuffle 后取 limit 个，查完整记录
     */
    public List<Question> shuffleAndFetch(List<Long> candidateIds, Set<Long> excludeIds, int limit) {
        List<Long> filtered = new ArrayList<>(candidateIds);
        filtered.removeAll(excludeIds);
        Collections.shuffle(filtered);
        List<Long> selected = filtered.subList(0, Math.min(limit, filtered.size()));
        if (selected.isEmpty()) return new ArrayList<>();
        return questionMapper.findByIds(selected);
    }

    /**
     * 根据会话难度计算 easy/medium/hard 三层分配数量
     */
    public Map<String, Integer> calculateDifficultyDistribution(String sessionDifficulty, int total) {
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
            default:
                easy = (int) Math.round(total * 0.2);
                medium = (int) Math.round(total * 0.6);
                hard = total - easy - medium;
                break;
        }
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
