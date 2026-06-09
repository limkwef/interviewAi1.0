package org.backend.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.backend.exception.BusinessException;
import org.backend.service.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 百度语音服务 — ASR 语音识别 + TTS 语音合成
 *
 * 职责：调用百度语音 REST API，管理 Access Token 缓存
 * 不包含：业务逻辑、Controller 层参数校验
 */
@Service
public class BaiduSpeechService {

    private static final Logger logger = LoggerFactory.getLogger(BaiduSpeechService.class);

    @Value("${baidu.speech.api-key:}")
    private String apiKey;

    @Value("${baidu.speech.secret-key:}")
    private String secretKey;

    @Value("${baidu.speech.tts.per:4}")
    private int defaultPer;

    @Value("${baidu.speech.tts.spd:5}")
    private int defaultSpd;

    @Value("${baidu.speech.tts.pit:5}")
    private int defaultPit;

    @Value("${baidu.speech.tts.vol:5}")
    private int defaultVol;

    @Value("${baidu.speech.asr.dev-pid:1537}")
    private int defaultDevPid;

    @Value("${baidu.speech.asr.format:pcm}")
    private String defaultFormat;

    @Value("${baidu.speech.asr.rate:16000}")
    private int defaultRate;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TOKEN_CACHE_KEY = "baidu:speech:access_token";
    private static final String ASR_URL = "https://vop.baidu.com/server_api";
    private static final String TTS_URL = "https://tsn.baidu.com/text2audio";
    private static final String TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";
    private static final String CUID = "ai-interview-system";

    /** 单例 HttpClient，复用连接 */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // ======================== Access Token 管理 ========================

    /**
     * 获取 Access Token（缓存到 Redis，有效期 30 天）
     * 缓存策略：提前 1 天刷新，避免过期
     */
    public String getAccessToken() {
        // 1. 从 Redis 获取
        try {
            String cached = cacheService.get(TOKEN_CACHE_KEY, String.class);
            if (cached != null && !cached.isEmpty()) {
                return cached;
            }
        } catch (Exception e) {
            logger.warn("Redis 读取 Token 失败，将从百度获取: {}", e.getMessage());
        }

        // 2. 从百度获取
        String token = fetchAccessTokenFromBaidu();

        // 3. 缓存到 Redis（设置 29 天过期，token 有效期 30 天）
        try {
            cacheService.set(TOKEN_CACHE_KEY, token, 29 * 24 * 3600L);
        } catch (Exception e) {
            logger.warn("Redis 缓存 Token 失败: {}", e.getMessage());
        }

        return token;
    }

