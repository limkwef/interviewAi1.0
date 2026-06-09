package org.backend.entity;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 结束面试结果 VO
 */
@Data
public class EndInterviewVO {
    private Long interviewId;
    private Long reportId;
    private Integer totalScore;
    private String level;
    private Map<String, Integer> dimensions;
    private List<ReportResultVO.Comment> comments;
    private String suggestion;
}
