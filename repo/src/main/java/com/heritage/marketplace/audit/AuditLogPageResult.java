package com.heritage.marketplace.audit;

import com.heritage.marketplace.audit.dto.AuditLogEntryResponse;
import java.util.List;

public record AuditLogPageResult(List<AuditLogEntryResponse> items, long totalItems) {
}
