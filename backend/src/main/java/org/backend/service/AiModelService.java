package org.backend.service;

import java.util.List;

import org.backend.entity.AiModel;
import org.backend.exception.BusinessException;
import org.backend.mapper.AiModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiModelService {

    private static final Logger logger = LoggerFactory.getLogger(AiModelService.class);

    private final AiModelMapper aiModelMapper;

    @Value("${ai.api-key:}")
    private String defaultApiKey;

    @Value("${ai.api-url:https://api.deepseek.com}")
    private String defaultApiUrl;

    @Value("${ai.model:deepseek-chat}")
    private String defaultModelCode;

    @Value("${ai.max-tokens:2048}")
    private int defaultMaxTokens;

    @Value("${ai.temperature:0.7}")
    private double defaultTemperature;

    public AiModelService(AiModelMapper aiModelMapper) {
        this.aiModelMapper = aiModelMapper;
    }

    /** 获取模型配置（优先数据库，兜底配置文件） */
    public AiModel getModelConfig(Long modelId) {
        if (modelId != null) {
            AiModel model = aiModelMapper.findById(modelId);
            if (model != null && model.getIsEnabled() == 1) return model;
        }
        AiModel defaultModel = aiModelMapper.findDefault();
        if (defaultModel != null) return defaultModel;
        return buildConfigFallback();
    }

    /** 获取默认模型 */
    public AiModel getDefaultModel() {
        AiModel defaultModel = aiModelMapper.findDefault();
        if (defaultModel != null) return defaultModel;
        return buildConfigFallback();
    }

    /** 配置文件兜底 */
    private AiModel buildConfigFallback() {
        AiModel fallback = new AiModel();
        fallback.setId(null);
        fallback.setModelName("DeepSeek Chat (配置文件)");
        fallback.setModelCode(defaultModelCode);
        fallback.setProvider("deepseek");
        fallback.setApiUrl(defaultApiUrl);
        fallback.setApiKey(defaultApiKey);
        fallback.setMaxTokens(defaultMaxTokens);
        fallback.setTemperature(new java.math.BigDecimal(String.valueOf(defaultTemperature)));
        fallback.setSupportsStream(1);
        fallback.setSupportsStructured(1);
        return fallback;
    }

    public List<AiModel> getAll() {
        return aiModelMapper.findAll();
    }

    public List<AiModel> getEnabled() {
        return aiModelMapper.findEnabled();
    }

    public AiModel getById(Long id) {
        return aiModelMapper.findById(id);
    }

    public AiModel create(AiModel model) {
        if (model.getIsDefault() == 1) {
            aiModelMapper.clearDefault();
        }
        model.setIsEnabled(model.getIsEnabled() != null ? model.getIsEnabled() : 1);
        model.setSupportsStream(model.getSupportsStream() != null ? model.getSupportsStream() : 1);
        model.setSupportsStructured(model.getSupportsStructured() != null ? model.getSupportsStructured() : 1);
        model.setSortOrder(model.getSortOrder() != null ? model.getSortOrder() : 0);
        aiModelMapper.insert(model);
        return model;
    }

    public AiModel update(Long id, AiModel model) {
        AiModel existing = aiModelMapper.findById(id);
        if (existing == null) throw new BusinessException(404, "模型不存在");

        model.setId(id);
        // apiKey 为空时保留原值（前端编辑时留空表示不修改，避免误清空密钥）
        if (model.getApiKey() == null || model.getApiKey().isBlank()) {
            model.setApiKey(existing.getApiKey());
        }
        if (model.getIsDefault() != null && model.getIsDefault() == 1) {
            aiModelMapper.clearDefault();
        }
        aiModelMapper.update(model);
        return aiModelMapper.findById(id);
    }

    public void delete(Long id) {
        AiModel existing = aiModelMapper.findById(id);
        if (existing == null) throw new BusinessException(404, "模型不存在");
        aiModelMapper.deleteById(id);
    }

    public void setDefault(Long id) {
        AiModel existing = aiModelMapper.findById(id);
        if (existing == null) throw new BusinessException(404, "模型不存在");
        aiModelMapper.setDefault(id);
    }

    // ======================== 用户端 ========================

    /** 用户获取自己的模型 + 系统模型 */
    public List<AiModel> getByUserId(Long userId) {
        return aiModelMapper.findByUserId(userId);
    }

    /** 用户获取可用模型（自己的 + 系统的） */
    public List<AiModel> getEnabledByUserId(Long userId) {
        return aiModelMapper.findEnabledByUserId(userId);
    }

    /** 用户添加自己的模型 */
    public AiModel createUserModel(Long userId, AiModel model) {
        model.setUserId(userId);
        model.setIsDefault(0);
        model.setIsEnabled(model.getIsEnabled() != null ? model.getIsEnabled() : 1);
        model.setSupportsStream(model.getSupportsStream() != null ? model.getSupportsStream() : 1);
        model.setSupportsStructured(model.getSupportsStructured() != null ? model.getSupportsStructured() : 1);
        model.setSortOrder(model.getSortOrder() != null ? model.getSortOrder() : 0);
        aiModelMapper.insert(model);
        return model;
    }

    /** 用户更新自己的模型（只能改自己的） */
    public AiModel updateUserModel(Long userId, Long id, AiModel model) {
        AiModel existing = aiModelMapper.findById(id);
        if (existing == null) throw new BusinessException(404, "模型不存在");
        if (existing.getUserId() == null || !existing.getUserId().equals(userId)) {
            throw new BusinessException(403, "只能操作自己的模型");
        }
        model.setId(id);
        model.setUserId(userId);
        model.setIsDefault(0);
        // apiKey 为空时保留原值（前端编辑时留空表示不修改，避免误清空密钥）
        if (model.getApiKey() == null || model.getApiKey().isBlank()) {
            model.setApiKey(existing.getApiKey());
        }
        aiModelMapper.update(model);
        return aiModelMapper.findById(id);
    }

    /** 用户删除自己的模型（只能删自己的） */
    public void deleteUserModel(Long userId, Long id) {
        AiModel existing = aiModelMapper.findById(id);
        if (existing == null) throw new BusinessException(404, "模型不存在");
        if (existing.getUserId() == null || !existing.getUserId().equals(userId)) {
            throw new BusinessException(403, "只能删除自己的模型");
        }
        aiModelMapper.deleteById(id);
    }
}
