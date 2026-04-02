package com.heritage.marketplace.listing;

import com.heritage.marketplace.listing.dto.ListingSearchResponse;
import java.util.List;

public record ListingPageResult(List<ListingSearchResponse> items, long totalItems) {
}
