package org.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.backend.entity.Tag;
import java.util.List;

@Mapper
public interface TagMapper {
    @Select("SELECT id, name FROM tag ORDER BY id")
    List<Tag> findAll();
}
