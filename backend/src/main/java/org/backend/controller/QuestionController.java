package org.backend.controller;

import org.backend.common.Result;
import org.backend.entity.Question;
import org.backend.service.QuestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private static final Logger logger = LoggerFactory.getLogger(QuestionController.class);

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String keyword) {
        Map<String, Object> data = questionService.getQuestionList(page, size, category, difficulty, direction, keyword);
        return Result.success(data);
    }

    @GetMapping("/{id}")
    public Result<Question> detail(@PathVariable Long id) {
        Question question = questionService.getQuestionById(id);
        return Result.success(question);
    }

    /**
     * 获取题目数量（支持按岗位和难度筛选）
     */
    @GetMapping("/count")
    public Result<Integer> count(@RequestParam(required = false) String direction,
                                 @RequestParam(required = false) String difficulty) {
        return Result.success(questionService.getQuestionCount(direction, difficulty));
    }
}
