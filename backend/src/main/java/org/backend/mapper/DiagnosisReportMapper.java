package org.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.backend.entity.DiagnosisReport;
import java.util.List;
import java.util.Map;

@Mapper
public interface DiagnosisReportMapper {
    int insert(DiagnosisReport report);
    DiagnosisReport findById(Long id);
    DiagnosisReport findBySessionId(Long sessionId);
    DiagnosisReport findByReportId(Long reportId);
    DiagnosisReport findLatestByUserId(Long userId);
    DiagnosisReport findLatestByUserIdAndPosition(@Param("userId") Long userId,
                                                    @Param("position") String position);
    List<DiagnosisReport> findByUserId(@Param("userId") Long userId, @Param("offset") int offset, @Param("size") int size);
    int countByUserId(Long userId);
    int deleteById(Long id);
    int updateLearningPlan(DiagnosisReport report);
}
