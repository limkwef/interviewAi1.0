# AI 模拟面试系统

基于 **Vue 3 + Spring Boot 3 + DeepSeek AI** 的智能模拟面试系统，支持多岗位、多轮次、多难度的 AI 模拟面试，提供实时评分、诊断报告、错题本、学习路径、简历解析等功能。

---

## 功能特性

### 🎯 模拟面试
- 多岗位方向：Java后端、前端开发、全栈开发、算法工程师
- 多面试轮次：技术面、HR面、综合面
- 多难度等级：简单、中等、困难
- AI 自适应追问：根据回答质量决定追问或跳题
- SSE 流式回复：实时展示 AI 生成内容，带网络降级兜底

### 📊 智能评分
- 五维度能力评估：技术能力、表达能力、逻辑思维、完整性、创新性
- 难度加权总分公式
- AI 评分 + 系统验算双重保障
- 三级降级机制：结构化输出 → 正则提取 → 模板评分

### 📋 面试报告
- AI 生成详细面试报告
- AI 深度诊断报告（知识分析、思维模式、错误模式、学习计划）
- 分数趋势图表（ECharts 雷达图 + 折线图）
- 历史报告查询与对比

### 📚 题库中心
- 分类浏览与搜索
- 题目收藏
- 错题本自动收录（评分 < 60 自动入库）
- 错题重做与统计分析

### 👤 用户系统
- 注册/登录（JWT 认证）
- 个人中心（头像上传、信息编辑、密码修改）
- 学习路径规划
- 面试历史

### 🔧 管理后台
- 系统仪表盘
- 用户管理（CRUD + 状态控制）
- 题库管理（CRUD + 批量导入）
- 操作日志审计
- 反馈管理

### 📄 简历模块
- 简历上传与解析（PDF/Word → AI 结构化提取）
- 简历数据管理（教育经历、工作经验、项目经历、技能标签）
- 简历面试模式（AI 根据简历内容针对性提问）
- 简历版本管理（多份简历切换、激活）

### 🧠 AI Agent 引擎
- 自主 Agent 驱动面试流程：感知 → 推理 → 行动循环
- 短期记忆（会话上下文）+ 长期记忆（用户画像、历史表现）
- 工具注册机制：知识检索、错题记录、难度调节、诊断触发
- 自适应面试策略：根据用户表现动态调整题目难度和方向

### 🤖 多模型支持
- 可配置多 AI 模型（DeepSeek、GPT、Qwen 等）
- 模型管理后台：在线切换、参数配置、启用/禁用
- 每个面试会话可指定使用模型
- 支持流式输出和结构化 JSON 输出

### 🎤 语音能力
- 语音输入（百度 ASR）
- AI 回复朗读（百度 TTS）

---

## 技术栈

### 前端
| 技术 | 说明 |
|------|------|
| Vue 3 | Composition API + `<script setup>` |
| Vite 5 | 构建工具 |
| Pinia | 状态管理 |
| Vue Router 4 | 路由 |
| Element Plus | UI 组件库 |
| ECharts + vue-echarts | 数据可视化 |
| Axios | HTTP 请求 |
| marked | Markdown 渲染 |
| html2pdf.js | 报告导出 |

### 后端
| 技术 | 说明 |
|------|------|
| Spring Boot 3.5 | Web 框架 |
| Spring Security | 安全框架（JWT 认证 + 权限控制） |
| MyBatis 3.0 | ORM |
| MySQL | 关系数据库 |
| Redis | 缓存 + 上下文存储 + 分布式锁 |
| JWT (jjwt 0.12) | 身份认证 |
| Spring Security Crypto | BCrypt 密码加密 |
| DeepSeek API（默认） | AI 对话模型，支持多模型可配置（GPT、Qwen 等），含管理后台 |
| 百度语音 API | ASR + TTS |
| SSE | 流式响应 |
| Apache PDFBox | PDF 文本提取 |

---

## 快速启动

### 前置条件
- JDK 21+
- Node.js 18+
- MySQL 8.0+
- Redis 7+

### 1. 数据库初始化
```sql
-- 创建数据库
CREATE DATABASE interview DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 执行建表脚本
USE interview;
source backend/init.sql

-- 导入 AI 模型配置（可选）
source backend/src/main/resources/db/ai_model_init.sql
```

### 2. 配置后端
复制并编辑配置文件：
```bash
cp backend/src/main/resources/application.yml.example backend/src/main/resources/application-local.yml
```

编辑 `application-local.yml`，填入你的实际配置：

```yaml
spring:
  datasource:
    password: your_db_password        # MySQL 密码
  data:
    redis:
      password: your_redis_password   # Redis 密码

jwt:
  secret: your_jwt_secret_key_at_least_32_bytes_long  # JWT 密钥（≥32字节）

ai:
  api-key: sk-xxx                     # DeepSeek API Key

baidu:
  speech:
    api-key: xxx                      # 百度语音 API Key
    secret-key: xxx                   # 百度语音 Secret Key
```

