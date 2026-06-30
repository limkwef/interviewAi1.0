package org.backend.agent.tool;

import org.backend.service.AIDiagnosisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 诊断报告触发工具
 * Agent 在面试结束后触发诊断报告生成
 * 
 * 注意：正常流程中，诊断报告在 InterviewEvaluateService.doEvaluate() 中自动生成。
 * 此工具用于特殊场景（如 Agent 判断需要提前生成诊断报告）。
 */
@Component
public class DiagnosisTriggerTool implements Tool {

    private static final Logger logger = LoggerFactory.getLogger(DiagnosisTriggerTool.class);

    private final AIDiagnosisService diagnosisService;

    public DiagnosisTriggerTool(AIDiagnosisService diagnosisService) {
        this.diagnosisService = diagnosisService;
    }

    @Override
    public String getName() {
        return "diagnosis_trigger";
    }

    @Override
    public String getDescription() {
        return "触发面试诊断报告生成。面试结束后自动调用，生成深度诊断分析。通常不需要手动调用。";
    }

    @Override
    public String getParametersSchema() {
        return "{\"sessionId\": \"面试会话ID(必填)\"}";
    }

    @Override
    public String execute(Map<String, Object> params) {
        Object sessionIdObj = params.get("sessionId");
        if (sessionIdObj == null) {
            return "错误：缺少 sessionId 参数";
        }

        Long sessionId = sessionIdObj instanceof Number
                ? ((Number) sessionIdObj).longValue() : Long.parseLong(sessionIdObj.toString());

        try {
            diagnosisService.generateDiagnosisReport(sessionId);
            logger.info("DiagnosisTriggerTool: 面试{}的诊断报告已触发生成", sessionId);
            return "诊断报告已触发生成，sessionId=" + sessionId;
        } catch (Exception e) {
            logger.error("DiagnosisTriggerTool 执行失败, sessionId={}", sessionId, e);
            return "诊断报告触发失败：" + e.getMessage();
        }
    }
}
