package org.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.backend.entity.ReportComment;
import java.util.List;

@Mapper
public interface ReportCommentMapper {
    int insert(ReportComment comment);
    List<ReportComment> findByReportId(Long reportId);
    int deleteByReportId(Long reportId);

    @Update("UPDATE report_comment SET duration_seconds = #{seconds} " +
            "WHERE report_id = #{reportId} AND sort_order = #{sortOrder}")
    int updateDurationByReportIdAndOrder(@Param("reportId") Long reportId,
                                          @Param("sortOrder") Integer sortOrder,
                                          @Param("seconds") Integer seconds);
}
