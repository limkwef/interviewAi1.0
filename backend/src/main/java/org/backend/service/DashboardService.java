package org.backend.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.backend.vo.DashboardVO;
import org.backend.entity.InterviewReport;
import org.backend.entity.InterviewSession;
import org.backend.entity.Question;
import org.backend.entity.User;
import org.backend.mapper.InterviewReportMapper;
import org.backend.mapper.InterviewSessionMapper;
import org.backend.mapper.MistakeMapper;
import org.backend.mapper.QuestionMapper;
import org.backend.mapper.UserMapper;
import org.backend.util.PositionConstants;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final InterviewReportMapper reportMapper;
    private final InterviewSessionMapper sessionMapper;
    private final QuestionMapper questionMapper;
    private final MistakeMapper mistakeMapper;
    private final UserMapper userMapper;

    private static final String[] TIPS = {
            "面试时注意结构化表达，使用STAR法则回答行为面试题。",
            "回答技术问题时，先说思路再写代码，展示你的思考过程。",
            "遇到不会的问题，诚实说明并尝试从已知知识推导。",
            "每天坚持练习2-3道算法题，保持手感。",
            "复习错题比刷新题更有效，定期回顾错题本。"
    };

    public DashboardService(InterviewReportMapper reportMapper,
                            InterviewSessionMapper sessionMapper,
                            QuestionMapper questionMapper,
                            MistakeMapper mistakeMapper,
                            UserMapper userMapper) {
        this.reportMapper = reportMapper;
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.mistakeMapper = mistakeMapper;
        this.userMapper = userMapper;
    }

    public DashboardVO getDashboardData(Long userId, String position) {
        DashboardVO vo = new DashboardVO();
        vo.setStats(buildStats(userId, position));
        vo.setUserGreeting(buildUserGreeting(userId));
        vo.setScoreTrend(buildScoreTrend(userId, position));
        vo.setKnowledgeOverview(buildKnowledgeOverview(userId, position));
        vo.setRecommendations(buildRecommendations(position));
        vo.setRecentInterviews(buildRecentInterviews(userId, position));
        return vo;
    }

    private DashboardVO.Stats buildStats(Long userId, String position) {
        DashboardVO.Stats stats = new DashboardVO.Stats();
        Map<String, Object> reportStats;
        if (position != null && !position.isEmpty()) {
            reportStats = reportMapper.findStatsByPosition(userId, position);
        } else {
            reportStats = reportMapper.findStats(userId);
        }
        stats.setTotalInterviews(((Number) reportStats.getOrDefault("totalInterviews", 0)).intValue());
        stats.setAvgScore((int) Math.round(((Number) reportStats.getOrDefault("avgScore", 0)).doubleValue()));
        stats.setTotalQuestions(reportMapper.countUserPracticedQuestions(userId));

        // 错题数量
        Map<String, Object> mistakeStats = mistakeMapper.findStats(userId);
        stats.setWrongCount(mistakeStats != null ? ((Number) mistakeStats.getOrDefault("total", 0)).intValue() : 0);

        // 较上次变化（使用对应岗位的数据）
        List<Map<String, Object>> trend;
        if (position != null && !position.isEmpty()) {
            trend = reportMapper.findGrowthDataByPosition(userId, position);
        } else {
            trend = reportMapper.findGrowthData(userId);
        }
        if (trend.size() >= 2) {
            int prevScore = ((Number) trend.get(trend.size() - 2).getOrDefault("score", 0)).intValue();
            int lastScore = ((Number) trend.get(trend.size() - 1).getOrDefault("score", 0)).intValue();
            stats.setScoreChange(lastScore - prevScore);
        } else {
            stats.setScoreChange(0);
        }

        // 最高分
        stats.setHighestScore(((Number) reportStats.getOrDefault("highestScore", 0)).intValue());

        return stats;
    }

    private DashboardVO.UserGreeting buildUserGreeting(Long userId) {
        DashboardVO.UserGreeting greeting = new DashboardVO.UserGreeting();
        User user = userMapper.findById(userId);
        if (user != null) {
            greeting.setName(user.getUsername());
            greeting.setAvatarUrl(user.getAvatar());
        }

        String tip = TIPS[new Random().nextInt(TIPS.length)];
        greeting.setTodayTip(tip);
        return greeting;
    }

    public List<DashboardVO.ScoreTrendItem> getScoreTrend(Long userId, String position) {
        return buildScoreTrend(userId, position);
    }

    public Map<String, Integer> getKnowledgeOverview(Long userId, String position) {
        return buildKnowledgeOverview(userId, position);
    }

    private List<DashboardVO.ScoreTrendItem> buildScoreTrend(Long userId, String position) {
        List<Map<String, Object>> trend;
        if (position != null && !position.isEmpty()) {
            trend = reportMapper.findGrowthDataByPosition(userId, position);
        } else {
            trend = reportMapper.findGrowthData(userId);
        }
        List<DashboardVO.ScoreTrendItem> items = new ArrayList<>();

        for (Map<String, Object> item : trend) {
            DashboardVO.ScoreTrendItem trendItem = new DashboardVO.ScoreTrendItem();
            trendItem.setSessionId(((Number) item.get("sessionId")).longValue());
            trendItem.setTotalScore(((Number) item.get("score")).intValue());
            Object createdAt = item.get("createdAt");
            if (createdAt != null) {
                if (createdAt instanceof java.sql.Timestamp) {
                    trendItem.setCreatedAt(((java.sql.Timestamp) createdAt).toLocalDateTime().toString());
                } else {
                    trendItem.setCreatedAt(createdAt.toString());
                }
            }
            items.add(trendItem);
        }
        return items;
    }

    private Map<String, Integer> buildKnowledgeOverview(Long userId, String position) {
        Map<String, Object> stats;
        if (position != null && !position.isEmpty()) {
            stats = reportMapper.findStatsByPosition(userId, position);
        } else {
            stats = reportMapper.findStats(userId);
        }
        Map<String, Integer> overview = new LinkedHashMap<>();
        overview.put("技术能力", ((Number) stats.getOrDefault("avgTechnical", 0)).intValue());
        overview.put("表达能力", ((Number) stats.getOrDefault("avgExpression", 0)).intValue());
        overview.put("逻辑思维", ((Number) stats.getOrDefault("avgLogic", 0)).intValue());
        overview.put("完整性", ((Number) stats.getOrDefault("avgCompleteness", 0)).intValue());
        overview.put("创新性", ((Number) stats.getOrDefault("avgInnovation", 0)).intValue());
        return overview;
    }

    private List<DashboardVO.Recommendation> buildRecommendations(String position) {
        // 新用户没有面试记录，不推荐题目
        if (position == null || position.isEmpty()) {
            return List.of();
        }

        Map<String, Object> params = new HashMap<>();
        params.put("offset", 0);
        params.put("size", 4);
        params.put("direction", position);
        List<Question> questions = questionMapper.findList(params);

        List<DashboardVO.Recommendation> recommendations = new ArrayList<>();
        for (Question q : questions) {
            DashboardVO.Recommendation rec = new DashboardVO.Recommendation();
            rec.setQuestionId(q.getId());
            rec.setTitle(q.getTitle());
            rec.setDifficulty(q.getDifficulty());
            rec.setTag(q.getCategory());
            recommendations.add(rec);
        }
        return recommendations;
    }

    private List<DashboardVO.RecentInterview> buildRecentInterviews(Long userId, String position) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("offset", 0);
        params.put("size", 5);
        List<InterviewReport> reports = reportMapper.findByUserId(params);
        List<DashboardVO.RecentInterview> recentList = new ArrayList<>();

        for (InterviewReport r : reports) {
            DashboardVO.RecentInterview recent = new DashboardVO.RecentInterview();
            recent.setSessionId(r.getSessionId());
            recent.setReportId(r.getId());
            recent.setTotalScore(r.getTotalScore());
            recent.setLevel(r.getLevel());
            recent.setCreatedAt(r.getCreatedAt());
            // findByUserId 已经 JOIN 了 interview_session，直接取值
            recent.setJobPosition(getPositionName(r.getPosition()));
            recent.setInterviewRound(getRoundName(r.getRound()));
            recentList.add(recent);
        }
        return recentList;
    }

    private String getPositionName(String position) {
        return PositionConstants.getShortName(position);
    }

    private String getRoundName(String round) {
        if (round == null) return "技术面";
        return switch (round) {
            case "hr" -> "HR面";
            case "comprehensive" -> "综合面";
            default -> "技术面";
        };
    }
}
