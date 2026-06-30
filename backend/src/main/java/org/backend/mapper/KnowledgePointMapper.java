package org.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.backend.entity.KnowledgePoint;

import java.util.List;

/**
 * 知识点 Mapper（Agent KnowledgeSearchTool 查询用）
 */
@Mapper
public interface KnowledgePointMapper {

    /** 按关键词搜索知识点（LIKE 模糊匹配 keyword 和 content） */
    List<KnowledgePoint> findByKeyword(@Param("keyword") String keyword,
                                       @Param("category") String category,
                                       @Param("limit") int limit);

    /** 按分类查询知识点 */
    List<KnowledgePoint> findByCategory(@Param("category") String category,
                                        @Param("limit") int limit);

    /** 插入知识点 */
    int insert(KnowledgePoint knowledgePoint);

    /** 批量插入 */
    int insertBatch(@Param("list") List<KnowledgePoint> list);

    /** 统计总数 */
    int countAll();
}
