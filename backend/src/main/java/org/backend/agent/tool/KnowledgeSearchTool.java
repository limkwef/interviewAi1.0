package org.backend.agent.tool;

import org.backend.entity.KnowledgePoint;
import org.backend.mapper.KnowledgePointMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 知识点查询工具
 * Agent 在面试中查询知识点参考答案，用于生成更精准的追问
 */
@Component
public class KnowledgeSearchTool implements Tool {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeSearchTool.class);

    private final KnowledgePointMapper knowledgePointMapper;

    public KnowledgeSearchTool(KnowledgePointMapper knowledgePointMapper) {
        this.knowledgePointMapper = knowledgePointMapper;
    }

    @Override
    public String getName() {
        return "knowledge_search";
    }

    @Override
    public String getDescription() {
        return "查询知识点参考信息。当你需要确认某个技术概念的准确内容、标准答案或最佳实践时使用。";
    }

    @Override
    public String getParametersSchema() {
        return "{\"keyword\": \"知识点关键词(必填)\", \"category\": \"分类(可选): java_basic/spring/database/redis/network 等\"}";
    }

    @Override
    public String execute(Map<String, Object> params) {
        String keyword = (String) params.get("keyword");
        String category = (String) params.get("category");

        if (keyword == null || keyword.isBlank()) {
            return "错误：缺少 keyword 参数";
        }

        try {
            List<KnowledgePoint> results = knowledgePointMapper.findByKeyword(
                    keyword, category, 3);

            if (results.isEmpty()) {
                return "未找到与「" + keyword + "」相关的知识点。请基于你的专业知识回答。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("找到 ").append(results.size()).append(" 条相关知识点：\n\n");
            for (int i = 0; i < results.size(); i++) {
                KnowledgePoint kp = results.get(i);
                sb.append(i + 1).append(". 【").append(kp.getKeyword()).append("】");
                sb.append(" [分类:").append(kp.getCategory()).append("]");
                sb.append(" [难度:").append(kp.getDifficulty()).append("]\n");
                sb.append("   ").append(kp.getContent()).append("\n\n");
            }

            logger.info("KnowledgeSearchTool: keyword={}, 找到{}条知识点", keyword, results.size());
            return sb.toString();
        } catch (Exception e) {
            logger.error("KnowledgeSearchTool 执行失败", e);
            return "知识点查询失败：" + e.getMessage();
        }
    }
}