> **注意**：`application.yml` 中的敏感信息已改为环境变量占位符（`${ENV_VAR}`），未配置时启动失败（fail-fast）。本地开发请复制 `application.yml.example` 为 `application-local.yml` 并填入真实值（`application-local.yml` 已在 `.gitignore` 中排除，不会提交）。生产环境通过环境变量注入。

### 3. 启动后端
```bash
cd backend
./mvnw spring-boot:run
```

后端默认运行在 `http://localhost:8080`

### 4. 启动前端
```bash
cd frontend
npm install
npm run dev
```

前端默认运行在 `http://localhost:3000`

---

## 项目结构

```
interview-ai/
├── frontend/                     # Vue 3 前端
│   ├── src/
│   │   ├── api/                  # API 接口模块
│   │   ├── components/           # 通用组件
│   │   ├── layout/               # 布局组件
│   │   ├── router/               # 路由配置
│   │   ├── stores/               # Pinia 状态管理
│   │   ├── styles/               # 全局样式
│   │   ├── utils/                # 工具函数
│   │   └── views/                # 页面视图
│   │       ├── admin/            # 管理后台（用户/题库/模型）
│   │       │   └── ai-model/     # AI 模型配置管理
│   │       ├── dashboard/        # 仪表盘
│   │       ├── interview/        # 面试（配置 + 会话）
│   │       ├── learning-path/    # 学习路径
│   │       ├── login/            # 登录
│   │       ├── questions/        # 题库（列表/详情/收藏/错题）
│   │       ├── register/         # 注册
│   │       ├── report/           # 报告（详情/诊断）
│   │       ├── resume-manage/    # 简历管理
│   │       └── settings/         # 系统设置
│   ├── index.html
│   └── vite.config.js
│
├── backend/                      # Spring Boot 后端
│   └── src/main/java/org/backend/
│       ├── agent/                # AI Agent 引擎（感知→推理→行动）
│       │   ├── memory/           # 短期/长期记忆
│       │   └── tool/             # 工具注册（知识检索、错题记录等）
│       ├── config/               # 配置类（Security、JWT、异步、跨域）
│       ├── controller/           # 控制器
│       ├── dto/                  # 数据传输对象
│       ├── entity/               # 实体
│       ├── exception/            # 全局异常处理
│       ├── interceptor/          # 拦截器（限流、用户认证）
│       ├── mapper/               # MyBatis 映射接口 + XML
│       ├── service/              # 业务逻辑
│       │   ├── InterviewService          # 面试核心流程
│       │   ├── InterviewEvaluateService  # AI 评估 + 报告生成
│       │   ├── InterviewContextService   # Redis 对话上下文
│       │   ├── AIDiagnosisService        # AI 深度诊断
│       │   ├── LearningPathService       # 学习路径规划
│       │   ├── QuestionSelector          # 智能抽题策略
│       │   ├── ResumeService             # 简历解析与管理
│       │   ├── AiModelService            # AI 模型管理
│       │   └── ...
│       ├── vo/                   # 视图对象（原 entity/vo）
│       └── util/                 # 工具类
│           ├── AIService         # AI API 调用 + 熔断器
│           ├── PromptBuilder     # Prompt 模板管理
│           ├── ScoringEngine     # 评分引擎 + 降级策略
│           └── JwtUtil           # JWT 工具
│
│   └── src/main/resources/
│       ├── db/                   # 数据库迁移脚本
│       ├── sql/                  # SQL 脚本
│       ├── prompts/              # AI Prompt 模板
│       └── mapper/               # MyBatis XML 映射
│
├── backend/init.sql              # 数据库初始化脚本
└── README.md
```

---

## 架构亮点

### 🔄 三级降级机制
系统在 AI 服务不可靠时具备完整的降级能力：
1. **结构化输出**（response_format: json_object）→ AI 直接返回合法 JSON
2. **普通 chat + 正则提取** → 兼容旧模型
3. **模板化兜底** → 确定性算法，不依赖 AI

### 🛡️ 熔断器模式
- 连续 3 次 AI 调用失败后自动熔断 60 秒
- 半开状态允许试探调用
- 防止雪崩效应

### 🧠 智能抽题
- 按难度比例分层抽取（easy/medium/hard）
- 排除用户近期做过的题（Redis 记录，30 天有效）
- 某层不够时从相邻层补足
- 整体不够时取消方向限制兜底

### 📐 系统验算评分
- AI 评分后系统按难度权重重新计算总分
- 维度分偏差过大（>15分）时自动按比例修正
- 确保评分一致性和可解释性

### 🔐 安全架构
- Spring Security + JWT 双重认证
- 角色权限控制（普通用户 / 管理员）
- 接口限流（Redis 滑动窗口）
- BCrypt 密码加密

### 📄 简历 AI 解析
- PDF/Word 文件上传 → 文本提取 → AI 结构化解析
- 解析结果：教育经历、工作经验、项目经历、技能标签
- 简历面试模式：AI 根据简历内容动态生成针对性问题

---

## 贡献

本项目为 课程实践项目，欢迎提出 Issue 或 Pull Request。

---

## 许可证

MIT License
