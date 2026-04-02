package com.heritage.marketplace.common.security;

import com.heritage.marketplace.common.exception.ApiException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class FileUploadSecurityValidator {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/jpeg", "image/png", "application/pdf");

    public List<Map<String, Object>> validateEvidenceFiles(List<MultipartFile> files) {
        List<Map<String, Object>> errors = new ArrayList<>();
        if (files.size() > 5) {
            errors.add(Map.of("field", "evidence", "message", "A maximum of 5 evidence files is allowed"));
            return errors;
        }

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String fileName = sanitizeFileName(file.getOriginalFilename());

            if (file.getSize() > MAX_FILE_SIZE) {
                errors.add(Map.of("index", i, "fileName", fileName, "message", "File exceeds 10 MB limit"));
                continue;
            }

            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
                errors.add(Map.of("index", i, "fileName", fileName, "message", "Unsupported MIME type"));
                continue;
            }

            try {
                if (!magicBytesMatch(file, contentType)) {
                    errors.add(Map.of("index", i, "fileName", fileName, "message", "File content does not match declared MIME type"));
                }
            } catch (IOException ex) {
                errors.add(Map.of("index", i, "fileName", fileName, "message", "Failed to inspect file content"));
            }
        }
        return errors;
    }

    public String sanitizeFileName(String original) {
        String candidate = original == null ? "file" : original;
        return candidate.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private boolean magicBytesMatch(MultipartFile file, String mimeType) throws IOException {
        byte[] bytes = file.getInputStream().readNBytes(16);
        if (mimeType.equals("image/jpeg")) {
            return bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
        }
        if (mimeType.equals("image/png")) {
            return bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && (bytes[1] & 0xFF) == 0x50
                && (bytes[2] & 0xFF) == 0x4E
                && (bytes[3] & 0xFF) == 0x47
                && (bytes[4] & 0xFF) == 0x0D
                && (bytes[5] & 0xFF) == 0x0A
                && (bytes[6] & 0xFF) == 0x1A
                && (bytes[7] & 0xFF) == 0x0A;
        }
        if (mimeType.equals("application/pdf")) {
            return bytes.length >= 4
                && (bytes[0] & 0xFF) == 0x25
                && (bytes[1] & 0xFF) == 0x50
                && (bytes[2] & 0xFF) == 0x44
                && (bytes[3] & 0xFF) == 0x46;
        }

        throw new ApiException(HttpStatus.BAD_REQUEST, "EVIDENCE_VALIDATION_FAILED", "Unsupported file MIME type");
    }
}
