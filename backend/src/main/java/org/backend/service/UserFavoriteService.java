package org.backend.service;

import org.backend.entity.Question;
import org.backend.entity.UserFavorite;
import org.backend.exception.BusinessException;
import org.backend.mapper.QuestionMapper;
import org.backend.mapper.UserFavoriteMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserFavoriteService {

    private static final Logger logger = LoggerFactory.getLogger(UserFavoriteService.class);

    private final UserFavoriteMapper userFavoriteMapper;
    private final QuestionMapper questionMapper;

    public UserFavoriteService(UserFavoriteMapper userFavoriteMapper, QuestionMapper questionMapper) {
        this.userFavoriteMapper = userFavoriteMapper;
        this.questionMapper = questionMapper;
    }

    @Transactional
    public boolean addFavorite(Long userId, Long questionId) {
        // 检查题目是否存在
        Question question = questionMapper.findById(questionId);
        if (question == null) {
            throw new BusinessException(404, "题目不存在");
        }

        // 检查是否已收藏
        boolean alreadyFavorite = isFavorite(userId, questionId);
        if (alreadyFavorite) {
            return false; // 已收藏，返回false表示重复
        }

        // 原子化插入
        UserFavorite favorite = new UserFavorite();
        favorite.setUserId(userId);
        favorite.setQuestionId(questionId);
        int inserted = userFavoriteMapper.insert(favorite);
        if (inserted > 0) {
            questionMapper.incrementFavoriteCount(questionId);
            logger.info("用户{}收藏题目{}", userId, questionId);
            return true;
        }
        return false;
    }

    @Transactional
    public void removeFavorite(Long userId, Long questionId) {
        int deleted = userFavoriteMapper.delete(userId, questionId);
        if (deleted > 0) {
            questionMapper.decrementFavoriteCount(questionId);
            logger.info("用户{}取消收藏题目{}", userId, questionId);
        }
    }

    public boolean isFavorite(Long userId, Long questionId) {
        return userFavoriteMapper.countByUserAndQuestion(userId, questionId) > 0;
    }

    public Map<String, Object> getFavoriteList(Long userId, Integer page, Integer size, String category, String difficulty) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("offset", (page - 1) * size);
        params.put("size", size);
        params.put("category", category);
        params.put("difficulty", difficulty);

        List<Question> records = userFavoriteMapper.findFavoriteQuestions(params);
        int total = userFavoriteMapper.countFavoriteQuestions(params);

        Map<String, Object> data = new HashMap<>();
        data.put("records", records);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return data;
    }

    /**
     * 批量取消收藏
     */
    @Transactional
    public int batchRemoveFavorite(Long userId, List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) return 0;
        int deleted = userFavoriteMapper.batchDelete(userId, questionIds);
        // 批量递减 favorite_count
        for (Long qid : questionIds) {
            try {
                questionMapper.decrementFavoriteCount(qid);
            } catch (Exception e) {
                logger.warn("递减题目{}收藏数失败", qid, e);
            }
        }
        return deleted;
    }
}
