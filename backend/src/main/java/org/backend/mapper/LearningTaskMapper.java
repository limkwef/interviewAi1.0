package org.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.backend.entity.LearningTask;
import java.util.List;

@Mapper
public interface LearningTaskMapper {
    int insert(LearningTask task);
    int insertBatch(@Param("list") List<LearningTask> tasks);
    int update(LearningTask task);
    int markComplete(@Param("userId") Long userId, @Param("phaseIndex") Integer phaseIndex,
                     @Param("taskIndex") Integer taskIndex);
    int unmarkComplete(@Param("userId") Long userId, @Param("phaseIndex") Integer phaseIndex,
                       @Param("taskIndex") Integer taskIndex);
    LearningTask findByUserPhaseTask(@Param("userId") Long userId, @Param("phaseIndex") Integer phaseIndex,
                                     @Param("taskIndex") Integer taskIndex);
    List<LearningTask> findByUserId(Long userId);
    List<LearningTask> findByUserAndPhase(@Param("userId") Long userId, @Param("phaseIndex") Integer phaseIndex);
    int countByUserId(Long userId);
    int countCompletedByUserId(Long userId);
    int deleteByUserId(Long userId);
    int deleteByUserIdAndCompleted(@Param("userId") Long userId, @Param("completed") Integer completed);
}
