# AI 模拟面试系统 — 接口设计

> **版本：** v2.0  
> **日期：** 2026-06-13  
> **基础路径：** `http://localhost:8080/api`  
> **认证方式：** JWT Bearer Token（`Authorization: Bearer <token>`）  
> **响应格式：** `{ code: 200, message: "success", data: {...} }`

---

## 1. 通用规范

### 1.1 响应结构

```json
{
  "code": 200,
  "message": "success",
  "data": { }
}
```

| code | 含义 |
|------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未认证 / Token 过期 |
| 403 | 无权限（非 admin 访问管理接口） |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

### 1.2 认证方式

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

- Token 有效期：24 小时
- 获取方式：登录接口返回
- 拦截器路径：
  - 公开（免认证）：`/api/auth/**`, `/api/questions/**`, `/api/tags/**`, `/api/public/**`, `/uploads/**`
  - 用户认证：其余所有 `/api/**` 路径
  - 管理员鉴权：`/api/admin/**`（额外校验 admin 角色）

### 1.3 公共查询参数

| 参数 | 类型 | 说明 |
|------|------|------|
| page | Integer | 页码（从 1 开始） |
| size | Integer | 每页条数（默认 10-20） |

### 1.4 分页响应结构

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [],
    "total": 100,
    "size": 10,
    "current": 1,
    "pages": 10
  }
}
```

---

## 2. 用户认证接口

### 2.1 注册

```
POST /api/auth/register
```

**Request Body：**

```json
{
  "email": "user@example.com",
  "phone": "13800138000",
  "username": "张三",
  "password": "abc123456"
}
```

| 参数 | 必填 | 类型 | 说明 |
|------|------|------|------|
| email | 否* | string | 邮箱（与 phone 二选一） |
| phone | 否* | string | 手机号（与 email 二选一） |
| username | 是 | string | 用户名 |
| password | 是 | string | 密码（BCrypt 加密存储） |

*email 和 phone 至少选一

**Response `data`：** `null`

---

### 2.2 登录

```
POST /api/auth/login
```

**Request Body：**

```json
{
  "account": "user@example.com",
  "password": "abc123456"
}
```

| 参数 | 必填 | 类型 | 说明 |
|------|------|------|------|
| account | 是 | string | 邮箱或手机号 |
| password | 是 | string | 密码 |

**Response `data`：**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "userInfo": {
    "id": 1,
    "username": "张三",
    "email": "user@example.com",
    "phone": "13800138000",
    "avatar": null,
    "targetPosition": "java_backend",
    "techStack": ["Java", "Spring"],
    "role": "user"
  }
}
```

---

## 3. 用户管理接口

### 3.1 获取当前用户信息

```
GET /api/user/info
```

**Response `data`：** `UserInfoVO`（含 id, username, email, phone, avatar, targetPosition, techStack, role）

### 3.2 更新用户信息

```
PUT /api/user/info
```

**Request Body：**

```json
{
  "username": "张三",
  "phone": "13800138000",
  "targetPosition": "java_backend",
  "techStack": ["Java", "Spring", "MySQL"]
}
```

| 参数 | 必填 | 类型 | 说明 |
|------|------|------|------|
| username | 否 | string | 用户名 |
| phone | 否 | string | 手机号 |
| targetPosition | 否 | string | 目标岗位枚举值 |
| techStack | 否 | array | 技术栈标签数组 |

### 3.3 修改密码

```
PUT /api/user/password
```

**Request Body：**

```json
{
  "oldPassword": "abc123456",
  "newPassword": "def789012"
}
```

### 3.4 上传头像

```
POST /api/user/avatar
```

| 参数 | 类型 | 说明 |
|------|------|------|
| file | MultipartFile | 头像图片（≤ 2MB） |

---

## 4. 题库接口

### 4.1 获取题目列表（公开）

```
GET /api/questions
```

**Query Parameters：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | Integer | 否 | 1 | 页码 |
| size | Integer | 否 | 10 | 每页条数 |
| category | String | 否 | - | 分类筛选 |
| difficulty | String | 否 | - | 难度筛选(easy/medium/hard) |
| direction | String | 否 | - | 方向筛选 |
| keyword | String | 否 | - | 关键词搜索（匹配标题和内容） |

