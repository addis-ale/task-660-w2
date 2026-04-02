package com.heritage.marketplace.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.heritage.marketplace.appeal.*;
import com.heritage.marketplace.appeal.dto.AppealDetailResponse;
import com.heritage.marketplace.appeal.dto.AppealFinalDecision;
import com.heritage.marketplace.appeal.dto.AppealFinalReviewRequest;
import com.heritage.marketplace.appeal.dto.AppealReviewDecision;
import com.heritage.marketplace.appeal.dto.AppealReviewRequest;
import com.heritage.marketplace.auth.JwtUserPrincipal;
import com.heritage.marketplace.common.exception.ApiException;
import com.heritage.marketplace.common.security.FileUploadSecurityValidator;
import com.heritage.marketplace.common.security.InputSanitizer;
import com.heritage.marketplace.common.service.AuditLogService;
import com.heritage.marketplace.ticket.Ticket;
import com.heritage.marketplace.ticket.TicketRepository;
import com.heritage.marketplace.user.User;
import com.heritage.marketplace.user.UserRepository;
import com.heritage.marketplace.user.UserRole;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AppealServiceTest {

    @Mock private AppealRepository appealRepository;
    @Mock private AppealEvidenceRepository appealEvidenceRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private FileUploadSecurityValidator fileUploadSecurityValidator;
    @Spy private InputSanitizer inputSanitizer = new InputSanitizer();

    private AppealService appealService;

    private User appellant;
    private User moderator;
    private User admin;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        appealService = new AppealService(
            appealRepository, appealEvidenceRepository, ticketRepository,
            userRepository, auditLogService, fileUploadSecurityValidator,
            inputSanitizer, "uploads/appeals"
        );

        appellant = new User();
        appellant.setId(UUID.randomUUID());
        appellant.setDisplayName("Appellant");
        appellant.setRole(UserRole.MEMBER);

        moderator = new User();
        moderator.setId(UUID.randomUUID());
        moderator.setDisplayName("Moderator");
        moderator.setRole(UserRole.MODERATOR);

        admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setDisplayName("Admin");
        admin.setRole(UserRole.ADMIN);

        ticket = new Ticket();
        ticket.setId(UUID.randomUUID());
        ticket.setReporter(appellant);
        ticket.setCreatedAt(LocalDateTime.now());
    }

    @Nested
    @DisplayName("createAppeal")
    class CreateAppealTests {

        @Test
        @DisplayName("should reject reason shorter than 10 characters")
        void rejectShortReason() {
            JwtUserPrincipal principal = new JwtUserPrincipal(appellant.getId(), UserRole.MEMBER);

            ApiException ex = assertThrows(ApiException.class,
                () -> appealService.createAppeal(ticket.getId(), "short", null, principal, "127.0.0.1"));

            assertEquals("VALIDATION_ERROR", ex.getCode());
        }

        @Test
        @DisplayName("should reject when appellant is not the ticket reporter")
        void rejectNonReporterAppeal() {
            JwtUserPrincipal otherUser = new JwtUserPrincipal(UUID.randomUUID(), UserRole.MEMBER);
            when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

            ApiException ex = assertThrows(ApiException.class,
                () -> appealService.createAppeal(
                    ticket.getId(), "This is a valid long enough reason for appeal",
                    null, otherUser, "127.0.0.1"));

            assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        }

        @Test
        @DisplayName("should create appeal with SUBMITTED status")
        void createAppealSuccessfully() {
            JwtUserPrincipal principal = new JwtUserPrincipal(appellant.getId(), UserRole.MEMBER);
            when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
            when(fileUploadSecurityValidator.validateEvidenceFiles(anyList())).thenReturn(List.of());
            when(userRepository.findById(appellant.getId())).thenReturn(Optional.of(appellant));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            AppealDetailResponse response = appealService.createAppeal(
                ticket.getId(), "This is a valid reason for the appeal submission",
                null, principal, "127.0.0.1");

            assertNotNull(response);
            assertEquals(AppealStatus.SUBMITTED, response.appeal().status());
            verify(auditLogService).log(eq("APPEAL"), any(), eq("CREATE"), any(), anyMap(), anyString());
        }
    }

    @Nested
    @DisplayName("reviewAppeal")
    class ReviewAppealTests {

        private Appeal buildAppeal(AppealStatus status) {
            Appeal appeal = new Appeal();
            appeal.setId(UUID.randomUUID());
            appeal.setTicket(ticket);
            appeal.setAppellant(appellant);
            appeal.setReason("Valid reason for appeal submission");
            appeal.setStatus(status);
            appeal.setCreatedAt(LocalDateTime.now());
            return appeal;
        }

        @Test
        @DisplayName("should reject review of already decided appeal")
        void rejectReviewOfDecidedAppeal() {
            Appeal appeal = buildAppeal(AppealStatus.APPROVED);
            when(appealRepository.findById(appeal.getId())).thenReturn(Optional.of(appeal));

            JwtUserPrincipal modPrincipal = new JwtUserPrincipal(moderator.getId(), UserRole.MODERATOR);
            AppealReviewRequest request = new AppealReviewRequest(AppealReviewDecision.APPROVED, "Looks good");

            ApiException ex = assertThrows(ApiException.class,
                () -> appealService.reviewAppeal(appeal.getId(), request, modPrincipal, "127.0.0.1"));

            assertEquals("INVALID_APPEAL_STATE", ex.getCode());
        }

        @Test
        @DisplayName("should escalate appeal to admin")
        void escalateAppealToAdmin() {
            Appeal appeal = buildAppeal(AppealStatus.SUBMITTED);
            when(appealRepository.findById(appeal.getId())).thenReturn(Optional.of(appeal));
            when(userRepository.findById(moderator.getId())).thenReturn(Optional.of(moderator));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));
            when(appealEvidenceRepository.findByAppeal_IdOrderByUploadedAtAsc(appeal.getId())).thenReturn(List.of());

            JwtUserPrincipal modPrincipal = new JwtUserPrincipal(moderator.getId(), UserRole.MODERATOR);
            AppealReviewRequest request = new AppealReviewRequest(AppealReviewDecision.ESCALATED_TO_ADMIN, "Needs admin review");

            AppealDetailResponse response = appealService.reviewAppeal(appeal.getId(), request, modPrincipal, "127.0.0.1");

            assertEquals(AppealStatus.ESCALATED_TO_ADMIN, response.appeal().status());
            assertNull(response.appeal().decidedAt());
        }
    }

    @Nested
    @DisplayName("finalReviewAppeal")
    class FinalReviewAppealTests {

        @Test
        @DisplayName("should reject final review of non-escalated appeal")
        void rejectFinalReviewOfNonEscalated() {
            Appeal appeal = new Appeal();
            appeal.setId(UUID.randomUUID());
            appeal.setTicket(ticket);
            appeal.setAppellant(appellant);
            appeal.setStatus(AppealStatus.SUBMITTED);
            appeal.setCreatedAt(LocalDateTime.now());

            when(appealRepository.findById(appeal.getId())).thenReturn(Optional.of(appeal));

            JwtUserPrincipal adminPrincipal = new JwtUserPrincipal(admin.getId(), UserRole.ADMIN);
            AppealFinalReviewRequest request = new AppealFinalReviewRequest(AppealFinalDecision.APPROVED, "Approved");

            ApiException ex = assertThrows(ApiException.class,
                () -> appealService.finalReviewAppeal(appeal.getId(), request, adminPrincipal, "127.0.0.1"));

            assertEquals("INVALID_APPEAL_STATE", ex.getCode());
        }

        @Test
        @DisplayName("should approve escalated appeal in final review")
        void approveFinalReview() {
            Appeal appeal = new Appeal();
            appeal.setId(UUID.randomUUID());
            appeal.setTicket(ticket);
            appeal.setAppellant(appellant);
            appeal.setStatus(AppealStatus.ESCALATED_TO_ADMIN);
            appeal.setCreatedAt(LocalDateTime.now());

            when(appealRepository.findById(appeal.getId())).thenReturn(Optional.of(appeal));
            when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));
            when(appealEvidenceRepository.findByAppeal_IdOrderByUploadedAtAsc(appeal.getId())).thenReturn(List.of());

            JwtUserPrincipal adminPrincipal = new JwtUserPrincipal(admin.getId(), UserRole.ADMIN);
            AppealFinalReviewRequest request = new AppealFinalReviewRequest(AppealFinalDecision.APPROVED, "Approved after review");

            AppealDetailResponse response = appealService.finalReviewAppeal(appeal.getId(), request, adminPrincipal, "127.0.0.1");

            assertEquals(AppealStatus.APPROVED, response.appeal().status());
            assertNotNull(response.appeal().decidedAt());
        }
    }

    @Nested
    @DisplayName("access control")
    class AccessControlTests {

        @Test
        @DisplayName("should throw FORBIDDEN when non-owner non-moderator accesses appeal")
        void throwForbiddenForNonOwner() {
            Appeal appeal = new Appeal();
            appeal.setId(UUID.randomUUID());
            appeal.setTicket(ticket);
            appeal.setAppellant(appellant);
            appeal.setStatus(AppealStatus.SUBMITTED);
            appeal.setCreatedAt(LocalDateTime.now());

            when(appealRepository.findById(appeal.getId())).thenReturn(Optional.of(appeal));

            JwtUserPrincipal otherMember = new JwtUserPrincipal(UUID.randomUUID(), UserRole.MEMBER);

            ApiException ex = assertThrows(ApiException.class,
                () -> appealService.getAppeal(appeal.getId(), otherMember));

            assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        }

        @Test
        @DisplayName("should allow MODERATOR to access any appeal")
        void allowModeratorAccessToAnyAppeal() {
            Appeal appeal = new Appeal();
            appeal.setId(UUID.randomUUID());
            appeal.setTicket(ticket);
            appeal.setAppellant(appellant);
            appeal.setStatus(AppealStatus.SUBMITTED);
            appeal.setReason("Reason");
            appeal.setCreatedAt(LocalDateTime.now());

            when(appealRepository.findById(appeal.getId())).thenReturn(Optional.of(appeal));
            when(appealEvidenceRepository.findByAppeal_IdOrderByUploadedAtAsc(appeal.getId())).thenReturn(List.of());

            JwtUserPrincipal modPrincipal = new JwtUserPrincipal(moderator.getId(), UserRole.MODERATOR);

            assertDoesNotThrow(() -> appealService.getAppeal(appeal.getId(), modPrincipal));
        }
    }
}
