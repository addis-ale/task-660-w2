package com.heritage.marketplace.appeal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.UUID;

public record AppealEvidenceResponse(
    UUID id,
    @JsonProperty("file_name")
    String fileName,
    @JsonProperty("mime_type")
    String mimeType,
    @JsonProperty("file_size_bytes")
    long fileSizeBytes,
    @JsonProperty("uploaded_at")
    LocalDateTime uploadedAt
) {
}
