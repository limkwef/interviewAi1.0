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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
    private final BCryptPasswordEncoder passwordEncoder;

    public AdminService(UserMapper userMapper, QuestionMapper questionMapper, 
                       AdminLogMapper adminLogMapper, InterviewSessionMapper interviewSessionMapper,
                       FeedbackMapper feedbackMapper) {
        this.userMapper = userMapper;
        this.questionMapper = questionMapper;
        this.adminLogMapper = adminLogMapper;
        this.interviewSessionMapper = interviewSessionMapper;
        this.feedbackMapper = feedbackMapper;
        this.passwordEncoder = new BCryptPasswordEncoder();
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
    }

    @Transactional
    public void updateUserStatus(Long id, Integer status) {
        userMapper.updateStatus(id, status);
    }

    @Transactional
    public void resetUserPassword(Long id, String newPassword) {
        String encodedPassword = passwordEncoder.encode(newPassword);
        userMapper.updatePassword(id, encodedPassword);
    }

    @Transactional
    public void deleteUser(Long id) {
        userMapper.logicalDelete(id);
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
        return question.getId();
    }

    @Transactional
    public void updateQuestion(Long id, Question question) {
        question.setId(id);
        questionMapper.update(question);
    }

    @Transactional
    public void deleteQuestion(Long id) {
        questionMapper.logicalDelete(id);
    }

    @Transactional
    public void batchImportQuestions(List<Question> questions) {
        for (Question q : questions) {
            questionMapper.insert(q);
        }
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