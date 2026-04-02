package com.heritage.marketplace.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.heritage.marketplace.auth.JwtUserPrincipal;
import com.heritage.marketplace.common.exception.ApiException;
import com.heritage.marketplace.common.security.InputSanitizer;
import com.heritage.marketplace.common.service.AuditLogService;
import com.heritage.marketplace.ticket.*;
import com.heritage.marketplace.ticket.dto.CreateTicketRequest;
import com.heritage.marketplace.ticket.dto.TicketResponse;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private TicketFollowUpRepository ticketFollowUpRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogService auditLogService;
    @Spy private InputSanitizer inputSanitizer = new InputSanitizer();

    @InjectMocks private TicketService ticketService;

    private User reporter;
    private UUID reporterId;

    @BeforeEach
    void setUp() {
        reporterId = UUID.randomUUID();
        reporter = new User();
        reporter.setId(reporterId);
        reporter.setDisplayName("Reporter User");
        reporter.setRole(UserRole.MEMBER);
    }

    @Nested
    @DisplayName("createTicket")
    class CreateTicketTests {

        @Test
        @DisplayName("should create ticket with OPEN status")
        void createTicketWithOpenStatus() {
            when(userRepository.findById(reporterId)).thenReturn(Optional.of(reporter));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateTicketRequest request = new CreateTicketRequest(
                TicketType.DELIVERY_DISPUTE, TicketSeverity.MEDIUM,
                "123 Main St", "Elm & Oak", "Package was damaged on delivery"
            );

            TicketResponse response = ticketService.createTicket(request, reporterId, "127.0.0.1");

            assertNotNull(response);
            assertEquals(TicketStatus.OPEN, response.status());
            assertEquals(TicketSeverity.MEDIUM, response.severity());
            assertEquals(TicketType.DELIVERY_DISPUTE, response.type());
            assertNull(response.assignedTo());
            verify(auditLogService).log(eq("TICKET"), any(), eq("CREATE"), eq(reporterId), anyMap(), anyString());
        }

        @Test
        @DisplayName("should throw when reporter not found")
        void throwWhenReporterNotFound() {
            when(userRepository.findById(reporterId)).thenReturn(Optional.empty());

            CreateTicketRequest request = new CreateTicketRequest(
                TicketType.SAFETY_CONCERN, TicketSeverity.HIGH,
                "456 Oak Ave", null, "Safety concern description"
            );

            ApiException ex = assertThrows(ApiException.class,
                () -> ticketService.createTicket(request, reporterId, "127.0.0.1"));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
            assertEquals("USER_NOT_FOUND", ex.getCode());
        }

        @Test
        @DisplayName("should sanitize input fields on creation")
        void sanitizeInputFields() {
            when(userRepository.findById(reporterId)).thenReturn(Optional.of(reporter));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateTicketRequest request = new CreateTicketRequest(
                TicketType.OTHER, TicketSeverity.LOW,
                "<script>alert('xss')</script>123 Main St", null,
                "Description with <script>bad</script> content"
            );

            TicketResponse response = ticketService.createTicket(request, reporterId, "127.0.0.1");

            assertFalse(response.locationAddress().contains("<script>"));
            assertFalse(response.description().contains("<script>"));
        }
    }

    @Nested
    @DisplayName("validateTransition (via updateStatus)")
    class TransitionTests {

        private Ticket buildTicket(TicketStatus status) {
            Ticket ticket = new Ticket();
            ticket.setId(UUID.randomUUID());
            ticket.setReporter(reporter);
            ticket.setType(TicketType.DELIVERY_DISPUTE);
            ticket.setSeverity(TicketSeverity.MEDIUM);
            ticket.setStatus(status);
            ticket.setCreatedAt(LocalDateTime.now());
            return ticket;
        }

        @Test
        @DisplayName("should allow OPEN -> ACKNOWLEDGED transition")
        void allowOpenToAcknowledged() {
            Ticket ticket = buildTicket(TicketStatus.OPEN);
            when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

            JwtUserPrincipal mod = new JwtUserPrincipal(UUID.randomUUID(), UserRole.MODERATOR);
            TicketResponse response = ticketService.updateStatus(ticket.getId(), TicketStatus.ACKNOWLEDGED, mod, "127.0.0.1");

            assertEquals(TicketStatus.ACKNOWLEDGED, response.status());
        }

        @Test
        @DisplayName("should reject OPEN -> RESOLVED transition")
        void rejectOpenToResolved() {
            Ticket ticket = buildTicket(TicketStatus.OPEN);
            when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

            JwtUserPrincipal mod = new JwtUserPrincipal(UUID.randomUUID(), UserRole.MODERATOR);

            ApiException ex = assertThrows(ApiException.class,
                () -> ticketService.updateStatus(ticket.getId(), TicketStatus.RESOLVED, mod, "127.0.0.1"));

            assertEquals("INVALID_TRANSITION", ex.getCode());
        }

        @Test
        @DisplayName("should reject CLOSED -> any transition")
        void rejectClosedToAny() {
            Ticket ticket = buildTicket(TicketStatus.CLOSED);
            when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

            JwtUserPrincipal mod = new JwtUserPrincipal(UUID.randomUUID(), UserRole.MODERATOR);

            ApiException ex = assertThrows(ApiException.class,
                () -> ticketService.updateStatus(ticket.getId(), TicketStatus.OPEN, mod, "127.0.0.1"));

            assertEquals("INVALID_TRANSITION", ex.getCode());
        }

        @Test
        @DisplayName("should allow ACKNOWLEDGED -> IN_PROGRESS transition")
        void allowAcknowledgedToInProgress() {
            Ticket ticket = buildTicket(TicketStatus.ACKNOWLEDGED);
            when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

            JwtUserPrincipal mod = new JwtUserPrincipal(UUID.randomUUID(), UserRole.MODERATOR);
            TicketResponse response = ticketService.updateStatus(ticket.getId(), TicketStatus.IN_PROGRESS, mod, "127.0.0.1");

            assertEquals(TicketStatus.IN_PROGRESS, response.status());
        }

        @Test
        @DisplayName("should allow IN_PROGRESS -> ESCALATED transition")
        void allowInProgressToEscalated() {
            Ticket ticket = buildTicket(TicketStatus.IN_PROGRESS);
            when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
            when(userRepository.findFirstByRole(UserRole.MODERATOR)).thenReturn(Optional.of(reporter));

            JwtUserPrincipal mod = new JwtUserPrincipal(UUID.randomUUID(), UserRole.MODERATOR);
            TicketResponse response = ticketService.updateStatus(ticket.getId(), TicketStatus.ESCALATED, mod, "127.0.0.1");

            assertEquals(TicketStatus.ESCALATED, response.status());
        }

        @Test
        @DisplayName("should require resolve before closing")
        void requireResolveBeforeClose() {
            Ticket ticket = buildTicket(TicketStatus.RESOLVED);
            ticket.setResolvedAt(null);
            when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

            JwtUserPrincipal mod = new JwtUserPrincipal(UUID.randomUUID(), UserRole.MODERATOR);

            ApiException ex = assertThrows(ApiException.class,
                () -> ticketService.updateStatus(ticket.getId(), TicketStatus.CLOSED, mod, "127.0.0.1"));

            assertEquals("INVALID_TRANSITION", ex.getCode());
        }
    }

    @Nested
    @DisplayName("resolve")
    class ResolveTests {

        @Test
        @DisplayName("should resolve ticket with closure code")
        void resolveTicketSuccessfully() {
            Ticket ticket = new Ticket();
            ticket.setId(UUID.randomUUID());
            ticket.setReporter(reporter);
            ticket.setStatus(TicketStatus.IN_PROGRESS);
            ticket.setType(TicketType.DELIVERY_DISPUTE);
            ticket.setSeverity(TicketSeverity.MEDIUM);
            ticket.setCreatedAt(LocalDateTime.now());

            when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

            JwtUserPrincipal mod = new JwtUserPrincipal(UUID.randomUUID(), UserRole.MODERATOR);
            TicketResponse response = ticketService.resolve(ticket.getId(), "RESOLVED_BY_AGENT", "Issue was resolved", mod, "127.0.0.1");

            assertEquals(TicketStatus.RESOLVED, response.status());
            assertEquals("RESOLVED_BY_AGENT", response.closureCode());
        }

        @Test
        @DisplayName("should reject resolving a CLOSED ticket")
        void rejectResolvingClosedTicket() {
            Ticket ticket = new Ticket();
            ticket.setId(UUID.randomUUID());
            ticket.setReporter(reporter);
            ticket.setStatus(TicketStatus.CLOSED);
            ticket.setType(TicketType.DELIVERY_DISPUTE);
            ticket.setSeverity(TicketSeverity.MEDIUM);
            ticket.setCreatedAt(LocalDateTime.now());

            when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

            JwtUserPrincipal mod = new JwtUserPrincipal(UUID.randomUUID(), UserRole.MODERATOR);

            ApiException ex = assertThrows(ApiException.class,
                () -> ticketService.resolve(ticket.getId(), "CODE", "Notes", mod, "127.0.0.1"));

            assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        }
    }

    @Nested
    @DisplayName("access control")
    class AccessControlTests {

        @Test
        @DisplayName("should throw FORBIDDEN when non-reporter non-moderator accesses ticket detail")
        void throwForbiddenForNonReporterNonModerator() {
            Ticket ticket = new Ticket();
            ticket.setId(UUID.randomUUID());
            ticket.setReporter(reporter);
            ticket.setStatus(TicketStatus.OPEN);
            ticket.setType(TicketType.DELIVERY_DISPUTE);
            ticket.setSeverity(TicketSeverity.MEDIUM);
            ticket.setAssignedTo(null);
            ticket.setCreatedAt(LocalDateTime.now());

            when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

            JwtUserPrincipal otherMember = new JwtUserPrincipal(UUID.randomUUID(), UserRole.MEMBER);

            ApiException ex = assertThrows(ApiException.class,
                () -> ticketService.getTicketDetail(ticket.getId(), otherMember));

            assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        }

        @Test
        @DisplayName("should allow ADMIN to access any ticket")
        void allowAdminAccessToAnyTicket() {
            Ticket ticket = new Ticket();
            ticket.setId(UUID.randomUUID());
            ticket.setReporter(reporter);
            ticket.setStatus(TicketStatus.OPEN);
            ticket.setType(TicketType.DELIVERY_DISPUTE);
            ticket.setSeverity(TicketSeverity.MEDIUM);
            ticket.setCreatedAt(LocalDateTime.now());

            when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
            when(ticketFollowUpRepository.findByTicket_IdOrderByCreatedAtAsc(ticket.getId())).thenReturn(List.of());

            JwtUserPrincipal admin = new JwtUserPrincipal(UUID.randomUUID(), UserRole.ADMIN);

            assertDoesNotThrow(() -> ticketService.getTicketDetail(ticket.getId(), admin));
        }
    }
}
