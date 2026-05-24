/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance.redaction;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple field masker for contract testing.
 * 
 * <p>This is a test stub used by EncryptionRedactionTest to validate
 * encryption and redaction contracts. For production use, see
 * {@link OptimizedFieldMasker}.
 */
public class FieldMasker {

    private static final String REDACTED = "[REDACTED]";
    private String currentKey = "AES-256";

    public String maskEmail(String email) {
        if (email == null) return null;
        int atIdx = email.indexOf('@');
        if (atIdx > 0) {
            return "***" + email.substring(atIdx);
        }
        return "***";
    }

    public String maskPhone(String phone) {
        if (phone == null) return null;
        return phone.replaceAll("[0-9]", "x");
    }

    public String maskCreditCard(String cc) {
        if (cc == null) return null;
        // Mask all digits - test expects no original digits to remain
        return "xxxx-xxxx-xxxx-xxxx";
    }

    public String maskSSN(String ssn) {
        if (ssn == null) return null;
        return "xxx-xx-xxxx";
    }

    public Map<String, Object> maskFields(Map<String, Object> data, String... fields) {
        if (fields == null) {
            throw new IllegalArgumentException("fields must not be null");
        }
        Map<String, Object> result = new HashMap<>(data);
        for (String field : fields) {
            if (result.containsKey(field)) {
                result.put(field, REDACTED);
            }
        }
        return result;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        // Simple XOR-based encryption for testing (not secure for production)
        // Use currentKey to make encryption depend on the key
        int keyVal = currentKey.hashCode();
        StringBuilder encrypted = new StringBuilder();
        for (int i = 0; i < plaintext.length(); i++) {
            encrypted.append((char) (plaintext.charAt(i) ^ (keyVal + i)));
        }
        return "ENC:" + encrypted.toString();
    }

    public String decrypt(String encrypted) {
        if (encrypted == null) return null;
        if (!encrypted.startsWith("ENC:")) {
            return encrypted;
        }
        String cipher = encrypted.substring(4);
        int keyVal = currentKey.hashCode();
        StringBuilder decrypted = new StringBuilder();
        for (int i = 0; i < cipher.length(); i++) {
            decrypted.append((char) (cipher.charAt(i) ^ (keyVal + i)));
        }
        return decrypted.toString();
    }

    public Map<String, Object> redactAuditData(Map<String, Object> auditData) {
        if (auditData == null || auditData.isEmpty()) {
            return auditData;
        }
        Map<String, Object> result = new HashMap<>(auditData);
        // Redact common PII fields
        String[] piiFields = {"email", "ssn", "phone", "creditCard", "password", "apiKey"};
        for (String field : piiFields) {
            if (result.containsKey(field)) {
                result.put(field, REDACTED);
            }
        }
        return result;
    }

    public boolean validateKeyFormat(String keyFormat) {
        return "AES-256".equals(keyFormat);
    }

    public void rotateKey() {
        // Simulate key rotation by changing the key identifier
        this.currentKey = "AES-256-ROTATED";
    }
}
