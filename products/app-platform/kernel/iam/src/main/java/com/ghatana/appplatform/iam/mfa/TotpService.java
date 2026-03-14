package com.ghatana.appplatform.iam.mfa;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * TOTP (Time-based One-Time Password) engine per RFC 6238 (STORY-K01-004).
 *
 * <p>Each secret is Base32-encoded; steps are 30 seconds; digits are 6.
 * Accepts a ±1 step window (90 seconds total) to tolerate clock drift.
 *
 * <p>Backup codes: 10 random 8-digit single-use codes generated at enrollment
 * and stored as SHA-256 hashes to prevent leakage at rest.
 *
 * @doc.type class
 * @doc.purpose RFC 6238 TOTP one-time password generation and verification (K01-004)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class TotpService {

    private static final int DIGITS = 6;
    private static final int STEP_SECONDS = 30;
    private static final int WINDOW = 1; // ±1 step (total 3 steps checked)
    private static final int BACKUP_CODE_COUNT = 10;
    private static final int SECRET_BYTES = 20; // 160-bit secret (TOTP recommendation)

    private final SecureRandom random = new SecureRandom();

    /**
     * Generates a new Base32-encoded TOTP secret.
     *
     * @return Base32 secret string (for storage and QR code URI)
     */
    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return base32Encode(bytes);
    }

    /**
     * Builds an {@code otpauth://totp/} URI for QR code rendering in authenticator apps.
     *
     * @param issuer     application name, e.g. "Ghatana Finance"
     * @param accountId  user identifier shown in the authenticator
     * @param secret     Base32-encoded TOTP secret
     * @return RFC 6238 otpauth URI
     */
    public String buildQrUri(String issuer, String accountId, String secret) {
        return "otpauth://totp/" + issuer + ":" + accountId
            + "?secret=" + secret
            + "&issuer=" + issuer
            + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    /**
     * Verifies a 6-digit TOTP code against a stored secret, with ±1 step tolerance.
     *
     * @param secret     Base32-encoded secret (from enrollment)
     * @param userCode   6-digit code submitted by the user
     * @return {@code true} if the code is valid within the clock-drift window
     */
    public boolean verify(String secret, String userCode) {
        if (secret == null || userCode == null || userCode.length() != DIGITS) return false;
        long currentStep = Instant.now().getEpochSecond() / STEP_SECONDS;
        byte[] keyBytes = base32Decode(secret);

        for (int delta = -WINDOW; delta <= WINDOW; delta++) {
            String expected = computeTotp(keyBytes, currentStep + delta);
            if (MessageDigest.isEqual(expected.getBytes(), userCode.getBytes())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generates {@value #BACKUP_CODE_COUNT} single-use backup codes.
     * Each code is an 8-digit string; callers should store them as SHA-256 hashes.
     *
     * @return immutable list of 10 backup codes (plaintext — show to user once)
     */
    public List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>(BACKUP_CODE_COUNT);
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            long value = Math.abs(random.nextLong()) % 100_000_000L;
            codes.add(String.format("%08d", value));
        }
        return List.copyOf(codes);
    }

    // ─── TOTP internals (RFC 6238 / RFC 4226) ────────────────────────────────

    private String computeTotp(byte[] key, long step) {
        try {
            byte[] msg = ByteBuffer.allocate(8).putLong(step).array();
            Mac hmac = Mac.getInstance("HmacSHA1");
            hmac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = hmac.doFinal(msg);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                       | ((hash[offset + 1] & 0xFF) << 16)
                       | ((hash[offset + 2] & 0xFF) << 8)
                       |  (hash[offset + 3] & 0xFF);
            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", otp);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("TOTP computation failed", e);
        }
    }

    // ─── Constant-time comparison (reuse JDK built-in) ───────────────────────
    private static final java.security.MessageDigest SHA256;
    static {
        try { SHA256 = java.security.MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new ExceptionInInitializerError(e); }
    }

    // ─── Minimal Base32 ──────────────────────────────────────────────────────

    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    static String base32Encode(byte[] input) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bitsLeft = 0;
        for (byte b : input) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                sb.append(BASE32_CHARS.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32_CHARS.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return sb.toString();
    }

    static byte[] base32Decode(String input) {
        input = input.toUpperCase().replaceAll("[=\\s]", "");
        int buffer = 0, bitsLeft = 0, idx = 0;
        byte[] result = new byte[input.length() * 5 / 8];
        for (char c : input.toCharArray()) {
            int val = BASE32_CHARS.indexOf(c);
            if (val < 0) continue;
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[idx++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return idx == result.length ? result : java.util.Arrays.copyOf(result, idx);
    }
}
