# Resume Module Implementation Plan

## Overview
Add resume upload/management and AI-based resume questioning to the AI Mock Interview System.

---

## 1. Database Schema Changes

### New Table: `resume`

```sql
CREATE TABLE `resume` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `title` VARCHAR(200) NOT NULL DEFAULT '我的简历',
  `file_name` VARCHAR(255) NOT NULL,
  `file_path` VARCHAR(500) NOT NULL,
  `file_size` INT NOT NULL DEFAULT 0,
  `file_type` VARCHAR(20) NOT NULL DEFAULT 'pdf',
  `parsed_content` LONGTEXT COMMENT 'AI提取的简历纯文本',
  `parsed_data` JSON COMMENT 'AI结构化提取：education,skills,experience,projects,certifications',
  `parse_status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=待解析 1=解析中 2=解析完成 3=解析失败',
  `parse_error` VARCHAR(500) DEFAULT NULL,
  `is_default` TINYINT NOT NULL DEFAULT 0 COMMENT '是否为默认简历',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  CONSTRAINT `fk_resume_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**`parsed_data` JSON 结构：**
```json
{
  "name": "张三",
  "email": "zhangsan@example.com",
  "phone": "13800138000",
  "education": [
    {
      "school": "XX大学",
      "degree": "本科",
      "major": "计算机科学与技术",
      "startDate": "2019-09",
      "endDate": "2023-06"
    }
  ],
  "skills": ["Java", "Spring Boot", "MySQL", "Redis", "Vue.js"],
  "experience": [
    {
      "company": "XX科技有限公司",
      "position": "后端开发工程师",
      "startDate": "2023-07",
      "endDate": "至今",
      "description": "负责..."
    }
  ],
  "projects": [
    {
      "name": "XX系统",
      "role": "核心开发",
      "techStack": ["Spring Boot", "MyBatis", "Redis"],
      "description": "..."
    }
  ],
  "certifications": [],
  "summary": "3年Java后端开发经验，熟悉微服务架构..."
}
```

### Alter Table: `interview_session`

```sql
ALTER TABLE `interview_session`
  ADD COLUMN `resume_id` BIGINT DEFAULT NULL COMMENT '关联的简历ID（简历面试模式）',
  ADD CONSTRAINT `fk_session_resume` FOREIGN KEY (`resume_id`) REFERENCES `resume`(`id`) ON DELETE SET NULL;
```

---

## 2. Backend Implementation

### 2.1 New Files

```
backend/src/main/java/org/backend/
├── entity/
│   └── Resume.java                    # 简历实体
├── mapper/
│   └── ResumeMapper.java              # MyBatis Mapper
├── service/
│   └── ResumeService.java             # 简历业务逻辑
├── controller/
│   └── ResumeController.java          # 简历API
└── resources/mapper/
    └── ResumeMapper.xml               # MyBatis XML
