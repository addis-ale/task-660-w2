package com.heritage.marketplace.scheduler;

import com.heritage.marketplace.risk.RiskService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RiskFlagAggregator {

    private final RiskService riskService;

    public RiskFlagAggregator(RiskService riskService) {
        this.riskService = riskService;
    }

    @Scheduled(cron = "${app.scheduler.risk-aggregation.cron:0 0 3 * * *}")
    public void aggregateRiskFlags() {
        riskService.generateRepeatIncidentFlags();
    }
}
