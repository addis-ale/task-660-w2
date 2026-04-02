package com.heritage.marketplace.listing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ListingSearchCriteria(
    String keyword,
    String neighborhood,
    Double radiusMiles,
    Double lat,
    Double lng,
    BigDecimal priceMin,
    BigDecimal priceMax,
    BigDecimal sqftMin,
    BigDecimal sqftMax,
    List<String> tags,
    LocalDate availFrom,
    LocalDate availTo,
    String sort,
    int page,
    int pageSize
) {
}
