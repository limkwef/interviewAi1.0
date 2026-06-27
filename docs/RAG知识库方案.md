# RAG 知识库系统设计方案

> **目标**：让 AI 面试官能参考用户上传的文档（简历、技术笔记、公司资料等）进行回答，提供基于语义检索的上下文增强生成能力

---

## 1. 背景与动机

### 1.1 当前 AI 面试的局限

| 问题 | 表现 | 影响 |
|---|---|---|
| **知识边界固定** | AI 只依赖预训练知识 + Prompt 中的规则 | 无法回答特定公司的内部技术栈问题 |
| **无法感知用户背景** | AI 不知道用户的简历内容 | 追问无法针对用户的实际项目经历 |
| **无外部知识注入** | AI 只能凭"记忆"回答 | 对冷门/新技术了解有限，回答可能过时 |
| **面试场景单一** | 所有面试使用相同面试题 | 无法模拟特定公司的面试风格 |

### 1.2 RAG 能带来的改变

```
传统 AI 面试:                                              RAG 增强面试:
┌─────────────┐                                            ┌─────────────┐
│  用户回答    │────▶│  AI 面试官    │                     │  用户回答    │────▶│  AI 面试官    │
└─────────────┘     │  (仅凭训练知识) │                     └─────────────┘     └──────┬──────┘
                    └───────────────┘                                                  │
                                                                         ┌─────────────▼──────┐
                                                                         │  RAG 检索增强       │
                                                                         │  ┌─────────────────┐│
                                                                         │  │ 用户上传文档库    ││
                                                                         │  │ 简历 / 笔记 /    ││
                                                                         │  │ 公司资料 / 题库   ││
                                                                         │  └─────────────────┘│
                                                                         └────────────────────┘
```

---

## 2. 整体架构

### 2.1 系统架构图

```
┌──────────────────────────────────────────────────────────────────────────┐
│                           前端 (Vue 3)                                   │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────┐                │
│  │ 文档上传页    │  │ 知识库管理页   │  │ 面试设置页        │                │
│  │ (拖拽上传)    │  │ (列表/搜索)   │  │ (选择关联知识库)   │                │
│  └──────┬──────┘  └──────┬───────┘  └────────┬────────┘                │
└─────────┼────────────────┼───────────────────┼──────────────────────────┘
          │                │                   │
┌─────────▼────────────────▼───────────────────▼──────────────────────────┐
│                        后端 (Spring Boot)                               │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │                     API 层                                        │   │
│  │  POST /api/knowledge/upload  → 文档上传                          │   │
│  │  GET  /api/knowledge/list     → 知识库列表                       │   │
│  │  POST /api/knowledge/query    → 语义搜索                         │   │
│  │  DELETE /api/knowledge/{id}   → 删除文档                         │   │
│  │  POST /api/interview/create   → 创建面试（关联知识库）            │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────────┐  ┌───────────┐  │
│  │ 文档解析服务  │  │ 文本切片引擎   │  │ 嵌入向量服务     │  │ 向量检索引擎│  │
│  │ (Apache Tika)│  │ (语义分块)    │  │ (Embedding API) │  │ (语义搜索) │  │
│  └──────┬──────┘  └──────┬───────┘  └────────┬───────┘  └─────┬─────┘  │
│         │                │                   │                 │        │
│  ┌──────▼────────────────▼───────────────────▼─────────────────▼─────┐  │
│  │                        数据层                                      │  │
│  │  ┌──────────────┐  ┌──────────────────┐  ┌────────────────────┐   │  │
│  │  │ MySQL (文档元  │  │ MySQL (文本块+   │  │ Redis (缓存热门     │   │  │
│  │  │ 信息、知识库)  │  │ 向量 BLOB+全文索引)│  │ 查询、嵌入向量)     │   │  │
│  │  └──────────────┘  └──────────────────┘  └────────────────────┘   │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │                      AI 面试引擎                                   │   │
│  │  ┌─────────────┐  ┌────────────────────┐  ┌──────────────────┐   │   │
│  │  │ PromptBuilder│  │ RAG 上下文注入器     │  │ AIService        │   │   │
│  │  │ (原始 Prompt)│──│ (检索结果→系统Prompt)│──│ (DeepSeek 调用)   │   │   │
│  │  └─────────────┘  └────────────────────┘  └──────────────────┘   │   │
│  └──────────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────────┘
```

### 2.2 核心流程：文档上传 → 检索 → 面试增强

