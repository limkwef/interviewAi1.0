package org.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.backend.entity.Resume;

import java.util.List;
import java.util.Map;

@Mapper
public interface ResumeMapper {

    int insert(Resume resume);

    Resume findById(Long id);

    List<Resume> findByUserId(Map<String, Object> params);

    int countByUserId(Long userId);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status, @Param("errorMsg") String errorMsg);

    int updateParsedData(@Param("id") Long id, @Param("parsedData") String parsedData);

    int updateRawText(@Param("id") Long id, @Param("rawText") String rawText);

    int deactivateAll(Long userId);

    int activateById(@Param("id") Long id);

    int deactivateById(@Param("id") Long id);

    Resume findActiveByUserId(Long userId);

    int deleteById(Long id);

    int countByUserIdAndSource(@Param("userId") Long userId, @Param("source") String source);
}
