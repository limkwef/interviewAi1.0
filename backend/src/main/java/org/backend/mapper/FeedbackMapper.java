package org.backend.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.backend.entity.Feedback;

import java.util.List;
import java.util.Map;

@Mapper
public interface FeedbackMapper {

    @Insert("INSERT INTO feedback (user_id, type, content, contact) VALUES (#{userId}, #{type}, #{content}, #{contact})")
    int insert(Feedback feedback);

    @Select("SELECT * FROM feedback WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<Feedback> findByUserId(@Param("userId") Long userId);

    @Select("<script>SELECT * FROM feedback <where><if test=\"status != null\">AND status = #{status}</if></where> ORDER BY created_at DESC LIMIT #{offset}, #{size}</script>")
    List<Feedback> findAll(Map<String, Object> params);

    @Select("<script>SELECT COUNT(*) FROM feedback <where><if test=\"status != null\">AND status = #{status}</if></where></script>")
    int countAll(Map<String, Object> params);

    @Update("UPDATE feedback SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    @Delete("DELETE FROM feedback WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