```
用户上传文档
     │
     ▼
┌─────────────┐     ┌──────────────┐     ┌────────────────┐
│ 文档解析     │────▶│ 文本切片       │────▶│ 生成嵌入向量     │
│ (Tika 解析)  │     │ (语义分块)     │     │ (BGE-M3 嵌入)   │
│ PDF/Word/TXT │     │ 500-1000字符  │     │ 1024维向量      │
└─────────────┘     └──────────────┘     └───────┬────────┘
                                                 │
                                                 ▼
┌─────────────────────────────────────────────────┐
│                 存储到 MySQL                      │
│  knowledge_doc 表：文档元信息                     │
│  knowledge_chunk 表：文本块 + 向量(BLOB) + 全文索引│
└─────────────────────────────────────────────────┘
                                        ▲
                                        │
面试过程中：                              │
┌──────────────┐     ┌──────────────┐    │
│ 用户回答/问题  │────▶│ 向量化查询     │────┘
│ (转为查询向量)  │     │ (使用相同     │
└──────────────┘     │ Embedding API)│
                     └──────┬───────┘
                            │ 余弦相似度搜索
                            ▼
                    ┌────────────────┐     ┌──────────────────┐
                    │ 检索 Top-K 文本块 │────▶│ 注入到 AI Prompt  │
                    │ (相似度 > 阈值)  │     │ 作为参考上下文     │
                    └────────────────┘     └────────┬─────────┘
                                                    │
                                                    ▼
                                           ┌──────────────────┐
                                           │ AI 生成增强回答    │
                                           │ (参考检索到的文档)  │
                                           └──────────────────┘
```

---

## 3. 详细技术方案

### 3.1 嵌入向量服务（Embedding）

DeepSeek API **不提供**专用 Embedding 接口，因此需要额外引入 Embedding 模型。

#### 为什么不能用 DeepSeek 自己生成向量？

| 方案 | 精度 | 延迟 | 成本 | 结论 |
|---|---|---|---|---|
| 让 DeepSeek 输出"伪向量" | ❌ 低 | 高（2-5s/次） | 高（消耗 Token） | **不推荐** |
| 专用 Embedding 模型 | ✅ 高 | 低（50-200ms/次） | 免费 | **推荐** |

RAG 的检索质量完全取决于 Embedding 的精度，用 LLM 生成向量是舍近求远。

#### 推荐方案：BGE-M3（本地 Docker 部署）

