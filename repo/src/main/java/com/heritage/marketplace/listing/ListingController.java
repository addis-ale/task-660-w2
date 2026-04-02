package com.heritage.marketplace.listing;

import com.heritage.marketplace.auth.JwtUserPrincipal;
import com.heritage.marketplace.common.api.ApiMeta;
import com.heritage.marketplace.common.api.ApiResponse;
import com.heritage.marketplace.listing.dto.CreateListingRequest;
import com.heritage.marketplace.listing.dto.ListingDetailResponse;
import com.heritage.marketplace.listing.dto.ListingSearchResponse;
import com.heritage.marketplace.listing.dto.RecentSearchResponse;
import com.heritage.marketplace.listing.dto.UpdateListingRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/listings")
public class ListingController {

    private final ListingService listingService;

    public ListingController(ListingService listingService) {
        this.listingService = listingService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ListingSearchResponse>>> searchListings(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String neighborhood,
        @RequestParam(required = false) Double radius,
        @RequestParam(required = false) Double lat,
        @RequestParam(required = false) Double lng,
        @RequestParam(required = false) BigDecimal priceMin,
        @RequestParam(required = false) BigDecimal priceMax,
        @RequestParam(required = false) BigDecimal sqftMin,
        @RequestParam(required = false) BigDecimal sqftMax,
        @RequestParam(required = false) List<String> tags,
        @RequestParam(required = false) LocalDate availFrom,
        @RequestParam(required = false) LocalDate availTo,
        @RequestParam(defaultValue = "newest") String sort,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int pageSize,
        @AuthenticationPrincipal JwtUserPrincipal principal
    ) {
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        int safePage = Math.max(0, page);

        ListingSearchCriteria criteria = new ListingSearchCriteria(
            keyword,
            neighborhood,
            radius,
            lat,
            lng,
            priceMin,
            priceMax,
            sqftMin,
            sqftMax,
            tags,
            availFrom,
            availTo,
            sort,
            safePage,
            safePageSize
        );

        ListingPageResult result = listingService.search(criteria, principal);
        int totalPages = safePageSize == 0 ? 0 : (int) Math.ceil((double) result.totalItems() / safePageSize);
        ApiMeta meta = ApiMeta.of(safePage, safePageSize, result.totalItems(), totalPages);
        return ResponseEntity.ok(ApiResponse.success(result.items(), meta));
    }

    @GetMapping("/trending")
    public ResponseEntity<ApiResponse<List<ListingSearchResponse>>> trending(
        @RequestParam(defaultValue = "10") int limit
    ) {
        List<ListingSearchResponse> data = listingService.trending(limit);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/{listingId}")
    public ResponseEntity<ApiResponse<ListingDetailResponse>> detail(
        @PathVariable UUID listingId,
        @AuthenticationPrincipal JwtUserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(listingService.getDetail(listingId, principal)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    public ResponseEntity<ApiResponse<ListingDetailResponse>> create(
        @Valid @RequestBody CreateListingRequest request,
        @AuthenticationPrincipal JwtUserPrincipal principal
    ) {
        ListingDetailResponse response = listingService.createListing(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PutMapping("/{listingId}")
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    public ResponseEntity<ApiResponse<ListingDetailResponse>> update(
        @PathVariable UUID listingId,
        @Valid @RequestBody UpdateListingRequest request,
        @AuthenticationPrincipal JwtUserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(listingService.updateListing(listingId, request, principal)));
    }

    @DeleteMapping("/{listingId}")
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> delete(
        @PathVariable UUID listingId,
        @AuthenticationPrincipal JwtUserPrincipal principal
    ) {
        listingService.softDeleteListing(listingId, principal);
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Listing removed")));
    }

    @GetMapping("/recent-searches")
    public ResponseEntity<ApiResponse<List<RecentSearchResponse>>> recentSearches(
        @AuthenticationPrincipal JwtUserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(listingService.recentSearches(principal.userId())));
    }

    @DeleteMapping("/recent-searches")
    public ResponseEntity<ApiResponse<Map<String, String>>> clearRecentSearches(
        @AuthenticationPrincipal JwtUserPrincipal principal
    ) {
        listingService.clearRecentSearches(principal.userId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Recent searches cleared")));
    }
}
