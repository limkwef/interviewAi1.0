-- AI模型配置表
CREATE TABLE IF NOT EXISTS ai_model (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    model_name VARCHAR(50) NOT NULL COMMENT '模型显示名称',
    model_code VARCHAR(50) NOT NULL COMMENT '模型标识(deepseek-chat/gpt-4o/qwen-turbo)',
    provider VARCHAR(50) NOT NULL COMMENT '供应商(deepseek/openai/aliyun/custom)',
    api_url VARCHAR(255) NOT NULL COMMENT 'API地址',
    api_key VARCHAR(255) NOT NULL COMMENT 'API密钥',
    max_tokens INT DEFAULT 2048,
    temperature DECIMAL(3,2) DEFAULT 0.70,
    supports_stream TINYINT DEFAULT 1 COMMENT '是否支持流式输出',
    supports_structured TINYINT DEFAULT 1 COMMENT '是否支持结构化JSON输出',
    is_default TINYINT DEFAULT 0 COMMENT '是否系统默认模型',
    is_enabled TINYINT DEFAULT 1 COMMENT '是否启用',
    description VARCHAR(255) COMMENT '模型描述',
    sort_order INT DEFAULT 0 COMMENT '排序',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 插入默认DeepSeek模型
INSERT INTO ai_model (model_name, model_code, provider, api_url, api_key, max_tokens, temperature, supports_stream, supports_structured, is_default, is_enabled, description, sort_order)
VALUES ('DeepSeek Chat', 'deepseek-chat', 'deepseek', 'https://api.deepseek.com', 'YOUR_DEEPSEEK_API_KEY', 2048, 0.70, 1, 1, 1, 1, 'DeepSeek V3，性价比高，支持中文', 1);

-- interview_session 新增字段
ALTER TABLE interview_session ADD COLUMN model_id BIGINT DEFAULT NULL COMMENT '使用的AI模型ID';
ALTER TABLE interview_session ADD COLUMN progressive_mode TINYINT DEFAULT 1 COMMENT '是否启用渐进式披露(1=是,0=否)';
