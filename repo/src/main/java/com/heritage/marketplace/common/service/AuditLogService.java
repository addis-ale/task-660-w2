package com.heritage.marketplace.common.service;

import com.heritage.marketplace.audit.AuditService;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private final AuditService auditService;

    public AuditLogService(AuditService auditService) {
        this.auditService = auditService;
    }

    public void log(
        String entityType,
        UUID entityId,
        String action,
        UUID actorId,
        Map<String, Object> changes,
        String ipAddress
    ) {
        auditService.log(entityType, entityId, action, actorId, null, changes == null ? Map.of() : changes, ipAddress);
    }
}
