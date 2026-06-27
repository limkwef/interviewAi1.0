# AI 模拟面试系统 — 数据库设计

> **版本：** v2.0  
> **日期：** 2026-06-13  
> **数据库：** MySQL 8.0+ (utf8mb4)  
> **缓存：** Redis 8.0

---

## 1. 数据库整体概览

### 1.1 ER 图概要

```
┌───────────┐       ┌──────────────────┐        ┌──────────────┐
│   user    │1───→N │ interview_session │1───→N  │interview_msg │
└───────────┘       └──────────────────┘        └──────────────┘
      │                      │                         │
      │                      │1                         │
      │                      ↓                         │
      │               ┌──────────────┐                  │
      │               │interview_    │                  │
      │               │   report     │                  │
      │               └──────────────┘                  │
      │                      │1                         │
      │                      ↓                          │
      │               ┌──────────────┐                  │
      │               │report_comment│                  │
      │               └──────────────┘                  │
      │                                                │
      │  ┌────────────┐     ┌──────────────┐           │
      │  │ diagnosis  │     │mistake_record│           │
      │  │  _report   │     └──────┬───────┘           │
      │  └────────────┘           │                    │
      │                           │1                    │
      │                           ↓                    │
      │                    ┌──────────────┐            │
      │                    │mistake_      │            │
      │                    │answer_detail │            │
      │                    └──────────────┘            │
      │                                                │
      │  ┌──────────────┐    ┌──────────────┐          │
      │  │learning_task │    │  user_fav    │          │
      │  └──────────────┘    └──────┬───────┘          │
      │                             │                  │
      │  ┌──────────────┐          │                  │
      │  │  feedback    │          N                  │
      │  └──────────────┘          │                  │
      │                     ┌──────┴───────┐          │
      │                     │  question    │N───M──tag │
      │                     └──────────────┘          │
      │                                                   
      │  ┌──────────────┐
      │  │  admin_log   │
      │  └──────────────┘
```

### 1.2 数据库命名约定

- 数据库名：`interview`
- 表名：全小写 + 下划线
- 字段名：全小写 + 下划线
- 主键：`id` (BIGINT AUTO_INCREMENT)
- 时间字段：`created_at`, `updated_at`
- 逻辑删除：使用 `is_deleted` 标记（`question` 表）

---

## 2. 表结构详细设计

### 2.1 用户表 (user)

用户核心信息表，支持邮箱/手机号双通道登录。

```sql
CREATE TABLE `user` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username`        VARCHAR(50)  NOT NULL                COMMENT '用户名',
  `email`           VARCHAR(100) DEFAULT NULL             COMMENT '邮箱（唯一）',
  `phone`           VARCHAR(20)  DEFAULT NULL             COMMENT '手机号（唯一）',
  `password`        VARCHAR(255) NOT NULL                COMMENT 'BCrypt加密密码',
  `avatar`          VARCHAR(500) DEFAULT NULL             COMMENT '头像URL',
  `target_position` VARCHAR(50)  DEFAULT NULL             COMMENT '目标岗位(java_backend/frontend/fullstack/algorithm)',
  `tech_stack`      JSON         DEFAULT NULL             COMMENT '技术栈标签["Java","Spring"]',
  `role`            VARCHAR(20)  NOT NULL DEFAULT 'user'  COMMENT '角色(user/admin)',
  `status`          TINYINT      NOT NULL DEFAULT 1       COMMENT '状态(1=启用, 0=禁用)',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_email` (`email`),
  UNIQUE KEY `uk_phone` (`phone`),
  KEY `idx_role` (`role`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';
```

**设计说明：**
- email 和 phone 各为唯一键，用户选其一注册
- 密码使用 BCrypt 加密存储，绝不存明文
- tech_stack 使用 JSON 类型存储标签数组
- target_position 映射固定四个岗位枚举值

---

### 2.2 题目表 (question)

面试题库核心表，支持多维度分类筛选。