**Response `data`：** 分页 `QuestionVO[]`

```json
{
  "records": [
    {
      "id": 1,
      "title": "HashMap 底层实现原理",
      "content": "请详细说明 HashMap 在 JDK 1.8 中的底层实现...",
      "category": "java",
      "difficulty": "hard",
      "direction": "java_backend",
      "viewCount": 128,
      "favoriteCount": 5,
      "tags": ["Java", "集合"]
    }
  ],
  "total": 50,
  "size": 10,
  "current": 1,
  "pages": 5
}
```

### 4.2 获取题目详情（公开）

```
GET /api/questions/{id}
```

**Response `data`：** `QuestionDetailVO`（含 id, title, content, answer, category, difficulty, direction, tags, viewCount, favoriteCount）

> 访问该接口会自动增加题目的浏览量。

### 4.3 获取题目统计（公开）

```
GET /api/questions/count
```

**Query Parameters：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| direction | String | 否 | 方向筛选 |
| difficulty | String | 否 | 难度筛选 |

**Response `data`：**

```json
{
  "java_backend": {
    "easy": 5,
    "medium": 8,
    "hard": 3
  },
  "frontend": {
    "easy": 3,
    "medium": 5,
    "hard": 2
  }
}
```

### 4.4 获取所有标签（公开）

```
GET /api/tags
```

**Response `data`：**

```json
[
  { "id": 1, "name": "Java" },
  { "id": 2, "name": "Spring" }
]
```

---

## 5. 收藏接口

### 5.1 添加收藏

```
POST /api/favorites/{questionId}
```

### 5.2 取消收藏

```
DELETE /api/favorites/{questionId}
```

### 5.3 获取收藏列表

```
GET /api/favorites
```

**Query Parameters：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | Integer | 否 | 页码 |
| size | Integer | 否 | 每页条数 |
| category | String | 否 | 分类筛选 |
| difficulty | String | 否 | 难度筛选 |

### 5.4 检查是否已收藏

```
GET /api/favorites/check/{questionId}
```

**Response `data`：**

```json
{ "favorited": true }
```

---

## 6. 反馈接口

### 6.1 提交反馈

```
POST /api/feedback
```

**Request Body：**

```json
{
  "type": "功能建议",
  "content": "希望能增加更多岗位的面试选项",
  "contact": "user@example.com"
}
```

---

## 7. 模拟面试接口（核心）

### 7.1 创建面试

```
POST /api/interview/create
```

**Request Body：**

```json
{
  "position": "java_backend",
  "round": "technical",
  "difficulty": "medium",
  "questionCount": 5
}
```

| 参数 | 必填 | 类型 | 说明 |
|------|------|------|------|
| position | 是 | string | 岗位（java_backend/frontend/fullstack/algorithm） |
| round | 是 | string | 轮次（technical/hr/comprehensive） |
| difficulty | 是 | string | 难度（easy/medium/hard） |
| questionCount | 是 | int | 题目数量（3-10） |

**Response `data`：** `InterviewCreateVO`

```json
{
  "sessionId": 1,
  "position": "java_backend",
  "round": "technical",
  "difficulty": "medium",
  "questionCount": 5,
  "firstMessage": {
    "role": "ai",
    "content": "你好！欢迎参加 Java 后端岗位的技术面试...",
    "messageType": "greeting"
  }
}
```

> 创建面试时涉及分布式锁（Redis），防止同用户并发创建。题目按难度比例从题库中平衡选取，30 天内已面试题目自动排除。

### 7.2 发送消息（非流式）

```
POST /api/interview/{id}/message
```

**Request Body：**

```json
{
  "content": "HashMap 在 JDK 1.8 中使用数组+链表+红黑树实现..."
}
```

**Response `data`：** `SendMessageVO`

```json
{
  "role": "ai",
  "content": "你的回答涵盖了 HashMap 的核心结构...",
  "messageType": "evaluation",
  "questionIndex": 1,
  "decision": "next_question",
  "currentQuestion": 2
}
```

