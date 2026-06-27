USE interview;

-- ======================== 简历模块 DDL ========================

-- 简历主表
CREATE TABLE IF NOT EXISTS `resume` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       BIGINT       NOT NULL                COMMENT '用户ID',
    `version`       INT          NOT NULL DEFAULT 1      COMMENT '版本号，同用户下递增',
    `source`        VARCHAR(20)  NOT NULL DEFAULT 'upload' COMMENT '来源：upload-上传解析, template-在线填写',
    `status`        TINYINT      NOT NULL DEFAULT 0      COMMENT '解析状态：0-解析中 1-解析完成 2-解析失败',
    `error_msg`     VARCHAR(500) DEFAULT NULL             COMMENT '解析失败原因',
    `raw_text`      MEDIUMTEXT   DEFAULT NULL             COMMENT '原始提取文本',
    `parsed_data`   JSON         DEFAULT NULL             COMMENT 'AI 解析结果 / 表单提交的结构化数据',
    `file_name`     VARCHAR(255) DEFAULT NULL             COMMENT '原始文件名',
    `file_url`      VARCHAR(500) DEFAULT NULL             COMMENT '文件存储路径',
    `file_size`     BIGINT       DEFAULT NULL             COMMENT '文件大小（字节）',
    `is_active`     TINYINT      NOT NULL DEFAULT 0       COMMENT '是否当前生效：0-否 1-是',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_user_active` (`user_id`, `is_active`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='简历主表';

-- interview_session 表添加 resume_id 字段（方案B：直接关联）
ALTER TABLE `interview_session` ADD COLUMN `resume_id` BIGINT DEFAULT NULL COMMENT '关联简历ID' AFTER `question_ids`;
ALTER TABLE `interview_session` ADD INDEX `idx_resume_id` (`resume_id`);
ALTER TABLE `interview_session` ADD INDEX `idx_user_created` (`user_id`, `created_at`);

-- interview_session 表添加 interview_type 字段
ALTER TABLE `interview_session` ADD COLUMN `interview_type` VARCHAR(20) NOT NULL DEFAULT 'normal' COMMENT '面试类型：normal-普通面试 resume-简历面试' AFTER `resume_id`;

INSERT IGNORE INTO user (username, email, phone, password, target_position, tech_stack, role) VALUES
('test', 'test@example.com', '13800138000', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'Java后端开发', JSON_ARRAY('Java', 'Spring Boot', 'MySQL'), 'user');

INSERT IGNORE INTO user_favorite (user_id, question_id) VALUES
(1, 1), (1, 2), (1, 4);
