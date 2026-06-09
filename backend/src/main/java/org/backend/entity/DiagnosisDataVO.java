package org.backend.entity;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AI 深度诊断报告数据 VO
 */
@Data
public class DiagnosisDataVO {
    private List<Map<String, Object>> knowledgeAnalysis;
    private Map<String, Object> thinkingAnalysis;
    private List<Map<String, Object>> mistakePatterns;
    private Map<String, Object> learningPlan;
    private List<Map<String, Object>> detailedComments;
}
