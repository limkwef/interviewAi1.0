package org.backend.agent.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 工具注册中心
 * 自动收集 Spring 容器中所有 Tool 实现，提供按名称查找功能
 */
@Component
public class ToolRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, Tool> toolMap = new LinkedHashMap<>();

    /**
     * Spring 自动注入所有 Tool 实现
     */
    public ToolRegistry(List<Tool> tools) {
        for (Tool tool : tools) {
            toolMap.put(tool.getName(), tool);
            logger.info("注册 Agent 工具: {} — {}", tool.getName(), tool.getDescription());
        }
        logger.info("Agent 工具注册完成，共 {} 个工具", toolMap.size());
    }

    /** 按名称查找工具，不存在返回 null */
    public Tool getTool(String name) {
        return toolMap.get(name);
    }

    /** 获取所有已注册的工具 */
    public List<Tool> getAllTools() {
        return new ArrayList<>(toolMap.values());
    }

    /** 获取所有工具的描述文本（注入到 LLM system prompt 中） */
    public String buildToolDescriptions() {
        if (toolMap.isEmpty()) return "当前没有可用工具。";

        StringBuilder sb = new StringBuilder("你可以使用以下工具：\n\n");
        int index = 1;
        for (Tool tool : toolMap.values()) {
            sb.append(index++).append(". ").append(tool.getName())
              .append(" — ").append(tool.getDescription()).append("\n")
              .append("   参数：").append(tool.getParametersSchema()).append("\n\n");
        }
        return sb.toString();
    }
}
