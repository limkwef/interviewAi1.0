package org.backend.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.backend.entity.AiModel;

import lombok.Data;

/**
 * AI模型配置视图对象（不包含 apiKey 敏感字段）
 */
@Data
public class AiModelVO {
    private Long id;
    private String modelName;
    private String modelCode;
    private String provider;
    private String apiUrl;
    private Integer maxTokens;
    private BigDecimal temperature;
    private Integer supportsStream;
    private Integer supportsStructured;
    private Integer isDefault;
    private Integer isEnabled;
    private String description;
    private Long userId;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 是否存在 API Key（供前端判断是否需要重新输入） */
    private Boolean hasApiKey;

    public static AiModelVO fromEntity(AiModel model) {
        if (model == null) return null;
        AiModelVO vo = new AiModelVO();
        vo.setId(model.getId());
        vo.setModelName(model.getModelName());
        vo.setModelCode(model.getModelCode());
        vo.setProvider(model.getProvider());
        vo.setApiUrl(model.getApiUrl());
        vo.setMaxTokens(model.getMaxTokens());
        vo.setTemperature(model.getTemperature());
        vo.setSupportsStream(model.getSupportsStream());
        vo.setSupportsStructured(model.getSupportsStructured());
        vo.setIsDefault(model.getIsDefault());
        vo.setIsEnabled(model.getIsEnabled());
        vo.setDescription(model.getDescription());
        vo.setUserId(model.getUserId());
        vo.setSortOrder(model.getSortOrder());
        vo.setCreatedAt(model.getCreatedAt());
        vo.setUpdatedAt(model.getUpdatedAt());
        vo.setHasApiKey(model.getApiKey() != null && !model.getApiKey().isBlank());
        return vo;
    }

    public static List<AiModelVO> fromList(List<AiModel> list) {
        if (list == null) return List.of();
        return list.stream().map(AiModelVO::fromEntity).collect(Collectors.toList());
    }
}
