package org.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.backend.entity.AgentMemory;
import java.util.List;

@Mapper
public interface AgentMemoryMapper {

    int insert(AgentMemory memory);

    /** 查询用户最近的高置信度记忆 */
    List<AgentMemory> findRecentByUserId(@Param("userId") Long userId,
                                          @Param("limit") int limit);

    /** 按类型查询用户记忆 */
    List<AgentMemory> findByUserIdAndType(@Param("userId") Long userId,
                                           @Param("memoryType") String memoryType,
                                           @Param("limit") int limit);

    /** 更新访问次数和最近访问时间 */
    int incrementAccessCount(@Param("id") Long id);

    /** 删除指定用户的旧记忆（清理用） */
    int deleteOldMemories(@Param("userId") Long userId,
                          @Param("keepCount") int keepCount);
}
