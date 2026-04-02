package com.heritage.marketplace.common.util;

import com.heritage.marketplace.common.exception.ApiException;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class EncryptionUtil {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_SIZE_BYTES = 12;
    private static final int TAG_SIZE_BITS = 128;

    private final String rawSecret;
    private SecretKey secretKey;

    public EncryptionUtil(@Value("${app.encryption.secret:change-this-encryption-secret}") String rawSecret) {
        this.rawSecret = rawSecret;
    }

    @PostConstruct
    void init() {
        try {
            byte[] key = MessageDigest.getInstance("SHA-256").digest(rawSecret.getBytes(StandardCharsets.UTF_8));
            this.secretKey = new SecretKeySpec(key, "AES");
        } catch (GeneralSecurityException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ENCRYPTION_INIT_FAILED", "Unable to initialize encryption key");
        }
    }

    public String encryptDeterministic(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return null;
        }

        try {
            byte[] iv = Arrays.copyOf(MessageDigest.getInstance("SHA-256").digest(plainText.getBytes(StandardCharsets.UTF_8)), IV_SIZE_BYTES);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_SIZE_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ENCRYPTION_FAILED", "Unable to encrypt sensitive value");
        }
    }

    public String decrypt(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isBlank()) {
            return null;
        }

        try {
            byte[] combined = Base64.getDecoder().decode(encryptedValue);
            if (combined.length <= IV_SIZE_BYTES) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "DECRYPTION_FAILED", "Encrypted payload is malformed");
            }

            byte[] iv = Arrays.copyOfRange(combined, 0, IV_SIZE_BYTES);
            byte[] cipherText = Arrays.copyOfRange(combined, IV_SIZE_BYTES, combined.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_SIZE_BITS, iv));
            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "DECRYPTION_FAILED", "Unable to decrypt sensitive value");
        }
    }
}
