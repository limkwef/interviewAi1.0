package org.backend.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Agent 配置
 * 控制 Agent 循环的行为参数
 */
@Component
public class AgentConfig {

    /** 最大循环迭代次数（防止无限循环） */
    @Value("${agent.max-iterations:5}")
    private int maxIterations;

    /** 单次 LLM 调用超时（秒） */
    @Value("${agent.timeout-seconds:30}")
    private int timeoutSeconds;

    /** 是否启用工具调用（关闭后 Agent 不会调用任何工具） */
    @Value("${agent.tools-enabled:true}")
    private boolean toolsEnabled;

    /** Agent 思考时的 temperature（比普通面试低，更确定性） */
    @Value("${agent.temperature:0.3}")
    private double temperature;

    public int getMaxIterations() { return maxIterations; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public boolean isToolsEnabled() { return toolsEnabled; }
    public double getTemperature() { return temperature; }
}
