package com.heritage.marketplace.appeal.dto;

import java.util.List;

public record AppealDetailResponse(
    AppealResponse appeal,
    List<AppealEvidenceResponse> evidence
) {
}