> `decision` 字段：`follow_up`（追问）/ `next_question`（下一题）/ `end`（结束面试）

### 7.3 发送消息（SSE 流式）

```
POST /api/interview/{id}/stream
```

**Request Body：**

```json
{
  "content": "HashMap 在 JDK 1.8 中使用数组+链表+红黑树实现..."
}
```

**Response：** SSE (text/event-stream)，数据格式：

```
data: {"type":"token","content":"你的回答覆盖了..."}
data: {"type":"token","content":"核心结构..."}
data: {"type":"meta","decision":"next_question","questionIndex":1}
data: [DONE]
```

| type | 说明 |
|------|------|
| token | AI 回复的流式文本片段 |
| meta | 元数据（决策、当前题号等） |

> 前端断连重试机制：SSE 连接失败 → 轮询 GET `/api/interview/{id}/poll` → 回退 POST `/api/interview/{id}/message`

### 7.4 SSE 轮询降级

```
GET /api/interview/{id}/poll
```

**Response：** SSE stream，用于前端 SSE 断开后的降级获取

### 7.5 结束面试

```
POST /api/interview/{id}/end
```

**Response `data`：** `EndInterviewVO`

```json
{
  "reportId": 1,
  "totalScore": 85,
  "level": "good",
  "dimensions": {
    "technical": 82,
    "expression": 88,
    "logic": 85,
    "completeness": 80,
    "innovation": 78
  },
  "suggestion": "整体表现良好，建议加强技术深度方面的学习..."
}
```

> 结束面试后将自动：
> 1. 生成评分报告（ScoringEngine）
> 2. 导入错题（评分 ≤ 3 分的题目）
> 3. 生成 AI 诊断报告
> 4. 刷新看板/竞技分析缓存

### 7.6 放弃面试

```
DELETE /api/interview/{id}
```

> 仅当面试未产生任何用户回答时可放弃（直接删除 session）。

### 7.7 获取面试历史

```
GET /api/interview/history
```

**Query Parameters：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | Integer | 否 | 页码 |
| size | Integer | 否 | 每页条数 |

**Response `data`：** 分页 `InterviewHistoryVO[]`

```json
{
  "records": [
    {
      "id": 1,
      "position": "java_backend",
      "round": "technical",
      "difficulty": "medium",
      "questionCount": 5,
      "status": "completed",
      "createdAt": "2026-06-13T10:30:00",
      "hasReport": true
    }
  ]
}
```

### 7.8 获取面试消息

```
GET /api/interview/{id}/messages
```

**Response `data`：** `SessionInfoVO`（含消息列表、当前进度）

### 7.9 获取面试信息

```
GET /api/interview/{id}/info
```

**Response `data`：** `SessionInfoVO`

---

## 8. 面试报告接口

### 8.1 获取报告详情

```
GET /api/report/{id}
```

**Response `data`：** `ReportResultVO`

```json
{
  "id": 1,
  "sessionId": 1,
  "totalScore": 85,
  "level": "good",
  "dimensions": {
    "technical": 82,
    "expression": 88,
    "logic": 85,
    "completeness": 80,
    "innovation": 78
  },
  "suggestion": "整体表现良好...",
  "comments": [
    {
      "questionText": "HashMap 底层实现原理",
      "userAnswer": "数组+链表+红黑树...",
      "score": 8,
      "comment": "回答结构清晰...",
      "sortOrder": 1,
      "durationSeconds": 45
    }
  ],
  "interviewInfo": {
    "position": "java_backend",
    "round": "technical",
    "difficulty": "medium"
  },
  "createdAt": "2026-06-13T10:30:00"
}
```

### 8.2 获取报告列表

```
GET /api/report/list
```

**Query Parameters：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | Integer | 否 | 页码 |
| size | Integer | 否 | 每页条数 |
| position | String | 否 | 岗位筛选 |
| round | String | 否 | 轮次筛选 |
| difficulty | String | 否 | 难度筛选 |
| minScore | Integer | 否 | 最低分筛选 |
| maxScore | Integer | 否 | 最高分筛选 |
| startDate | String | 否 | 开始日期(yyyy-MM-dd) |
| endDate | String | 否 | 结束日期(yyyy-MM-dd) |

