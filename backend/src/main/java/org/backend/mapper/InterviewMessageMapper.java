package org.backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.backend.entity.InterviewMessage;

@Mapper
public interface InterviewMessageMapper {
    int insert(InterviewMessage message);
    List<InterviewMessage> findBySessionId(Long sessionId);
    int countBySessionId(Long sessionId);
    int countUserMessages(Long sessionId);
    int deleteBySessionId(Long sessionId);
    InterviewMessage findLatestBySessionId(Long sessionId);
    int updateContent(@Param("id") Long id, @Param("content") String content);
    int updateMessageTypeAndQuestionIndex(@Param("id") Long id, @Param("messageType") String messageType, @Param("questionIndex") Integer questionIndex);
}