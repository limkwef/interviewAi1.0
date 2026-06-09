package org.backend.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.backend.entity.InterviewReport;

@Mapper
public interface InterviewReportMapper {
    int insert(InterviewReport report);
    InterviewReport findById(Long id);
    InterviewReport findBySessionId(Long sessionId);
    List<InterviewReport> findByUserId(Map<String, Object> params);
    int countByUserId(Long userId);
    List<Map<String, Object>> findGrowthData(Long userId);
    Map<String, Object> findStats(Long userId);

    /**
     * 查询同岗位所有用户的最新面试分数（用于计算分位数和排名）
     * 返回每个用户的最高分，按 position 分组
     */
    List<Map<String, Object>> findPositionScoreDistribution(@Param("position") String position);

    /**
     * 查询同岗位同轮次所有用户的最新面试分数（用于诊断页精确对标）
     * 返回每个用户的最高分，按 position + round 分组
     */
    List<Map<String, Object>> findPositionRoundScoreDistribution(@Param("position") String position, @Param("round") String round);

    /** 按岗位过滤的成长趋势（不分轮次） */
    List<Map<String, Object>> findGrowthDataByPosition(@Param("userId") Long userId,
                                                        @Param("position") String position);

    /** 按岗位过滤的统计 */
    Map<String, Object> findStatsByPosition(@Param("userId") Long userId,
                                             @Param("position") String position);

    /** 按岗位+轮次过滤的统计（知识雷达图专用） */
    Map<String, Object> findStatsByPositionAndRound(@Param("userId") Long userId,
                                                      @Param("position") String position,
                                                      @Param("round") String round);

    /** 按岗位+轮次过滤的成长趋势 */
    List<Map<String, Object>> findGrowthDataByPositionAndRound(@Param("userId") Long userId,
                                                                @Param("position") String position,
                                                                @Param("round") String round);

    /** 按岗位过滤的报告列表 */
    List<InterviewReport> findByUserIdAndPosition(Map<String, Object> params);

    /** 按岗位过滤的报告数量 */
    int countByUserIdAndPosition(Map<String, Object> params);

    /** 按条件过滤的报告列表（支持 position/round/difficulty） */
    List<InterviewReport> findByUserIdFiltered(Map<String, Object> params);

    /** 按条件过滤的报告数量 */
    int countByUserIdFiltered(Map<String, Object> params);

    /** 统计用户实际练习过的题目数（通过 report_comment 关联 interview_report） */
    int countUserPracticedQuestions(@Param("userId") Long userId);

    int deleteById(Long id);
}
