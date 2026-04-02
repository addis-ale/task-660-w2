package com.heritage.marketplace.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.heritage.marketplace.auth.JwtUserPrincipal;
import com.heritage.marketplace.common.exception.ApiException;
import com.heritage.marketplace.common.security.InputSanitizer;
import com.heritage.marketplace.inventory.InventoryRecord;
import com.heritage.marketplace.inventory.InventoryRecordRepository;
import com.heritage.marketplace.inventory.Warehouse;
import com.heritage.marketplace.listing.*;
import com.heritage.marketplace.listing.dto.CreateListingRequest;
import com.heritage.marketplace.listing.dto.ListingDetailResponse;
import com.heritage.marketplace.listing.dto.ListingSearchResponse;
import com.heritage.marketplace.tier.BenefitPackageRepository;
import com.heritage.marketplace.tier.MembershipRepository;
import com.heritage.marketplace.user.User;
import com.heritage.marketplace.user.UserRepository;
import com.heritage.marketplace.user.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ListingServiceTest {

    @Mock private ListingRepository listingRepository;
    @Mock private ListingSearchRepository listingSearchRepository;
    @Mock private RecentSearchRepository recentSearchRepository;
    @Mock private UserRepository userRepository;
    @Mock private InventoryRecordRepository inventoryRecordRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private BenefitPackageRepository benefitPackageRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @Spy private InputSanitizer inputSanitizer = new InputSanitizer();

    @InjectMocks private ListingService listingService;

    private User seller;
    private Listing listing;

    @BeforeEach
    void setUp() {
        seller = new User();
        seller.setId(UUID.randomUUID());
        seller.setDisplayName("Test Seller");
        seller.setRole(UserRole.SELLER);

        listing = new Listing();
        listing.setId(UUID.randomUUID());
        listing.setSeller(seller);
        listing.setTitle("Heritage Desk");
        listing.setDescription("A classic desk");
        listing.setCategory("furniture");
        listing.setPrice(BigDecimal.valueOf(250));
        listing.setTags(new String[]{"vintage", "desk"});
        listing.setNeighborhood("Downtown");
        listing.setStatus(ListingStatus.ACTIVE);
        listing.setViewCount(10);
        listing.setOrderCount7d(5);
        listing.setTrendingScore(BigDecimal.valueOf(7.0));
        listing.setCreatedAt(LocalDateTime.now());
    }

    @Nested
    @DisplayName("trending")
    class TrendingTests {

        @Test
        @DisplayName("should clamp limit between 1 and 50")
        void clampLimitBounds() {
            when(listingRepository.findByStatusOrderByTrendingScoreDesc(eq(ListingStatus.ACTIVE), any(PageRequest.class)))
                .thenReturn(List.of());

            listingService.trending(0);
            verify(listingRepository).findByStatusOrderByTrendingScoreDesc(
                eq(ListingStatus.ACTIVE),
                argThat(pr -> pr.getPageSize() == 1)
            );

            listingService.trending(999);
            verify(listingRepository).findByStatusOrderByTrendingScoreDesc(
                eq(ListingStatus.ACTIVE),
                argThat(pr -> pr.getPageSize() == 50)
            );
        }

        @Test
        @DisplayName("should return mapped search responses")
        void returnMappedResponses() {
            when(listingRepository.findByStatusOrderByTrendingScoreDesc(eq(ListingStatus.ACTIVE), any(PageRequest.class)))
                .thenReturn(List.of(listing));

            List<ListingSearchResponse> results = listingService.trending(10);

            assertEquals(1, results.size());
            assertEquals(listing.getId(), results.get(0).id());
            assertEquals("Heritage Desk", results.get(0).title());
        }
    }

    @Nested
    @DisplayName("getDetail")
    class GetDetailTests {

        @Test
        @DisplayName("should throw NOT_FOUND for removed listing")
        void throwNotFoundForRemovedListing() {
            when(listingRepository.findByIdAndStatusNot(any(), eq(ListingStatus.REMOVED)))
                .thenReturn(Optional.empty());

            JwtUserPrincipal principal = new JwtUserPrincipal(UUID.randomUUID(), UserRole.MEMBER);

            ApiException ex = assertThrows(ApiException.class,
                () -> listingService.getDetail(UUID.randomUUID(), principal));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
            assertEquals("LISTING_NOT_FOUND", ex.getCode());
        }

        @Test
        @DisplayName("should increment view count on detail access")
        void incrementViewCount() {
            int initialViews = listing.getViewCount();
            when(listingRepository.findByIdAndStatusNot(listing.getId(), ListingStatus.REMOVED))
                .thenReturn(Optional.of(listing));
            when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));

            JwtUserPrincipal principal = new JwtUserPrincipal(UUID.randomUUID(), UserRole.MEMBER);
            ListingDetailResponse detail = listingService.getDetail(listing.getId(), principal);

            assertEquals(initialViews + 1, listing.getViewCount());
            assertNotNull(detail);
        }

        @Test
        @DisplayName("should show stock summary for SELLER role")
        void showStockSummaryForSeller() {
            when(listingRepository.findByIdAndStatusNot(listing.getId(), ListingStatus.REMOVED))
                .thenReturn(Optional.of(listing));
            when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));

            Warehouse wh = new Warehouse();
            wh.setId(UUID.randomUUID());
            wh.setName("WH-1");
            InventoryRecord ir = new InventoryRecord();
            ir.setWarehouse(wh);
            ir.setAvailableQty(20);
            ir.setReservedQty(3);
            ir.setLowStockThreshold(5);
            when(inventoryRecordRepository.findByListing_Id(listing.getId())).thenReturn(List.of(ir));

            JwtUserPrincipal sellerPrincipal = new JwtUserPrincipal(seller.getId(), UserRole.SELLER);
            ListingDetailResponse detail = listingService.getDetail(listing.getId(), sellerPrincipal);

            assertFalse(detail.stockSummary().isEmpty());
        }

        @Test
        @DisplayName("should hide stock summary for GUEST/null principal")
        void hideStockSummaryForGuest() {
            when(listingRepository.findByIdAndStatusNot(listing.getId(), ListingStatus.REMOVED))
                .thenReturn(Optional.of(listing));
            when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));

            ListingDetailResponse detail = listingService.getDetail(listing.getId(), null);

            assertTrue(detail.stockSummary().isEmpty());
        }
    }

    @Nested
    @DisplayName("createListing")
    class CreateListingTests {

        @Test
        @DisplayName("should create listing with sanitized inputs")
        void createListingSuccessfully() {
            JwtUserPrincipal principal = new JwtUserPrincipal(seller.getId(), UserRole.SELLER);
            when(userRepository.findById(seller.getId())).thenReturn(Optional.of(seller));
            when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateListingRequest request = new CreateListingRequest(
                null, "New<script>alert(1)</script> Desk", "A desk description",
                "furniture", BigDecimal.valueOf(300), List.of("vintage"),
                "Uptown", null, null, null, null, null
            );

            ListingDetailResponse response = listingService.createListing(request, principal);

            assertNotNull(response);
            assertFalse(response.title().contains("<script>"));
        }

        @Test
        @DisplayName("should reject invalid date range")
        void rejectInvalidDateRange() {
            JwtUserPrincipal principal = new JwtUserPrincipal(seller.getId(), UserRole.SELLER);

            CreateListingRequest request = new CreateListingRequest(
                null, "Desk", "Description", "furniture",
                BigDecimal.valueOf(100), null, "Downtown", null, null, null,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 5, 1)
            );

            ApiException ex = assertThrows(ApiException.class,
                () -> listingService.createListing(request, principal));

            assertEquals("INVALID_DATE_RANGE", ex.getCode());
        }
    }

    @Nested
    @DisplayName("softDeleteListing")
    class SoftDeleteTests {

        @Test
        @DisplayName("should mark listing as REMOVED")
        void markListingAsRemoved() {
            when(listingRepository.findByIdAndStatusNot(listing.getId(), ListingStatus.REMOVED))
                .thenReturn(Optional.of(listing));
            when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));

            JwtUserPrincipal principal = new JwtUserPrincipal(seller.getId(), UserRole.SELLER);
            listingService.softDeleteListing(listing.getId(), principal);

            assertEquals(ListingStatus.REMOVED, listing.getStatus());
        }

        @Test
        @DisplayName("should throw FORBIDDEN when non-owner seller tries to delete")
        void throwForbiddenForNonOwnerSeller() {
            when(listingRepository.findByIdAndStatusNot(listing.getId(), ListingStatus.REMOVED))
                .thenReturn(Optional.of(listing));

            JwtUserPrincipal otherSeller = new JwtUserPrincipal(UUID.randomUUID(), UserRole.SELLER);

            ApiException ex = assertThrows(ApiException.class,
                () -> listingService.softDeleteListing(listing.getId(), otherSeller));

            assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        }
    }
}