```sql
CREATE TABLE `question` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `title`          VARCHAR(200) NOT NULL                COMMENT '题目标题',
  `content`        TEXT         NOT NULL                COMMENT '题目内容（Markdown）',
  `answer`         TEXT         NOT NULL                COMMENT '参考答案（Markdown）',
  `category`       VARCHAR(50)  NOT NULL                COMMENT '分类(java/spring/数据库/前端/...）',
  `difficulty`     VARCHAR(20)  NOT NULL DEFAULT 'medium' COMMENT '难度(easy/medium/hard)',
  `direction`      VARCHAR(50)  DEFAULT NULL             COMMENT '方向(java_backend/frontend/fullstack/algorithm)',
  `view_count`     INT          NOT NULL DEFAULT 0       COMMENT '浏览量',
  `favorite_count` INT          NOT NULL DEFAULT 0       COMMENT '收藏数',
  `is_deleted`     TINYINT      NOT NULL DEFAULT 0       COMMENT '逻辑删除(0=正常,1=删除)',
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category`),
  KEY `idx_difficulty` (`difficulty`),
  KEY `idx_direction` (`direction`),
  KEY `idx_category_difficulty` (`category`, `difficulty`),
  KEY `idx_is_deleted` (`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目表';
```

**设计说明：**
- is_deleted 逻辑删除，管理员删除后数据保留
- 联合索引 `category + difficulty` 覆盖面试出题查询
- view_count 和 favorite_count 为冗余计数，避免 COUNT 查询

---

### 2.3 标签表 (tag)

```sql
CREATE TABLE `tag` (
  `id`   BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` VARCHAR(50) NOT NULL                COMMENT '标签名称',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签表';
```

### 2.4 题目-标签关联表 (question_tag_rel)

```sql
CREATE TABLE `question_tag_rel` (
  `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `question_id` BIGINT NOT NULL                COMMENT '题目ID',
  `tag_id`      BIGINT NOT NULL                COMMENT '标签ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_question_tag` (`question_id`, `tag_id`),
  KEY `idx_tag_id` (`tag_id`),
  CONSTRAINT `fk_rel_question` FOREIGN KEY (`question_id`) REFERENCES `question`(`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_rel_tag`      FOREIGN KEY (`tag_id`)      REFERENCES `tag`(`id`)      ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目标签关联表';
```

---

### 2.5 用户收藏表 (user_favorite)

```sql
CREATE TABLE `user_favorite` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id`     BIGINT   NOT NULL                COMMENT '用户ID',
  `question_id` BIGINT   NOT NULL                COMMENT '题目ID',
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_question` (`user_id`, `question_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_question_id` (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏表';
```

**设计说明：**
- 联合唯一键防止重复收藏
- 收藏数量通过 `question.favorite_count` 冗余维护

---

### 2.6 反馈表 (feedback)

```sql
CREATE TABLE `feedback` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id`    BIGINT       DEFAULT NULL            COMMENT '用户ID',
  `type`       VARCHAR(50)  NOT NULL                COMMENT '反馈类型',
  `content`    TEXT         NOT NULL                COMMENT '反馈内容',
  `contact`    VARCHAR(200) DEFAULT NULL            COMMENT '联系方式',
  `status`     TINYINT      NOT NULL DEFAULT 0      COMMENT '处理状态(0=未处理,1=已处理)',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='反馈表';
```

---

### 2.7 面试会话表 (interview_session)

```sql
CREATE TABLE `interview_session` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id`         BIGINT       NOT NULL                COMMENT '用户ID',
  `position`        VARCHAR(50)  NOT NULL                COMMENT '岗位',
  `round`           VARCHAR(20)  NOT NULL                COMMENT '轮次(technical/hr/comprehensive)',
  `difficulty`      VARCHAR(20)  NOT NULL                COMMENT '难度(easy/medium/hard)',
  `question_count`  INT          NOT NULL                COMMENT '总题目数',
  `current_question`INT          NOT NULL DEFAULT 0      COMMENT '当前题目索引',
  `question_ids`    TEXT         NOT NULL                COMMENT '题目ID列表JSON [1,2,3]',
  `conversation_json` LONGTEXT   DEFAULT NULL            COMMENT '备用对话记录JSON（预留）',
  `status`          VARCHAR(20)  NOT NULL DEFAULT 'in_progress' COMMENT '状态(in_progress/completed)',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_user_status` (`user_id`, `status`),
  KEY `idx_created_at` (`created_at`),
  CONSTRAINT `fk_session_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='面试会话表';
```

**设计说明：**
- question_ids 存储为 JSON 字符串（如 `[3, 7, 12]`），记录本次面试的题目顺序
- status + user_id 联合索引用于查询用户的进行中/已完成面试
- 新增面试时通过 Redis 分布式锁防止同用户重复创建

---

### 2.8 面试消息表 (interview_message)

```sql
CREATE TABLE `interview_message` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id`     BIGINT       NOT NULL                COMMENT '会话ID',
  `role`           VARCHAR(10)  NOT NULL                COMMENT '角色(ai/user)',
  `content`        TEXT         NOT NULL                COMMENT '消息内容（Markdown）',
  `message_type`   VARCHAR(50)  DEFAULT NULL            COMMENT '消息类型(question/answer/evaluation/followup/greeting/system)',
  `question_index` INT          DEFAULT NULL            COMMENT '所属题目序号',
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_session_question` (`session_id`, `question_index`),
  CONSTRAINT `fk_msg_session` FOREIGN KEY (`session_id`) REFERENCES `interview_session`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='面试消息表';
```

**设计说明：**
- role 区分 AI 和用户消息，message_type 进一步细化消息用途
- question_index 关联到第几题，便于按题展示对话

---

### 2.9 面试报告表 (interview_report)

```sql
CREATE TABLE `interview_report` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id`        BIGINT       NOT NULL                COMMENT '会话ID',
  `user_id`           BIGINT       NOT NULL                COMMENT '用户ID',
  `total_score`       INT          NOT NULL DEFAULT 0      COMMENT '总分(0-100)',
  `level`             VARCHAR(20)  DEFAULT NULL             COMMENT '等级(excellent/good/medium/need_improvement/fail)',
  `technical_score`   INT          NOT NULL DEFAULT 0      COMMENT '技术能力分',
  `expression_score`  INT          NOT NULL DEFAULT 0      COMMENT '表达能力分',
  `logic_score`       INT          NOT NULL DEFAULT 0      COMMENT '逻辑思维分',
  `completeness_score` INT         NOT NULL DEFAULT 0      COMMENT '完整度分',
  `innovation_score`  INT          NOT NULL DEFAULT 0      COMMENT '创新性分',
  `suggestion`        TEXT         DEFAULT NULL             COMMENT '综合建议',
  `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session` (`session_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_total_score` (`total_score`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_user_created` (`user_id`, `created_at`),
  CONSTRAINT `fk_report_session` FOREIGN KEY (`session_id`) REFERENCES `interview_session`(`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_report_user`    FOREIGN KEY (`user_id`)    REFERENCES `user`(`id`)           ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='面试报告表';
```

**设计说明：**
- session_id 唯一键确保一个面试只会生成一份报告
- 五维分数各自存储，便于按维度统计和分析
- level 字段由总分自动计算等级

---

### 2.10 报告评语表 (report_comment)

```sql
CREATE TABLE `report_comment` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `report_id`        BIGINT       NOT NULL                COMMENT '报告ID',
  `question_text`    VARCHAR(500) DEFAULT NULL             COMMENT '题目文本',
  `user_answer`      TEXT         DEFAULT NULL             COMMENT '用户回答',
  `score`            INT          DEFAULT NULL             COMMENT '本题得分',
  `comment`          TEXT         DEFAULT NULL             COMMENT 'AI评语',
  `sort_order`       INT          NOT NULL DEFAULT 0      COMMENT '排序序号',
  `duration_seconds` INT          DEFAULT NULL             COMMENT '答题耗时(秒)',
  PRIMARY KEY (`id`),
  KEY `idx_report_id` (`report_id`),
  KEY `idx_sort_order` (`sort_order`),
  CONSTRAINT `fk_comment_report` FOREIGN KEY (`report_id`) REFERENCES `interview_report`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报告评语表';
```

---

### 2.11 错题记录表 (mistake_record)

```sql
CREATE TABLE `mistake_record` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id`          BIGINT       NOT NULL                COMMENT '用户ID',
  `question_id`      BIGINT       NOT NULL                COMMENT '题目ID',
  `first_mistake_time` DATETIME   NOT NULL                COMMENT '首次错误时间',
  `last_mistake_time`  DATETIME   NOT NULL                COMMENT '最近错误时间',
  `mistake_count`    INT          NOT NULL DEFAULT 1      COMMENT '错误次数',
  `status`           TINYINT      NOT NULL DEFAULT 0      COMMENT '状态(0=待复习,1=已掌握)',
  `mastered_time`    DATETIME     DEFAULT NULL             COMMENT '掌握时间',
  `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_question` (`user_id`, `question_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_user_status` (`user_id`, `status`),
  KEY `idx_last_mistake_time` (`last_mistake_time`),
  CONSTRAINT `fk_mistake_user`     FOREIGN KEY (`user_id`)     REFERENCES `user`(`id`)     ON DELETE CASCADE,
  CONSTRAINT `fk_mistake_question` FOREIGN KEY (`question_id`) REFERENCES `question`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='错题记录表';
```

**设计说明：**
- user_id + question_id 联合唯一，同一道题不会重复记录
- mistake_count 累计错误次数，status 标记已掌握/待复习
- 面试结束后评分 ≤ 3 分的题目自动导入

---

### 2.12 错题作答详情表 (mistake_answer_detail)

```sql
CREATE TABLE `mistake_answer_detail` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `mistake_id`   BIGINT       NOT NULL                COMMENT '错题记录ID',
  `interview_id` BIGINT       DEFAULT NULL             COMMENT '面试会话ID',
  `user_answer`  TEXT         DEFAULT NULL             COMMENT '用户回答内容',
  `ai_comment`   TEXT         DEFAULT NULL             COMMENT 'AI评语',
  `category`     VARCHAR(50)  DEFAULT NULL             COMMENT '错误分类',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_mistake_id` (`mistake_id`),
  CONSTRAINT `fk_detail_mistake` FOREIGN KEY (`mistake_id`) REFERENCES `mistake_record`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='错题作答详情表';
```

---

### 2.13 诊断报告表 (diagnosis_report)

```sql
CREATE TABLE `diagnosis_report` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id`           BIGINT       NOT NULL                COMMENT '用户ID',
  `session_id`        BIGINT       DEFAULT NULL             COMMENT '面试会话ID',
  `report_id`         BIGINT       DEFAULT NULL             COMMENT '面试报告ID',
  `total_score`       INT          NOT NULL DEFAULT 0      COMMENT '总分',
  `level`             VARCHAR(20)  DEFAULT NULL             COMMENT '等级',
  `score_change`      INT          DEFAULT 0               COMMENT '分数变化',
  `previous_score`    INT          DEFAULT NULL             COMMENT '上次分数',
  `knowledge_analysis`     JSON    DEFAULT NULL             COMMENT '知识维度分析',
  `thinking_analysis`      JSON    DEFAULT NULL             COMMENT '思维模式分析',
  `mistake_patterns`       JSON    DEFAULT NULL             COMMENT '错误模式归纳',
  `learning_plan`          JSON    DEFAULT NULL             COMMENT '学习计划',
  `detailed_comments`      JSON    DEFAULT NULL             COMMENT '详细评语（每题逐条）',
  `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_report_id` (`report_id`),
  KEY `idx_created_at` (`created_at`),
  CONSTRAINT `fk_diag_user`    FOREIGN KEY (`user_id`)    REFERENCES `user`(`id`)      ON DELETE CASCADE,
  CONSTRAINT `fk_diag_session` FOREIGN KEY (`session_id`) REFERENCES `interview_session`(`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_diag_report`  FOREIGN KEY (`report_id`)  REFERENCES `interview_report`(`id`)  ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='诊断报告表';
```

**设计说明：**
- 五个分析字段均为 JSON 类型，存储 DeepSeek 生成的复杂结构化分析结果
- score_change + previous_score 支持与前次对比的分数趋势
- 每次面试结束后自动生成一份诊断报告

---

### 2.14 学习任务表 (learning_task)

```sql
CREATE TABLE `learning_task` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id`      BIGINT       NOT NULL                COMMENT '用户ID',
  `phase_index`  INT          NOT NULL                COMMENT '阶段序号',
  `task_index`   INT          NOT NULL                COMMENT '任务序号',
  `phase_name`   VARCHAR(100) DEFAULT NULL             COMMENT '阶段名称',
  `task_text`    TEXT         NOT NULL                COMMENT '任务描述',
  `focus_area`   VARCHAR(50)  DEFAULT NULL             COMMENT '专注领域',
  `completed`    TINYINT      NOT NULL DEFAULT 0      COMMENT '是否完成(0=未完成,1=已完成)',
  `completed_at` DATETIME     DEFAULT NULL             COMMENT '完成时间',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_phase_task` (`user_id`, `phase_index`, `task_index`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_phase_index` (`phase_index`),
  CONSTRAINT `fk_task_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习任务表';
```

**设计说明：**
- 基于诊断报告的 learning_plan 解析生成，分阶段组织
- phase_index + task_index 确定唯一任务

---

### 2.15 管理员操作日志表 (admin_log)

```sql
CREATE TABLE `admin_log` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `admin_id`    BIGINT       NOT NULL                COMMENT '管理员用户ID',
  `admin_name`  VARCHAR(50)  NOT NULL                COMMENT '管理员用户名',
  `action`      VARCHAR(100) NOT NULL                COMMENT '操作类型(create/update/delete/...)',
  `target_type` VARCHAR(50)  NOT NULL                COMMENT '操作对象类型(user/question/feedback)',
  `target_id`   BIGINT       DEFAULT NULL             COMMENT '操作对象ID',
  `detail`      TEXT         DEFAULT NULL             COMMENT '操作详情',
  `ip_address`  VARCHAR(50)  DEFAULT NULL             COMMENT '操作IP',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_admin_id` (`admin_id`),
  KEY `idx_target_type` (`target_type`),
  KEY `idx_action` (`action`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员操作日志表';
```

---

## 3. 表关系汇总

| # | 表名 | 关联 | 外键字段 | 参照表 |
|---|------|------|---------|--------|
| 1 | interview_session | N:1 → user | user_id | user |
| 2 | interview_message | N:1 → interview_session | session_id | interview_session |
| 3 | interview_report | 1:1 → interview_session | session_id | interview_session |
| 4 | interview_report | N:1 → user | user_id | user |
| 5 | report_comment | N:1 → interview_report | report_id | interview_report |
| 6 | question_tag_rel | N:1 → question | question_id | question |
| 7 | question_tag_rel | N:1 → tag | tag_id | tag |
| 8 | user_favorite | N:1 → user | user_id | user |
| 9 | user_favorite | N:1 → question | question_id | question |
| 10 | mistake_record | N:1 → user | user_id | user |
| 11 | mistake_record | N:1 → question | question_id | question |
| 12 | mistake_answer_detail | N:1 → mistake_record | mistake_id | mistake_record |
| 13 | diagnosis_report | N:1 → user | user_id | user |
| 14 | diagnosis_report | N:1 → interview_session | session_id | interview_session |
| 15 | diagnosis_report | N:1 → interview_report | report_id | interview_report |
| 16 | learning_task | N:1 → user | user_id | user |

---

## 4. 索引策略

### 4.1 唯一索引

| 表 | 索引名 | 字段 | 作用 |
|----|--------|------|------|
| user | uk_email | email | 邮箱唯一 |
| user | uk_phone | phone | 手机号唯一 |
| tag | uk_name | name | 标签名唯一 |
| interview_report | uk_session | session_id | 一面试一报告 |
| question_tag_rel | uk_question_tag | question_id, tag_id | 防重复关联 |
| user_favorite | uk_user_question | user_id, question_id | 防重复收藏 |
| mistake_record | uk_user_question | user_id, question_id | 防重复记录 |
| learning_task | uk_user_phase_task | user_id, phase_index, task_index | 定位唯一任务 |

### 4.2 联合索引

| 表 | 索引名 | 字段 | 用途 |
|----|--------|------|------|
| question | idx_category_difficulty | category, difficulty | 面试出题筛选 |
| interview_session | idx_user_status | user_id, status | 查用户面试列表 |
| interview_message | idx_session_question | session_id, question_index | 按题查对话 |

### 4.3 高频查询索引

| 表 | 字段 | 场景 |
|----|------|------|
| question | is_deleted | 排除已删除题目 |
| feedback | status | 管理后台按状态筛选 |
| mistake_record | user_id, status | 错题本按用户+状态 |
| admin_log | created_at | 日志按时间排序 |

---

## 5. 缓存设计 (Redis)

### 5.1 缓存数据

| Key 模式 | 数据类型 | TTL | 用途 |
|----------|---------|-----|------|
| `question:list:{params}` | String (JSON) | 10min | 题目分页列表 |
| `question:id:{id}` | String (JSON) | 10min | 单题详情 |
| `question:count:{params}` | String (JSON) | 10min | 题目统计数 |
| `user:{id}` | String (JSON) | 1h | 用户信息 |
| `dashboard:{userId}` | String (JSON) | 5min | 首页看板 |
| `dashboard:stats:{userId}` | String (JSON) | 5min | 统计数据 |
| `dashboard:trend:{userId}` | String (JSON) | 5min | 分数趋势 |
| `dashboard:knowledge:{userId}` | String (JSON) | 5min | 知识概览 |
| `diagnosis:{id}` | String (JSON) | 2h | 诊断报告 |
| `diagnosis:session:{sessionId}` | String (JSON) | 2h | 按会话查诊断 |
| `diagnosis:latest:{userId}` | String (JSON) | 2h | 最新诊断 |
| `competition:{userId}` | String (JSON) | 1h | 竞技分析 |
| `interview:lock:user:{userId}` | String (锁) | 30s | 创建面试分布式锁 |
| `interview:context:{sessionId}` | Hash | 2h | 面试上下文（当前进度） |
| `interview:recent:{userId}` | Set | 30d | 近期题目 ID 去重 |
| `baidu:access_token` | String | 29d | 百度语音 Token |

### 5.2 缓存清除策略

| 触发事件 | 清除内容 |
|----------|---------|
| 管理员新增/修改/删除题目 | `question:*` 全部清除 |
| 用户修改信息 | `user:{id}` 清除 |
| 面试结束 | `dashboard:*:{userId}` 清除 |
| 新诊断生成 | `diagnosis:*` 相关清除 |
| 竞技分析刷新 | `competition:{userId}` 清除 |

---

## 6. Redis-only 数据结构（非 MySQL）

### 6.1 竞技分析 (CompetitiveAnalysis)

不落 MySQL，完全基于 Redis 缓存构建，数据结构如下：

```json
{
  "userId": 1,
  "currentScore": 85,
  "targetScore": 92,
  "dimensions": {
    "technical": 82,
    "expression": 88,
    "logic": 85,
    "completeness": 80,
    "innovation": 78
  },
  "peerComparison": {
    "technical": { "percentile": 65, "avg": 75 },
    "expression": { "percentile": 80, "avg": 72 }
  },
  "trend": { "slope": 2.3, "direction": "up" },
  "improvementPrediction": "预计3次面试后可提升至88分"
}
```

---

## 7. E-R 关系描述

```
User (1) ──< 创建 >── (N) InterviewSession
InterviewSession (1) ──< 包含 >── (N) InterviewMessage
InterviewSession (1) ──< 生成 >── (1) InterviewReport
InterviewReport (1) ──< 包含 >── (N) ReportComment

User (1) ──< 收藏 >── (N) UserFavorite (N) ──< 被收藏 >── (1) Question
Question (N) ──< 标签关联 >── (N) Tag

User (1) ──< 记录 >── (N) MistakeRecord (N) ──< 关联 >── (1) Question
MistakeRecord (1) ──< 包含 >── (N) MistakeAnswerDetail

User (1) ──< 拥有 >── (N) DiagnosisReport
User (1) ──< 拥有 >── (N) LearningTask
User (1) ──< 提交 >── (N) Feedback

Admin (1) ──< 产生 >── (N) AdminLog
```
