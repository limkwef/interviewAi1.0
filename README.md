# AI 模拟面试系统

基于 **Vue 3 + Spring Boot 3 + DeepSeek AI** 的智能模拟面试系统，支持多岗位、多轮次、多难度的 AI 模拟面试，提供实时评分、诊断报告、错题本、学习路径等功能。

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
| MyBatis 3.0 | ORM |
| MySQL | 关系数据库 |
| Redis | 缓存 + 上下文存储 |
| JWT (jjwt 0.12) | 身份认证 |
| Spring Security Crypto | BCrypt 密码加密 |
| DeepSeek API | AI 对话模型 |
| 百度语音 API | ASR + TTS |
| SSE | 流式响应 |

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

-- 执行初始化脚本
source backend/init.sql
source backend/seed.sql
```

### 2. 配置后端
复制并编辑配置文件：
```bash
cp backend/src/main/resources/application.yml backend/src/main/resources/application-local.yml
```

> **注意**：`application.yml` 中的 API Key 和密码需要替换为你自己的凭据。建议使用环境变量：
> - `AI_API_KEY` — DeepSeek API Key
> - `BAIDU_API_KEY` — 百度语音 API Key
> - `BAIDU_SECRET_KEY` — 百度语音 Secret Key

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
ai-interview-system/
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
│   ├── index.html
│   └── vite.config.js
│
├── backend/                      # Spring Boot 后端
│   └── src/main/java/org/backend/
│       ├── common/               # 通用响应封装
│       ├── config/               # 配置类
│       ├── controller/           # 控制器
│       ├── dto/                  # 数据传输对象
│       ├── entity/               # 实体 + VO
│       ├── exception/            # 异常处理
│       ├── interceptor/          # 拦截器
│       ├── mapper/               # MyBatis 映射
│       ├── service/              # 业务逻辑
│       └── util/                 # 工具类
│
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
- 连续 5 次 AI 调用失败后自动熔断 30 秒
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

---

## 贡献

本项目为毕业设计 / 课程实践项目，欢迎提出 Issue 或 Pull Request。

---

## 许可证

MIT License
