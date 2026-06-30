package org.backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.backend.entity.AiModel;

@Mapper
public interface AiModelMapper {
    int insert(AiModel model);
    int update(AiModel model);
    int deleteById(Long id);
    AiModel findById(Long id);
    AiModel findDefault();
    List<AiModel> findAll();
    List<AiModel> findEnabled();
    int setDefault(@Param("id") Long id);
    int clearDefault();

    /** 用户端：获取用户的模型 + 系统模型 */
    List<AiModel> findByUserId(@Param("userId") Long userId);
    /** 用户端：获取用户的启用模型 + 系统启用模型 */
    List<AiModel> findEnabledByUserId(@Param("userId") Long userId);
}
