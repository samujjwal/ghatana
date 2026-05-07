package com.ghatana.kernel.security;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

/**
 * In-memory implementation of {@link PrivacyManager} for testing.
 *
 * @doc.type class
 * @doc.purpose In-memory privacy manager for testing (KERNEL-P1)
 * @doc.layer core
 * @doc.pattern Service
 */
public final class InMemoryPrivacyManager implements PrivacyManager {

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryPrivacyManager.class);

    private final Map<String, Policy> policies = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Boolean>> consents = new ConcurrentHashMap<>();
    private final Map<String, String> encryptionKeys = new ConcurrentHashMap<>();
    private final Executor executor;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\+?[0-9]{10,15}");
    private static final Pattern SSN_PATTERN = Pattern.compile("\\d{3}-?\\d{2}-?\\d{4}");

    public InMemoryPrivacyManager(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public Promise<ConsentStatus> checkConsent(DataRequest request, String tenantId) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        return Promise.ofBlocking(executor, () -> {
            Map<String, Boolean> tenantConsents = consents.get(tenantId);
            if (tenantConsents == null) {
                return ConsentStatus.NOT_REQUIRED;
            }
            Boolean granted = tenantConsents.get(request.getPurpose());
            return granted != null ? (granted ? ConsentStatus.GRANTED : ConsentStatus.DENIED) : ConsentStatus.PENDING;
        });
    }

    @Override
    public DataClassification classifyData(Object data) {
        if (data == null) {
            return DataClassification.PUBLIC;
        }

        String dataStr = data.toString().toLowerCase();
        if (dataStr.contains("ssn") || dataStr.contains("social security") || SSN_PATTERN.matcher(dataStr).find()) {
            return DataClassification.PII;
        }
        if (EMAIL_PATTERN.matcher(dataStr).find()) {
            return DataClassification.PII;
        }
        if (PHONE_PATTERN.matcher(dataStr).find()) {
            return DataClassification.PII;
        }
        if (dataStr.contains("credit card") || dataStr.contains("account number")) {
            return DataClassification.RESTRICTED;
        }
        if (dataStr.contains("health") || dataStr.contains("medical")) {
            return DataClassification.PHI;
        }
        return DataClassification.INTERNAL;
    }

    @Override
    public Promise<Boolean> enforceResidency(DataLocation location, String tenantId) {
        Objects.requireNonNull(location, "location must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        return Promise.ofBlocking(executor, () -> {
            Policy policy = policies.get(tenantId);
            if (policy == null) {
                LOG.info("[PRIVACY-MANAGER] No policy for tenant {}, residency check defaults to true", tenantId);
                return true;
            }
            boolean allowed = location.getCountry().equals("US") || location.getCountry().equals("EU");
            LOG.info("[PRIVACY-MANAGER] Residency check tenant={} location={} allowed={}", tenantId, location.getCountry(), allowed);
            return allowed;
        });
    }

    @Override
    public Promise<Void> recordConsent(String tenantId, String userId, String purpose, boolean granted) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(purpose, "purpose must not be null");

        return Promise.ofBlocking(executor, () -> {
            Map<String, Boolean> tenantConsents = consents.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
            tenantConsents.put(userId + ":" + purpose, granted);
            LOG.info("[PRIVACY-MANAGER] Recorded consent tenant={} userId={} purpose={} granted={}", tenantId, userId, purpose, granted);
            return null;
        });
    }

    @Override
    public Promise<Optional<Policy>> getPrivacyPolicy(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        return Promise.ofBlocking(executor, () -> Optional.ofNullable(policies.get(tenantId)));
    }

    @Override
    public Promise<String> encryptPII(String tenantId, String pii) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(pii, "pii must not be null");

        return Promise.ofBlocking(executor, () -> {
            String key = encryptionKeys.computeIfAbsent(tenantId, k -> UUID.randomUUID().toString());
            String encrypted = Base64.getEncoder().encodeToString((pii + ":" + key).getBytes(StandardCharsets.UTF_8));
            LOG.info("[PRIVACY-MANAGER] Encrypted PII tenant={} length={}", tenantId, pii.length());
            return encrypted;
        });
    }

    @Override
    public Promise<String> decryptPII(String tenantId, String encryptedPii) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(encryptedPii, "encryptedPii must not be null");

        return Promise.ofBlocking(executor, () -> {
            String key = encryptionKeys.get(tenantId);
            if (key == null) {
                throw new IllegalArgumentException("No encryption key found for tenant: " + tenantId);
            }
            String decoded = new String(Base64.getDecoder().decode(encryptedPii), StandardCharsets.UTF_8);
            String pii = decoded.substring(0, decoded.lastIndexOf(":"));
            LOG.info("[PRIVACY-MANAGER] Decrypted PII tenant={} length={}", tenantId, pii.length());
            return pii;
        });
    }

    @Override
    public Promise<String> hashPIIIdentifier(String tenantId, String identifier) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(identifier, "identifier must not be null");

        return Promise.ofBlocking(executor, () -> {
            String key = encryptionKeys.computeIfAbsent(tenantId, k -> UUID.randomUUID().toString());
            String hashed = Base64.getEncoder().encodeToString(
                (identifier + ":" + key).getBytes(StandardCharsets.UTF_8)
            ).substring(0, 16);
            LOG.info("[PRIVACY-MANAGER] Hashed PII identifier tenant={}", tenantId);
            return hashed;
        });
    }

    @Override
    public String redactPII(String data, DataClassification classification) {
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(classification, "classification must not be null");

        if (classification == DataClassification.PUBLIC || classification == DataClassification.INTERNAL) {
            return data;
        }

        String redacted = data;
        redacted = EMAIL_PATTERN.matcher(redacted).replaceAll("[REDACTED-EMAIL]");
        redacted = PHONE_PATTERN.matcher(redacted).replaceAll("[REDACTED-PHONE]");
        redacted = SSN_PATTERN.matcher(redacted).replaceAll("[REDACTED-SSN]");
        
        if (classification == DataClassification.RESTRICTED || classification == DataClassification.PHI || classification == DataClassification.PII) {
            redacted = redacted.replaceAll("\\b\\d{16}\\b", "[REDACTED-CARD]");
        }

        LOG.debug("[PRIVACY-MANAGER] Redacted PII classification={} originalLength={} redactedLength={}",
            classification, data.length(), redacted.length());
        return redacted;
    }

    @Override
    public Promise<DSarResult> processDSAR(DSARRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        return Promise.ofBlocking(executor, () -> {
            LOG.info("[PRIVACY-MANAGER] Processing DSAR requestId={} tenantId={} subjectId={} type={}",
                request.requestId(), request.tenantId(), request.subjectId(), request.type());

            Map<String, Object> data = Map.of(
                "subjectId", request.subjectId(),
                "tenantId", request.tenantId(),
                "requestId", request.requestId(),
                "processedAt", Instant.now().toString()
            );

            return new DSarResult(
                request.requestId(),
                DSARStatus.COMPLETED,
                data,
                "DSAR processed successfully",
                Instant.now()
            );
        });
    }

    @Override
    public Promise<Void> deleteSubjectData(String tenantId, String subjectId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(subjectId, "subjectId must not be null");

        return Promise.ofBlocking(executor, () -> {
            consents.remove(tenantId);
            encryptionKeys.remove(tenantId);
            LOG.info("[PRIVACY-MANAGER] Deleted subject data tenant={} subjectId={}", tenantId, subjectId);
            return null;
        });
    }

    @Override
    public Promise<Map<String, Object>> exportSubjectData(String tenantId, String subjectId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(subjectId, "subjectId must not be null");

        return Promise.ofBlocking(executor, () -> {
            LOG.info("[PRIVACY-MANAGER] Exporting subject data tenant={} subjectId={}", tenantId, subjectId);
            return Map.of(
                "subjectId", subjectId,
                "tenantId", tenantId,
                "exportedAt", Instant.now().toString(),
                "consents", consents.getOrDefault(tenantId, Map.of()),
                "dataRetention", new DataRetention(365, 2555, 90)
            );
        });
    }
}
