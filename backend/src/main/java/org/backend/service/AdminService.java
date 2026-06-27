package org.backend.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.backend.entity.AdminLog;
import org.backend.entity.Feedback;
import org.backend.entity.Question;
import org.backend.entity.User;
import org.backend.mapper.AdminLogMapper;
import org.backend.mapper.FeedbackMapper;
import org.backend.mapper.InterviewSessionMapper;
import org.backend.mapper.QuestionMapper;
import org.backend.mapper.UserMapper;
import org.backend.service.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private static final Logger logger = LoggerFactory.getLogger(AdminService.class);

    private final UserMapper userMapper;
    private final QuestionMapper questionMapper;
    private final AdminLogMapper adminLogMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final FeedbackMapper feedbackMapper;
    private final QuestionService questionService;
    private final UserService userService;
    private final CacheService cacheService;
    private final PasswordEncoder passwordEncoder;

    public AdminService(UserMapper userMapper, QuestionMapper questionMapper,
                       AdminLogMapper adminLogMapper, InterviewSessionMapper interviewSessionMapper,
                       FeedbackMapper feedbackMapper, QuestionService questionService,
                       UserService userService, CacheService cacheService,
                       PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.questionMapper = questionMapper;
        this.adminLogMapper = adminLogMapper;
        this.interviewSessionMapper = interviewSessionMapper;
        this.feedbackMapper = feedbackMapper;
        this.questionService = questionService;
        this.userService = userService;
        this.cacheService = cacheService;
        this.passwordEncoder = passwordEncoder;
    }

    // 用户管理
    public Map<String, Object> getUserList(Integer page, Integer size, Integer status, String keyword) {
        int offset = (page - 1) * size;
        Map<String, Object> params = new HashMap<>();
        params.put("offset", offset);
        params.put("size", size);
        if (status != null) params.put("status", status);
        if (keyword != null) params.put("keyword", keyword);
        
        List<User> list = userMapper.findList(params);
        int total = userMapper.countList(params);
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    public User getUserById(Long id) {
        return userMapper.findById(id);
    }

    @Transactional
    public void updateUser(Long id, User user) {
        user.setId(id);
        userMapper.updateUser(user);
        userService.clearUserCache(id);
    }

    @Transactional
    public void updateUserStatus(Long id, Integer status) {
        userMapper.updateStatus(id, status);
        userService.clearUserCache(id);
        cacheService.increment("user:token_version:" + id);
    }

    @Transactional
    public void resetUserPassword(Long id, String newPassword) {
        String encodedPassword = passwordEncoder.encode(newPassword);
        userMapper.updatePassword(id, encodedPassword);
        userService.clearUserCache(id);
        cacheService.increment("user:token_version:" + id);
    }

    @Transactional
    public void deleteUser(Long id) {
        userMapper.logicalDelete(id);
        userService.clearUserCache(id);
        cacheService.increment("user:token_version:" + id);
    }

    // 题目管理
    public Map<String, Object> getQuestionList(Integer page, Integer size, 
                                               String category, String difficulty, 
                                               String direction, String keyword) {
        int offset = (page - 1) * size;
        Map<String, Object> params = new HashMap<>();
        params.put("offset", offset);
        params.put("size", size);
        if (category != null) params.put("category", category);
        if (difficulty != null) params.put("difficulty", difficulty);
        if (direction != null) params.put("direction", direction);
        if (keyword != null) params.put("keyword", keyword);
        
        List<Question> list = questionMapper.findList(params);
        int total = questionMapper.countList(params);
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    public Question getQuestionById(Long id) {
        return questionMapper.findById(id);
    }

    @Transactional
    public Long createQuestion(Question question) {
        questionMapper.insert(question);
        questionService.clearQuestionCache();
        return question.getId();
    }

    @Transactional
    public void updateQuestion(Long id, Question question) {
        question.setId(id);
        questionMapper.update(question);
        questionService.clearQuestionCache();
    }

    @Transactional
    public void deleteQuestion(Long id) {
        questionMapper.logicalDelete(id);
        questionService.clearQuestionCache();
    }

    @Transactional
    public Map<String, Object> batchImportQuestions(List<Question> questions, String duplicateStrategy) {
        int successCount = 0;
        int skipCount = 0;
        int failCount = 0;
        List<Map<String, Object>> failures = new ArrayList<>();

        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);

            // 1. 字段校验
            List<String> fieldErrors = new ArrayList<>();
            if (q.getTitle() == null || q.getTitle().isBlank()) {
                fieldErrors.add("标题不能为空");
            }
            if (q.getContent() == null || q.getContent().isBlank()) {
                fieldErrors.add("内容不能为空");
            }
            if (q.getAnswer() == null || q.getAnswer().isBlank()) {
                fieldErrors.add("答案不能为空");
            }
            if (q.getCategory() == null || q.getCategory().isBlank()) {
                fieldErrors.add("分类不能为空");
            }
            if (q.getDifficulty() == null || q.getDifficulty().isBlank()) {
                fieldErrors.add("难度不能为空");
            }
            if (q.getDirection() == null || q.getDirection().isBlank()) {
                fieldErrors.add("方向不能为空");
            }

            if (!fieldErrors.isEmpty()) {
                failCount++;
                Map<String, Object> failure = new HashMap<>();
                failure.put("index", i + 1);
                failure.put("title", q.getTitle());
                failure.put("reason", String.join("；", fieldErrors));
                failures.add(failure);
                continue;
            }

            String title = q.getTitle().trim();

            // 2. 数据库重复检测
            Question existing = questionMapper.findByTitle(title);

            if (existing != null) {
                switch (duplicateStrategy) {
                    case "skip":
                        skipCount++;
                        continue;
                    case "update":
                        q.setId(existing.getId());
                        questionMapper.update(q);
                        successCount++;
                        continue;
                    case "duplicate":
                        questionMapper.insert(q);
                        successCount++;
                        continue;
                    default:
                        skipCount++;
                        continue;
                }
            } else {
                // 无重复，直接插入
                questionMapper.insert(q);
                successCount++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("skipCount", skipCount);
        result.put("failCount", failCount);
        result.put("failures", failures);

        // 清除题库缓存
        if (successCount > 0) {
            questionService.clearQuestionCache();
        }

        logger.info("批量导入完成：成功{}条，跳过{}条，失败{}条，策略={}", successCount, skipCount, failCount, duplicateStrategy);
        return result;
    }

    // 系统统计
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userMapper.countAll());
        stats.put("totalQuestions", questionMapper.countAll());
        stats.put("totalInterviews", interviewSessionMapper.countAll());

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(6);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        Map<String, Object> params = new HashMap<>();
        params.put("startDate", startDate.format(formatter));

        List<Map<String, Object>> dbData = userMapper.countGroupByDate(params);
        Map<String, Long> dateCountMap = new LinkedHashMap<>();
        for (Map<String, Object> row : dbData) {
            String date = row.get("date").toString();
            dateCountMap.put(date, ((Number) row.get("count")).longValue());
        }

        List<String> dates = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            String dateStr = today.minusDays(i).format(formatter);
            dates.add(dateStr);
            counts.add(dateCountMap.getOrDefault(dateStr, 0L));
        }

        Map<String, Object> trend = new LinkedHashMap<>();
        trend.put("dates", dates);
        trend.put("counts", counts);
        stats.put("userGrowthTrend", trend);

        return stats;
    }

    // 反馈管理
    public Map<String, Object> getFeedbackList(Integer page, Integer size, Integer status) {
        int offset = (page - 1) * size;
        Map<String, Object> params = new HashMap<>();
        params.put("offset", offset);
        params.put("size", size);
        if (status != null) params.put("status", status);
        
        List<Feedback> list = feedbackMapper.findAll(params);
        int total = feedbackMapper.countAll(params);
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @Transactional
    public void updateFeedbackStatus(Long id, Integer status) {
        feedbackMapper.updateStatus(id, status);
    }

    @Transactional
    public void deleteFeedback(Long id) {
        feedbackMapper.deleteById(id);
    }

    // 操作日志
    public Map<String, Object> getLogList(Integer page, Integer size, String action, 
                                          String startDate, String endDate) {
        int offset = (page - 1) * size;
        Map<String, Object> params = new HashMap<>();
        params.put("offset", offset);
        params.put("size", size);
        if (action != null) params.put("action", action);
        if (startDate != null) params.put("startDate", startDate);
        if (endDate != null) params.put("endDate", endDate);
        
        List<AdminLog> list = adminLogMapper.findList(params);
        int total = adminLogMapper.countList(params);
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }
}