### 8.3 获取成长数据

```
GET /api/report/growth
```

**Response `data`：** `GrowthDataVO`

```json
{
  "trend": [
    { "date": "2026-06-01", "score": 72 },
    { "date": "2026-06-08", "score": 78 },
    { "date": "2026-06-13", "score": 85 }
  ],
  "stats": {
    "totalInterviews": 3,
    "averageScore": 78.3,
    "highestScore": 85,
    "improvement": 13
  }
}
```

### 8.4 提交答题耗时

```
POST /api/report/{id}/durations
```

**Request Body：**

```json
{
  "durations": [
    { "questionIndex": 1, "durationSeconds": 45 },
    { "questionIndex": 2, "durationSeconds": 62 }
  ]
}
```

### 8.5 删除报告

```
DELETE /api/report/{id}
```

---

## 9. 数据看板接口

### 9.1 获取看板概览（完整）

```
GET /api/dashboard/overview
```

**Response `data`：** `DashboardVO`

```json
{
  "stats": {
    "totalInterviews": 3,
    "averageScore": 78.3,
    "highestScore": 85,
    "totalQuestions": 15
  },
  "greeting": "下午好！今天要继续练习吗？",
  "scoreTrend": [
    { "date": "2026-06-01", "score": 72 },
    { "date": "2026-06-08", "score": 78 },
    { "date": "2026-06-13", "score": 85 }
  ],
  "knowledgeOverview": {
    "technical": 82,
    "expression": 88,
    "logic": 85,
    "completeness": 80,
    "innovation": 78
  },
  "recommendations": [
    { "id": 5, "title": "JVM 垃圾回收机制", "reason": "创新性维度偏弱，建议练习" }
  ],
  "recentInterviews": [
    {
      "sessionId": 1,
      "position": "java_backend",
      "round": "technical",
      "score": 85,
      "date": "2026-06-13"
    }
  ]
}
```

### 9.2 获取统计数据

```
GET /api/dashboard/stats
```

### 9.3 获取分数趋势

```
GET /api/dashboard/score-trend
```

### 9.4 获取知识概览

```
GET /api/dashboard/knowledge-overview
```

---

## 10. 错题本接口

### 10.1 获取错题列表

```
GET /api/mistakes
```

**Query Parameters：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | Integer | 否 | 页码 |
| size | Integer | 否 | 每页条数 |
| status | Integer | 否 | 状态(0=待复习, 1=已掌握) |
| category | String | 否 | 分类筛选 |

### 10.2 获取错题统计

```
GET /api/mistakes/stats
```

**Response `data`：**

```json
{
  "total": 8,
  "pending": 5,
  "mastered": 3,
  "byCategory": [
    { "category": "java", "count": 4 },
    { "category": "spring", "count": 2 },
    { "category": "database", "count": 2 }
  ]
}
```

### 10.3 获取错题详情

```
GET /api/mistakes/{id}/details
```

### 10.4 标记已掌握

```
PUT /api/mistakes/{id}/master
```

### 10.5 重置待复习

```
PUT /api/mistakes/{id}/reset
```

### 10.6 删除错题

```
DELETE /api/mistakes/{id}
```

### 10.7 获取复习题目

```
GET /api/mistakes/review/questions
```

### 10.8 提交复习检查

```
POST /api/mistakes/review/check
```

**Request Body：**

```json
{
  "questionId": 1,
  "userAnswer": "HashMap 使用数组+链表+红黑树..."
}
```

**Response `data`：**

```json
{
  "correct": true,
  "matchedKeywords": ["数组", "链表", "红黑树"],
  "suggestion": "回答正确，建议进一步了解红黑树的平衡条件"
}
```

---

## 11. AI 诊断报告接口

### 11.1 生成诊断报告

```
POST /api/diagnosis/generate/{sessionId}
```

> 调用 DeepSeek API 生成深度诊断分析。

### 11.2 获取诊断报告（按 ID）

```
GET /api/diagnosis/{id}
```

