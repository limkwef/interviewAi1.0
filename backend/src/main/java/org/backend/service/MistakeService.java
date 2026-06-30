package org.backend.service;

import org.backend.vo.CheckAnswerVO;
import org.backend.entity.MistakeAnswerDetail;
import org.backend.vo.MistakeDetailVO;
import org.backend.vo.MistakeListVO;
import org.backend.entity.MistakeRecord;
import org.backend.vo.MistakeStatsVO;
import org.backend.entity.Question;
import org.backend.dto.WrongAnswerDTO;
import org.backend.exception.BusinessException;
import org.backend.mapper.MistakeMapper;
import org.backend.mapper.QuestionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class MistakeService {

    private static final Logger logger = LoggerFactory.getLogger(MistakeService.class);

    private final MistakeMapper mistakeMapper;
    private final QuestionMapper questionMapper;

    public MistakeService(MistakeMapper mistakeMapper, QuestionMapper questionMapper) {
        this.mistakeMapper = mistakeMapper;
        this.questionMapper = questionMapper;
    }

    private static final Map<String, String> CATEGORY_LABEL_MAP = Map.ofEntries(
        Map.entry("java_basic", "Java基础"),
        Map.entry("spring", "Spring框架"),
        Map.entry("database", "数据库"),
        Map.entry("redis", "Redis"),
        Map.entry("design_pattern", "设计模式"),
        Map.entry("algorithm", "算法"),
        Map.entry("frontend", "前端"),
        Map.entry("devops", "运维部署"),
        Map.entry("microservice", "微服务"),
        Map.entry("network", "网络"),
        Map.entry("operating_system", "操作系统"),
        Map.entry("project", "项目经验"),
        Map.entry("architecture", "系统架构")
    );

    /**
     * 获取错题列表（分页+筛选）
     */
    public MistakeListVO getMistakeList(Long userId, Integer page, Integer size,
                                                String category, String difficulty,
                                                Integer status, String keyword) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("offset", (page - 1) * size);
        params.put("size", size);
        if (category != null && !category.isEmpty()) params.put("category", category);
        if (difficulty != null && !difficulty.isEmpty()) params.put("difficulty", difficulty);
        if (status != null) params.put("status", status);
        if (keyword != null && !keyword.isEmpty()) params.put("keyword", keyword);

        List<MistakeRecord> records = mistakeMapper.findList(params);
        int total = mistakeMapper.countList(params);

        MistakeListVO data = new MistakeListVO();
        data.setRecords(records);
        data.setTotal(total);
        data.setPage(page);
        data.setSize(size);
        return data;
    }

    /**
     * 获取错题统计
     */
    public MistakeStatsVO getStats(Long userId) {
        Map<String, Object> stats = mistakeMapper.findStats(userId);
        if (stats == null) {
            stats = new HashMap<>();
            stats.put("total", 0);
            stats.put("pendingReview", 0);
            stats.put("mastered", 0);
        }

        long total = ((Number) stats.getOrDefault("total", 0)).longValue();
        long pendingReview = ((Number) stats.getOrDefault("pendingReview", 0)).longValue();
        long mastered = ((Number) stats.getOrDefault("mastered", 0)).longValue();

        String masteredRate = total > 0 ? Math.round((mastered * 100.0 / total)) + "%" : "0%";

        List<Map<String, Object>> byCategoryRaw = mistakeMapper.findCategoryStats(userId);
        List<MistakeStatsVO.CategoryStat> byCategory = new ArrayList<>();
        for (Map<String, Object> item : byCategoryRaw) {
            MistakeStatsVO.CategoryStat stat = new MistakeStatsVO.CategoryStat();
            String cat = (String) item.get("category");
            stat.setCategory(cat);
            stat.setLabel(CATEGORY_LABEL_MAP.getOrDefault(cat, cat));
            Object countObj = item.get("count");
            stat.setCount(countObj instanceof Number ? ((Number) countObj).intValue() : 0);
            byCategory.add(stat);
        }

        MistakeStatsVO result = new MistakeStatsVO();
        result.setTotal(total);
        result.setPendingReview(pendingReview);
        result.setMastered(mastered);
        result.setMasteredRate(masteredRate);
        result.setByCategory(byCategory);
        return result;
    }

    /**
     * 获取错题详情（包含题目信息和错误记录）
     */
    public MistakeDetailVO getDetail(Long id, Long userId) {
        MistakeRecord record = mistakeMapper.findById(id);
        if (record == null) {
            throw new BusinessException(404, "错题记录不存在");
        }
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问此记录");
        }

        Question question = questionMapper.findById(record.getQuestionId());
        if (question == null) {
            throw new BusinessException(404, "题目不存在或已删除");
        }

        List<MistakeAnswerDetail> details = mistakeMapper.findDetailsByMistakeId(id);

        MistakeDetailVO result = new MistakeDetailVO();

        MistakeDetailVO.MistakeInfo mistakeInfo = new MistakeDetailVO.MistakeInfo();
        mistakeInfo.setId(record.getId());
        mistakeInfo.setQuestionId(record.getQuestionId());
        mistakeInfo.setMistakeCount(record.getMistakeCount());
        mistakeInfo.setStatus(record.getStatus());
        mistakeInfo.setFirstMistakeTime(record.getFirstMistakeTime());
        mistakeInfo.setLastMistakeTime(record.getLastMistakeTime());
        result.setMistake(mistakeInfo);

        MistakeDetailVO.QuestionInfo questionInfo = new MistakeDetailVO.QuestionInfo();
        questionInfo.setId(question.getId());
        questionInfo.setTitle(question.getTitle());
        questionInfo.setContent(question.getContent());
        questionInfo.setAnswer(question.getAnswer());
        questionInfo.setDifficulty(question.getDifficulty());
        questionInfo.setCategory(question.getCategory());
        result.setQuestion(questionInfo);

        result.setDetails(details);
        return result;
    }

    /**
     * 标记为已掌握
     */
    @Transactional
    public void markAsMastered(Long id, Long userId) {
        MistakeRecord record = mistakeMapper.findById(id);
        if (record == null) {
            throw new BusinessException(404, "错题记录不存在");
        }
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作此记录");
        }
        mistakeMapper.updateStatus(id, 1, LocalDateTime.now());
    }

    /**
     * 重置为待复习
     */
    @Transactional
    public void resetToPending(Long id, Long userId) {
        MistakeRecord record = mistakeMapper.findById(id);
        if (record == null) {
            throw new BusinessException(404, "错题记录不存在");
        }
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作此记录");
        }
        mistakeMapper.updateStatus(id, 0, null);
    }

    /**
     * 移出错题本
     */
    @Transactional
    public void deleteById(Long id, Long userId) {
        MistakeRecord record = mistakeMapper.findById(id);
        if (record == null) {
            throw new BusinessException(404, "错题记录不存在");
        }
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作此记录");
        }
        mistakeMapper.deleteById(id);
    }

    /**
     * 批量标记为已掌握
     */
    public int batchMarkAsMastered(List<Long> ids, Long userId) {
        if (ids == null || ids.isEmpty()) return 0;
        return mistakeMapper.batchUpdateStatus(ids, 1, LocalDateTime.now());
    }

    /**
     * 批量重置为待复习
     */
    public int batchResetToPending(List<Long> ids, Long userId) {
        if (ids == null || ids.isEmpty()) return 0;
        return mistakeMapper.batchUpdateStatus(ids, 0, null);
    }

    /**
     * 批量移出错题本
     */
    public int batchDelete(List<Long> ids, Long userId) {
        if (ids == null || ids.isEmpty()) return 0;
        return mistakeMapper.batchDeleteByIds(ids, userId);
    }

    /**
     * 获取重做题目列表
     */
    public List<Map<String, Object>> getReviewQuestions(Long userId, String category,
                                                         String difficulty, Integer count,
                                                         String ids) {
        // 如果传入了指定错题ID列表
        if (ids != null && !ids.isEmpty()) {
            List<Long> idList = new ArrayList<>();
            for (String s : ids.split(",")) {
                try {
                    idList.add(Long.parseLong(s.trim()));
                } catch (NumberFormatException ignored) {}
            }
            if (!idList.isEmpty()) {
                List<Map<String, Object>> questions = mistakeMapper.findReviewQuestionsByIds(idList);
                // 只返回属于该用户的错题
                questions.removeIf(q -> {
                    Long recordId = ((Number) q.get("mistakeRecordId")).longValue();
                    MistakeRecord record = mistakeMapper.findById(recordId);
                    return record == null || !record.getUserId().equals(userId);
                });
                return questions;
            }
        }

        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("count", count != null ? count : 10);
        if (category != null && !category.isEmpty()) params.put("category", category);
        if (difficulty != null && !difficulty.isEmpty()) params.put("difficulty", difficulty);

        return mistakeMapper.findReviewQuestions(params);
    }

    /**
     * 检查答案（简单关键词匹配 + 长度对比）
     * 注意：这是一个轻量的本地检查，后续可以接入 DeepSeek 做语义判断
     */
    public CheckAnswerVO checkAnswer(Long questionId, String userAnswer) {
        Question question = questionMapper.findById(questionId);
        if (question == null) {
            throw new BusinessException(404, "题目不存在");
        }

        boolean correct = false;
        if (question.getAnswer() != null && userAnswer != null) {
            String correctAnswer = question.getAnswer().toLowerCase().trim();
            String userAnswerLower = userAnswer.toLowerCase().trim();

            // 如果正确答案较长，检查用户答案是否覆盖了关键内容
            String[] keyPoints = correctAnswer.split("[.。；;\\n]");
            int matchCount = 0;
            for (String point : keyPoints) {
                point = point.trim();
                if (point.length() > 5 && userAnswerLower.contains(point)) {
                    matchCount++;
                }
            }
            // 如果超过一半的关键点都命中了，算正确
            correct = keyPoints.length > 0 && (double) matchCount / keyPoints.length >= 0.5;
        }

        CheckAnswerVO result = new CheckAnswerVO();
        result.setCorrect(correct);
        return result;
    }

    /**
     * 批量导入错题（面试结束后调用）
     */
    @Transactional
    public void batchImport(Long userId, Long interviewId,
                            List<WrongAnswerDTO> wrongAnswers) {
        for (WrongAnswerDTO wa : wrongAnswers) {
            Long questionId = wa.getQuestionId();
            String userAnswer = wa.getUserAnswer();
            String aiComment = wa.getAiComment();
            String category = wa.getCategory();

            MistakeRecord existing = mistakeMapper.findByUserAndQuestion(userId, questionId);
            if (existing != null) {
                // 已存在则累加错误次数
                mistakeMapper.incrementMistakeCount(existing.getId());
                // 新增错误详情
                MistakeAnswerDetail detail = new MistakeAnswerDetail();
                detail.setMistakeId(existing.getId());
                detail.setInterviewId(interviewId);
                detail.setUserAnswer(userAnswer);
                detail.setAiComment(aiComment);
                detail.setCategory(category);
                mistakeMapper.insertDetail(detail);
            } else {
                // 新增错题记录
                MistakeRecord record = new MistakeRecord();
                record.setUserId(userId);
                record.setQuestionId(questionId);
                record.setFirstMistakeTime(LocalDateTime.now());
                record.setLastMistakeTime(LocalDateTime.now());
                record.setMistakeCount(1);
                record.setStatus(0);
                mistakeMapper.insert(record);

                // 新增错误详情
                MistakeAnswerDetail detail = new MistakeAnswerDetail();
                detail.setMistakeId(record.getId());
                detail.setInterviewId(interviewId);
                detail.setUserAnswer(userAnswer);
                detail.setAiComment(aiComment);
                detail.setCategory(category);
                mistakeMapper.insertDetail(detail);
            }
        }

        logger.info("批量导入 {} 条错题记录，用户{}，面试{}", wrongAnswers.size(), userId, interviewId);
    }
}
