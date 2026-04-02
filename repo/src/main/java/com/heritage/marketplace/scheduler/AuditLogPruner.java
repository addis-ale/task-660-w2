package com.heritage.marketplace.scheduler;

import com.heritage.marketplace.audit.AuditLogQueryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuditLogPruner {

    private final AuditLogQueryService auditLogQueryService;
    private final int retentionYears;

    public AuditLogPruner(
        AuditLogQueryService auditLogQueryService,
        @Value("${app.scheduler.audit-pruner.retention-years:2}") int retentionYears
    ) {
        this.auditLogQueryService = auditLogQueryService;
        this.retentionYears = retentionYears;
    }

    @Scheduled(cron = "${app.scheduler.audit-pruner.cron:0 0 1 1 * *}")
    public void pruneOldAuditPartitions() {
        auditLogQueryService.pruneOlderThanYears(retentionYears);
    }
}