    /**
     * 从百度 OAuth API 获取 Access Token
     */
    private String fetchAccessTokenFromBaidu() {
        try {
            String url = TOKEN_URL
                    + "?grant_type=client_credentials"
                    + "&client_id=" + apiKey
                    + "&client_secret=" + secretKey;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            JsonNode root = objectMapper.readTree(response.body());
            String token = root.path("access_token").asText();
            if (token == null || token.isEmpty()) {
                throw new BusinessException(500, "获取百度 Access Token 失败: "
                        + root.path("error_description").asText("未知错误"));
            }
            logger.info("百度 Access Token 获取成功");
            return token;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "百度 Token 请求异常: " + e.getMessage());
        }
    }

    // ======================== 语音识别 ASR ========================

    /**
     * 语音识别：音频字节 → 文字
     * 使用百度 ASR RAW 方式（二进制流直接放入 Body）
     * 推荐使用 WAV 格式（含头部），百度对 WAV 容错性更好
     *
     * @param audioData  音频二进制数据（推荐带 WAV 头部）
     * @param format     音频格式（pcm/wav/amr/m4a）
     * @param rate       采样率（16000 推荐）
     * @param devPid     语言模型（1537=普通话, 1737=英语）
     * @return 识别结果文字，失败返回 null
     */
    public String speechToText(byte[] audioData, String format, int rate, int devPid) {
        try {
            String token = getAccessToken();

            // 检查音频大小（至少 320 字节 ≈ 10ms 16kHz 单声道 16bit）
            if (audioData == null || audioData.length < 320) {
                logger.warn("ASR 音频数据过小: {} bytes（至少需要 320 字节）",
                        audioData == null ? 0 : audioData.length);
                return null;
            }
            
            // WAV 格式验证
            if ("wav".equalsIgnoreCase(format)) {
                boolean isValid = isValidWav(audioData);
                logger.debug("WAV 格式验证结果: {}, 音频大小: {} bytes", isValid, audioData.length);
                
                if (!isValid) {
                    logger.warn("WAV 文件格式不正确，尝试修复...");
                    byte[] fixedWav = fixWavHeader(audioData);
                    if (fixedWav != null) {
                        audioData = fixedWav;
                        logger.info("WAV 头部修复成功，修复后大小: {} bytes", audioData.length);
                    } else {
                        logger.error("无法修复 WAV 文件格式");
                        return null;
                    }
                }
                
                // 打印 WAV 文件头部信息
                logWavHeader(audioData);
            }

            // 检查 token 是否有效
            if (token == null || token.isEmpty()) {
                logger.error("ASR 失败: Access Token 为空");
                return null;
            }
            
            // 打印 token 信息（脱敏）
            String maskedToken = token.length() > 10 
                    ? token.substring(0, 5) + "..." + token.substring(token.length() - 5)
                    : token;
            logger.info("使用 Access Token: {}", maskedToken);
            
            String url = ASR_URL
                    + "?dev_pid=" + devPid
                    + "&token=" + token
                    + "&cuid=" + CUID;

            logger.info("ASR 请求已发送，音频大小: {} bytes, 请求超时: 10s", audioData.length);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "audio/" + format + ";rate=" + rate)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(audioData))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            String responseBody = response.body();
            logger.info("ASR HTTP 状态码: {}", response.statusCode());
            logger.info("ASR 响应原始数据: {}", responseBody);
            
            JsonNode root = objectMapper.readTree(responseBody);
            int errNo = root.path("err_no").asInt();

            if (errNo == 0) {
                JsonNode resultNode = root.path("result");
                if (resultNode.isArray() && resultNode.size() > 0) {
                    String result = resultNode.get(0).asText();
                    logger.info("ASR 识别成功: {}", result);
                    return result;
                }
                logger.warn("ASR 响应无结果数据");
                return null;
            }

            String errMsg = root.path("err_msg").asText();
            logger.error("ASR 识别失败: err_no={}, err_msg={}, 音频大小={} bytes, format={}, rate={}",
                    errNo, errMsg, audioData.length, format, rate);
            
            // 常见错误码提示
            if (errNo == 3307) {
                logger.error("ASR错误码3307分析: 可能原因包括 - " +
                    "1) 音频格式不正确 2) 音频数据损坏 3) WAV头部不完整 4) 采样率不匹配");
            } else if (errNo == 500) {
                logger.error("ASR错误码500: 服务端内部错误，建议稍后重试");
            } else if (errNo == 3301) {
                logger.error("ASR错误码3301: 音频质量差，静音或噪声过大");
            }
            
            return null;
        } catch (Exception e) {
            logger.error("ASR 调用异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将裸 PCM 数据包装为 WAV 格式
     * WAV 头部包含采样率/位深/通道数等元信息，百度对 WAV 格式容错性更好
     *
     * @param pcmData    16kHz 单声道 16bit PCM 数据
     * @param sampleRate 采样率
     * @return 包含 WAV 头部的完整音频数据
     */
    public static byte[] pcmToWav(byte[] pcmData, int sampleRate) {
        int dataSize = pcmData.length;
        int fileSize = 44 + dataSize;

        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(fileSize);
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);

        // RIFF 头
        buffer.put("RIFF".getBytes());
        buffer.putInt(fileSize - 8);
        buffer.put("WAVE".getBytes());

        // fmt 子块
        buffer.put("fmt ".getBytes());
        buffer.putInt(16);                  // fmt 子块大小
        buffer.putShort((short) 1);         // 音频格式 PCM=1
        buffer.putShort((short) 1);         // 通道数
        buffer.putInt(sampleRate);          // 采样率
        buffer.putInt(sampleRate * 2);      // 字节率 = 采样率 * 通道数 * 位深/8
        buffer.putShort((short) 2);         // 块对齐
        buffer.putShort((short) 16);        // 位深

        // data 子块
        buffer.put("data".getBytes());
        buffer.putInt(dataSize);
        buffer.put(pcmData);

        return buffer.array();
    }

    /**
     * 验证 WAV 文件格式是否正确
     */
    private boolean isValidWav(byte[] data) {
        if (data.length < 44) return false;
        
        // 检查 RIFF 标识
        String riff = new String(data, 0, 4, StandardCharsets.US_ASCII);
        if (!"RIFF".equals(riff)) {
            logger.debug("WAV 验证失败: 不是 RIFF 格式");
            return false;
        }
        
        // 检查 WAVE 标识
        String wave = new String(data, 8, 4, StandardCharsets.US_ASCII);
        if (!"WAVE".equals(wave)) {
            logger.debug("WAV 验证失败: 不是 WAVE 格式");
            return false;
        }
        
        // 检查 fmt 子块
        String fmt = new String(data, 12, 4, StandardCharsets.US_ASCII);
        if (!"fmt ".equals(fmt)) {
            logger.debug("WAV 验证失败: 缺少 fmt 子块");
            return false;
        }
        
        // 检查 PCM 格式（音频格式应为 1）
        int audioFormat = ((data[20] & 0xFF) | ((data[21] & 0xFF) << 8));
        if (audioFormat != 1) {
            logger.debug("WAV 验证失败: 不是 PCM 格式 (format={})", audioFormat);
            return false;
        }
        
        // 检查通道数（应为 1）
        int channels = ((data[22] & 0xFF) | ((data[23] & 0xFF) << 8));
        if (channels != 1) {
            logger.debug("WAV 验证失败: 不是单声道 (channels={})", channels);
            return false;
        }
        
        // 检查采样率（应为 16000）
        int sampleRate = ((data[24] & 0xFF) | ((data[25] & 0xFF) << 8) 
                        | ((data[26] & 0xFF) << 16) | ((data[27] & 0xFF) << 24));
        if (sampleRate != 16000) {
            logger.debug("WAV 验证失败: 采样率不正确 (rate={})", sampleRate);
            return false;
        }
        
        return true;
    }
    
    /**
     * 尝试修复损坏的 WAV 头部
     * 如果 WAV 头部损坏但数据完整，重新生成正确的头部
     */
    private byte[] fixWavHeader(byte[] data) {
        try {
            // 尝试提取 PCM 数据（跳过可能存在的头部）
            int pcmStart = 0;
            
            // 如果有 RIFF 头，找到 data 子块的位置
            if (data.length > 12 && "RIFF".equals(new String(data, 0, 4, StandardCharsets.US_ASCII))) {
                int offset = 12;
                while (offset + 8 < data.length) {
                    String chunkName = new String(data, offset, 4, StandardCharsets.US_ASCII);
                    int chunkSize = ((data[offset + 4] & 0xFF) | ((data[offset + 5] & 0xFF) << 8)
                                   | ((data[offset + 6] & 0xFF) << 16) | ((data[offset + 7] & 0xFF) << 24));
                    
                    if ("data".equals(chunkName)) {
                        pcmStart = offset + 8;
                        break;
                    }
                    offset += 8 + chunkSize;
                }
            }
            
            // 提取 PCM 数据
            byte[] pcmData = new byte[data.length - pcmStart];
            System.arraycopy(data, pcmStart, pcmData, 0, pcmData.length);
            
            // 如果 PCM 数据太少，返回 null
            if (pcmData.length < 320) {
                logger.debug("修复失败: PCM 数据不足");
                return null;
            }
            
            // 重新生成 WAV 头部
            return pcmToWav(pcmData, defaultRate);
            
        } catch (Exception e) {
            logger.debug("修复 WAV 头部失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 打印 WAV 文件头部详细信息
     */
    private void logWavHeader(byte[] data) {
        if (data.length < 44) {
            logger.debug("WAV 头部不完整，长度: {} bytes", data.length);
            return;
        }
        
        try {
            String riff = new String(data, 0, 4, StandardCharsets.US_ASCII);
            int fileSize = ((data[4] & 0xFF) | ((data[5] & 0xFF) << 8) 
                          | ((data[6] & 0xFF) << 16) | ((data[7] & 0xFF) << 24));
            String wave = new String(data, 8, 4, StandardCharsets.US_ASCII);
            String fmt = new String(data, 12, 4, StandardCharsets.US_ASCII);
            int fmtSize = ((data[16] & 0xFF) | ((data[17] & 0xFF) << 8) 
                         | ((data[18] & 0xFF) << 16) | ((data[19] & 0xFF) << 24));
            int audioFormat = ((data[20] & 0xFF) | ((data[21] & 0xFF) << 8));
            int channels = ((data[22] & 0xFF) | ((data[23] & 0xFF) << 8));
            int sampleRate = ((data[24] & 0xFF) | ((data[25] & 0xFF) << 8) 
                           | ((data[26] & 0xFF) << 16) | ((data[27] & 0xFF) << 24));
            int byteRate = ((data[28] & 0xFF) | ((data[29] & 0xFF) << 8) 
                         | ((data[30] & 0xFF) << 16) | ((data[31] & 0xFF) << 24));
            int blockAlign = ((data[32] & 0xFF) | ((data[33] & 0xFF) << 8));
            int bitsPerSample = ((data[34] & 0xFF) | ((data[35] & 0xFF) << 8));
            
            String formatName = audioFormat == 1 ? "PCM" : "未知(" + audioFormat + ")";
            String channelName = channels == 1 ? "单声道" : (channels == 2 ? "立体声" : channels + "声道");
            
            logger.info("========== WAV 文件头部信息 ==========");
            logger.info("文件标识: {} {}", riff, wave);
            logger.info("文件大小: {} bytes", fileSize + 8);
            logger.info("fmt 子块: {} (大小: {} bytes)", fmt, fmtSize);
            logger.info("音频格式: {} (format={})", formatName, audioFormat);
            logger.info("通道数: {} (channels={})", channelName, channels);
            logger.info("采样率: {} Hz", sampleRate);
            logger.info("比特率: {} bytes/sec", byteRate);
            logger.info("块对齐: {} bytes", blockAlign);
            logger.info("位深度: {} bits", bitsPerSample);
            logger.info("=======================================");
            
        } catch (Exception e) {
            logger.debug("解析 WAV 头部失败: {}", e.getMessage());
        }
    }

    /**
     * 语音识别（自动包装 WAV，推荐）
     */
    public String speechToText(byte[] audioData, String format) {
        // 如果是裸 PCM，自动包装为 WAV（百度对 WAV 容错性更好）
        if ("pcm".equalsIgnoreCase(format)) {
            byte[] wavData = pcmToWav(audioData, defaultRate);
            return speechToText(wavData, "wav", defaultRate, defaultDevPid);
        }
        return speechToText(audioData, format, defaultRate, defaultDevPid);
    }

    // ======================== 语音合成 TTS ========================

    /**
     * 语音合成：文字 → MP3 音频字节
     * 使用百度 TTS POST 方式
     *
     * @param text  待合成文本（≤500 字符，超过截断）
     * @param per   发音人（0=度小美, 1=度小宇, 3=度逍遥, 4=度丫丫, 5003=度逍遥精品）
     * @param spd   语速（0-15, 5=正常）
     * @param pit   音调（0-15, 5=正常）
     * @param vol   音量（0-15, 5=正常）
     * @return MP3 音频二进制数据，失败返回 null
     */
    public byte[] textToSpeech(String text, int per, int spd, int pit, int vol) {
        try {
            String token = getAccessToken();
            if (text == null || text.trim().isEmpty()) return null;

            // 截断超长文本（百度 TTS 限制 1024 GBK 字节，建议 120 以内）
            if (text.length() > 500) {
                text = text.substring(0, 500);
            }

            // 两次 URL 编码（百度要求）
            String encodedText = URLEncoder.encode(
                    URLEncoder.encode(text, StandardCharsets.UTF_8), StandardCharsets.UTF_8);

            String formBody = "tok=" + token
                    + "&tex=" + encodedText
                    + "&cuid=" + CUID
                    + "&ctp=1&lan=zh"
                    + "&spd=" + spd
                    + "&pit=" + pit
                    + "&vol=" + vol
                    + "&per=" + per
                    + "&aue=3";  // 3=MP3

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TTS_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<byte[]> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofByteArray());

            // 成功: Content-Type 以 "audio/" 开头
            String contentType = response.headers()
                    .firstValue("Content-Type").orElse("");
            if (contentType.startsWith("audio/")) {
                return response.body();
            }

            // 失败: 返回 JSON 错误信息
            String errorJson = new String(response.body(), StandardCharsets.UTF_8);
            logger.warn("TTS 合成失败: {}", errorJson);
            return null;
        } catch (Exception e) {
            logger.error("TTS 调用异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * TTS 重载方法（面试场景默认参数）
     */
    public byte[] textToSpeech(String text) {
        return textToSpeech(text, defaultPer, defaultSpd, defaultPit, defaultVol);
    }
}
