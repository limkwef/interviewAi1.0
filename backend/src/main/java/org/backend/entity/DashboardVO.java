package org.backend.entity;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class DashboardVO {
    private Stats stats;
    private UserGreeting userGreeting;
    private List<ScoreTrendItem> scoreTrend;
    private Map<String, Integer> knowledgeOverview;
    private List<Recommendation> recommendations;
    private List<RecentInterview> recentInterviews;

    @Data
    public static class Stats {
        private int totalInterviews;
        private int totalQuestions;
        private Integer avgScore;
        private int wrongCount;
        private Integer scoreChange;  // 较上次变化
        private int highestScore;        // 最高分数
    }

    @Data
    public static class UserGreeting {
        private String name;
        private String avatarUrl;
        private String todayTip;
    }

    @Data
    public static class ScoreTrendItem {
        private Long sessionId;
        private Integer totalScore;
        private String createdAt;
    }

    @Data
    public static class Recommendation {
        private Long questionId;
        private String title;
        private String difficulty;
        private String tag;
    }

    @Data
    public static class RecentInterview {
        private Long sessionId;
        private Long reportId;
        private String jobPosition;
        private String interviewRound;
        private Integer totalScore;
        private String level;
        private java.time.LocalDateTime createdAt;
    }
}
