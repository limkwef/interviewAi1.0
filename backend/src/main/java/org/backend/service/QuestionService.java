package org.backend.service;

import org.backend.entity.Question;
import org.backend.exception.BusinessException;
import org.backend.mapper.QuestionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class QuestionService {

    private static final Logger logger = LoggerFactory.getLogger(QuestionService.class);

    private final QuestionMapper questionMapper;

    public QuestionService(QuestionMapper questionMapper) {
        this.questionMapper = questionMapper;
    }

    @Cacheable(cacheNames = "questions", key = "'list:' + #category + ':' + #difficulty + ':' + #direction + ':' + #keyword + ':' + #page")
    public Map<String, Object> getQuestionList(Integer page, Integer size, String category, String difficulty, String direction, String keyword) {
        logger.debug("查询题目列表，page={}, size={}, category={}, difficulty={}", page, size, category, difficulty);

        Map<String, Object> params = new HashMap<>();
        params.put("category", category);
        params.put("difficulty", difficulty);
        params.put("direction", direction);
        params.put("keyword", keyword);
        params.put("offset", (page - 1) * size);
        params.put("size", size);

        List<Question> records = questionMapper.findList(params);
        int total = questionMapper.countList(params);

        Map<String, Object> data = new HashMap<>();
        data.put("records", records);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return data;
    }

    public Question getQuestionById(Long id) {
        questionMapper.incrementViewCount(id);
        Question question = questionMapper.findById(id);
        if (question == null) {
            throw new BusinessException(404, "题目不存在");
        }
        return question;
    }

    public int getQuestionCount(String direction, String difficulty) {
        Map<String, Object> params = new HashMap<>();
        params.put("direction", direction);
        params.put("difficulty", difficulty);
        return questionMapper.countList(params);
    }

    @CacheEvict(cacheNames = "questions", allEntries = true)
    public void clearQuestionCache() {
        logger.info("清除题库缓存");
    }
}
