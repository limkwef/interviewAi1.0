CREATE DATABASE IF NOT EXISTS interview DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci;
USE interview;

CREATE TABLE IF NOT EXISTS user (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  username        VARCHAR(50)   NOT NULL DEFAULT '',
  email           VARCHAR(100)  NOT NULL,
  phone           VARCHAR(20)   DEFAULT '',
  password        VARCHAR(255)  NOT NULL,
  avatar          VARCHAR(255)  DEFAULT '',
  target_position VARCHAR(50)   DEFAULT '',
  tech_stack      JSON          DEFAULT NULL,
  role            VARCHAR(20)   NOT NULL DEFAULT 'user',
  status          TINYINT       NOT NULL DEFAULT 1,
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_email (email),
  UNIQUE KEY uk_phone (phone),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 题目表
CREATE TABLE IF NOT EXISTS question (
  id             BIGINT        NOT NULL AUTO_INCREMENT,
  title          VARCHAR(200)  NOT NULL,
  content        TEXT          NOT NULL,
  answer         TEXT          NOT NULL,
  category       VARCHAR(50)   NOT NULL,
  difficulty     VARCHAR(20)   NOT NULL,
  direction      VARCHAR(50)   NOT NULL,
  view_count     INT           NOT NULL DEFAULT 0,
  favorite_count INT           NOT NULL DEFAULT 0,
  is_deleted     TINYINT       NOT NULL DEFAULT 0,
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_category (category),
  KEY idx_difficulty (difficulty),
  KEY idx_direction (direction),
  KEY idx_category_difficulty (category, difficulty),
  KEY idx_is_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 标签表
CREATE TABLE IF NOT EXISTS tag (
  id   BIGINT       NOT NULL AUTO_INCREMENT,
  name VARCHAR(50)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 题目-标签关联表
CREATE TABLE IF NOT EXISTS question_tag_rel (
  id          BIGINT NOT NULL AUTO_INCREMENT,
  question_id BIGINT NOT NULL,
  tag_id      BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_question_tag (question_id, tag_id),
  KEY idx_tag_id (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 用户收藏表
CREATE TABLE IF NOT EXISTS user_favorite (
  id          BIGINT   NOT NULL AUTO_INCREMENT,
  user_id     BIGINT   NOT NULL,
  question_id BIGINT   NOT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_question (user_id, question_id),
  KEY idx_user_id (user_id),
  KEY idx_question_id (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 初始标签数据
INSERT IGNORE INTO tag (name) VALUES
  ('集合'), ('数据结构'), ('多线程'), ('JVM'),
  ('Spring'), ('Spring Boot'), ('MyBatis'), ('MySQL'),
  ('Redis'), ('消息队列'), ('微服务'), ('设计模式'),
  ('HTML'), ('CSS'), ('JavaScript'), ('Vue'), ('React');

-- 示例题目
INSERT IGNORE INTO question (title, content, answer, category, difficulty, direction) VALUES
(
  '请解释HashMap的底层实现原理',
  '请详细说明HashMap的数据结构、put和get操作的执行过程、扩容机制等。',
  'HashMap底层采用数组+链表+红黑树的数据结构。JDK 1.8中，当链表长度超过8且数组长度大于等于64时，链表会转化为红黑树以提高查询效率。默认初始容量为16，负载因子为0.75。当元素数量超过容量×负载因子时触发扩容，扩容为原来的2倍。put操作首先计算key的hash值，然后定位到数组索引，若该位置为空则直接插入，若存在元素则判断key是否相等，相等则覆盖，否则以链表或红黑树方式插入。',
  'java_basic',
  'medium',
  'java_backend'
),
(
  '谈谈你对Spring IoC的理解',
  '请说明控制反转（IoC）的概念、实现方式以及Spring IoC容器的核心功能。',
  'IoC（控制反转）是一种设计思想，将对象的创建和依赖关系的管理从程序代码中转移到外部容器。Spring IoC容器通过依赖注入（DI）实现控制反转，主要有三种注入方式：构造器注入、Setter注入和注解注入。Spring IoC容器本质上是一个Bean工厂，负责读取配置、创建Bean实例、管理Bean的生命周期。主要接口是BeanFactory和ApplicationContext，后者在前者基础上增加了更多企业级功能。',
  'spring',
  'medium',
  'java_backend'
),
(
  '什么是数据库事务？事务的四大特性是什么？',
  '请解释数据库事务的概念以及ACID特性的含义。',
  '数据库事务是数据库管理系统执行过程中的一个逻辑单位，由一个有限的数据库操作序列构成。事务具有四大特性（ACID）：1. 原子性（Atomicity）：事务中的操作要么全部完成，要么全部不完成；2. 一致性（Consistency）：事务执行前后，数据库都处于一致状态；3. 隔离性（Isolation）：多个并发事务之间互不干扰；4. 持久性（Durability）：事务一旦提交，对数据库的改变是永久性的。',
  'database',
  'easy',
  'java_backend'
),
(
  'Vue的双向数据绑定原理是什么？',
  '请说明Vue.js中v-model的实现原理，以及响应式系统的运作机制。',
  'Vue 2使用Object.defineProperty()来劫持数据的getter和setter，实现响应式。当数据变化时，setter被触发，通知依赖该数据的Watcher进行更新。Vue 3则使用Proxy来替代Object.defineProperty，可以直接监听对象和数组的变化。v-model本质上是语法糖，等价于:value绑定和@input事件监听的组合。当用户输入时触发input事件，更新数据；数据变化时通过响应式系统自动更新视图。',
  'frontend',
  'medium',
  'frontend'
),
(
  '请解释JVM的垃圾回收机制',
  '请说明JVM中哪些对象需要被回收，以及常见的垃圾回收算法和收集器。',
  'JVM通过可达性分析算法判断对象是否可回收，从GC Roots出发，不可达的对象即为垃圾。常见垃圾回收算法：1. 标记-清除：标记存活对象，清除未标记对象，会产生内存碎片；2. 复制算法：将内存分为两块，每次使用一块，GC时将存活对象复制到另一块；3. 标记-整理：标记存活对象后向一端移动，消除碎片。常见收集器：Serial、ParNew、Parallel Scavenge、CMS、G1、ZGC等。JVM堆分为年轻代（Eden+Survivor）和老年代，不同代采用不同回收策略。',
  'java_basic',
  'hard',
  'java_backend'
);

-- 意见反馈表
CREATE TABLE IF NOT EXISTS feedback (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  user_id     BIGINT       NOT NULL,
  type        VARCHAR(50)  NOT NULL,
  content     TEXT         NOT NULL,
  contact     VARCHAR(100) DEFAULT '',
  status      TINYINT      NOT NULL DEFAULT 0,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 面试会话表
CREATE TABLE IF NOT EXISTS interview_session (
  id               BIGINT        NOT NULL AUTO_INCREMENT,
  user_id          BIGINT        NOT NULL,
  position         VARCHAR(50)   NOT NULL,
  round            VARCHAR(30)   NOT NULL,
  difficulty       VARCHAR(20)   NOT NULL,
  question_count   INT           NOT NULL,
  current_question INT           NOT NULL DEFAULT 1,
  conversation_json LONGTEXT     DEFAULT NULL,
  status           VARCHAR(20)   NOT NULL DEFAULT 'in_progress',
  created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_status (status),
  KEY idx_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 面试消息表
CREATE TABLE IF NOT EXISTS interview_message (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  session_id     BIGINT       NOT NULL,
  role           VARCHAR(10)  NOT NULL,
  content        TEXT         NOT NULL,
  message_type   VARCHAR(20)  DEFAULT '',
  question_index INT          DEFAULT 0,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_session_id (session_id),
  KEY idx_session_created (session_id, created_at),
  KEY idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 面试报告表
CREATE TABLE IF NOT EXISTS interview_report (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  session_id          BIGINT       NOT NULL,
  user_id             BIGINT       NOT NULL,
  total_score         INT          NOT NULL,
  level               VARCHAR(10)  NOT NULL,
  technical_score     INT          NOT NULL DEFAULT 0,
  expression_score    INT          NOT NULL DEFAULT 0,
  logic_score         INT          NOT NULL DEFAULT 0,
  completeness_score  INT          NOT NULL DEFAULT 0,
  innovation_score    INT          NOT NULL DEFAULT 0,
  suggestion          TEXT         ,
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_session_id (session_id),
  KEY idx_user_id (user_id),
  KEY idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 报告逐题点评表
CREATE TABLE IF NOT EXISTS report_comment (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  report_id     BIGINT       NOT NULL,
  question_text VARCHAR(500) NOT NULL,
  user_answer   TEXT         ,
  score         INT          NOT NULL DEFAULT 0,
  comment       TEXT         ,
  sort_order    INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_report_id (report_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