### 11.3 获取诊断报告（按会话）

```
GET /api/diagnosis/session/{sessionId}
```

### 11.4 获取诊断历史

```
GET /api/diagnosis/history
```

### 11.5 获取最新诊断

```
GET /api/diagnosis/latest
```

---

## 12. 学习路径接口

### 12.1 获取学习路径

```
GET /api/learning-path
```

**Response `data`：** `LearningPathVO`

```json
{
  "phases": [
    {
      "phaseIndex": 1,
      "phaseName": "基础知识巩固",
      "progress": "60%",
      "tasks": [
        {
          "taskIndex": 1,
          "taskText": "复习 Java 集合框架核心类源码",
          "focusArea": "java",
          "completed": true
        },
        {
          "taskIndex": 2,
          "taskText": "理解 HashMap 扩容机制",
          "focusArea": "java",
          "completed": false
        }
      ]
    }
  ],
  "overallProgress": "35%"
}
```

### 12.2 刷新学习路径

```
POST /api/learning-path/refresh
```

> 基于最新诊断报告重新生成学习路径。

### 12.3 标记任务完成

```
PUT /api/learning-path/tasks/complete
```

**Request Body：**

```json
{
  "phaseIndex": 1,
  "taskIndex": 2
}
```

### 12.4 取消任务完成

```
PUT /api/learning-path/tasks/uncomplete
```

**Request Body：**

```json
{
  "phaseIndex": 1,
  "taskIndex": 2
}
```

### 12.5 获取学习进度统计

```
GET /api/learning-path/stats
```

---

## 13. 竞技分析接口

### 13.1 获取竞技分析

```
GET /api/competitive-analysis
```

**Response `data`：** `CompetitiveAnalysisVO`

```json
{
  "currentScore": 85.0,
  "targetScore": 92,
  "dimensions": {
    "technical": 82,
    "expression": 88,
    "logic": 85,
    "completeness": 80,
    "innovation": 78
  },
  "peerPercentiles": {
    "technical": 65,
    "expression": 80,
    "logic": 72,
    "completeness": 55,
    "innovation": 60
  },
  "trend": {
    "direction": "up",
    "slope": 2.3
  },
  "prediction": {
    "expectedScore": 90,
    "interviewsNeeded": 3,
    "confidence": "high"
  }
}
```

### 13.2 刷新竞技分析

```
POST /api/competitive-analysis/refresh
```

### 13.3 获取指定会话的竞技分析

```
GET /api/competitive-analysis/session/{sessionId}
```

---

## 14. 语音接口

### 14.1 语音转文字 (ASR)

```
POST /api/speech/asr
```

| 参数 | 类型 | 说明 |
|------|------|------|
| audio | MultipartFile | WAV 音频文件（≤ 5MB） |

**Response `data`：**

```json
{
  "text": "HashMap 在 JDK 1.8 中使用数组加链表加红黑树实现"
}
```

### 14.2 文字转语音 (TTS)

```
POST /api/speech/tts
```

**Request Body：**

```json
{
  "text": "请介绍一下 HashMap 的底层实现原理"
}
```

**Response：** MP3 音频二进制流（Content-Type: audio/mpeg）

---

## 15. 管理后台接口

### 15.1 系统统计

```
GET /api/admin/statistics
```

**Response `data`：**

```json
{
  "totalUsers": 100,
  "totalQuestions": 50,
  "totalInterviews": 200,
  "todayInterviews": 5,
  "userGrowth": [
    { "date": "2026-06-07", "count": 80 },
    { "date": "2026-06-13", "count": 100 }
  ]
}
```

### 15.2 用户管理

```
GET  /api/admin/users              # 用户列表（分页，支持筛选）
GET  /api/admin/users/{id}         # 用户详情
PUT  /api/admin/users/{id}         # 编辑用户
PUT  /api/admin/users/{id}/status  # 启用/禁用用户
PUT  /api/admin/users/{id}/password # 重置密码
DELETE /api/admin/users/{id}       # 删除用户
```

### 15.3 题目管理

