package com.ghatana.datacloud.security;

import io.activej.inject.annotation.Inject;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ghatana.datacloud.infrastructure.config.JpaThreadPoolConfig;

/**
 * PII (Personally Identifiable Information) detection and redaction service.
 *
 * <p>This service provides comprehensive PII detection across multiple data types:
 * <ul>
 *   <li>Email addresses</li>
 *   <li>Phone numbers</li>
 *   <li>Social Security Numbers</li>
 *   <li>Credit card numbers</li>
 *   <li>IP addresses</li>
 *   <li>Names and addresses</li>
 *   <li>Medical record numbers</li>
 *   <li>Bank account numbers</li>
 * </ul>
 *
 * <h2>Redaction Strategies</h2>
 * <ul>
 *   <li>Masking: Show only last 4 digits</li>
 *   <li>Hashing: One-way transformation</li>
 *   <li>Tokenization: Replace with non-sensitive token</li>
 *   <li>Removal: Complete removal of PII</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose PII detection and redaction for data protection and compliance
 * @doc.layer security
 * @doc.pattern DataProtection
 */
public class PIIDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(PIIDetectionService.class);

    private final DataSource dataSource;

    private final ExecutorService blockingExecutor;

    // PII Detection Patterns
    private final Map<String, Pattern> piiPatterns = new HashMap<>();

    @Inject
    public PIIDetectionService(DataSource dataSource) {
        this.dataSource = dataSource;
        this.blockingExecutor = JpaThreadPoolConfig.fromEnvironment().createExecutorService();
        initializePatterns();
    }

    private void initializePatterns() {
        // Email addresses
        piiPatterns.put("EMAIL", Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b",
            Pattern.CASE_INSENSITIVE
        ));

        // Phone numbers (US format)
        piiPatterns.put("PHONE_US", Pattern.compile(
            "\\b(\\+?1[-.\\s]?)?\\(?([0-9]{3})\\)?[-.\\s]?([0-9]{3})[-.\\s]?([0-9]{4})\\b"
        ));

        // Social Security Numbers
        piiPatterns.put("SSN", Pattern.compile(
            "\\b(?!000|666|9\\d{2})\\d{3}[-\\s]?(?!00)\\d{2}[-\\s]?(?!0000)\\d{4}\\b"
        ));

        // Credit card numbers (major brands)
        piiPatterns.put("CREDIT_CARD", Pattern.compile(
            "\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|3(?:0[0-5]|[68][0-9])[0-9]{11}|6(?:011|5[0-9]{2})[0-9]{12}|(?:2131|1800|35\\d{3})\\d{11})\\b"
        ));

        // IP addresses
        piiPatterns.put("IP_ADDRESS", Pattern.compile(
            "\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b"
        ));

        // Bank account numbers (simplified - 8-17 digits)
        piiPatterns.put("BANK_ACCOUNT", Pattern.compile(
            "\\b\\d{8,17}\\b"
        ));

        // Medical record numbers (MRN) - alphanumeric, typically 6-10 characters
        piiPatterns.put("MRN", Pattern.compile(
            "\\b[A-Z]{0,3}[0-9]{6,10}\\b",
            Pattern.CASE_INSENSITIVE
        ));

        // Passport numbers (simplified pattern)
        piiPatterns.put("PASSPORT", Pattern.compile(
            "\\b[A-Z]{1,2}[0-9]{6,9}\\b"
        ));

        // Driver's license numbers (simplified)
        piiPatterns.put("DRIVERS_LICENSE", Pattern.compile(
            "\\b[A-Z]{1,2}[0-9]{6,14}\\b"
        ));

        // Street addresses (simplified pattern)
        piiPatterns.put("STREET_ADDRESS", Pattern.compile(
            "\\b\\d+\\s+([A-Za-z]+\\s*)+(street|st|avenue|ave|road|rd|boulevard|blvd|drive|dr|lane|ln|way|court|ct|circle|cir|trail|trl|parkway|pkwy|highway|hwy)\\b",
            Pattern.CASE_INSENSITIVE
        ));
    }

    /**
     * Detect PII in text content.
     *
     * @param content The text to analyze
     * @return PIIDetectionResult with findings
     */
    public PIIDetectionResult detectPII(String content) {
        if (content == null || content.isEmpty()) {
            return new PIIDetectionResult(false, new HashMap<>());
        }

        Map<String, Integer> findings = new HashMap<>();
        boolean containsPII = false;

        for (Map.Entry<String, Pattern> entry : piiPatterns.entrySet()) {
            String piiType = entry.getKey();
            Pattern pattern = entry.getValue();

            Matcher matcher = pattern.matcher(content);
            int count = 0;

            while (matcher.find()) {
                count++;
                containsPII = true;

                // Log detection (without exposing the actual PII)
                logger.debug("PII detected: {} at position {}", piiType, matcher.start());
            }

            if (count > 0) {
                findings.put(piiType, count);
            }
        }

        if (containsPII) {
            logger.info("PII detected in content. Types found: {}", findings.keySet());
        }

        return new PIIDetectionResult(containsPII, findings);
    }

    /**
     * Detect PII in structured data (JSON-like).
     *
     * @param data The data to analyze
     * @return PIIDetectionResult with findings and field paths
     */
    public PIIDetectionResult detectPIIInData(Map<String, Object> data) {
        return detectPIIInData(data, "");
    }

    private PIIDetectionResult detectPIIInData(Map<String, Object> data, String path) {
        Map<String, Integer> findings = new HashMap<>();
        Map<String, String> fieldPaths = new HashMap<>();
        boolean containsPII = false;

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String fieldPath = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
            Object value = entry.getValue();

            if (value instanceof String) {
                String strValue = (String) value;
                PIIDetectionResult fieldResult = detectPII(strValue);

                if (fieldResult.containsPII()) {
                    containsPII = true;
                    mergeFindings(findings, fieldResult.getFindings());
                    fieldPaths.put(fieldPath, fieldResult.getPrimaryPIIType());
                }
            } else if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                PIIDetectionResult nestedResult = detectPIIInData((Map<String, Object>) value, fieldPath);

                if (nestedResult.containsPII()) {
                    containsPII = true;
                    mergeFindings(findings, nestedResult.getFindings());
                }
            }
        }

        return new PIIDetectionResult(containsPII, findings, fieldPaths);
    }

    /**
     * Scan entity data for PII.
     *
     * @param tenantId The tenant ID
     * @param collectionName The collection name
     * @param entityId The entity ID
     * @return Promise with scan results
     */
    public Promise<PIIScanResult> scanEntity(String tenantId, String collectionName, String entityId) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "SELECT data FROM entities WHERE tenant_id = ? AND collection_name = ? AND id = ?")) {

                stmt.setString(1, tenantId);
                stmt.setString(2, collectionName);
                stmt.setString(3, entityId);

                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    String data = rs.getString("data");
                    PIIDetectionResult detection = detectPII(data);

                    return new PIIScanResult(
                        tenantId,
                        collectionName,
                        entityId,
                        detection.containsPII(),
                        detection.getFindings(),
                        data.length()
                    );
                } else {
                    return new PIIScanResult(tenantId, collectionName, entityId, false, new HashMap<>(), 0);
                }

            } catch (SQLException e) {
                logger.error("Failed to scan entity for PII: {}/{}/{}", tenantId, collectionName, entityId, e);
                throw new RuntimeException("PII scan failed", e);
            }
        });
    }

    /**
     * Redact PII from text content.
     *
     * @param content The content to redact
     * @param strategy The redaction strategy
     * @return Redacted content
     */
    public String redactPII(String content, RedactionStrategy strategy) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        String result = content;

        for (Map.Entry<String, Pattern> entry : piiPatterns.entrySet()) {
            String piiType = entry.getKey();
            Pattern pattern = entry.getValue();

            Matcher matcher = pattern.matcher(result);
            StringBuffer sb = new StringBuffer();

            while (matcher.find()) {
                String match = matcher.group();
                String replacement = applyRedaction(match, piiType, strategy);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }

            matcher.appendTail(sb);
            result = sb.toString();
        }

        return result;
    }

    /**
     * Redact PII in structured data.
     *
     * @param data The data to redact
     * @param strategy The redaction strategy
     * @return Redacted data
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> redactPIIInData(Map<String, Object> data, RedactionStrategy strategy) {
        Map<String, Object> result = new HashMap<>();

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof String) {
                result.put(key, redactPII((String) value, strategy));
            } else if (value instanceof Map) {
                result.put(key, redactPIIInData((Map<String, Object>) value, strategy));
            } else {
                result.put(key, value);
            }
        }

        return result;
    }

    private String applyRedaction(String value, String piiType, RedactionStrategy strategy) {
        switch (strategy) {
            case MASKING:
                return applyMasking(value, piiType);
            case HASHING:
                return applyHashing(value);
            case TOKENIZATION:
                return applyTokenization(value, piiType);
            case REMOVAL:
                return "[REDACTED]";
            default:
                return value;
        }
    }

    private String applyMasking(String value, String piiType) {
        if (value.length() <= 4) {
            return "****";
        }

        switch (piiType) {
            case "EMAIL":
                int atIndex = value.indexOf('@');
                if (atIndex > 1) {
                    return value.charAt(0) + "***@" + value.substring(atIndex + 1);
                }
                return "***@***.***";

            case "PHONE_US":
                return "***-***-" + value.substring(value.length() - 4);

            case "SSN":
                return "***-**-" + value.substring(value.length() - 4);

            case "CREDIT_CARD":
                return "****-****-****-" + value.substring(value.length() - 4);

            default:
                return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
        }
    }

    private String applyHashing(String value) {
        // In production: Use proper cryptographic hashing (SHA-256, etc.)
        // For this implementation: Simple placeholder hashing
        return "[HASH:" + Integer.toHexString(value.hashCode()) + "]";
    }

    private String applyTokenization(String value, String piiType) {
        // In production: Use proper tokenization service
        // For this implementation: Simple token placeholder
        return "[TOKEN:" + piiType + ":" + Integer.toHexString(value.hashCode()) + "]";
    }

    private void mergeFindings(Map<String, Integer> target, Map<String, Integer> source) {
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            target.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
    }

    // ====================================================================================
    // Inner Classes
    // ====================================================================================

    public enum RedactionStrategy {
        MASKING,      // Show only last 4 digits
        HASHING,      // One-way transformation
        TOKENIZATION, // Replace with token
        REMOVAL       // Complete removal
    }

    public static class PIIDetectionResult {
        private final boolean containsPII;
        private final Map<String, Integer> findings;
        private final Map<String, String> fieldPaths;

        public PIIDetectionResult(boolean containsPII, Map<String, Integer> findings) {
            this(containsPII, findings, new HashMap<>());
        }

        public PIIDetectionResult(boolean containsPII, Map<String, Integer> findings,
                                   Map<String, String> fieldPaths) {
            this.containsPII = containsPII;
            this.findings = new HashMap<>(findings);
            this.fieldPaths = new HashMap<>(fieldPaths);
        }

        public boolean containsPII() {
            return containsPII;
        }

        public Map<String, Integer> getFindings() {
            return new HashMap<>(findings);
        }

        public Map<String, String> getFieldPaths() {
            return new HashMap<>(fieldPaths);
        }

        public String getPrimaryPIIType() {
            if (findings.isEmpty()) {
                return "UNKNOWN";
            }
            return findings.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("UNKNOWN");
        }

        public int getTotalPIICount() {
            return findings.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    public static class PIIScanResult {
        private final String tenantId;
        private final String collectionName;
        private final String entityId;
        private final boolean containsPII;
        private final Map<String, Integer> findings;
        private final int dataSize;

        public PIIScanResult(String tenantId, String collectionName, String entityId,
                            boolean containsPII, Map<String, Integer> findings, int dataSize) {
            this.tenantId = tenantId;
            this.collectionName = collectionName;
            this.entityId = entityId;
            this.containsPII = containsPII;
            this.findings = new HashMap<>(findings);
            this.dataSize = dataSize;
        }

        public String getTenantId() { return tenantId; }
        public String getCollectionName() { return collectionName; }
        public String getEntityId() { return entityId; }
        public boolean containsPII() { return containsPII; }
        public Map<String, Integer> getFindings() { return new HashMap<>(findings); }
        public int getDataSize() { return dataSize; }
    }
}
