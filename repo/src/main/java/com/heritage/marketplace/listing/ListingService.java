package com.heritage.marketplace.listing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.marketplace.auth.JwtUserPrincipal;
import com.heritage.marketplace.common.exception.ApiException;
import com.heritage.marketplace.common.security.InputSanitizer;
import com.heritage.marketplace.inventory.InventoryRecord;
import com.heritage.marketplace.inventory.InventoryRecordRepository;
import com.heritage.marketplace.listing.dto.CreateListingRequest;
import com.heritage.marketplace.listing.dto.ListingDetailResponse;
import com.heritage.marketplace.listing.dto.ListingSearchResponse;
import com.heritage.marketplace.listing.dto.RecentSearchResponse;
import com.heritage.marketplace.listing.dto.StockSummaryResponse;
import com.heritage.marketplace.listing.dto.TierPricingResponse;
import com.heritage.marketplace.listing.dto.UpdateListingRequest;
import com.heritage.marketplace.tier.BenefitPackage;
import com.heritage.marketplace.tier.BenefitPackageRepository;
import com.heritage.marketplace.tier.BenefitType;
import com.heritage.marketplace.tier.Membership;
import com.heritage.marketplace.tier.MembershipRepository;
import com.heritage.marketplace.user.User;
import com.heritage.marketplace.user.UserRepository;
import com.heritage.marketplace.user.UserRole;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListingService {

    private final ListingRepository listingRepository;
    private final ListingSearchRepository listingSearchRepository;
    private final RecentSearchRepository recentSearchRepository;
    private final UserRepository userRepository;
    private final InventoryRecordRepository inventoryRecordRepository;
    private final MembershipRepository membershipRepository;
    private final BenefitPackageRepository benefitPackageRepository;
    private final ObjectMapper objectMapper;
    private final InputSanitizer inputSanitizer;

    public ListingService(
        ListingRepository listingRepository,
        ListingSearchRepository listingSearchRepository,
        RecentSearchRepository recentSearchRepository,
        UserRepository userRepository,
        InventoryRecordRepository inventoryRecordRepository,
        MembershipRepository membershipRepository,
        BenefitPackageRepository benefitPackageRepository,
        ObjectMapper objectMapper,
        InputSanitizer inputSanitizer
    ) {
        this.listingRepository = listingRepository;
        this.listingSearchRepository = listingSearchRepository;
        this.recentSearchRepository = recentSearchRepository;
        this.userRepository = userRepository;
        this.inventoryRecordRepository = inventoryRecordRepository;
        this.membershipRepository = membershipRepository;
        this.benefitPackageRepository = benefitPackageRepository;
        this.objectMapper = objectMapper;
        this.inputSanitizer = inputSanitizer;
    }

    public ListingPageResult search(ListingSearchCriteria criteria, JwtUserPrincipal principal) {
        ListingPageResult result = listingSearchRepository.search(criteria);
        if (principal != null) {
            saveRecentSearch(principal.userId(), criteria);
        }
        return result;
    }

    public List<ListingSearchResponse> trending(int limit) {
        int safeLimit = Math.max(1, Math.min(50, limit));
        listingSearchRepository.refreshTrendingScores();

        return listingRepository.findByStatusOrderByTrendingScoreDesc(ListingStatus.ACTIVE, PageRequest.of(0, safeLimit))
            .stream()
            .map(this::toSearchResponse)
            .toList();
    }

    @Transactional
    public ListingDetailResponse getDetail(UUID listingId, JwtUserPrincipal principal) {
        Listing listing = getActiveListing(listingId);
        listing.setViewCount(listing.getViewCount() + 1);
        listing.setTrendingScore(computeTrendingScore(listing));
        listingRepository.save(listing);

        List<StockSummaryResponse> stockSummary = shouldShowStockSummary(principal)
            ? buildStockSummary(listing.getId())
            : List.of();

        TierPricingResponse tierPricing = shouldShowTierPricing(principal)
            ? resolveTierPricing(principal.userId(), listing)
            : null;

        return toDetailResponse(listing, stockSummary, tierPricing);
    }

    @Transactional
    public ListingDetailResponse createListing(CreateListingRequest request, JwtUserPrincipal principal) {
        validateDateRange(request.availabilityStart(), request.availabilityEnd());

        UUID sellerId = resolveSellerId(request.sellerId(), principal);
        User seller = userRepository.findById(sellerId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SELLER_NOT_FOUND", "Seller was not found"));

        Listing listing = new Listing();
        listing.setId(UUID.randomUUID());
        listing.setSeller(seller);
        listing.setTitle(inputSanitizer.sanitize(request.title()));
        listing.setDescription(inputSanitizer.sanitize(request.description()));
        listing.setCategory(inputSanitizer.sanitize(request.category()));
        listing.setPrice(request.price());
        listing.setTags(toTagArray(request.tags()));
        listing.setNeighborhood(inputSanitizer.sanitize(request.neighborhood()));
        listing.setLatitude(request.latitude());
        listing.setLongitude(request.longitude());
        listing.setLayoutSqft(request.layoutSqft());
        listing.setAvailabilityStart(request.availabilityStart());
        listing.setAvailabilityEnd(request.availabilityEnd());
        listing.setStatus(ListingStatus.ACTIVE);
        listing.setViewCount(0);
        listing.setOrderCount7d(0);
        listing.setTrendingScore(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        listing.setCreatedAt(LocalDateTime.now());

        listingRepository.save(listing);
        return toDetailResponse(listing, List.of(), null);
    }

    @Transactional
    public ListingDetailResponse updateListing(UUID listingId, UpdateListingRequest request, JwtUserPrincipal principal) {
        Listing listing = getActiveListing(listingId);
        ensureCanManageListing(listing, principal);
        validateDateRange(request.availabilityStart(), request.availabilityEnd());

        listing.setTitle(inputSanitizer.sanitize(request.title()));
        listing.setDescription(inputSanitizer.sanitize(request.description()));
        listing.setCategory(inputSanitizer.sanitize(request.category()));
        listing.setPrice(request.price());
        listing.setTags(toTagArray(request.tags()));
        listing.setNeighborhood(inputSanitizer.sanitize(request.neighborhood()));
        listing.setLatitude(request.latitude());
        listing.setLongitude(request.longitude());
        listing.setLayoutSqft(request.layoutSqft());
        listing.setAvailabilityStart(request.availabilityStart());
        listing.setAvailabilityEnd(request.availabilityEnd());

        listingRepository.save(listing);
        return toDetailResponse(listing, List.of(), null);
    }

    @Transactional
    public void softDeleteListing(UUID listingId, JwtUserPrincipal principal) {
        Listing listing = getActiveListing(listingId);
        ensureCanManageListing(listing, principal);
        listing.setStatus(ListingStatus.REMOVED);
        listingRepository.save(listing);
    }

    public List<RecentSearchResponse> recentSearches(UUID userId) {
        return recentSearchRepository.findTop20ByUser_IdOrderBySearchedAtDesc(userId)
            .stream()
            .map(rs -> new RecentSearchResponse(rs.getId(), rs.getQuery(), rs.getFilters(), rs.getSearchedAt()))
            .toList();
    }

    public void clearRecentSearches(UUID userId) {
        recentSearchRepository.deleteByUser_Id(userId);
    }

    private Listing getActiveListing(UUID listingId) {
        return listingRepository.findByIdAndStatusNot(listingId, ListingStatus.REMOVED)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LISTING_NOT_FOUND", "Listing was not found"));
    }

    private ListingSearchResponse toSearchResponse(Listing listing) {
        return new ListingSearchResponse(
            listing.getId(),
            listing.getTitle(),
            listing.getCategory(),
            listing.getPrice(),
            toTagList(listing.getTags()),
            listing.getNeighborhood(),
            listing.getTrendingScore(),
            listing.getStatus(),
            listing.getCreatedAt(),
            null
        );
    }

    private List<StockSummaryResponse> buildStockSummary(UUID listingId) {
        List<InventoryRecord> records = inventoryRecordRepository.findByListing_Id(listingId);
        return records.stream()
            .map(ir -> new StockSummaryResponse(
                ir.getWarehouse().getId(),
                ir.getWarehouse().getName(),
                ir.getAvailableQty(),
                ir.getReservedQty(),
                ir.getLowStockThreshold(),
                ir.getAvailableQty() < ir.getLowStockThreshold()
            ))
            .toList();
    }

    private TierPricingResponse resolveTierPricing(UUID userId, Listing listing) {
        Membership membership = membershipRepository.findByUser_Id(userId).orElse(null);
        if (membership == null) {
            return null;
        }

        LocalDate today = LocalDate.now();
        return benefitPackageRepository.findByTier_Id(membership.getTier().getId())
            .stream()
            .filter(bp -> bp.getType() == BenefitType.EXCLUSIVE_PRICE)
            .filter(bp -> isApplicable(bp, listing, today))
            .max((left, right) -> Integer.compare(left.getPriority(), right.getPriority()))
            .map(bp -> new TierPricingResponse(
                bp.getValue(),
                membership.getTier().getName(),
                "Exclusive pricing is active for your membership tier"
            ))
            .orElse(null);
    }

    private boolean isApplicable(BenefitPackage benefit, Listing listing, LocalDate today) {
        boolean categoryOk = benefit.getScopeCategory() == null || benefit.getScopeCategory().equalsIgnoreCase(listing.getCategory());
        boolean sellerOk = benefit.getScopeSeller() == null || benefit.getScopeSeller().getId().equals(listing.getSeller().getId());
        boolean startOk = benefit.getScopeDateStart() == null || !today.isBefore(benefit.getScopeDateStart());
        boolean endOk = benefit.getScopeDateEnd() == null || !today.isAfter(benefit.getScopeDateEnd());
        return categoryOk && sellerOk && startOk && endOk;
    }

    private ListingDetailResponse toDetailResponse(
        Listing listing,
        List<StockSummaryResponse> stockSummary,
        TierPricingResponse tierPricing
    ) {
        return new ListingDetailResponse(
            listing.getId(),
            listing.getSeller().getId(),
            listing.getTitle(),
            listing.getDescription(),
            listing.getCategory(),
            listing.getPrice(),
            toTagList(listing.getTags()),
            listing.getNeighborhood(),
            listing.getLatitude(),
            listing.getLongitude(),
            listing.getLayoutSqft(),
            listing.getAvailabilityStart(),
            listing.getAvailabilityEnd(),
            listing.getStatus(),
            listing.getViewCount(),
            listing.getOrderCount7d(),
            listing.getTrendingScore(),
            listing.getCreatedAt(),
            stockSummary,
            tierPricing
        );
    }

    private String[] toTagArray(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return new String[0];
        }
        return tags.stream()
            .filter(tag -> tag != null && !tag.isBlank())
            .map(inputSanitizer::sanitize)
            .toArray(String[]::new);
    }

    private List<String> toTagList(String[] tags) {
        if (tags == null || tags.length == 0) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String tag : tags) {
            if (tag != null && !tag.isBlank()) {
                values.add(tag);
            }
        }
        return values;
    }

    private boolean shouldShowStockSummary(JwtUserPrincipal principal) {
        if (principal == null) {
            return false;
        }
        return principal.role() == UserRole.SELLER
            || principal.role() == UserRole.WAREHOUSE_STAFF
            || principal.role() == UserRole.ADMIN;
    }

    private boolean shouldShowTierPricing(JwtUserPrincipal principal) {
        return principal != null && principal.role() == UserRole.MEMBER;
    }

    private void ensureCanManageListing(Listing listing, JwtUserPrincipal principal) {
        if (principal.role() == UserRole.ADMIN) {
            return;
        }
        if (principal.role() == UserRole.SELLER && listing.getSeller().getId().equals(principal.userId())) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You cannot modify this listing");
    }

    private UUID resolveSellerId(UUID requestedSellerId, JwtUserPrincipal principal) {
        if (principal.role() == UserRole.ADMIN) {
            if (requestedSellerId != null) {
                return requestedSellerId;
            }
            return principal.userId();
        }
        return principal.userId();
    }

    private void validateDateRange(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "availability_end must be on or after availability_start");
        }
    }

    private void saveRecentSearch(UUID userId, ListingSearchCriteria criteria) {
        Map<String, Object> filters = new HashMap<>();
        filters.put("neighborhood", criteria.neighborhood());
        filters.put("radiusMiles", criteria.radiusMiles());
        filters.put("lat", criteria.lat());
        filters.put("lng", criteria.lng());
        filters.put("priceMin", criteria.priceMin());
        filters.put("priceMax", criteria.priceMax());
        filters.put("sqftMin", criteria.sqftMin());
        filters.put("sqftMax", criteria.sqftMax());
        filters.put("tags", criteria.tags());
        filters.put("availFrom", criteria.availFrom());
        filters.put("availTo", criteria.availTo());
        filters.put("sort", criteria.sort());

        String json;
        try {
            json = objectMapper.writeValueAsString(filters);
        } catch (JsonProcessingException ex) {
            json = "{}";
        }

        listingSearchRepository.saveRecentSearch(userId, criteria.keyword(), json);
    }

    private BigDecimal computeTrendingScore(Listing listing) {
        BigDecimal views = BigDecimal.valueOf(listing.getViewCount()).multiply(BigDecimal.valueOf(0.4));
        BigDecimal orders = BigDecimal.valueOf(listing.getOrderCount7d()).multiply(BigDecimal.valueOf(0.6));
        return views.add(orders).setScale(2, RoundingMode.HALF_UP);
    }
}
