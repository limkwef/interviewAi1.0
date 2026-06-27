package org.backend.mapper;

import org.apache.ibatis.annotations.*;
import org.backend.entity.Question;
import java.util.List;
import java.util.Map;

@Mapper
public interface QuestionMapper {
    List<Question> findList(Map<String, Object> params);
    int countList(Map<String, Object> params);
    Question findById(Long id);
    int incrementViewCount(Long id);
    int incrementFavoriteCount(Long id);
    int decrementFavoriteCount(Long id);
    
    int insert(Question question);

    int insertBatch(@Param("list") List<Question> questions);

    @Select("SELECT title FROM question WHERE is_deleted = 0")
    List<String> findAllTitles();

    @Select("SELECT id, title, content, answer, category, difficulty, direction, " +
            "view_count, favorite_count, is_deleted, created_at, updated_at " +
            "FROM question WHERE title = #{title} AND is_deleted = 0 LIMIT 1")
    Question findByTitle(@Param("title") String title);
    
    int update(Question question);
    
    @Update("UPDATE question SET is_deleted = 1 WHERE id = #{id}")
    int logicalDelete(Long id);

    @Select("SELECT COUNT(*) FROM question WHERE is_deleted = 0")
    int countAll();

    @Select("SELECT COUNT(*) FROM question WHERE direction = #{direction} AND is_deleted = 0")
    int countByDirection(@Param("direction") String direction);

    // ============ 应用层随机：先查 ID 列表，Java 端 shuffle，再 findByIds ============

    /** 按方向查题目 ID 列表 */
    List<Long> findIdsByDirection(@Param("direction") String direction);

    /** 按方向+难度查题目 ID 列表 */
    List<Long> findIdsByDirectionAndDifficulty(@Param("direction") String direction,
                                                @Param("difficulty") String difficulty);

    /** 按分类+难度查题目 ID 列表 */
    List<Long> findIdsByCategoryAndDifficulty(@Param("category") String category,
                                                @Param("difficulty") String difficulty);

    /** 按分类查题目 ID 列表 */
    List<Long> findIdsByCategory(@Param("category") String category);

    /** 按分类查题目 ID 列表（排除指定 ID） */
    List<Long> findIdsByCategoryWithExclusion(@Param("category") String category,
                                                @Param("excludeIds") List<Long> excludeIds);

    /** 按方向查题目 ID 列表（排除指定 ID） */
    List<Long> findIdsByDirectionWithExclusion(@Param("direction") String direction,
                                                @Param("excludeIds") List<Long> excludeIds);

    /** 根据 ID 列表批量查询题目 */
    List<Question> findByIds(@Param("ids") List<Long> ids);
}