```

### 2.2 Entity: `Resume.java`

```java
@Data
public class Resume {
    private Long id;
    private Long userId;
    private String title;
    private String fileName;
    private String filePath;
    private Integer fileSize;
    private String fileType;
    private String parsedContent;
    private String parsedData;   // JSON string
    private Integer parseStatus;
    private String parseError;
    private Integer isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### 2.3 API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/resume/upload` | 上传简历（multipart） |
| GET | `/api/resume/list` | 获取用户简历列表 |
| GET | `/api/resume/{id}` | 获取简历详情（含解析数据） |
| DELETE | `/api/resume/{id}` | 删除简历 |
| PUT | `/api/resume/{id}/default` | 设为默认简历 |
| POST | `/api/resume/{id}/reparse` | 重新解析简历 |
| GET | `/api/resume/{id}/parsed` | 获取AI结构化解析结果 |

### 2.4 ResumeService.java 核心逻辑

```java
@Service
public class ResumeService {

    // 上传简历
    public Resume uploadResume(Long userId, MultipartFile file, String title) {
        // 1. 验证文件类型（pdf, doc, docx, txt）
        // 2. 验证文件大小（最大 5MB）
        // 3. 保存文件到 ./uploads/resumes/{userId}/{timestamp}_{filename}
        // 4. 读取文件文本内容（PDF用PDFBox，TXT直接读，doc/docx用Apache POI）
        // 5. 创建 Resume 记录，parse_status=0
        // 6. 异步调用 aiParseResume() 解析
        // 7. 返回 Resume 对象
    }

    // AI解析简历（异步）
    @Async
    public void aiParseResume(Long resumeId) {
        // 1. 更新 parse_status=1（解析中）
        // 2. 读取 resume 的文本内容
        // 3. 调用 DeepSeek AI 提取结构化信息
        //    Prompt: "请从以下简历文本中提取结构化信息..."
        // 4. 保存 parsed_content 和 parsed_data
        // 5. 更新 parse_status=2（完成）或 3（失败）
    }

    // 获取简历列表
    public List<Resume> getUserResumes(Long userId) { ... }

    // 删除简历
    public void deleteResume(Long userId, Long resumeId) { ... }

    // 设为默认简历
    public void setDefault(Long userId, Long resumeId) { ... }

    // 重新解析
    public void reparse(Long userId, Long resumeId) { ... }
}
```

### 2.5 ResumeController.java

```java
@RestController
@RequestMapping("/api/resume")
public class ResumeController extends BaseController {

    @PostMapping("/upload")
    public Result<Resume> upload(
        HttpServletRequest request,
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "title", defaultValue = "我的简历") String title) {
        Long userId = getUserIdFromToken(request);
        Resume resume = resumeService.uploadResume(userId, file, title);
        return Result.success("上传成功", resume);
    }

    @GetMapping("/list")
    public Result<List<Resume>> list(HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        return Result.success(resumeService.getUserResumes(userId));
    }

    @GetMapping("/{id}")
    public Result<Resume> detail(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserIdFromToken(request);
        return Result.success(resumeService.getResumeDetail(userId, id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserIdFromToken(request);
        resumeService.deleteResume(userId, id);
        return Result.success("删除成功", null);
    }

    @PutMapping("/{id}/default")
    public Result<Void> setDefault(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserIdFromToken(request);
        resumeService.setDefault(userId, id);
        return Result.success("设置成功", null);
    }

    @PostMapping("/{id}/reparse")
    public Result<Void> reparse(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserIdFromToken(request);
        resumeService.reparse(userId, id);
        return Result.success("重新解析已触发", null);
    }
}
```

### 2.6 文件解析依赖

```xml
<!-- backend/pom.xml 新增依赖 -->
<!-- PDF解析 -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.3</version>
</dependency>
<!-- Word文档解析 -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
```

---

## 3. Interview System Integration

### 3.1 修改 InterviewService.createInterview()

```java
// 新增参数 resumeId（可选）
public InterviewCreateVO createInterview(Long userId, String position, String round,
                                          String difficulty, int questionCount,
                                          Long resumeId) {
    // ... 原有逻辑 ...

    // 如果提供了 resumeId，加载简历数据
    String resumeContext = null;
    if (resumeId != null) {
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume != null && resume.getParseStatus() == 2) {
            resumeContext = resume.getParsedContent();
        }
    }

    // 生成问候语时传入简历上下文
    String greeting = aiService.generateGreeting(position, round, difficulty,
                                                  questionCount, resumeContext);

    // ... 保存 session（新增 resume_id 字段）...
}
```

### 3.2 修改 PromptBuilder

**新增方法：`buildResumeInterviewSystemPrompt()`**

```java
public String buildResumeInterviewSystemPrompt(String position, String round,
                                                String difficulty, int questionCount,
                                                String resumeContent) {
    String base = buildInterviewSystemPrompt(position, round, difficulty, questionCount, "");

    String resumeSection = """

        === 候选人简历信息 ===

        以下是候选人的简历内容，请基于简历中的项目经验、技术栈和工作经历，
        针对性地提出面试问题：

        """ + resumeContent + """

        简历面试规则：
        1. 优先围绕简历中的项目经验提问（技术选型、架构设计、难点攻克）
        2. 针对简历中提到的技术栈进行深度追问
        3. 询问项目中的具体贡献和个人成长
        4. 适当追问简历中可能存在的疑点或亮点
        5. 如果简历信息不足，可以结合通用技术问题
        6. 保持与普通面试相同的追问和评价规则
        """;

    return base + resumeSection;
}
```

**修改 `buildGreetingMessages()`**

```java
public List<Map<String, String>> buildGreetingMessages(String position, String round,
                                                        String difficulty, int questionCount,
                                                        String questionsText,
                                                        String resumeContent) {
    List<Map<String, String>> messages = new ArrayList<>();

    String systemPrompt;
    if (resumeContent != null && !resumeContent.isEmpty()) {
        systemPrompt = buildResumeInterviewSystemPrompt(position, round, difficulty,
                                                         questionCount, resumeContent);
    } else {
        systemPrompt = buildInterviewSystemPrompt(position, round, difficulty,
                                                   questionCount, questionsText);
    }

    messages.add(Map.of("role", "system", "content", systemPrompt));

    String userPrompt;
    if (resumeContent != null && !resumeContent.isEmpty()) {
        userPrompt = "面试开始，请作为面试官向候选人打招呼，简要介绍本次面试安排，" +
                     "然后基于候选人的简历内容提出第一个面试问题。";
    } else {
        userPrompt = "面试开始，请作为面试官向候选人打招呼，简要介绍本次面试安排，" +
                     "然后直接提出第一个面试问题。";
    }

    messages.add(Map.of("role", "user", "content", userPrompt));
    return messages;
}
```

### 3.3 修改 InterviewController.create()

```java
@PostMapping("/create")
public Result<InterviewCreateVO> create(HttpServletRequest request,
                                         @RequestBody Map<String, Object> body) {
    Long userId = getUserIdFromToken(request);
    String position = (String) body.get("position");
    String round = (String) body.get("round");
    String difficulty = (String) body.get("difficulty");
    int questionCount = body.get("questionCount") != null
        ? ((Number) body.get("questionCount")).intValue() : 5;
    Long resumeId = body.get("resumeId") != null
        ? Long.valueOf(body.get("resumeId").toString()) : null;

    // ... 参数校验 ...

    InterviewCreateVO data = interviewService.createInterview(
        userId, position, round, difficulty, questionCount, resumeId);
    return Result.success("面试创建成功", data);
}
```

---

## 4. Frontend Implementation

### 4.1 New Files

```
frontend/src/
├── api/
│   └── resume.js              # 简历API模块
├── views/
│   └── resume/
│       └── index.vue          # 简历管理页面
├── components/
│   └── ResumeUpload.vue       # 简历上传组件（可复用）
│   └── ResumeSelector.vue     # 简历选择器（面试配置中使用）
└── router/index.js            # 新增路由
```

### 4.2 API Module: `resume.js`

```javascript
import request from '@/utils/request'

export function uploadResume(file, title) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('title', title || '我的简历')
  return request({
    url: '/api/resume/upload',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function getResumeList() {
  return request({ url: '/api/resume/list', method: 'get' })
}

export function getResumeDetail(id) {
  return request({ url: `/api/resume/${id}`, method: 'get' })
}

export function deleteResume(id) {
  return request({ url: `/api/resume/${id}`, method: 'delete' })
}

export function setDefaultResume(id) {
  return request({ url: `/api/resume/${id}/default`, method: 'put' })
}

export function reparseResume(id) {
  return request({ url: `/api/resume/${id}/reparse`, method: 'post' })
}
```

### 4.3 ResumeUpload.vue 组件

功能：
- 拖拽上传区域 + 点击选择文件
- 支持格式：PDF、DOC、DOCX、TXT
- 文件大小限制：5MB
- 上传进度显示
- 上传成功后显示解析状态（待解析/解析中/解析完成/解析失败）
- 解析完成后展示简历摘要（姓名、学校、技能标签等）

### 4.4 ResumeSelector.vue 组件

功能：
- 下拉选择已有简历
- 显示简历标题和解析状态
- "管理简历"链接跳转到简历管理页
- 可选"不使用简历"选项

### 4.5 简历管理页面 (`views/resume/index.vue`)

布局：
- 页面顶部：上传按钮 + 简历数量统计
- 简历列表：卡片式展示
  - 简历标题
  - 文件名、大小、上传时间
  - 解析状态标签（颜色区分）
  - 默认简历标记
  - 操作按钮：设为默认 / 重新解析 / 删除
- 简历详情弹窗：
  - 基本信息（姓名、邮箱、电话）
  - 教育经历列表
  - 技能标签云
  - 工作经历列表
  - 项目经历列表

### 4.6 面试配置页修改

在 `views/interview/config/index.vue` 中新增简历选择区域：

```vue
<!-- 在岗位方向选择之后，新增简历选择 -->
<div class="form-group">
  <label class="form-label">
    简历（可选）
    <span class="optional-tag">选填</span>
  </label>
  <ResumeSelector v-model="config.resumeId" />
  <p class="form-tip">上传简历后，AI将基于你的简历内容进行针对性提问</p>
</div>
```

### 4.7 Router 新增

```javascript
{
  path: '/resume',
  name: 'Resume',
  component: () => import('@/views/resume/index.vue'),
  meta: { title: '简历管理', requiresAuth: true }
}
```

### 4.8 侧边栏导航新增

在 `layout/index.vue` 的导航菜单中新增"简历管理"入口。

---

## 5. AI Prompting Strategy

### 5.1 简历解析 Prompt

```
请从以下简历文本中提取结构化信息，严格按JSON格式返回：

{
  "name": "姓名",
  "email": "邮箱",
  "phone": "电话",
  "education": [{"school": "学校", "degree": "学位", "major": "专业", "startDate": "起始", "endDate": "结束"}],
  "skills": ["技能1", "技能2"],
  "experience": [{"company": "公司", "position": "职位", "startDate": "起始", "endDate": "结束", "description": "职责描述"}],
  "projects": [{"name": "项目名", "role": "角色", "techStack": ["技术栈"], "description": "项目描述"}],
  "certifications": ["证书1"],
  "summary": "一句话总结候选人背景"
}

简历文本：
{resumeText}
```

### 5.2 简历面试 System Prompt 要点

- 明确告知 AI 候选人的技术栈和项目经验
- 要求 AI 围绕简历中的具体项目提问
- 要求 AI 针对简历中提到的技术进行深度追问
- 保持原有追问/评分/结束规则不变

---

## 6. Implementation Phases

### Phase 1: 基础简历管理（预计 2-3 天）

1. **数据库**：创建 `resume` 表，`interview_session` 新增 `resume_id` 字段
2. **后端**：
   - `Resume` 实体类
   - `ResumeMapper` + XML
   - `ResumeService`（上传、查询、删除、设为默认）
   - `ResumeController`
   - 添加 PDFBox、Apache POI 依赖
3. **前端**：
   - `resume.js` API 模块
   - `ResumeUpload.vue` 组件
   - `views/resume/index.vue` 简历管理页
   - 路由配置
   - 侧边栏导航

### Phase 2: AI 简历解析（预计 1-2 天）

1. **后端**：
   - `ResumeService.aiParseResume()` 异步解析方法
   - 简历解析 Prompt 模板
   - 文件内容提取（PDF/DOC/TXT）
   - `@Async` 异步配置
2. **前端**：
   - 解析状态实时显示（轮询或 WebSocket）
   - 简历详情展示（解析结果可视化）

### Phase 3: 简历面试集成（预计 1-2 天）

1. **后端**：
   - 修改 `InterviewController.create()` 支持 `resumeId` 参数
   - 修改 `InterviewService.createInterview()` 加载简历上下文
   - 修改 `PromptBuilder` 新增简历面试 Prompt
   - 修改 `AIService.generateGreeting()` 支持简历上下文
2. **前端**：
   - `ResumeSelector.vue` 组件
   - 面试配置页集成简历选择
   - 面试会话中显示"基于简历面试"标识

### Phase 4: 优化与完善（预计 1 天）

1. 简历上传的错误处理和边界情况
2. 解析失败重试机制
3. 简历数据的安全性（只允许访问自己的简历）
4. 样式美化和交互优化

---

## 7. Key Design Decisions

| 决策 | 方案 | 理由 |
|------|------|------|
| 文件存储 | 本地文件系统 `./uploads/resumes/` | 与现有 avatar 存储方式一致，简单直接 |
| 简历解析 | DeepSeek AI 异步解析 | 利用现有 AI 能力，无需引入第三方解析服务 |
| 解析结果存储 | MySQL JSON 列 | 方便查询和展示，结构灵活 |
| 集成方式 | 可选参数 resumeId | 向后兼容，不影响现有面试流程 |
| 文件格式 | PDF/DOC/DOCX/TXT | 覆盖主流简历格式 |

---

## 8. File Changes Summary

### New Files
- `backend/src/main/java/org/backend/entity/Resume.java`
- `backend/src/main/java/org/backend/mapper/ResumeMapper.java`
- `backend/src/main/resources/mapper/ResumeMapper.xml`
- `backend/src/main/java/org/backend/service/ResumeService.java`
- `backend/src/main/java/org/backend/controller/ResumeController.java`
- `frontend/src/api/resume.js`
- `frontend/src/views/resume/index.vue`
- `frontend/src/components/ResumeUpload.vue`
- `frontend/src/components/ResumeSelector.vue`
- `backend/init.sql` (追加 resume 表 DDL)
- `docs/resume-module-plan.md` (本文档)

### Modified Files
- `backend/src/main/java/org/backend/entity/InterviewSession.java` (新增 resumeId 字段)
- `backend/src/main/java/org/backend/controller/InterviewController.java` (create 方法新增 resumeId 参数)
- `backend/src/main/java/org/backend/service/InterviewService.java` (createInterview 加载简历上下文)
- `backend/src/main/java/org/backend/util/PromptBuilder.java` (新增简历面试 Prompt)
- `backend/src/main/java/org/backend/util/AIService.java` (generateGreeting 支持简历上下文)
- `backend/src/main/resources/mapper/InterviewSessionMapper.xml` (新增 resume_id 映射)
- `backend/pom.xml` (新增 PDFBox、Apache POI 依赖)
- `frontend/src/router/index.js` (新增简历路由)
- `frontend/src/views/interview/config/index.vue` (集成简历选择器)
- `frontend/src/layout/index.vue` (侧边栏新增简历管理入口)
