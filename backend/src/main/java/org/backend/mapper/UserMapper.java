package org.backend.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.backend.entity.User;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM user WHERE email = #{email}")
    User findByEmail(String email);

    @Select("SELECT * FROM user WHERE id = #{id}")
    User findById(Long id);

    @Select("SELECT * FROM user WHERE phone = #{phone}")
    User findByPhone(String phone);

    @Insert("INSERT INTO user (username, email, phone, password, role, status) " +
            "VALUES (#{username}, #{email}, #{phone}, #{password}, #{role}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Select("SELECT COUNT(*) FROM user WHERE email = #{email}")
    int countByEmail(String email);

    @Select("SELECT COUNT(*) FROM user WHERE phone = #{phone}")
    int countByPhone(String phone);

    int updateUser(User user);

    @Update("UPDATE user SET password = #{password} WHERE id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("password") String password);

    List<User> findList(Map<String, Object> params);

    int countList(Map<String, Object> params);

    @Update("UPDATE user SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    @Update("UPDATE user SET status = 0 WHERE id = #{id}")
    int logicalDelete(Long id);

    @Select("SELECT COUNT(*) FROM user")
    int countAll();

    List<Map<String, Object>> countGroupByDate(Map<String, Object> params);
}
