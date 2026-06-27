# 语音面试 WebSocket 实时方案设计书

> **目标**：将当前"录制→上传→ASR→手动发送→AI回复→TTS播放"的串行语音流程，改造为"边说边传→实时ASR→AI自动回复→语音合成"的实时对话流程

---

## 1. 现状分析

### 当前语音流程

```
用户说话 → 点击停止 → PCM转换 → POST上传 → 百度ASR → 显示文字 → 用户点击发送 → SSE流式AI回复 → 点击播放TTS
```

### 存在的问题

| 问题 | 表现 |
|---|---|
| **非实时** | 用户必须停止录音、等待上传、手动发送，无法自然对话 |
| **多次等待** | 每次说话都要等 ASR（2~5s）+ AI（3~10s）+ TTS（1~3s），总延迟可达 10s+ |
| **手动操作** | 语音识别结果只填入输入框，用户需手动点击发送 |
| **无法打断** | AI 说话时用户无法插话 |
| **串行依赖** | ASR → AI → TTS 三个阶段严格串行 |

---

## 2. 目标架构

### 2.1 总体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        浏览器 (Vue 3)                           │
│  ┌──────────────┐    ┌────────────────┐    ┌───────────────┐   │
│  │ getUserMedia │───▶│ WebSocket Client│◀───│ AudioPlayer   │   │
│  │ (音频采集)    │    │ (stomp.js)      │    │ (TTS 播放)    │   │
│  └──────────────┘    └───────┬────────┘    └───────────────┘   │
│                              │ ▲                               │
└──────────────────────────────┼─┼───────────────────────────────┘
                               │ │ WebSocket (ws://host/ws/interview)
                               │ │ 帧协议: JSON 控制帧 + Binary 音频帧
┌──────────────────────────────┼─┼───────────────────────────────┐
│                    Spring Boot 后端                             │
│  ┌───────────────────────────┴─┴──────────────────────────────┐│
│  │              WebSocket 处理器                               ││
│  │  ┌──────────────┐  ┌───────────┐  ┌────────────────────┐   ││
│  │  │ 音频流接收器   │  │ 会话管理器  │  │ 消息分发器          │   ││
│  │  └──────┬───────┘  └───────────┘  └────┬───────────────┘   ││
│  └─────────┼──────────────────────────────┼────────────────────┘│
│            │                              │                     │
│  ┌─────────▼──────────┐    ┌──────────────▼───────────────┐    │
│  │  ASR 服务           │    │  AI 面试引擎                   │    │
│  │  (流式语音识别)      │    │  (DeepSeek + 上下文管理)        │    │
│  │  ┌───────────────┐  │    │  ┌────────────────────────┐   │    │
│  │  │ 百度 ASR       │  │    │  │ Prompt 构建 + 流式输出   │   │    │
│  │  │ (WebSocket版)  │  │    │  └───────────┬────────────┘   │    │
│  │  └───────┬───────┘  │    └──────────────┼────────────────┘    │
│  └──────────┼──────────┘                   │                     │
│             │                              │                     │
│  ┌──────────▼──────────────────────────────▼───────────────┐    │
│  │  TTS 服务 (文本→语音，并发合成)                          │    │
│  │  ┌────────────────────────────────────────────────────┐  │    │
│  │  │ 百度 TTS (REST，按句子并行合成)                      │  │    │
│  │  └────────────────────────────────────────────────────┘  │    │
│  └──────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────┘
```

### 2.2 核心流程

```
用户:   ──音频流──▶  ──音频流──▶  ──音频流──▶  [停顿检测]
后端ASR:              ──中间结果──▶  ──中间结果──▶  ──最终结果──▶
后端AI:                                               ──流式回复──▶
后端TTS(并发):                                          ──每句音频──▶
前端显示:  [实时字幕更新]                         [完整文字]  [自动播放语音]
```

### 2.3 关键设计决策

| 决策点 | 选择 | 理由 |
|---|---|---|
| 通信协议 | **WebSocket**（原生，非 STOMP） | 需要同时传输二进制音频 + JSON 控制消息，STOMP 不支持二进制 |
| ASR 方案 | **百度实时语音识别**（WebSocket API） | 保持百度体系，无需新增 AI 供应商，百度有完整的 WebSocket ASR API |
| TTS 方案 | **百度 TTS**（REST API）+ **句子级并发** | 百度 TTS 虽然 REST，但合成速度快（~300ms/句），按句子并行可掩盖延迟 |
| 停顿检测 | **服务端 VAD**（基于静音检测） | 简单可靠，无需额外 AI 成本 |
| 音频格式 | **16kHz 单声道 16bit PCM** | 与百度 ASR 兼容，浏览器可直接采集 |

---

## 3. 详细技术方案

### 3.1 WebSocket 通信协议设计

#### 连接端点
```
ws://localhost:8080/ws/voice-interview/{sessionId}?token={jwt}
```

#### 帧格式

**客户端 → 服务端（音频数据帧）：**

```
Binary 帧:
┌─────────┬──────────────────────────────┐
│  头部   │          音频负载              │
│  4 byte  │        N byte (PCM)          │
└─────────┴──────────────────────────────┘

头部: [序号(2byte,BE) | 标志(1byte) | 保留(1byte)]
  标志: 0x01 = 音频中间帧
       0x02 = 音频结束帧（用户停顿/结束说话）
```

**客户端 → 服务端（控制帧）：**

```json
// 控制帧 - 暂停
{"type": "pause"}
// 控制帧 - 恢复
{"type": "resume"}
// 控制帧 - 结束面试
{"type": "end"}
```

**服务端 → 客户端（消息帧）：**

```json
// ASR 中间结果（实时字幕）
{"type": "asr_interim", "text": "我想先谈谈Java的", "isFinal": false}

// ASR 最终结果
{"type": "asr_final", "text": "我想先谈谈Java的并发编程模型", "confidence": 0.92}

// AI 回复开始
{"type": "ai_start", "question": "请解释Java并发编程模型"}

// AI 回复流式内容（实时文字）
{"type": "ai_chunk", "text": "Java的并发编程模型主要包括..."}

// AI 回复结束（附加 TTS 信息）
{"type": "ai_end", "fullText": "..."}

// TTS 音频数据
Binary 帧 (type=0x03):
┌─────────┬──────────┬────────────────┐
│  头部   │  句子ID   │   MP3 音频数据  │
│  4 byte │  2 byte   │    N byte      │
└─────────┴──────────┴────────────────┘
```

### 3.2 前端实现

#### 新增/修改文件

```
frontend/src/
├── composables/
│   └── useVoiceWebSocket.js     # WebSocket 连接管理（新增）
├── utils/
│   └── audio.js                 # 增强：添加实时音频流采集
├── views/
│   └── interview/
│       └── session/
│           ├── index.vue        # 修改：增加"语音模式"切换
│           └── components/
│               ├── VoiceRecorder.vue  # 修改：支持实时流式录音
│               └── VoiceChatPanel.vue # 新增：语音对话面板
```

#### 核心逻辑：WebSocket 客户端

```javascript
// composables/useVoiceWebSocket.js
import { ref, onBeforeUnmount } from 'vue'

export function useVoiceWebSocket(sessionId) {
  const ws = ref(null)
  const connected = ref(false)
  const interimText = ref('')    // ASR 中间结果
  const finalText = ref('')      // ASR 最终结果
  const aiStreamText = ref('')   // AI 流式回复
  const isAiSpeaking = ref(false)
  const isRecording = ref(false)
  const audioContext = ref(null)
  const mediaStream = ref(null)

  let reconnectTimer = null
  let sendInterval = null
  const SEND_INTERVAL_MS = 200   // 每 200ms 发送一次音频

  // 连接 WebSocket
  function connect(token) {
    const url = `ws://localhost:8080/ws/voice-interview/${sessionId}?token=${token}`
    ws.value = new WebSocket(url)
    ws.value.binaryType = 'arraybuffer'

    ws.value.onopen = () => {
      connected.value = true
      console.log('[VoiceWS] 连接成功')
    }

    ws.value.onmessage = (event) => {
      if (event.data instanceof ArrayBuffer) {
        handleBinaryMessage(event.data)
      } else {
        handleTextMessage(JSON.parse(event.data))
      }
    }

    ws.value.onclose = () => {
      connected.value = false
      // 自动重连（最多 3 次）
      attemptReconnect(token)
    }

    ws.value.onerror = (err) => {
      console.error('[VoiceWS] 连接错误', err)
    }
  }

  // 处理文本消息（ASR 结果、AI 回复）
  function handleTextMessage(msg) {
    switch (msg.type) {
      case 'asr_interim':
        interimText.value = msg.text
        break
      case 'asr_final':
        finalText.value = msg.text
        interimText.value = ''
        break
      case 'ai_start':
        isAiSpeaking.value = true
        aiStreamText.value = ''
        break
      case 'ai_chunk':
        aiStreamText.value += msg.text
        break
      case 'ai_end':
        isAiSpeaking.value = false
        // 完整回答追加到消息列表
        appendAiMessage(msg.fullText, msg.ttsSentences)
        break
    }
  }

  // 处理二进制消息（TTS 音频）
  function handleBinaryMessage(data) {
    const view = new DataView(data)
    const type = view.getUint8(0)
    if (type === 0x03) { // TTS 音频
      const sentenceId = view.getUint16(1)
      const audioData = data.slice(3)
      playTtsAudio(sentenceId, audioData)
    }
  }

  // 开始录音 → 实时发送音频流
  async function startRecording() {
    mediaStream.value = await navigator.mediaDevices.getUserMedia({
      audio: {
        sampleRate: 16000,
        channelCount: 1,
        echoCancellation: true,
        noiseSuppression: true
      }
    })

    audioContext.value = new AudioContext({ sampleRate: 16000 })
    const source = audioContext.value.createMediaStreamSource(mediaStream.value)
    const processor = audioContext.value.createScriptProcessor(4096, 1, 1)

    source.connect(processor)
    processor.connect(audioContext.value.destination)

    let sequence = 0
    processor.onaudioprocess = (event) => {
      if (ws.value?.readyState !== WebSocket.OPEN) return

      const input = event.inputBuffer.getChannelData(0)
      // Float32 → Int16 PCM
      const pcmData = new Int16Array(input.length)
      for (let i = 0; i < input.length; i++) {
        const s = Math.max(-1, Math.min(1, input[i]))
        pcmData[i] = s < 0 ? s * 0x8000 : s * 0x7FFF
      }

      // 组装二进制帧：头部(4byte) + PCM数据
      const header = new ArrayBuffer(4)
      const headerView = new DataView(header)
      headerView.setUint16(0, sequence++, true)  // 序号
      headerView.setUint8(2, 0x01)               // 标志：中间帧

      const combined = new Blob([header, pcmData.buffer])
      ws.value.send(combined)
    }

    isRecording.value = true
  }

  // 停止录音
  function stopRecording() {
    if (audioContext.value) {
      audioContext.value.close()
      audioContext.value = null
    }
    if (mediaStream.value) {
      mediaStream.value.getTracks().forEach(t => t.stop())
      mediaStream.value = null
    }
    // 发送结束帧
    const header = new ArrayBuffer(4)
    const view = new DataView(header)
    view.setUint8(2, 0x02)  // 结束帧
    ws.value?.send(header)

    isRecording.value = false
  }

  function disconnect() {
    if (ws.value) {
      ws.value.close()
      ws.value = null
    }
    stopRecording()
  }

  onBeforeUnmount(() => disconnect())

  return {
    connected, interimText, finalText, aiStreamText,
    isAiSpeaking, isRecording,
    connect, startRecording, stopRecording, disconnect
  }
}
```

#### 音频采集优化

当前 `audio.js` 使用 `MediaRecorder`（块输出），需要新增**实时音频流采集**能力：

```javascript
// audio.js 新增
/**
 * 创建实时音频流处理器
 * 每隔 SEND_INTERVAL_MS 回调一次 PCM 数据
 */
export function createAudioStreamProcessor(stream, onPcmData) {
  const audioContext = new AudioContext({ sampleRate: 16000 })
  const source = audioContext.createMediaStreamSource(stream)
  // 使用 AudioWorklet（现代方案）或 ScriptProcessor（兼容方案）
  // 此处使用 ScriptProcessor 简化
  const processor = audioContext.createScriptProcessor(4096, 1, 1)

  source.connect(processor)
  processor.connect(audioContext.destination)

  processor.onaudioprocess = (event) => {
    const input = event.inputBuffer.getChannelData(0)
    const pcmData = float32ToInt16(input)
    onPcmData(pcmData)
  }

  return {
    close: () => {
      source.disconnect()
      processor.disconnect()
      audioContext.close()
    }
  }
}
```

### 3.3 后端实现

#### 新增依赖（pom.xml）

```xml
<!-- WebSocket -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>

<!-- 百度 WebSocket ASR 需要 Java WebSocket 客户端 -->
<dependency>
    <groupId>org.java-websocket</groupId>
    <artifactId>Java-WebSocket</artifactId>
    <version>1.5.7</version>
</dependency>
```

#### 新增模块结构

```
backend/src/main/java/org/backend/
├── config/
│   └── VoiceWebSocketConfig.java      # WebSocket 端点注册
├── controller/
│   └── VoiceInterviewController.java   # REST 入口（创建语音面试）
└── voice/                             # 语音面试模块（新增包）
    ├── handler/
    │   └── VoiceInterviewHandler.java  # WebSocket 核心处理器
    ├── service/
    │   ├── VoiceSessionManager.java    # 语音面试会话管理器
    │   ├── VoiceAsrService.java        # 百度流式 ASR 封装
    │   ├── VoiceTtsService.java        # 百度 TTS 封装（句子级并发）
    │   └── VoiceAiService.java         # AI 回复生成（对接现有 AIService）
    └── model/
        ├── VoiceMessage.java           # 消息类型枚举
        ├── VoiceSession.java           # 语音会话状态
        └── TtsSentence.java            # TTS 句子单元
```

#### WebSocket 配置

```java
// VoiceWebSocketConfig.java
@Configuration
public class VoiceWebSocketConfig {

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(64 * 1024);
        container.setMaxBinaryMessageBufferSize(256 * 1024);
        container.setMaxSessionIdleTimeout(30 * 60 * 1000L); // 30 分钟
        return container;
    }

    @Bean
    public WebSocketHandler voiceInterviewHandler() {
        return new VoiceInterviewHandler();
    }
}
```

```java
// 在 WebConfig 中注册 WebSocket 端点
@Configuration
public class WebConfig implements WebSocketConfigurer {
    // ... 现有配置 ...

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(voiceInterviewHandler(), "/ws/voice-interview/{sessionId}")
                .setAllowedOrigins("*")
                .addInterceptors(new VoiceAuthInterceptor());
    }
}
```

#### WebSocket 核心处理器

```java
// VoiceInterviewHandler.java
@Component
public class VoiceInterviewHandler extends TextWebSocketHandler
    implements BinaryWebSocketHandler {

    @Autowired
    private VoiceSessionManager sessionManager;
    @Autowired
    private VoiceAsrService asrService;
    @Autowired
    private VoiceAiService aiService;
    @Autowired
    private VoiceTtsService ttsService;

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = getSessionId(session.getUri());
        Long userId = validateToken(getToken(session));
        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        sessions.put(session.getId(), session);
        sessionManager.createSession(sessionId, userId);

        // 初始化百度 ASR WebSocket 连接
        asrService.connect(sessionId, asrResult -> {
            // ASR 结果回调，发送给前端
            sendToClient(session, asrResult);
        });

        log.info("语音面试连接建立: sessionId={}, userId={}", sessionId, userId);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        byte[] payload = message.getPayload().array();
        if (payload.length < 4) return;

        // 解析头部
        int sequence = ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
        byte flag = payload[2];

        if (flag == 0x02) {
            // 音频结束帧 → 用户说完话
            String sessionId = getSessionId(session.getUri());
            asrService.finalize(sessionId); // 触发 ASR 最终识别

            // ASR 最终结果到达后，自动触发 AI 回复
            asrService.onFinalResult(sessionId, text -> {
                // 保存用户语音消息
                saveVoiceMessage(sessionId, "user", text);

                // 调用 AI（使用现有 AI 引擎）
                String aiReply = aiService.generateReply(sessionId, text);

                // AI 回复逐句 TTS 并发合成
                List<String> sentences = splitSentences(aiReply);
                List<CompletableFuture<Void>> ttsFutures = new ArrayList<>();

                for (int i = 0; i < sentences.size(); i++) {
                    final int sentenceIdx = i;
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        byte[] audio = ttsService.synthesize(sentences.get(sentenceIdx));
                        if (audio != null) {
                            // TTS 完成后，发送音频到前端
                            sendTtsToClient(session, sentenceIdx, audio);
                        }
                    });
                    ttsFutures.add(future);
                }

                // 所有 TTS 完成后 + 少量缓冲
                CompletableFuture.allOf(ttsFutures.toArray(new CompletableFuture[0]))
                    .thenRun(() -> sendToClient(session, new VoiceMessage("ai_tts_done")));
            });
        } else {
            // 音频数据帧 → 转发到百度 ASR
            byte[] audioData = Arrays.copyOfRange(payload, 4, payload.length);
            String sessionId = getSessionId(session.getUri());
            asrService.sendAudio(sessionId, audioData);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        VoiceMessage msg = parseMessage(message.getPayload());
        switch (msg.getType()) {
            case "pause" -> sessionManager.pause(sessionId);
            case "resume" -> sessionManager.resume(sessionId);
            case "end" -> handleEndInterview(session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = getSessionId(session.getUri());
        asrService.disconnect(sessionId);
        sessionManager.destroySession(sessionId);
        sessions.remove(session.getId());
    }
}
```

#### 百度流式 ASR 封装

```java
// VoiceAsrService.java
@Service
public class VoiceAsrService {

    // 百度实时语音识别 WebSocket 端点
    private static final String BAIDU_ASR_WS_URL =
        "wss://vop.baidu.com/realtime_asr?sn=ai_interview&role=user";

    private final Map<String, WebSocket> asrConnections = new ConcurrentHashMap<>();
    private final Map<String, Consumer<VoiceMessage>> resultCallbacks = new ConcurrentHashMap<>();
    private final Map<String, Consumer<String>> finalResultCallbacks = new ConcurrentHashMap<>();

    /**
     * 建立百度 ASR WebSocket 连接
     */
    public void connect(String sessionId, Consumer<VoiceMessage> onResult) {
        resultCallbacks.put(sessionId, onResult);

        String token = baiduSpeechService.getAccessToken();
        String url = BAIDU_ASR_WS_URL + "&token=" + token;

        WebSocket asrWs = new WebSocket(new URI(url)) {
            @Override
            public void onMessage(String message) {
                handleBaiduResponse(sessionId, message);
            }
            @Override
            public void onOpen(ServerHandshake handshake) {
                // 发送开始帧
                send("{\"type\":\"START\",\"appid\":\"" + APP_ID + "\",\"cmd\":" +
                    "\"realtime_asr\",\"language\":\"zh\",\"rate\":16000}");
            }
            @Override
            public void onClose(int code, String reason, boolean remote) { }
            @Override
            public void onError(Exception ex) { }
        };

        asrWs.connect();
        asrConnections.put(sessionId, asrWs);
    }

    /**
     * 发送音频数据到百度 ASR
     */
    public void sendAudio(String sessionId, byte[] pcmData) {
        WebSocket asrWs = asrConnections.get(sessionId);
        if (asrWs != null && asrWs.isOpen()) {
            // 百度 ASR 帧格式
            JSONObject frame = new JSONObject();
            frame.put("type", "MID_FRAME");
            frame.put("data", Base64.getEncoder().encodeToString(pcmData));
            asrWs.send(frame.toString());
        }
    }

    /**
     * 触发 ASR 最终识别结果
     */
    public void finalize(String sessionId) {
        WebSocket asrWs = asrConnections.get(sessionId);
        if (asrWs != null && asrWs.isOpen()) {
            JSONObject end = new JSONObject();
            end.put("type", "END_FRAME");
            asrWs.send(end.toString());
        }
    }

    /**
     * 处理百度 ASR 的返回结果
     */
    private void handleBaiduResponse(String sessionId, String response) {
        JSONObject json = new JSONObject(response);
        String type = json.optString("type");

        if ("MID_TEXT".equals(type)) {
            // 中间结果 → 实时字幕
            String text = json.optString("text");
            VoiceMessage msg = new VoiceMessage("asr_interim", text);
            Consumer<VoiceMessage> cb = resultCallbacks.get(sessionId);
            if (cb != null) cb.accept(msg);

        } else if ("FIN_TEXT".equals(type)) {
            // 最终结果
            String text = json.optString("text");
            VoiceMessage msg = new VoiceMessage("asr_final", text);
            Consumer<VoiceMessage> cb = resultCallbacks.get(sessionId);
            if (cb != null) cb.accept(msg);

            // 单独通知最终结果回调（触发 AI 回复）
            Consumer<String> finalCb = finalResultCallbacks.get(sessionId);
            if (finalCb != null && !text.isEmpty()) {
                finalCb.accept(text);
            }
        }
    }
}
```

#### TTS 句子级并发合成

```java
// VoiceTtsService.java
@Service
public class VoiceTtsService {

    private static final int MAX_CONCURRENT_TTS = 3; // 最多并发合成 3 句

    /**
     * 合成单个句子 → MP3 字节
     */
    public byte[] synthesize(String sentence) {
        if (sentence == null || sentence.trim().isEmpty()) return null;
        // 复用现有的 BaiduSpeechService.textToSpeech()
        return baiduSpeechService.textToSpeech(sentence.trim());
    }

    /**
     * 将 AI 回复拆句 + 并行合成
     * 返回句子列表（含音频），保持原始顺序
     */
    public List<TtsSentence> synthesizeWithConcurrency(String fullText) {
        List<String> sentences = splitByPunctuation(fullText);
        List<TtsSentence> results = Collections.synchronizedList(new ArrayList<>());

        // 使用线程池并行合成（限制并发数）
        ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_TTS);
        CountDownLatch latch = new CountDownLatch(sentences.size());

        for (int i = 0; i < sentences.size(); i++) {
            final int index = i;
            final String sentence = sentences.get(i);
            executor.submit(() -> {
                try {
                    long start = System.currentTimeMillis();
                    byte[] audio = synthesize(sentence);
                    long elapsed = System.currentTimeMillis() - start;
                    log.debug("TTS 句子{}合成完成: {}ms, 文本={}", index, elapsed,
                        sentence.substring(0, Math.min(20, sentence.length())));

                    TtsSentence tts = new TtsSentence(index, sentence, audio);
                    results.add(tts);
                } catch (Exception e) {
                    log.warn("TTS 句子{}合成失败: {}", index, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(15, TimeUnit.SECONDS); // 最多等 15s
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor.shutdownNow();

        // 按原始顺序排序返回
        results.sort(Comparator.comparingInt(TtsSentence::getIndex));
        return results;
    }
}
```

### 3.4 新表设计

```sql
-- 语音面试会话表
CREATE TABLE IF NOT EXISTS voice_session (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    user_id         BIGINT        NOT NULL,
    position        VARCHAR(50)   NOT NULL,
    round           VARCHAR(30)   NOT NULL,
    difficulty      VARCHAR(20)   NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'in_progress', -- in_progress / completed / cancelled
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 语音消息表（保留完整对话记录）
CREATE TABLE IF NOT EXISTS voice_message (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    session_id      BIGINT        NOT NULL,
    role            VARCHAR(10)   NOT NULL,     -- user / ai
    content         TEXT          NOT NULL,
    asr_confidence  DECIMAL(5,2)  DEFAULT NULL, -- ASR 置信度（仅用户消息）
    duration_ms     INT           DEFAULT 0,    -- 用户说话时长（ms）
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_session_id (session_id),
    KEY idx_session_created (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 4. 用户体验流程

### 4.1 语音模式入口

在面试配置页面新增**"语音模式"开关**：

```
面试配置页:
┌────────────────────────────────────┐
│  [职位] Java后端                    │
│  [轮次] 技术面                      │
│  [难度] 中等                        │
│  [题目数] 5                         │
│  ✔ 启用语音对话模式                  │  ← 新增开关
│  [开始面试]                         │
└────────────────────────────────────┘
```

### 4.2 语音对话界面

新增语音模式下的对话界面（与文本模式可切换）：

```
┌──────────────────────────────────────────┐
│  AI模拟面试 · 语音模式   [切换文字] [结束]  │
├──────────────────────────────────────────┤
│                                          │
│  AI: 请解释Java的并发编程模型              │
│                                          │
│  ┌──────────────────────────────────────┐│
│  │ 我想先谈谈Java的...                   ││  ← AI 回复先显示文字
│  │ 我想先谈谈Java的线程模型               ││  ← 实时 ASR 逐字出现
│  │                                      ││
│  │ 实时字幕: Java的并发编程模...          ││  ← ASR 中间结果
│  └──────────────────────────────────────┘│
│                                          │
│  ┌──────────────────────────────────────┐│
│  │                                      ││
│  │ AI 回复文字流...                      ││  ← AI 流式文字
│  │ 🔊 当前正在播报...                    ││  ← TTS 状态
│  │                                      ││
│  └──────────────────────────────────────┘│
│                                          │
│  ┌───────────────────────────┐           │
│  │  ● 说话中 0:12  ████████  │           │  ← 录音按钮自动控制
│  └───────────────────────────┘           │
│  提示: 直接说话即可，AI会自动回应          │
└──────────────────────────────────────────┘
```

### 4.3 交互状态机

用户状态流转：

```
[空闲] → AI说完话 → [可说话] → 检测到音量 → [说话中]
    ↑                          ↓
    └── ASR完成+AI回复中 ──────┘
    ↑                          ↓
    └──── AI说完 ─── [等待] ←──┘
```

### 4.4 VAD（语音活动检测）策略

服务端简单 VAD 实现：

```java
/**
 * VAD 检测：基于音频能量和静音时长
 * 连续 N 帧低于阈值 → 判定用户说完话
 */
public class SimpleVAD {
    private static final double ENERGY_THRESHOLD = 0.02;  // 能量阈值
    private static final int SILENCE_FRAMES = 15;          // 连续 15 帧静音（约 600ms 16kHz）
    private int silenceCounter = 0;
    private boolean isSpeaking = false;

    public VadResult process(short[] pcmFrame) {
        double energy = computeEnergy(pcmFrame);
        boolean hasVoice = energy > ENERGY_THRESHOLD;

        if (hasVoice) {
            silenceCounter = 0;
            if (!isSpeaking) {
                isSpeaking = true;
                return VadResult.SPEECH_START;
            }
            return VadResult.SPEECHING;
        } else {
            if (isSpeaking) {
                silenceCounter++;
                if (silenceCounter >= SILENCE_FRAMES) {
                    isSpeaking = false;
                    silenceCounter = 0;
                    return VadResult.SPEECH_END;
                }
            }
            return VadResult.SILENCE;
        }
    }

    private double computeEnergy(short[] samples) {
        double sum = 0;
        for (short s : samples) sum += Math.abs(s / 32768.0);
        return sum / samples.length;
    }
}
```

---

## 5. 可选替代方案

### 方案 A（推荐）：百度 WebSocket ASR + REST TTS
- **优点**：与现有系统一致，百度有免费额度，文档完善
- **缺点**：百度 TTS 不是流式，需要句子级并发掩盖

### 方案 B：替换为阿里云通义千问语音
- **优点**：ASR/TTS 都支持 WebSocket 全双工，延迟更低，音质更好
- **缺点**：需要新增 SDK（aliyun-sdk-dashscope），增加依赖
- **参考**：InterviewGuide 项目已完整实现此方案

### 方案 C：WebRTC + 本地语音活动检测
- **优点**：客户端 VAD 减轻服务端压力，中断更灵敏
- **缺点**：前端复杂性大增，需要使用 `onnxruntime-web` 加载 VAD 模型

---

## 6. 开发计划

| 阶段 | 内容 | 预计工时 |
|---|---|---|
| **Phase 1** | WebSocket 基础设施：端点注册、认证、心跳、断线重连 | 2 天 |
| **Phase 2** | 前端实时音频采集 + WebSocket 发送 | 2 天 |
| **Phase 3** | 百度流式 ASR 集成 | 2 天 |
| **Phase 4** | AI 自动回复链路（ASR → AI 引擎联动） | 1 天 |
| **Phase 5** | 句子级并发 TTS + 前端播放 | 2 天 |
| **Phase 6** | VAD 停顿检测 + 打断逻辑 | 1 天 |
| **Phase 7** | 语音面试记录持久化 + 报告 | 1 天 |
| **Phase 8** | 集成测试 + 边界情况处理 | 2 天 |
| **总计** | | **13 天** |

---

## 7. 现有代码复用与改造

### 可直接复用的组件

| 现有代码 | 复用方式 |
|---|---|
| `BaiduSpeechService.textToSpeech()` | TTS 合成直接复用 |
| `AIService.evaluateAndRespond()` | AI 回复生成直接复用 |
| `PromptBuilder.buildInterviewSystemPrompt()` | 面试 Prompt 直接复用 |
| `ScoringEngine` | 评估报告生成直接复用 |
| `InterviewService.endInterview()` | 结束面试逻辑复用 |
| `InterviewContextService` | 上下文管理复用 |

### 需要改造的组件

| 现有代码 | 改造方式 |
|---|---|
| `BaiduSpeechService` | 新增 WebSocket 流式 ASR 方法，保留原有 REST 方法作为降级 |
| `AIService` | 新增支持语音上下文（加入"语音"标记到 prompt） |
| `SpeechController` | 保留 REST 接口作为降级/备用 |

### 不修改、新增的组件

| 组件 | 说明 |
|---|---|
| `VoiceInterviewHandler` | 新增，WebSocket 核心处理 |
| `VoiceAsrService` | 新增，百度流式 ASR 封装 |
| `VoiceTtsService` | 新增，并发 TTS 调度 |
| `VoiceAiService` | 新增，AI 回复的语音场景封装 |
| `VoiceSessionManager` | 新增，语音会话生命周期管理 |
| `useVoiceWebSocket.js` | 新增，前端 WebSocket 客户端 |
| `VoiceChatPanel.vue` | 新增，语音对话 UI |

---

## 8. 边界情况处理

| 场景 | 处理策略 |
|---|---|
| **网络断开** | WebSocket 自动重连（最多 3 次，间隔 1s/2s/4s），重连后恢复上下文 |
| **用户不说话** | 15 秒超时自动提示，30 秒无操作自动结束 |
| **ASR 识别失败** | 返回"未识别到语音，请重试"，不影响对话流程 |
| **AI 生成中断** | 熔断器降级（复用现有机制），提示用户稍后重试 |
| **TTS 合成失败** | 跳过音频，仅显示文字，不影响对话继续 |
| **用户打断 AI** | 停止当前 TTS 播放，新语音直接进入 ASR，AI 上下文保留 |
| **长句处理** | AI 回复超过 500 字符时，分批 TTS 合成 + 顺序播放 |
| **并发说话** | 服务端状态机确保同一时刻只有一个流方向（用户说 / AI说） |
