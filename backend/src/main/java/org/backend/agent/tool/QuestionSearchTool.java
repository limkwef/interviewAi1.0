package org.backend.agent.tool;

import org.backend.entity.Question;
import org.backend.mapper.QuestionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 题库搜索工具
 * Agent 在面试中按关键词搜索题库，找到相关题目作为追问参考
 */
@Component
public class QuestionSearchTool implements Tool {

    private static final Logger logger = LoggerFactory.getLogger(QuestionSearchTool.class);

    private final QuestionMapper questionMapper;

    public QuestionSearchTool(QuestionMapper questionMapper) {
        this.questionMapper = questionMapper;
    }

    @Override
    public String getName() {
        return "question_search";
    }

    @Override
    public String getDescription() {
        return "按关键词搜索题库，找到相关题目。当你需要了解某个知识点的常见面试题时使用。";
    }

    @Override
    public String getParametersSchema() {
        return "{\"keyword\": \"搜索关键词(必填)\", \"category\": \"题目分类(可选): java_basic/spring/database/redis/algorithm/network 等\"}";
    }

    @Override
    public String execute(Map<String, Object> params) {
        String keyword = (String) params.get("keyword");
        String category = (String) params.get("category");

        if (keyword == null || keyword.isBlank()) {
            return "错误：缺少 keyword 参数";
        }

        try {
            Map<String, Object> queryParams = new HashMap<>();
            queryParams.put("keyword", keyword);
            queryParams.put("offset", 0);
            queryParams.put("size", 5);
            if (category != null && !category.isBlank()) {
                queryParams.put("category", category);
            }

            List<Question> questions = questionMapper.findList(queryParams);

            if (questions.isEmpty()) {
                return "未找到与「" + keyword + "」相关的题目。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("找到 ").append(questions.size()).append(" 道相关题目：\n\n");
            for (int i = 0; i < questions.size(); i++) {
                Question q = questions.get(i);
                sb.append(i + 1).append(". ").append(q.getTitle());
                sb.append(" [分类:").append(q.getCategory()).append("]");
                sb.append(" [难度:").append(q.getDifficulty()).append("]");
                if (q.getContent() != null && !q.getContent().isEmpty()) {
                    String content = q.getContent().length() > 200
                            ? q.getContent().substring(0, 200) + "..." : q.getContent();
                    sb.append("\n   ").append(content);
                }
                sb.append("\n");
            }

            logger.info("QuestionSearchTool: keyword={}, 找到{}题", keyword, questions.size());
            return sb.toString();
        } catch (Exception e) {
            logger.error("QuestionSearchTool 执行失败", e);
            return "题库搜索失败：" + e.getMessage();
        }
    }
}
