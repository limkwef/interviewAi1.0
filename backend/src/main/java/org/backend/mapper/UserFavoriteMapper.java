package org.backend.mapper;

import org.apache.ibatis.annotations.*;
import org.backend.entity.UserFavorite;
import org.backend.entity.Question;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserFavoriteMapper {
    @Insert("INSERT IGNORE INTO user_favorite (user_id, question_id) VALUES (#{userId}, #{questionId})")
    int insert(UserFavorite userFavorite);

    @Delete("DELETE FROM user_favorite WHERE user_id = #{userId} AND question_id = #{questionId}")
    int delete(@Param("userId") Long userId, @Param("questionId") Long questionId);

    int batchDelete(@Param("userId") Long userId, @Param("questionIds") List<Long> questionIds);

    @Select("SELECT COUNT(*) FROM user_favorite WHERE user_id = #{userId} AND question_id = #{questionId}")
    int countByUserAndQuestion(@Param("userId") Long userId, @Param("questionId") Long questionId);

    List<Question> findFavoriteQuestions(Map<String, Object> params);

    int countFavoriteQuestions(Map<String, Object> params);
}
