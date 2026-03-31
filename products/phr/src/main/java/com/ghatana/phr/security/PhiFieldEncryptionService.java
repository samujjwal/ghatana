package com.ghatana.phr.security;

import com.ghatana.phr.kernel.policy.PhrDataClassification;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;

/**
 * Field-level encryption service for Protected Health Information (PHI).
 * <p>
 * Encrypts individual fields of patient records based on their data
 * classification tier (C1–C4). C3 and C4 fields always get field-level
 * encryption. C2 fields get field-level encryption when explicitly marked.
 * C1 (administrative) fields are not encrypted at the field level.
 * <p>
 * Encryption uses AES-256-GCM with per-field random IV.
 *
 * @doc.type class
 * @doc.purpose Field-level encryption for PHI fields based on data classification
 * @doc.layer product
 * @doc.pattern Service
 */
public class PhiFieldEncryptionService {
    private static final Logger logger = LoggerFactory.getLogger(PhiFieldEncryptionService.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final SecretKey encryptionKey;
    private final SecureRandom secureRandom;

    /**
     * Fields that require field-level encryption at C3/C4 classification.
     * These are the HIPAA-designated PHI identifiers.
     */
    private static final Set<String> PHI_FIELDS = Set.of(
            "ssn", "socialSecurityNumber",
            "dateOfBirth", "dob",
            "address", "streetAddress", "zipCode",
            "phoneNumber", "phone", "mobilePhone",
            "email", "emailAddress",
            "medicalRecordNumber", "mrn",
            "healthPlanBeneficiaryNumber",
            "diagnosis", "diagnosisCode",
            "labResult", "labValue",
            "medication", "prescription",
            "geneticData",
            "biometricIdentifier"
    );

    public PhiFieldEncryptionService(SecretKey encryptionKey) {
        this.encryptionKey = encryptionKey;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Encrypt PHI fields in a record based on classification tier.
     *
     * @param fields         the field name → value map
     * @param classification the data classification tier
     * @return a new map with PHI fields encrypted (prefixed with {@code ENC:})
     */
    public Promise<Map<String, String>> encryptFields(
            Map<String, String> fields,
            PhrDataClassification classification
    ) {
        return Promise.of(doEncrypt(fields, classification));
    }

    /**
     * Decrypt PHI fields in a record.
     *
     * @param fields the field name → value map (encrypted fields prefixed with {@code ENC:})
     * @return a new map with decrypted values
     */
    public Promise<Map<String, String>> decryptFields(Map<String, String> fields) {
        return Promise.of(doDecrypt(fields));
    }

    /**
     * Check whether a field name is classified as PHI.
     */
    public static boolean isPhiField(String fieldName) {
        return PHI_FIELDS.contains(fieldName);
    }

    private Map<String, String> doEncrypt(Map<String, String> fields, PhrDataClassification classification) {
        boolean requiresFieldEncryption = classification == PhrDataClassification.C3
                || classification == PhrDataClassification.C4;

        if (!requiresFieldEncryption) {
            return fields;
        }

        Map<String, String> result = new LinkedHashMap<>(fields.size());
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (value != null && PHI_FIELDS.contains(key)) {
                result.put(key, encryptValue(value));
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    private Map<String, String> doDecrypt(Map<String, String> fields) {
        Map<String, String> result = new LinkedHashMap<>(fields.size());
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String value = entry.getValue();
            if (value != null && value.startsWith("ENC:")) {
                result.put(entry.getKey(), decryptValue(value.substring(4)));
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    private String encryptValue(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Combine IV + ciphertext
            byte[] combined = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, combined, IV_LENGTH, ciphertext.length);

            return "ENC:" + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            logger.error("Failed to encrypt PHI field value", e);
            throw new SecurityException("PHI field encryption failed", e);
        }
    }

    private String decryptValue(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Failed to decrypt PHI field value", e);
            throw new SecurityException("PHI field decryption failed", e);
        }
    }
}