```
GET    /api/admin/questions            # 题目列表（含已删除）
GET    /api/admin/questions/{id}       # 题目详情
POST   /api/admin/questions            # 创建题目
PUT    /api/admin/questions/{id}       # 修改题目
DELETE /api/admin/questions/{id}       # 逻辑删除题目
POST   /api/admin/questions/batch      # 批量导入题目
```

**批量导入 Request Body：**

```json
[
  {
    "title": "HashMap 底层实现原理",
    "content": "请详细说明...",
    "answer": "HashMap 在 JDK 1.8 中...",
    "category": "java",
    "difficulty": "hard",
    "direction": "java_backend",
    "tags": ["Java", "集合"]
  }
]
```

### 15.4 操作日志

```
GET /api/admin/logs
```

**Query Parameters：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | Integer | 否 | 页码 |
| size | Integer | 否 | 每页条数 |
| adminId | Integer | 否 | 管理员 ID 筛选 |
| action | String | 否 | 操作类型筛选 |
| targetType | String | 否 | 对象类型筛选 |
| startDate | String | 否 | 开始日期 |
| endDate | String | 否 | 结束日期 |

### 15.5 反馈管理

```
GET    /api/admin/feedback            # 反馈列表
PUT    /api/admin/feedback/{id}/status # 更新反馈状态（已处理/未处理）
DELETE /api/admin/feedback/{id}       # 删除反馈
```

---

## 16. 接口权限总览

