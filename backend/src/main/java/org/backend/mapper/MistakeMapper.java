package org.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.backend.entity.MistakeAnswerDetail;
import org.backend.entity.MistakeRecord;

import java.util.List;
import java.util.Map;

@Mapper
public interface MistakeMapper {

    // ======================== 错题记录 ========================

    List<MistakeRecord> findList(Map<String, Object> params);

    int countList(Map<String, Object> params);

    MistakeRecord findById(Long id);

    MistakeRecord findByUserAndQuestion(@Param("userId") Long userId, @Param("questionId") Long questionId);

    int insert(MistakeRecord record);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status, @Param("masteredTime") java.time.LocalDateTime masteredTime);

    int incrementMistakeCount(@Param("id") Long id);

    int updateLastMistakeTime(@Param("id") Long id, @Param("time") java.time.LocalDateTime time);

    int deleteById(Long id);

    // ======================== 统计 ========================

    Map<String, Object> findStats(Long userId);

    List<Map<String, Object>> findCategoryStats(Long userId);

    /** 按岗位过滤的错题分类统计 */
    List<Map<String, Object>> findCategoryStatsByPosition(@Param("userId") Long userId,
                                                            @Param("position") String position);

    /**
     * 错题知识点标签统计（关联 question + tag 表）
     */
    List<Map<String, Object>> findTagStats(Long userId);

    // ======================== 错误详情 ========================

    List<MistakeAnswerDetail> findDetailsByMistakeId(Long mistakeId);

    int insertDetail(MistakeAnswerDetail detail);

    // ======================== 重做 ========================

    List<Map<String, Object>> findReviewQuestions(Map<String, Object> params);

    int countReviewQuestions(Map<String, Object> params);

    List<Map<String, Object>> findReviewQuestionsByIds(@Param("ids") List<Long> ids);
}
