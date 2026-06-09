-- 给面试会话表添加 question_ids 字段，存储从题库中抽选的题目ID列表
ALTER TABLE interview_session
    ADD COLUMN question_ids TEXT COMMENT '选中题目ID列表(JSON数组)';

-- 错题记录表
CREATE TABLE IF NOT EXISTS mistake_record (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id            BIGINT NOT NULL COMMENT '用户ID',
    question_id        BIGINT NOT NULL COMMENT '题目ID',
    first_mistake_time DATETIME NOT NULL COMMENT '首次答错时间',
    last_mistake_time  DATETIME NOT NULL COMMENT '最近一次答错时间',
    mistake_count      INT NOT NULL DEFAULT 1 COMMENT '累计答错次数',
    status             TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-待复习, 1-已掌握',
    mastered_time      DATETIME DEFAULT NULL COMMENT '标记为已掌握的时间',
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_question (user_id, question_id),
    INDEX idx_user_status (user_id, status),
    INDEX idx_user_last_time (user_id, last_mistake_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='错题记录表';

-- 错误作答详情表
CREATE TABLE IF NOT EXISTS mistake_answer_detail (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    mistake_id      BIGINT NOT NULL COMMENT '关联 mistake_record.id',
    interview_id    BIGINT DEFAULT NULL COMMENT '来源面试ID',
    user_answer     TEXT COMMENT '用户的错误答案',
    ai_comment      TEXT COMMENT 'AI点评',
    category        VARCHAR(50) DEFAULT NULL COMMENT '知识点分类',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_mistake_id (mistake_id),
    INDEX idx_interview_id (interview_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='错误作答详情表';

-- AI 深度诊断报告表
CREATE TABLE IF NOT EXISTS diagnosis_report (
    id                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '诊断报告ID',
    user_id              BIGINT       NOT NULL                 COMMENT '用户ID',
    session_id           BIGINT       NOT NULL                 COMMENT '面试会话ID',
    report_id            BIGINT       NOT NULL                 COMMENT '关联评分报告ID',
    total_score          INT          NOT NULL DEFAULT 0       COMMENT '总分',
    level                VARCHAR(10)  DEFAULT ''               COMMENT '等级',
    score_change         INT          DEFAULT 0                COMMENT '较上次变化',
    previous_score       INT          DEFAULT 0                COMMENT '上次总分',
    knowledge_analysis   JSON         DEFAULT NULL             COMMENT '知识维度分析',
    thinking_analysis    JSON         DEFAULT NULL             COMMENT '思维模式分析',
    mistake_patterns     JSON         DEFAULT NULL             COMMENT '错误模式识别',
    learning_plan        JSON         DEFAULT NULL             COMMENT '学习计划',
    detailed_comments    JSON         DEFAULT NULL             COMMENT '逐题详细点评',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_session_id (session_id),
    KEY idx_report_id (report_id),
    KEY idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI面试诊断报告表';

-- 学习任务表（个性化学习路径）
CREATE TABLE IF NOT EXISTS learning_task (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id      BIGINT NOT NULL COMMENT '用户ID',
    phase_index  INT NOT NULL COMMENT '阶段序号（0-based）',
    task_index   INT NOT NULL COMMENT '任务序号（0-based）',
    phase_name   VARCHAR(100) COMMENT '阶段名称（冗余，方便展示）',
    task_text    VARCHAR(500) COMMENT '任务描述（冗余）',
    focus_area   VARCHAR(200) COMMENT '该阶段的学习重点',
    completed    TINYINT(1) DEFAULT 0 COMMENT '是否完成',
    completed_at DATETIME COMMENT '完成时间',
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_phase_task (user_id, phase_index, task_index),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习任务表';

-- v1.1: 面试计时功能 - report_comment 增加每题耗时字段
ALTER TABLE report_comment
    ADD COLUMN duration_seconds INT NOT NULL DEFAULT 0 COMMENT '该题耗时（秒）';