| # | 接口 | 方法 | 免认证 | 用户 | 管理员 |
|---|------|------|--------|------|--------|
| 1 | /api/auth/register | POST | ✅ | - | - |
| 2 | /api/auth/login | POST | ✅ | - | - |
| 3 | /api/questions | GET | ✅ | ✅ | ✅ |
| 4 | /api/questions/{id} | GET | ✅ | ✅ | ✅ |
| 5 | /api/questions/count | GET | ✅ | ✅ | ✅ |
| 6 | /api/tags | GET | ✅ | ✅ | ✅ |
| 7 | /api/user/info | GET/PUT | ❌ | ✅ | ❌ |
| 8 | /api/user/password | PUT | ❌ | ✅ | ❌ |
| 9 | /api/user/avatar | POST | ❌ | ✅ | ❌ |
| 10 | /api/favorites/* | POST/DELETE/GET | ❌ | ✅ | ❌ |
| 11 | /api/feedback | POST | ❌ | ✅ | ❌ |
| 12 | /api/interview/* | ALL | ❌ | ✅ | ❌ |
| 13 | /api/report/* | GET/POST/DELETE | ❌ | ✅ | ❌ |
| 14 | /api/dashboard/* | GET | ❌ | ✅ | ❌ |
| 15 | /api/mistakes/* | GET/PUT/POST/DELETE | ❌ | ✅ | ❌ |
| 16 | /api/diagnosis/* | GET/POST | ❌ | ✅ | ❌ |
| 17 | /api/learning-path/* | GET/POST/PUT | ❌ | ✅ | ❌ |
| 18 | /api/competitive-analysis/* | GET/POST | ❌ | ✅ | ❌ |
| 19 | /api/speech/* | POST | ❌ | ✅ | ❌ |
| 20 | /api/admin/* | ALL | ❌ | ❌ | ✅ |

---

## 17. 接口路径汇总

| 模块 | 方法 | 路径 | 说明 |
|------|------|------|------|
| **Auth** | POST | /api/auth/register | 注册 |
| | POST | /api/auth/login | 登录 |
| **User** | GET | /api/user/info | 获取用户信息 |
| | PUT | /api/user/info | 更新用户信息 |
| | PUT | /api/user/password | 修改密码 |
| | POST | /api/user/avatar | 上传头像 |
| **Questions** | GET | /api/questions | 题目列表 |
| | GET | /api/questions/{id} | 题目详情 |
| | GET | /api/questions/count | 题目统计 |
| **Tags** | GET | /api/tags | 标签列表 |
| **Favorites** | POST | /api/favorites/{questionId} | 添加收藏 |
| | DELETE | /api/favorites/{questionId} | 取消收藏 |
| | GET | /api/favorites | 收藏列表 |
| | GET | /api/favorites/check/{questionId} | 检查收藏 |
| **Feedback** | POST | /api/feedback | 提交反馈 |
| **Interview** | POST | /api/interview/create | 创建面试 |
| | POST | /api/interview/{id}/message | 发送消息（非流式） |
| | POST | /api/interview/{id}/stream | 发送消息（SSE流式） |
| | GET | /api/interview/{id}/poll | SSE轮询降级 |
| | POST | /api/interview/{id}/end | 结束面试 |
| | DELETE | /api/interview/{id} | 放弃面试 |
| | GET | /api/interview/history | 面试历史 |
| | GET | /api/interview/{id}/messages | 面试消息 |
| | GET | /api/interview/{id}/info | 面试信息 |
| **Report** | GET | /api/report/{id} | 报告详情 |
| | GET | /api/report/list | 报告列表 |
| | GET | /api/report/growth | 成长数据 |
| | POST | /api/report/{id}/durations | 提交答题耗时 |
| | DELETE | /api/report/{id} | 删除报告 |
| **Dashboard** | GET | /api/dashboard/overview | 看板概览 |
| | GET | /api/dashboard/stats | 统计数据 |
| | GET | /api/dashboard/score-trend | 分数趋势 |
| | GET | /api/dashboard/knowledge-overview | 知识概览 |
| **Mistakes** | GET | /api/mistakes | 错题列表 |
| | GET | /api/mistakes/stats | 错题统计 |
| | GET | /api/mistakes/{id}/details | 错题详情 |
| | PUT | /api/mistakes/{id}/master | 标记已掌握 |
| | PUT | /api/mistakes/{id}/reset | 重置待复习 |
| | DELETE | /api/mistakes/{id} | 删除错题 |
| | GET | /api/mistakes/review/questions | 复习题目 |
| | POST | /api/mistakes/review/check | 提交检查 |
| **Diagnosis** | POST | /api/diagnosis/generate/{sessionId} | 生成诊断 |
| | GET | /api/diagnosis/{id} | 诊断详情 |
| | GET | /api/diagnosis/session/{sessionId} | 按会话查诊断 |
| | GET | /api/diagnosis/history | 诊断历史 |
| | GET | /api/diagnosis/latest | 最新诊断 |
| **Learning Path** | GET | /api/learning-path | 学习路径 |
| | POST | /api/learning-path/refresh | 刷新路径 |
| | PUT | /api/learning-path/tasks/complete | 完成任务 |
| | PUT | /api/learning-path/tasks/uncomplete | 取消完成 |
| | GET | /api/learning-path/stats | 学习进度统计 |
| **Competitive** | GET | /api/competitive-analysis | 竞技分析 |
| | POST | /api/competitive-analysis/refresh | 刷新分析 |
| | GET | /api/competitive-analysis/session/{sessionId} | 会话分析 |
| **Speech** | POST | /api/speech/asr | 语音转文字 |
| | POST | /api/speech/tts | 文字转语音 |
| **Admin** | GET | /api/admin/statistics | 系统统计 |
| | GET | /api/admin/users | 用户列表 |
| | GET | /api/admin/users/{id} | 用户详情 |
| | PUT | /api/admin/users/{id} | 编辑用户 |
| | PUT | /api/admin/users/{id}/status | 启用/禁用用户 |
| | PUT | /api/admin/users/{id}/password | 重置密码 |
| | DELETE | /api/admin/users/{id} | 删除用户 |
| | GET | /api/admin/questions | 管理题目列表 |
| | GET | /api/admin/questions/{id} | 管理题目详情 |
| | POST | /api/admin/questions | 创建题目 |
| | PUT | /api/admin/questions/{id} | 修改题目 |
| | DELETE | /api/admin/questions/{id} | 删除题目 |
| | POST | /api/admin/questions/batch | 批量导入 |
| | GET | /api/admin/logs | 操作日志 |
| | GET | /api/admin/feedback | 反馈列表 |
| | PUT | /api/admin/feedback/{id}/status | 更新反馈状态 |
| | DELETE | /api/admin/feedback/{id} | 删除反馈 |
