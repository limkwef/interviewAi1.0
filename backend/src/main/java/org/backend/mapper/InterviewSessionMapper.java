package org.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.backend.entity.InterviewSession;
import java.util.List;
import java.util.Map;

@Mapper
public interface InterviewSessionMapper {
    int insert(InterviewSession session);
    int updateStatus(@Param("id") Long id, @Param("status") String status);
    int updateCurrentQuestion(@Param("id") Long id, @Param("currentQuestion") int currentQuestion);
    int updateQuestionIds(@Param("id") Long id, @Param("questionIds") String questionIds);
    InterviewSession findById(Long id);
    List<InterviewSession> findByUserId(Map<String, Object> params);
    int countByUserId(Map<String, Object> params);
    
    @Select("SELECT COUNT(*) FROM interview_session")
    int countAll();

    int deleteById(Long id);
}