package org.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * AI模型配置实体
 * 
 * 普通用户可以添加自己的模型（填写API地址和密钥），也可以使用管理员配好的系统模型。
 * 管理员可以管理系统模型（增删改查、设置默认模型、启用/禁用）。
 */
@Data
public class AiModel {
    /** 主键ID */
    private Long id;

    /** 模型显示名称，如"DeepSeek Chat" */
    private String modelName;

    /** 模型标识，发送给AI API的model参数值，如"deepseek-chat"、"gpt-4o" */
    private String modelCode;

    /** 
     * 供应商标识，决定调用哪个API端点：
     * - deepseek: api.deepseek.com
     * - openai: api.openai.com
     * - aliyun: dashscope.aliyuncs.com
     * - custom: 用户自定义地址
     */
    private String provider;

    /** API请求地址，如"https://api.deepseek.com" */
    private String apiUrl;

    /** API密钥，用于认证，格式如"sk-xxx" */
    private String apiKey;

    /** 已废弃，不再使用 */
    private Integer maxTokens;

    /** 
     * 创造性参数（Temperature），控制AI回答的随机性，取值范围0-2：
     * - 0: 最确定，每次回答几乎一样，适合代码生成、数学计算
     * - 0.3-0.5: 较稳定，略有变化，适合面试评分场景
     * - 0.7: 平衡，有创造性又不失准确，适合通用对话
     * - 1.0+: 最随机，回答发散，适合创意写作
     */
    private BigDecimal temperature;

    /** 是否支持流式输出（SSE），1=支持，0=不支持 */
    private Integer supportsStream;

    /** 是否支持结构化JSON输出，1=支持，0=不支持 */
    private Integer supportsStructured;

    /** 是否为系统默认模型，1=默认，0=非默认。同一时间只能有一个默认模型 */
    private Integer isDefault;

    /** 是否启用，1=启用（用户可见），0=禁用（用户不可见） */
    private Integer isEnabled;

    /** 模型描述信息 */
    private String description;

    /** 
     * 用户ID：
     * - NULL: 系统模型（管理员添加，所有用户可用）
     * - 非NULL: 用户自己的模型（仅该用户可见）
     */
    private Long userId;

    /** 排序序号，数值越小越靠前 */
    private Integer sortOrder;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