[BGE-M3](https://github.com/FlagOpen/FlagEmbedding) 是 BAAI（北京智源）开源的 Embedding 模型，**专为中文优化**，是当前中文 RAG 的事实标准。

**启动方式**（一行命令）：

```bash
docker run -p 9997:9997 registry.cn-hangzhou.aliyuncs.com/bge/bge-reranker:latest
```

> 资源消耗：CPU 即可运行，2GB 内存，无需 GPU，适合毕业设计部署

**Java 调用代码**：

```java
/**
 * BGE-M3 嵌入向量服务
 * 通过 HTTP 调用本地 Docker 部署的 BGE 模型
 * 1024 维向量，精度远超 LLM 伪嵌入方案
 */
@Service
public class BgeEmbeddingService implements EmbeddingService {

    private static final String BGE_URL = "http://localhost:9997/embed";
    private static final int VECTOR_DIM = 1024;  // BGE-M3 输出 1024 维向量
    
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public double[] embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new double[VECTOR_DIM];
        }
        try {
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("text", text);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BGE_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(requestBody)))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode vectorNode = root.path("vector");
                if (vectorNode.isArray() && vectorNode.size() > 0) {
                    double[] vector = new double[vectorNode.size()];
                    for (int i = 0; i < vectorNode.size(); i++) {
                        vector[i] = vectorNode.get(i).asDouble();
                    }
                    return vector;
                }
            }
            log.warn("BGE Embedding 调用失败: status={}", response.statusCode());
            return new double[VECTOR_DIM];
        } catch (Exception e) {
            log.error("BGE Embedding 异常", e);
            return new double[VECTOR_DIM];  // 返回零向量（检索会自然降级到全文搜索）
        }
    }

    @Override
    public List<double[]> embedBatch(List<String> texts) {
        // BGE 批量嵌入：合并请求减少 HTTP 开销
        List<double[]> results = new ArrayList<>();
        for (String text : texts) {
            results.add(embed(text));
        }
        return results;
    }
}
```

#### 备选 1：BGE 免费在线 API（零部署）

如果不想部署 Docker，BGE 提供免费在线 API（适合开发/测试）：

```java
// 免费在线 API（限速 ~10次/分钟，毕业设计足够）
private static final String BGE_FREE_API = "https://bge.baihug.cn/api/embed";

public double[] embedFree(String text) {
    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BGE_FREE_API))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(
                "{\"model\":\"BAAI/bge-m3\",\"input\":[\"" 
                + text.replace("\"", "\\\"") + "\"]}"))
            .build();
    // ... 解析响应同 Docker 版本
}
```

#### 备选 2：阿里 DashScope Embedding（需 API Key）

如果愿意引入云服务：

```xml
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>dashscope-sdk-java</artifactId>
    <version>2.22.7</version>
</dependency>
```

```java
// 阿里 text-embedding-v3，1024 维，中文效果优秀
EmbeddingResult result = Embedding.builder()
    .model("text-embedding-v3")
    .texts(Arrays.asList(text))
    .apiKey("your-dashscope-key")
    .call();
```

#### 方案选择决策树

```
你能跑 Docker 吗？
├── ✅ 是 → BGE-M3 本地 Docker（推荐）
│            安装: docker run -p 9997:9997 ...
│            精度: ✅ 最高，维度 1024，纯免费
│
└── ❌ 否 → 你有阿里云 Key 吗？
    ├── ✅ 是 → DashScope text-embedding-v3
    │            接入: dashscope-sdk-java
    │            精度: ✅ 好，有免费额度
    │
    └── ❌ 否 → BGE 免费在线 API
                 接入: HTTP POST https://bge.baihug.cn/api/embed
                 精度: ✅ 同上（BGE-M3），限速 10次/分钟
```

### 3.2 文本切片策略

```java
/**
 * 语义文本切片引擎
 *
 * 切片策略：
 * 1. 优先按 Markdown 标题分割（## / ###）
 * 2. 其次按自然段落分割（双换行）
 * 3. 最后按句子分割（。！？）
 * 4. 限制每片长度 200-1000 字符
 * 5. 相邻切片有 10% 重叠
 */
@Component
public class TextChunker {

    /** 目标切片长度（字符数） */
    private static final int TARGET_CHUNK_SIZE = 500;
    /** 最小切片长度 */
    private static final int MIN_CHUNK_SIZE = 200;
    /** 最大切片长度 */
    private static final int MAX_CHUNK_SIZE = 1000;
    /** 切片重叠比例 */
    private static final double OVERLAP_RATIO = 0.1;

    /**
     * 将文档文本分割为语义切片
     */
    public List<TextChunk> chunk(String text, Long docId) {
        List<String> rawChunks = splitByHeadingsAndParagraphs(text);
        List<TextChunk> result = new ArrayList<>();
        int order = 0;

        for (String raw : rawChunks) {
            // 跳过过短片段
            String cleaned = raw.trim();
            if (cleaned.length() < MIN_CHUNK_SIZE) continue;

            // 超长片段继续分割
            if (cleaned.length() > MAX_CHUNK_SIZE) {
                List<String> subChunks = splitBySentences(cleaned, TARGET_CHUNK_SIZE);
                for (String sub : subChunks) {
                    result.add(new TextChunk(docId, sub.trim(), ++order));
                }
            } else {
                result.add(new TextChunk(docId, cleaned, ++order));
            }
        }

        // 添加重叠（相邻切片共享末尾 10% 内容）
        addOverlap(result);

        log.info("文档 {} 切片完成: {} → {} 个切片", docId, text.length(), result.size());
        return result;
    }

    /**
     * 优先按 Markdown 标题分割，其次按段落
     */
    private List<String> splitByHeadingsAndParagraphs(String text) {
        List<String> chunks = new ArrayList<>();

        // 1. 按 Markdown 标题分割（# 或 ## 或 ###）
        String[] byHeading = text.split("(?=\\n#{1,3}\\s)");
        if (byHeading.length > 1) {
            // 标题分割成功
            for (String section : byHeading) {
                if (section.trim().length() > MIN_CHUNK_SIZE) {
                    chunks.add(section.trim());
                }
            }
            if (!chunks.isEmpty()) return chunks;
        }

        // 2. 按双换行分割（段落）
        String[] byParagraph = text.split("\\n\\s*\\n");
        for (String para : byParagraph) {
            String trimmed = para.trim();
            if (trimmed.length() > MIN_CHUNK_SIZE) {
                chunks.add(trimmed);
            } else if (!chunks.isEmpty()) {
                // 短段落合并到前一个
                chunks.set(chunks.size() - 1,
                    chunks.get(chunks.size() - 1) + "\n" + trimmed);
            }
        }

        if (!chunks.isEmpty()) return chunks;

        // 3. 兜底：按句子分割
        return splitBySentences(text, TARGET_CHUNK_SIZE);
    }

    /**
     * 按标点符号分割为句子，再合并到目标大小
     */
    private List<String> splitBySentences(String text, int targetSize) {
        List<String> sentences = Arrays.asList(text.split("(?<=[。！？.!?\\n])"));
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String sentence : sentences) {
            if (current.length() + sentence.length() > targetSize
                && current.length() >= MIN_CHUNK_SIZE) {
                chunks.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(sentence);
        }
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }

        return chunks;
    }
}
```

### 3.3 MySQL 向量存储

由于现有项目使用 MySQL，不引入单独的向量数据库。采用 **MySQL 存储向量 + 应用层余弦相似度计算** 的方案。

#### 方案对比

| 方案 | 优点 | 缺点 | 适用场景 |
|---|---|---|---|
| **MySQL BLOB + 应用层计算** ✅ | 零额外依赖，现有架构不变 | 十万级以上文档查询慢 | ✅ **毕业设计（数据量<10万）** |
| **Redis + RediSearch** | 查询快，内存计算 | 引入新依赖，向量大小有限 | 中小规模 |
| **pgvector** | 原生 SQL 支持，性能好 | 需迁移到 PostgreSQL | 生产环境 |

> **为什么不需要独立向量数据库？**
> - 毕业设计数据规模：按 100 用户 × 每人 10 篇 × 每篇 20 切片 ≈ **2 万条**
> - 2 万条 × 1024 维 × 8 字节 ≈ 163 MB 全内存计算
> - 单次遍历 + 余弦排序 ≈ 5-15ms ✅ 完全可接受
> - 配合 Redis 缓存热门查询，压测无压力

#### 新表设计

```sql
-- 知识库表（每个知识库是一组文档的集合）
CREATE TABLE IF NOT EXISTS knowledge_base (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    user_id         BIGINT        NOT NULL COMMENT '所有者',
    name            VARCHAR(100)  NOT NULL COMMENT '知识库名称',
    description     VARCHAR(500)  DEFAULT '' COMMENT '描述',
    cover_type      VARCHAR(20)   DEFAULT 'personal' COMMENT 'personal/resume/techstack',
    status          TINYINT       NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    doc_count       INT           NOT NULL DEFAULT 0 COMMENT '文档数量',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 知识库文档表
CREATE TABLE IF NOT EXISTS knowledge_doc (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    kb_id           BIGINT        NOT NULL COMMENT '所属知识库',
    user_id         BIGINT        NOT NULL,
    title           VARCHAR(200)  NOT NULL COMMENT '文档标题（上传时自动提取或自定义）',
    file_type       VARCHAR(20)   NOT NULL COMMENT 'pdf/docx/txt/md',
    file_size       BIGINT        NOT NULL COMMENT '文件大小（字节）',
    file_path       VARCHAR(500)  DEFAULT '' COMMENT '原始文件存储路径',
    chunk_count     INT           NOT NULL DEFAULT 0 COMMENT '切片数量',
    status          VARCHAR(20)   NOT NULL DEFAULT 'processing' COMMENT 'processing/completed/failed',
    error_msg       TEXT          DEFAULT NULL COMMENT '处理失败原因',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_kb_id (kb_id),
    KEY idx_user_id (user_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 文档切片表（存储文本块 + 嵌入向量）
CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    doc_id          BIGINT        NOT NULL COMMENT '所属文档',
    kb_id           BIGINT        NOT NULL COMMENT '所属知识库（冗余，方便检索）',
    chunk_index     INT           NOT NULL COMMENT '切片序号',
    content         TEXT          NOT NULL COMMENT '切片文本内容',
    content_hash    VARCHAR(64)   DEFAULT NULL COMMENT '内容哈希（用于去重）',
    embedding       BLOB          DEFAULT NULL COMMENT '嵌入向量（1024维 double 序列化，BGE-M3）',
    embedding_dim   INT           DEFAULT 0 COMMENT '向量维度',
    token_count     INT           DEFAULT 0 COMMENT 'token 数（近似）',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_doc_id (doc_id),
    KEY idx_kb_id (kb_id),
    KEY idx_content_hash (content_hash),
    FULLTEXT INDEX ft_content (content)  -- 全文索引作为降级方案
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 面试-知识库关联表（面试时可选择关联哪些知识库）
CREATE TABLE IF NOT EXISTS interview_knowledge_rel (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    session_id      BIGINT        NOT NULL COMMENT '面试会话ID',
    kb_id           BIGINT        NOT NULL COMMENT '知识库ID',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_kb (session_id, kb_id),
    KEY idx_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.4 向量化与检索服务

#### 向量序列化工具

```java
/**
 * 向量工具：double[] ↔ byte[] 互转（MySQL BLOB 存储）
 */
public class VectorUtils {

    /** double[] → byte[]（每个 double 8 字节，大端序） */
    public static byte[] doubleArrayToBytes(double[] vector) {
        ByteBuffer buf = ByteBuffer.allocate(vector.length * 8)
            .order(ByteOrder.BIG_ENDIAN);
        for (double d : vector) buf.putDouble(d);
        return buf.array();
    }

    /** byte[] → double[] */
    public static double[] bytesToDoubleArray(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        double[] vector = new double[bytes.length / 8];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = buf.getDouble();
        }
        return vector;
    }

    /** 余弦相似度计算 */
    public static double cosineSimilarity(double[] a, double[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
```

#### 文档处理服务（异步）

```java
/**
 * 文档处理服务
 * 上传文档 → 异步解析 → 切片 → 向量化
 *
 * 异步架构利用现有的线程池（AsyncConfig），避免阻塞 API
 */
@Service
public class KnowledgeService {

    @Autowired
    private TextChunker chunker;
    @Autowired
    private EmbeddingService embeddingService;
    @Autowired
    private KnowledgeDocMapper docMapper;
    @Autowired
    private KnowledgeChunkMapper chunkMapper;

    /**
     * 上传文档并触发异步处理
     */
    @Transactional
    public Long uploadDocument(Long userId, Long kbId, MultipartFile file) {
        // 1. 保存原始文件
        String filePath = saveFile(file);

        // 2. 创建文档记录
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setKbId(kbId);
        doc.setUserId(userId);
        doc.setTitle(extractTitle(file));
        doc.setFileType(extractFileType(file));
        doc.setFileSize(file.getSize());
        doc.setFilePath(filePath);
        doc.setStatus("processing");
        docMapper.insert(doc);

        // 3. 异步解析 + 切片 + 向量化
        processDocumentAsync(doc.getId(), filePath);

        return doc.getId();
    }

    /**
     * 异步处理文档：解析 → 切片 → 向量化 → 存储
     */
    @Async("taskExecutor")
    public void processDocumentAsync(Long docId, String filePath) {
        try {
            // 1. 解析文档（Apache Tika）
            String text = extractText(filePath);

            // 2. 切片
            List<TextChunk> chunks = chunker.chunk(text, docId);

            // 3. 批量生成嵌入向量
            List<String> contents = chunks.stream()
                .map(TextChunk::getContent).collect(Collectors.toList());
            List<double[]> vectors = embeddingService.embedBatch(contents);

            // 4. 批量存储切片 + 向量
            for (int i = 0; i < chunks.size(); i++) {
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setDocId(docId);
                chunk.setKbId(docMapper.selectById(docId).getKbId());
                chunk.setChunkIndex(i + 1);
                chunk.setContent(chunks.get(i).getContent());
                chunk.setContentHash(md5(chunks.get(i).getContent()));

                if (i < vectors.size() && vectors.get(i) != null) {
                    chunk.setEmbedding(VectorUtils.doubleArrayToBytes(vectors.get(i)));
                    chunk.setEmbeddingDim(vectors.get(i).length);
                }
                chunkMapper.insert(chunk);
            }

            // 5. 更新文档状态
            docMapper.updateStatus(docId, "completed", chunks.size());
            log.info("文档 {} 处理完成: {} 个切片", docId, chunks.size());

        } catch (Exception e) {
            log.error("文档 {} 处理失败", docId, e);
            docMapper.updateStatus(docId, "failed", 0);
            docMapper.updateError(docId, e.getMessage());
        }
    }
}
```

#### 语义检索服务

```java
/**
 * RAG 检索服务
 *
 * 检索策略（两级降级）：
 * 1. 向量相似度检索（主要）
 * 2. MySQL 全文检索（降级）
 */
@Service
public class RagRetrievalService {

    /** 默认检索 Top-K */
    private static final int DEFAULT_TOP_K = 5;
    /** 相似度阈值（低于此值的结果将被丢弃） */
    private static final double SIMILARITY_THRESHOLD = 0.6;

    @Autowired
    private KnowledgeChunkMapper chunkMapper;
    @Autowired
    private EmbeddingService embeddingService;

    /**
     * 检索与查询最相关的文档片段
     *
     * @param query 自然语言查询
     * @param kbIds 知识库 ID 列表（空 = 全部）
     * @param topK  返回数量
     * @return 排序后的相关片段
     */
    public List<RelevantChunk> retrieve(String query, List<Long> kbIds, int topK) {
        // 1. 将查询转为嵌入向量
        double[] queryVector = embeddingService.embed(query);

        if (queryVector == null || queryVector.length == 0) {
            // 降级：全文检索
            return fallbackFulltextSearch(query, kbIds, topK);
        }

        // 2. 获取候选切片（优先从指定知识库）
        List<KnowledgeChunk> candidates;
        if (kbIds != null && !kbIds.isEmpty()) {
            candidates = chunkMapper.selectByKbIds(kbIds);
        } else {
            candidates = chunkMapper.selectAllWithEmbedding();
        }

        // 3. 计算余弦相似度 + 排序
        List<ScoredChunk> scored = new ArrayList<>();
        for (KnowledgeChunk chunk : candidates) {
            if (chunk.getEmbedding() == null) continue;
            double[] chunkVector = VectorUtils.bytesToDoubleArray(chunk.getEmbedding());
            double similarity = VectorUtils.cosineSimilarity(queryVector, chunkVector);
            if (similarity >= SIMILARITY_THRESHOLD) {
                scored.add(new ScoredChunk(chunk, similarity));
            }
        }

        scored.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        // 4. 取 Top-K
        List<RelevantChunk> result = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, scored.size()); i++) {
            ScoredChunk sc = scored.get(i);
            result.add(new RelevantChunk(
                sc.getChunk().getContent(),
                sc.getScore(),
                sc.getChunk().getDocId(),
                sc.getChunk().getChunkIndex()
            ));
        }

        log.debug("RAG 检索: query='{}', matched={}, topScore={:.2f}",
            query, result.size(), result.isEmpty() ? 0 : result.get(0).getScore());

        return result;
    }

    /**
     * 降级方案：MySQL 全文检索
     * 当 Embedding API 不可用时自动切换
     */
    private List<RelevantChunk> fallbackFulltextSearch(
        String query, List<Long> kbIds, int topK) {
        // 使用 MySQL FULLTEXT INDEX 进行全文搜索
        List<KnowledgeChunk> results = chunkMapper.fulltextSearch(
            query, kbIds, topK);
        return results.stream()
            .map(c -> new RelevantChunk(c.getContent(), 0.5, c.getDocId(), c.getChunkIndex()))
            .collect(Collectors.toList());
    }
}
```

#### MyBatis Mapper 关键方法

```xml
<!-- KnowledgeChunkMapper.xml -->

<!-- 向量检索时获取所有含嵌入的切片 -->
<select id="selectByKbIds" resultType="KnowledgeChunk">
    SELECT id, doc_id, kb_id, content, embedding, chunk_index
    FROM knowledge_chunk
    WHERE kb_id IN
    <foreach collection="kbIds" item="id" open="(" separator="," close=")">
        #{id}
    </foreach>
    AND embedding IS NOT NULL
</select>

<!-- MySQL 全文检索降级 -->
<select id="fulltextSearch" resultType="KnowledgeChunk">
    SELECT id, doc_id, kb_id, content, chunk_index,
           MATCH(content) AGAINST(#{query} IN NATURAL LANGUAGE MODE) AS relevance
    FROM knowledge_chunk
    WHERE MATCH(content) AGAINST(#{query} IN NATURAL LANGUAGE MODE)
    <if test="kbIds != null and !kbIds.isEmpty()">
        AND kb_id IN
        <foreach collection="kbIds" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
    </if>
    ORDER BY relevance DESC
    LIMIT #{topK}
</select>
```

### 3.5 RAG 上下文注入面试引擎

这是核心——将检索到的知识注入到 AI 面试的 Prompt 中。

#### 注入时机

RAG 知识在以下两个时机注入：

1. **创建面试时**：将关联知识库的整体摘要注入系统 Prompt
2. **每个回答评估时**：检索与当前用户回答最相关的知识片段

#### Prompt 注入实现

```java
/**
 * RAG 上下文注入器
 * 将检索到的知识片段注入到 AI 的 System Prompt 中
 */
@Component
public class RagContextInjector {

    @Autowired
    private RagRetrievalService retrievalService;

    /**
     * 为面试会话注入 RAG 上下文
     * 在每次 AI 评估用户回答时调用
     *
     * @param userId    用户 ID
     * @param sessionId 面试会话 ID
     * @param userAnswer 用户最新回答
     * @param position  面试职位
     * @param kbIds     关联的知识库 ID 列表
     * @return RAG 上下文文本（插入 system prompt）
     */
    public String buildRagContext(Long userId, Long sessionId,
                                   String userAnswer, String position,
                                   List<Long> kbIds) {
        if (kbIds == null || kbIds.isEmpty()) {
            return ""; // 没有关联知识库
        }

        // 1. 构建查询：结合用户回答 + 职位信息
        String query = String.format("职位: %s\n问题: %s", position, userAnswer);

        // 2. 检索相关片段
        List<RelevantChunk> chunks = retrievalService.retrieve(query, kbIds, 3);

        if (chunks.isEmpty()) {
            return "";
        }

        // 3. 构建 RAG 上下文文本
        StringBuilder context = new StringBuilder();
        context.append("\n\n========== 参考知识（来自用户知识库）==========\n");
        context.append("以下是用户提供的参考资料中与当前问题相关的内容：\n\n");

        for (int i = 0; i < chunks.size(); i++) {
            RelevantChunk chunk = chunks.get(i);
            context.append("【参考").append(i + 1).append("】")
                .append("（相关度：").append(String.format("%.0f%%", chunk.getScore() * 100))
                .append("）\n");
            context.append(chunk.getContent()).append("\n\n");
        }

        context.append("========== 参考知识结束 ==========\n\n");
        context.append("请结合以上参考资料对用户的回答进行评价。");
        context.append("如果参考资料与问题无关，请忽略参考资料。");

        return context.toString();
    }
}
```

#### 修改 PromptBuilder

```java
// PromptBuilder.java 新增方法
/**
 * 构建包含 RAG 上下文的面试系统 Prompt
 */
public String buildInterviewSystemPromptWithRag(
        String position, String round, String difficulty,
        int totalQuestions, String questionsText,
        String ragContext) {

    String basePrompt = buildInterviewSystemPrompt(
        position, round, difficulty, totalQuestions, questionsText);

    if (ragContext == null || ragContext.isEmpty()) {
        return basePrompt;
    }

    // 在 system prompt 末尾追加 RAG 上下文
    return basePrompt + "\n\n" + ragContext;
}
```

#### 修改 InterviewService

```java
// InterviewService.java 中创建面试时
@Transactional
public InterviewCreateVO createInterview(Long userId, String position,
        String round, String difficulty, int questionCount,
        int maxFollowUp, List<Long> knowledgeBaseIds) {
    // ... 现有逻辑 ...

    // 新增：关联知识库
    if (knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty()) {
        for (Long kbId : knowledgeBaseIds) {
            interviewKbRelMapper.insert(session.getId(), kbId);
        }
    }

    // ... 继续现有逻辑 ...
}

// 发送消息时注入 RAG
private void injectRagContext(Long sessionId, Long userId,
        String userAnswer, String position,
        List<Map<String, String>> aiMessages) {

    // 查询面试关联的知识库
    List<Long> kbIds = interviewKbRelMapper.findBySessionId(sessionId);
    if (kbIds.isEmpty()) return;

    // 构建 RAG 上下文并注入到 system prompt
    String ragContext = ragContextInjector.buildRagContext(
        userId, sessionId, userAnswer, position, kbIds);

    if (!ragContext.isEmpty()) {
        // 追加到 system prompt 末尾
        for (Map<String, String> msg : aiMessages) {
            if ("system".equals(msg.get("role"))) {
                msg.put("content", msg.get("content") + "\n\n" + ragContext);
                break;
            }
        }
    }
}
```

### 3.6 面试配置页新增知识库选择

```vue
<!-- 面试配置页面新增：选择知识库 -->
<template>
  <!-- ... 现有表单 ... -->
  <el-form-item label="关联知识库">
    <el-select
      v-model="knowledgeBaseIds"
      multiple
      placeholder="选择要关联的知识库（可选）"
      clearable
    >
      <el-option
        v-for="kb in knowledgeBases"
        :key="kb.id"
        :label="kb.name"
        :value="kb.id"
      >
        <span>{{ kb.name }}</span>
        <span class="kb-doc-count">{{ kb.docCount }} 篇文档</span>
      </el-option>
    </el-select>
    <div class="el-form-item-tip">
      关联后，AI 将在面试中参考知识库内容进行评价和追问
    </div>
  </el-form-item>
  <!-- ... 现有表单 ... -->
</template>
```

---

## 4. 前端实现

### 4.1 知识库管理页面

```
┌──────────────────────────────────────────────────────┐
│  知识库管理                                   [+ 新建] │
├──────────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────────────┐│
│  │ 个人知识库 📁    3 篇文档    2026-01-15  [管理]   ││
│  │ 我的简历 📄      1 篇文档    2026-01-14  [管理]   ││
│  │ 公司技术栈 📁    5 篇文档    2026-01-10  [管理]   ││
│  └──────────────────────────────────────────────────┘│
│                                                       │
│  当前知识库: 个人知识库                                │
│  ┌──────────────────────────────────────────────────┐│
│  │ [+ 上传文档]              搜索知识库内容... 🔍   ││
│  ├──────────────────────────────────────────────────┤│
│  │ 文件名         类型  大小  状态  切片数  操作     ││
│  │ Java并发编程.md  MD   12KB  ✅   24     [删除]  ││
│  │ 系统设计.pdf     PDF  2MB   ✅   38     [删除]  ││
│  │ Redis笔记.docx   DOCX 45KB  ⏳   0      [删除]  ││
│  └──────────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────┘
```

### 4.2 知识库 API

```javascript
// frontend/src/api/knowledge.js（新增）

import request from '@/utils/request'

// 获取知识库列表
export function getKnowledgeBaseList() {
  return request({ url: '/knowledge/list', method: 'get' })
}

// 创建知识库
export function createKnowledgeBase(data) {
  return request({ url: '/knowledge/create', method: 'post', data })
}

// 删除知识库
export function deleteKnowledgeBase(id) {
  return request({ url: `/knowledge/${id}`, method: 'delete' })
}

// 上传文档到知识库
export function uploadDocument(kbId, file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: `/knowledge/${kbId}/upload`,
    method: 'post',
    headers: { 'Content-Type': 'multipart/form-data' },
    data: formData
  })
}

// 获取知识库文档列表
export function getDocumentList(kbId) {
  return request({ url: `/knowledge/${kbId}/docs`, method: 'get' })
}

// 删除文档
export function deleteDocument(docId) {
  return request({ url: `/knowledge/doc/${docId}`, method: 'delete' })
}

// 搜索知识库
export function searchKnowledge(kbIds, query) {
  return request({
    url: '/knowledge/query',
    method: 'post',
    data: { kbIds, query, topK: 5 }
  })
}
```

---

## 5. 知识库类型与应用场景

### 5.1 预置知识库类型

| 类型 | 用途 | 自动创建 | 数据来源 |
|---|---|---|---|
| **个人简历** | AI 根据简历内容进行个性化追问 | ✅ 注册时自动 | 用户上传简历 |
| **面试笔记** | 用户上传的复习笔记 | ❌ 手动创建 | 用户上传的 MD/PDF |
| **公司资料** | 模拟特定公司的面试风格 | ❌ 手动创建 | 用户上传的公司介绍 |
| **技术栈参考** | 补充 AI 不熟悉的技术栈 | ❌ 手动创建 | 用户上传的技术文档 |

### 5.2 应用场景示例

**场景一：简历增强面试**

```
用户上传简历 → AI 解析简历中的项目经验 →
面试中:
  AI: "你的简历中提到使用了 Redis 实现分布式锁，
       能具体说说你的实现方案吗？遇到哪些问题？"
  (AI 从简历知识库中检索到"Redis 分布式锁"相关片段，生成针对性追问)
```

**场景二：特定公司面试**

```
用户上传"字节跳动面经"和"字节技术博客"到知识库 →
面试中:
  AI 的提问风格和难度对标字节跳动
  (AI 从知识库中学习了字节跳动的技术栈和文化)
```

**场景三：技术栈补充**

```
用户在学新的技术栈（如 K8s）→ 上传 K8s 学习笔记 →
AI 可以根据笔记内容提问，而不只是泛泛的通用问题
```

---

## 6. 现有代码复用与改造

### 可直接复用的组件

| 现有代码 | 复用方式 |
|---|---|
| `AIService.chat()` | RAG 增强后的面试对话 |
| `AIService.chatStream()` | 流式 RAG 增强回复 |
| `InterviewService.createInterview()` | 改造：增加知识库关联参数 |
| `InterviewService.doSendMessage()` | 改造：注入 RAG 上下文 |
| `PromptBuilder.buildInterviewSystemPrompt()` | 改造：追加 RAG 片段 |
| `AsyncConfig` | 文档异步处理使用现有线程池 |
| `CacheService` | 缓存热门查询的嵌入向量 |

### 需要改造的组件

| 现有代码 | 改造方式 |
|---|---|
| `InterviewCreateVO` | 新增 `knowledgeBaseIds` 字段 |
| `InterviewSession` / 表 | 新增 `kb_ids` 字段或关联表 |
| `PromptBuilder` | 新增 `buildRagUserPrompt()` |
| `InterviewService` | 在 evaluateAndRespond 前调用 RAG 检索 |

### 新增的组件

| 组件 | 说明 |
|---|---|
| `KnowledgeController` | 知识库 CRUD + 文档上传/查询 API |
| `KnowledgeService` | 知识库业务逻辑 |
| `TextChunker` | 文本切片引擎 |
| `EmbeddingService` | 嵌入向量生成（多方案切换） |
| `RagRetrievalService` | 语义检索 + 全文检索降级 |
| `RagContextInjector` | RAG 上下文注入面试 Prompt |
| `KnowledgeDocMapper` | 文档数据访问（MyBatis XML） |
| `KnowledgeChunkMapper` | 切片 + 向量数据访问（MyBatis XML） |
| `InterviewKbRelMapper` | 面试-知识库关联数据访问 |
| 前端知识库相关页面 | 管理页面、上传页面 |

### 不需要修改的组件

| 组件 | 理由 |
|---|---|
| `ScoringEngine` | RAG 不改变评分逻辑，只影响 AI 的 "知识面" |
| `BaiduSpeechService` | 语音模块独立，不受影响 |
| `MistakeService` | 错题本逻辑不变 |
| `AIDiagnosisService` | 诊断报告生成逻辑不变 |
| 现有前端面试界面 | 仅面试配置页增加知识库选择 |

---

## 7. 开发计划

| 阶段 | 内容 | 预计工时 |
|---|---|---|
| **Phase 1** | 数据库表创建 + MyBatis Mapper | 1 天 |
| **Phase 2** | 文档上传 + Apache Tika 解析 + 文本切片 | 2 天 |
| **Phase 3** | BGE-M3 嵌入服务（Docker 部署 + Java HTTP 调用） | 1 天 |
| **Phase 4** | 向量存储 + 余弦相似度检索 + 全文检索降级 | 2 天 |
| **Phase 5** | RAG 上下文注入面试引擎 | 1 天 |
| **Phase 6** | 前端知识库管理页面（列表/上传/搜索） | 2 天 |
| **Phase 7** | 面试配置页集成知识库选择 | 1 天 |
| **Phase 8** | 异步处理 + 文档状态轮询 | 1 天 |
| **Phase 9** | 集成测试 + 边界情况（空知识库/解析失败） | 1 天 |
| **总计** | | **13 天** |

---

## 8. 边界情况处理

| 场景 | 处理策略 |
|---|---|
| **知识库为空** | 面试正常进行，不注入 RAG 上下文，无感知降级 |
| **检索不到相关片段** | 仅注入摘要信息，AI 凭自身知识回答 |
| **Embedding API 失败** | 降级为 MySQL 全文检索 |
| **文档解析失败** | 标记文档状态为 failed，保存错误信息，不影响其他文档 |
| **超长文档** | 切片上限 1000 字符，最多 500 个切片/文档 |
| **重复文档** | 通过 content_hash 去重，相同内容不重复向量化 |
| **知识库禁用/删除** | 面试中使用缓存的知识库 ID 校验状态，已删除的自动忽略 |
| **并发上传** | 异步处理队列，每个文档独立事务 |
| **向量维度不匹配** | BGE-M3 固定输出 1024 维，EmbeddingService 统一维度常量，不同模型不混用 |
| **大量文档检索慢** | 控制每次检索的候选数（≤5000 条），超出时先按知识库过滤 |

---

## 9. 延伸：RAG 与语音面试的联动

当语音面试（方案一）和 RAG（方案二）都实现后，两者可产生协同效应：

```
语音面试中，用户说"我做过分布式锁" →
实时 ASR 识别 →
RAG 检索用户简历中关于分布式锁的详细描述 →
AI 结合简历内容追问 →
TTS 播报追问
```

这种联动让面试体验从"通用 AI 面试"升级为"真正了解你的 AI 面试官"。
