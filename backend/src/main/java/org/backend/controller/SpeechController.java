package org.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.backend.common.Result;
import org.backend.util.BaiduSpeechService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 语音接口控制器
 *
 * ASR: POST /api/speech/asr  — 音频 → 文字
 * TTS: POST /api/speech/tts  — 文字 → 音频
 */
@RestController
@RequestMapping("/api/speech")
public class SpeechController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(SpeechController.class);

    private final BaiduSpeechService baiduSpeechService;

    public SpeechController(BaiduSpeechService baiduSpeechService) {
        this.baiduSpeechService = baiduSpeechService;
    }

    /**
     * ASR：音频 → 文字
     * POST /api/speech/asr
     * Content-Type: multipart/form-data
     * 参数: audio (File), format (String, 可选, 默认 pcm)
     */
    @PostMapping("/asr")
    public Result<Map<String, Object>> asr(
            HttpServletRequest request,
            @RequestParam("audio") MultipartFile audioFile,
            @RequestParam(value = "format", defaultValue = "pcm") String format) {

        Long userId = getUserIdFromToken(request);

        if (audioFile.isEmpty()) {
            return Result.error(400, "音频文件为空");
        }
        // 检查文件大小（最大 2MB，60s PCM ≈ 960KB）
        if (audioFile.getSize() > 2 * 1024 * 1024) {
            return Result.error(400, "音频文件过大，请控制在 2MB 以内");
        }

        try {
            byte[] audioData = audioFile.getBytes();
            String text = baiduSpeechService.speechToText(audioData, format);

            Map<String, Object> data = new HashMap<>();
            data.put("text", text != null ? text : "");
            data.put("format", format);
            return Result.success(data);
        } catch (Exception e) {
            logger.error("语音识别失败", e);
            return Result.error(500, "语音识别失败: " + e.getMessage());
        }
    }

    /**
     * TTS：文字 → 音频
     * POST /api/speech/tts
     * Content-Type: application/json
     * 参数: { text: "...", per?: 4, spd?: 5, pit?: 5, vol?: 5 }
     * 返回: audio/mp3 二进制流
     */
    @PostMapping(value = "/tts", produces = "audio/mp3")
    public ResponseEntity<byte[]> tts(
            HttpServletRequest request,
            @RequestBody Map<String, Object> body) {

        getUserIdFromToken(request);  // 验证身份

        String text = (String) body.get("text");
        if (text == null || text.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        int per = body.containsKey("per") ? ((Number) body.get("per")).intValue() : 4;
        int spd = body.containsKey("spd") ? ((Number) body.get("spd")).intValue() : 5;
        int pit = body.containsKey("pit") ? ((Number) body.get("pit")).intValue() : 5;
        int vol = body.containsKey("vol") ? ((Number) body.get("vol")).intValue() : 5;

        byte[] audioData = baiduSpeechService.textToSpeech(text, per, spd, pit, vol);
        if (audioData == null) {
            return ResponseEntity.internalServerError().build();
        }

        return ResponseEntity.ok()
                .header("Content-Type", "audio/mp3")
                .header("Content-Length", String.valueOf(audioData.length))
                .body(audioData);
    }
}
