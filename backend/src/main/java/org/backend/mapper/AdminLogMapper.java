package org.backend.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.backend.entity.AdminLog;
import java.util.List;
import java.util.Map;

@Mapper
public interface AdminLogMapper {
    List<AdminLog> findList(Map<String, Object> params);
    int countList(Map<String, Object> params);
    
    @Insert("INSERT INTO admin_log (admin_id, admin_name, action, target_type, target_id, detail, ip_address) " +
            "VALUES (#{adminId}, #{adminName}, #{action}, #{targetType}, #{targetId}, #{detail}, #{ipAddress})")
    int insert(AdminLog log);
}
