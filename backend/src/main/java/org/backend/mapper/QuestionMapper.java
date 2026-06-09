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
    
    int update(Question question);
    
    @Update("UPDATE question SET is_deleted = 1 WHERE id = #{id}")
    int logicalDelete(Long id);

    @Select("SELECT COUNT(*) FROM question WHERE is_deleted = 0")
    int countAll();

    List<Question> findRandomByDirection(@Param("direction") String direction, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM question WHERE direction = #{direction} AND is_deleted = 0")
    int countByDirection(@Param("direction") String direction);

    /** 按岗位+难度随机抽题（分层抽题用） */
    List<Question> findRandomByDirectionAndDifficulty(@Param("direction") String direction,
                                                       @Param("difficulty") String difficulty,
                                                       @Param("limit") int limit);

    /** 随机抽题并排除指定 ID 列表（去重用） */
    List<Question> findRandomWithExclusion(@Param("direction") String direction,
                                            @Param("limit") int limit,
                                            @Param("excludeIds") List<Long> excludeIds);

    /** 根据 ID 列表批量查询题目 */
    List<Question> findByIds(@Param("ids") List<Long> ids);
}
