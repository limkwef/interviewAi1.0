package org.backend.service;

import org.backend.entity.AdminLog;
import org.backend.mapper.AdminLogMapper;
import org.springframework.stereotype.Service;

@Service
public class AdminLogService {

    private final AdminLogMapper adminLogMapper;

    public AdminLogService(AdminLogMapper adminLogMapper) {
        this.adminLogMapper = adminLogMapper;
    }

    public void log(Long adminId, String adminName, String action, 
                   String targetType, Long targetId, String detail, String ipAddress) {
        AdminLog log = new AdminLog();
        log.setAdminId(adminId);
        log.setAdminName(adminName);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        log.setIpAddress(ipAddress);
        adminLogMapper.insert(log);
    }
}