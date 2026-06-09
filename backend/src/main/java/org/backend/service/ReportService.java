package org.backend.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.backend.entity.InterviewReport;
import org.backend.entity.InterviewSession;
import org.backend.exception.BusinessException;
import org.backend.mapper.InterviewReportMapper;
import org.backend.mapper.InterviewSessionMapper;
import org.backend.mapper.QuestionMapper;
import org.backend.mapper.ReportCommentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);

    private final InterviewReportMapper reportMapper;
    private final InterviewSessionMapper sessionMapper;
    private final ReportCommentMapper commentMapper;
    private final QuestionMapper questionMapper;

    public ReportService(InterviewReportMapper reportMapper,
                        InterviewSessionMapper sessionMapper,
                        ReportCommentMapper commentMapper,
                        QuestionMapper questionMapper) {
        this.reportMapper = reportMapper;
        this.sessionMapper = sessionMapper;
        this.commentMapper = commentMapper;
        this.questionMapper = questionMapper;
    }

    public Map<String, Object> getReportDetail(Long reportId, Long userId) {
        InterviewReport report = reportMapper.findById(reportId);
        if (report == null) {
            throw new BusinessException(404, "报告不存在");
        }
        if (!report.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问此报告");
        }

        InterviewSession session = sessionMapper.findById(report.getSessionId());

        Map<String, Object> data = new HashMap<>();
        data.put("id", report.getId());
        data.put("interviewId", report.getSessionId());
        data.put("totalScore", report.getTotalScore());
        data.put("level", report.getLevel());
        data.put("suggestion", report.getSuggestion());
        data.put("createdAt", report.getCreatedAt());

        if (session != null) {
            data.put("position", session.getPosition());
            data.put("round", session.getRound());
            data.put("difficulty", session.getDifficulty());
        }

        Map<String, Integer> dimensions = new LinkedHashMap<>();
        dimensions.put("technical", report.getTechnicalScore());
        dimensions.put("expression", report.getExpressionScore());
        dimensions.put("logic", report.getLogicScore());
        dimensions.put("completeness", report.getCompletenessScore());
        dimensions.put("innovation", report.getInnovationScore());
        data.put("dimensions", dimensions);

        if (report.getComments() != null) {
            List<Map<String, Object>> commentsList = new ArrayList<>();
            for (var c : report.getComments()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("question", c.getQuestionText());
                item.put("yourAnswer", c.getUserAnswer());
                item.put("score", c.getScore());
                item.put("comment", c.getComment());
                item.put("durationSeconds", c.getDurationSeconds());
                commentsList.add(item);
            }
            data.put("comments", commentsList);
        }

        return data;
    }

    public Map<String, Object> getReportList(Long userId, Integer page, Integer pageSize,
                                              String position, String round, String difficulty,
                                              Integer scoreMin, Integer scoreMax,
                                              String startDate, String endDate) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("offset", (page - 1) * pageSize);
        params.put("size", pageSize);

        boolean hasFilter = (position != null && !position.isEmpty())
                || (round != null && !round.isEmpty())
                || (difficulty != null && !difficulty.isEmpty())
                || scoreMin != null
                || scoreMax != null
                || (startDate != null && !startDate.isEmpty())
                || (endDate != null && !endDate.isEmpty());

        List<InterviewReport> records;
        int total;
        if (hasFilter) {
            params.put("position", position);
            params.put("round", round);
            params.put("difficulty", difficulty);
            params.put("scoreMin", scoreMin);
            params.put("scoreMax", scoreMax);
            params.put("startDate", startDate);
            params.put("endDate", endDate);
            records = reportMapper.findByUserIdFiltered(params);
            total = reportMapper.countByUserIdFiltered(params);
        } else {
            records = reportMapper.findByUserId(params);
            total = reportMapper.countByUserId(userId);
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (InterviewReport r : records) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", r.getId());
            item.put("interviewId", r.getSessionId());
            item.put("totalScore", r.getTotalScore());
            item.put("level", r.getLevel());
            item.put("position", r.getPosition());
            item.put("round", r.getRound());
            item.put("difficulty", r.getDifficulty());
            item.put("createdAt", r.getCreatedAt());
            // 计算总耗时：从 session.created_at 到 report.created_at
            InterviewSession session = sessionMapper.findById(r.getSessionId());
            if (session != null && session.getCreatedAt() != null && r.getCreatedAt() != null) {
                long seconds = java.time.Duration.between(session.getCreatedAt(), r.getCreatedAt()).getSeconds();
                item.put("totalDurationSeconds", (int) seconds);
            }
            list.add(item);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("records", list);
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);
        return data;
    }

    public Map<String, Object> getGrowthData(Long userId, String position, String round) {
        List<Map<String, Object>> trend;
        Map<String, Object> stats;
        if (position != null && !position.isEmpty() && round != null && !round.isEmpty()) {
            trend = reportMapper.findGrowthDataByPositionAndRound(userId, position, round);
            stats = reportMapper.findStatsByPositionAndRound(userId, position, round);
        } else if (position != null && !position.isEmpty()) {
            trend = reportMapper.findGrowthDataByPosition(userId, position);
            stats = reportMapper.findStatsByPosition(userId, position);
        } else {
            trend = reportMapper.findGrowthData(userId);
            stats = reportMapper.findStats(userId);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("trend", trend);
        data.put("totalInterviews", stats.getOrDefault("totalInterviews", 0));
        data.put("avgScore", Math.round(((Number) stats.getOrDefault("avgScore", 0)).doubleValue()));
        data.put("highestScore", stats.getOrDefault("highestScore", 0));

        if (!trend.isEmpty()) {
            Map<String, Object> first = trend.get(0);
            Map<String, Object> last = trend.get(trend.size() - 1);
            int firstScore = ((Number) first.getOrDefault("score", 0)).intValue();
            int lastScore = ((Number) last.getOrDefault("score", 0)).intValue();
            data.put("improvement", lastScore - firstScore);
        } else {
            data.put("improvement", 0);
        }

        return data;
    }

    public Map<String, Object> getDashboardStats(Long userId) {
        Map<String, Object> stats = reportMapper.findStats(userId);

        int totalQuestions = reportMapper.countUserPracticedQuestions(userId);
        int totalInterviews = ((Number) stats.getOrDefault("totalInterviews", 0)).intValue();
        int avgScore = (int) Math.round(((Number) stats.getOrDefault("avgScore", 0)).doubleValue());
        int highestScore = ((Number) stats.getOrDefault("highestScore", 0)).intValue();

        Map<String, Object> data = new HashMap<>();
        data.put("totalQuestions", totalQuestions);
        data.put("totalInterviews", totalInterviews);
        data.put("avgScore", avgScore);
        data.put("highestScore", highestScore);

        List<Map<String, Object>> trend = reportMapper.findGrowthData(userId);
        if (trend.size() >= 2) {
            int firstScore = ((Number) trend.get(0).getOrDefault("score", 0)).intValue();
            int lastScore = ((Number) trend.get(trend.size() - 1).getOrDefault("score", 0)).intValue();
            data.put("improvement", firstScore > 0 ? Math.round((lastScore - firstScore) * 100.0 / firstScore) : 0);
        } else {
            data.put("improvement", 0);
        }

        return data;
    }

    /**
     * 保存每题耗时数据
     */
    @Transactional
    public void saveQuestionDurations(Long reportId, Long userId, List<Map<String, Object>> durations) {
        InterviewReport report = reportMapper.findById(reportId);
        if (report == null || !report.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作此报告");
        }
        for (Map<String, Object> item : durations) {
            Integer questionIndex = ((Number) item.get("questionIndex")).intValue();
            Integer seconds = ((Number) item.get("seconds")).intValue();
            commentMapper.updateDurationByReportIdAndOrder(reportId, questionIndex, seconds);
        }
        logger.info("报告{}已保存每题耗时数据，共{}条", reportId, durations.size());
    }

    @Transactional
    public void deleteReport(Long reportId, Long userId) {
        InterviewReport report = reportMapper.findById(reportId);
        if (report == null) {
            throw new BusinessException(404, "报告不存在");
        }
        if (!report.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权删除此报告");
        }
        commentMapper.deleteByReportId(reportId);
        reportMapper.deleteById(reportId);
        logger.info("用户{}删除报告{}", userId, reportId);
    }
}
