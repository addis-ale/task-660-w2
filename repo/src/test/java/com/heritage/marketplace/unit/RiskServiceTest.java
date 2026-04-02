package com.heritage.marketplace.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.heritage.marketplace.appeal.AppealRepository;
import com.heritage.marketplace.auth.JwtUserPrincipal;
import com.heritage.marketplace.common.service.AuditLogService;
import com.heritage.marketplace.common.util.EncryptionUtil;
import com.heritage.marketplace.risk.*;
import com.heritage.marketplace.risk.dto.RiskDashboardResponse;
import com.heritage.marketplace.ticket.TicketRepository;
import com.heritage.marketplace.ticket.TicketSeverity;
import com.heritage.marketplace.user.User;
import com.heritage.marketplace.user.UserRepository;
import com.heritage.marketplace.user.UserRole;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RiskServiceTest {

    @Mock private RiskFlagRepository riskFlagRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private AppealRepository appealRepository;
    @Mock private UserRepository userRepository;
    @Mock private EncryptionUtil encryptionUtil;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private RiskService riskService;

    @Nested
    @DisplayName("dashboard")
    class DashboardTests {

        @Test
        @DisplayName("should return dashboard with ticket severity counts")
        void returnDashboardWithSeverityCounts() {
            JwtUserPrincipal admin = new JwtUserPrincipal(UUID.randomUUID(), UserRole.ADMIN);

            when(ticketRepository.countByStatusNotInAndSeverity(anyList(), eq(TicketSeverity.LOW))).thenReturn(5L);
            when(ticketRepository.countByStatusNotInAndSeverity(anyList(), eq(TicketSeverity.MEDIUM))).thenReturn(3L);
            when(ticketRepository.countByStatusNotInAndSeverity(anyList(), eq(TicketSeverity.HIGH))).thenReturn(1L);
            when(ticketRepository.averageResolutionHours()).thenReturn(12.5);
            when(ticketRepository.count()).thenReturn(100L);
            when(ticketRepository.countByEscalatedAtIsNotNull()).thenReturn(10L);
            when(riskFlagRepository.countByWindowEndGreaterThanEqual(any(LocalDate.class))).thenReturn(7L);
            when(riskFlagRepository.findTop10ByEntityTypeOrderByIncidentCountDescCreatedAtDesc(any())).thenReturn(List.of());

            RiskDashboardResponse dashboard = riskService.dashboard(admin, "127.0.0.1");

            assertEquals(5L, dashboard.openTicketsLow());
            assertEquals(3L, dashboard.openTicketsMedium());
            assertEquals(1L, dashboard.openTicketsHigh());
            assertEquals(12.5, dashboard.avgResolutionHours());
            assertEquals(10.0, dashboard.escalationRatePercent());
            assertEquals(7L, dashboard.activeRiskFlags());
            verify(auditLogService).log(eq("RISK"), any(), eq("VIEW_DASHBOARD"), any(), anyMap(), anyString());
        }

        @Test
        @DisplayName("should handle zero total tickets for escalation rate")
        void handleZeroTotalTickets() {
            JwtUserPrincipal admin = new JwtUserPrincipal(UUID.randomUUID(), UserRole.ADMIN);

            when(ticketRepository.countByStatusNotInAndSeverity(anyList(), any())).thenReturn(0L);
            when(ticketRepository.averageResolutionHours()).thenReturn(null);
            when(ticketRepository.count()).thenReturn(0L);
            when(ticketRepository.countByEscalatedAtIsNotNull()).thenReturn(0L);
            when(riskFlagRepository.countByWindowEndGreaterThanEqual(any())).thenReturn(0L);
            when(riskFlagRepository.findTop10ByEntityTypeOrderByIncidentCountDescCreatedAtDesc(any())).thenReturn(List.of());

            RiskDashboardResponse dashboard = riskService.dashboard(admin, "127.0.0.1");

            assertEquals(0.0, dashboard.escalationRatePercent());
            assertEquals(0.0, dashboard.avgResolutionHours());
        }
    }

    @Nested
    @DisplayName("generateRepeatIncidentFlags")
    class GenerateRepeatIncidentFlagsTests {

        @Test
        @DisplayName("should return 0 when no recent tickets")
        void returnZeroWhenNoRecentTickets() {
            when(ticketRepository.findByCreatedAtAfter(any())).thenReturn(List.of());

            int result = riskService.generateRepeatIncidentFlags();

            assertEquals(0, result);
        }
    }
}
