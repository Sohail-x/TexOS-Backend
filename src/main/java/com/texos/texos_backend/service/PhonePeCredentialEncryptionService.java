package com.texos.texos_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class PhonePeCredentialEncryptionService {
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final byte[] key;

    public PhonePeCredentialEncryptionService(
            @Value("${app.phonepe.credential-encryption-key:}") String configuredKey) {
        if (configuredKey == null || configuredKey.isBlank()) {
            key = null;
            return;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(configuredKey);
            if (decoded.length != 32) {
                throw new IllegalArgumentException("PhonePe credential encryption key must decode to 32 bytes");
            }
            key = decoded;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "TEXOS_PHONEPE_CREDENTIAL_ENCRYPTION_KEY must be base64-encoded 32-byte key material", ex);
        }
    }

    public String encrypt(String value) {
        requireKey();
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            SECURE_RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + ciphertext.length)
                    .put(nonce).put(ciphertext).array());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encrypt PhonePe credentials", ex);
        }
    }

    public String decrypt(String value) {
        requireKey();
        try {
            byte[] packed = Base64.getDecoder().decode(value);
            if (packed.length <= NONCE_LENGTH) {
                throw new IllegalArgumentException("Invalid encrypted PhonePe credential");
            }
            ByteBuffer input = ByteBuffer.wrap(packed);
            byte[] nonce = new byte[NONCE_LENGTH];
            input.get(nonce);
            byte[] ciphertext = new byte[input.remaining()];
            input.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to decrypt PhonePe credentials", ex);
        }
    }

    public void requireKey() {
        if (key == null) {
            throw new IllegalStateException(
                    "PhonePe credentials cannot be stored until TEXOS_PHONEPE_CREDENTIAL_ENCRYPTION_KEY is configured");
        }
    }
}
