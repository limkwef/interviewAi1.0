package org.backend.agent.tool;

import java.util.Map;

/**
 * Agent 工具接口
 * 所有 Agent 可调用的工具都实现此接口
 */
public interface Tool {

    /** 工具名（LLM 可读，英文下划线格式） */
    String getName();

    /** 工具描述（给 LLM 看，说明何时应该调用此工具） */
    String getDescription();

    /** 参数 JSON Schema（给 LLM 看，说明调用时需要哪些参数） */
    String getParametersSchema();

    /** 执行工具，返回文本结果（注入到 LLM 上下文中） */
    String execute(Map<String, Object> params);
}
