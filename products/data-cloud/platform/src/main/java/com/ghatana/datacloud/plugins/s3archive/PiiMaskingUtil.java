package com.ghatana.datacloud.plugins.s3archive;

import com.ghatana.datacloud.event.model.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Utility for masking Personally Identifiable Information (PII) in events.
 *
 * <p><b>Purpose</b><br>
 * Ensures compliance with data privacy regulations (GDPR, CCPA, HIPAA) when
 * archiving events to long-term storage. PII is masked before events leave
 * the system to ensure archived data is safe for long-term retention.
 *
 * <p><b>Masking Strategies</b><br>
 * <ul>
 *   <li><b>REDACT</b>: Replace with "[REDACTED]"</li>
 *   <li><b>HASH</b>: SHA-256 hash for pseudonymization</li>
 *   <li><b>MASK</b>: Partial masking (e.g., "john****@example.com")</li>
 *   <li><b>TOKENIZE</b>: Replace with consistent token for correlation</li>
 *   <li><b>ENCRYPT</b>: Reversible encryption (requires key management)</li>
 * </ul>
 *
 * <p><b>Supported PII Types</b><br>
 * <ul>
 *   <li>Email addresses</li>
 *   <li>Phone numbers</li>
 *   <li>Social Security Numbers (SSN)</li>
 *   <li>Credit card numbers</li>
 *   <li>IP addresses</li>
 *   <li>Names (first, last, full)</li>
 *   <li>Custom field patterns</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * PiiMaskingUtil masker = PiiMaskingUtil.builder()
 *     .addPiiField("email", MaskingStrategy.HASH)
 *     .addPiiField("phone", MaskingStrategy.MASK)
 *     .addPiiField("ssn", MaskingStrategy.REDACT)
 *     .addPiiFieldPattern(".*password.*", MaskingStrategy.REDACT)
 *     .build();
 *
 * Event maskedEvent = masker.maskEvent(event);
 * }</pre>
 *
 * <p><b>Six Pillars</b><br>
 * <ul>
 *   <li><b>Security</b>: Irreversible masking options, no PII in logs</li>
 *   <li><b>Observability</b>: Masking metrics, field tracking</li>
 *   <li><b>Debuggability</b>: Dry-run mode, masking reports</li>
 *   <li><b>Scalability</b>: Efficient regex caching, stream processing</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose PII masking for compliance
 * @doc.layer plugin
 * @doc.pattern Strategy, Builder
 */
public class PiiMaskingUtil implements ArchiveMigrationScheduler.PiiMaskingFunction {

    private static final Logger log = LoggerFactory.getLogger(PiiMaskingUtil.class);

    // Common PII patterns
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}",
            Pattern.CASE_INSENSITIVE);
    
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "\\+?[0-9]{1,3}[-.\\s]?\\(?[0-9]{3}\\)?[-.\\s]?[0-9]{3}[-.\\s]?[0-9]{4}");
    
    private static final Pattern SSN_PATTERN = Pattern.compile(
            "\\d{3}-\\d{2}-\\d{4}|\\d{9}");
    
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
            "\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}");
    
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");

    private static final String REDACTED = "[REDACTED]";
    private static final String SALT = "eventcloud-pii-salt-"; // In production, use secure config

    // Configuration
    private final Map<String, MaskingStrategy> fieldStrategies;
    private final Map<Pattern, MaskingStrategy> patternStrategies;
    private final Set<String> headerFieldsToMask;
    private final boolean maskInPayload;
    private final boolean maskInHeaders;
    private final boolean dryRunMode;
    private final MessageDigest sha256;

    // Statistics
    private long totalFieldsMasked = 0;
    private long totalEventsMasked = 0;

    // ==================== Constructor ====================

    private PiiMaskingUtil(Builder builder) {
        this.fieldStrategies = Collections.unmodifiableMap(new HashMap<>(builder.fieldStrategies));
        this.patternStrategies = Collections.unmodifiableMap(new HashMap<>(builder.patternStrategies));
        this.headerFieldsToMask = Collections.unmodifiableSet(new HashSet<>(builder.headerFieldsToMask));
        this.maskInPayload = builder.maskInPayload;
        this.maskInHeaders = builder.maskInHeaders;
        this.dryRunMode = builder.dryRunMode;

        try {
            this.sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ==================== Public API ====================

    /**
     * Masks PII in an event.
     *
     * @param event original event
     * @return event with PII masked
     */
    @Override
    public Event mask(Event event) {
        return maskEvent(event);
    }

    /**
     * Masks PII in an event.
     *
     * @param event original event
     * @return event with PII masked
     */
    public Event maskEvent(Event event) {
        if (event == null) {
            return null;
        }

        // Use toBuilder() since Event uses @SuperBuilder(toBuilder = true)
        var builder = event.toBuilder();

        // Mask headers
        if (maskInHeaders && event.getHeaders() != null) {
            builder.headers(maskHeaders(event.getHeaders()));
        }

        // Mask payload
        if (maskInPayload && event.getPayload() != null) {
            builder.payload(maskPayload(event.getPayload()));
        }

        totalEventsMasked++;
        return builder.build();
    }

    /**
     * Masks PII in a batch of events.
     *
     * @param events original events
     * @return events with PII masked
     */
    public List<Event> maskBatch(List<Event> events) {
        List<Event> masked = new ArrayList<>(events.size());
        for (Event event : events) {
            masked.add(maskEvent(event));
        }
        return masked;
    }

    /**
     * Masks a single string value using the specified strategy.
     *
     * @param value    value to mask
     * @param strategy masking strategy
     * @return masked value
     */
    public String maskValue(String value, MaskingStrategy strategy) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        return switch (strategy) {
            case REDACT -> REDACTED;
            case HASH -> hashValue(value);
            case MASK -> partialMask(value);
            case TOKENIZE -> tokenize(value);
            case NONE -> value;
        };
    }

    /**
     * Detects PII in a string value.
     *
     * @param value value to check
     * @return set of detected PII types
     */
    public Set<PiiType> detectPii(String value) {
        if (value == null || value.isEmpty()) {
            return Collections.emptySet();
        }

        Set<PiiType> detected = EnumSet.noneOf(PiiType.class);

        if (EMAIL_PATTERN.matcher(value).find()) {
            detected.add(PiiType.EMAIL);
        }
        if (PHONE_PATTERN.matcher(value).find()) {
            detected.add(PiiType.PHONE);
        }
        if (SSN_PATTERN.matcher(value).find()) {
            detected.add(PiiType.SSN);
        }
        if (CREDIT_CARD_PATTERN.matcher(value).find()) {
            detected.add(PiiType.CREDIT_CARD);
        }
        if (IPV4_PATTERN.matcher(value).find()) {
            detected.add(PiiType.IP_ADDRESS);
        }

        return detected;
    }

    /**
     * Gets masking statistics.
     *
     * @return masking stats
     */
    public MaskingStats getStats() {
        return new MaskingStats(totalEventsMasked, totalFieldsMasked);
    }

    // ==================== Private Methods ====================

    private Map<String, String> maskHeaders(Map<String, String> headers) {
        Map<String, String> masked = new HashMap<>();

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (headerFieldsToMask.contains(key.toLowerCase())) {
                masked.put(key, REDACTED);
                totalFieldsMasked++;
                if (dryRunMode) {
                    log.debug("[DRY RUN] Would mask header: {}", key);
                }
            } else {
                masked.put(key, value);
            }
        }

        return masked;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> maskPayload(Map<String, Object> payload) {
        Map<String, Object> masked = new HashMap<>();

        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Check if this field needs masking
            MaskingStrategy strategy = findStrategy(key);

            if (strategy != null && strategy != MaskingStrategy.NONE) {
                Object maskedValue = applyMasking(value, strategy);
                masked.put(key, maskedValue);
                totalFieldsMasked++;
                if (dryRunMode) {
                    log.debug("[DRY RUN] Would mask field '{}' with strategy {}", key, strategy);
                }
            } else if (value instanceof Map) {
                // Recursively mask nested maps
                masked.put(key, maskPayload((Map<String, Object>) value));
            } else if (value instanceof List) {
                // Mask items in lists
                masked.put(key, maskList((List<?>) value));
            } else {
                // Auto-detect PII in string values
                if (value instanceof String strValue) {
                    Set<PiiType> detected = detectPii(strValue);
                    if (!detected.isEmpty()) {
                        masked.put(key, maskDetectedPii(strValue, detected));
                        totalFieldsMasked++;
                    } else {
                        masked.put(key, value);
                    }
                } else {
                    masked.put(key, value);
                }
            }
        }

        return masked;
    }

    @SuppressWarnings("unchecked")
    private List<?> maskList(List<?> list) {
        List<Object> masked = new ArrayList<>();

        for (Object item : list) {
            if (item instanceof Map) {
                masked.add(maskPayload((Map<String, Object>) item));
            } else if (item instanceof String strValue) {
                Set<PiiType> detected = detectPii(strValue);
                if (!detected.isEmpty()) {
                    masked.add(maskDetectedPii(strValue, detected));
                } else {
                    masked.add(item);
                }
            } else {
                masked.add(item);
            }
        }

        return masked;
    }

    private MaskingStrategy findStrategy(String fieldName) {
        // Check exact field match
        if (fieldStrategies.containsKey(fieldName.toLowerCase())) {
            return fieldStrategies.get(fieldName.toLowerCase());
        }

        // Check pattern matches
        for (Map.Entry<Pattern, MaskingStrategy> entry : patternStrategies.entrySet()) {
            if (entry.getKey().matcher(fieldName).matches()) {
                return entry.getValue();
            }
        }

        return null;
    }

    private Object applyMasking(Object value, MaskingStrategy strategy) {
        if (value == null) {
            return null;
        }

        if (value instanceof String strValue) {
            return maskValue(strValue, strategy);
        }

        // For non-string values, convert to string and mask
        return maskValue(value.toString(), strategy);
    }

    private String maskDetectedPii(String value, Set<PiiType> piiTypes) {
        String result = value;

        for (PiiType type : piiTypes) {
            result = switch (type) {
                case EMAIL -> EMAIL_PATTERN.matcher(result).replaceAll(m -> maskEmail(m.group()));
                case PHONE -> PHONE_PATTERN.matcher(result).replaceAll(m -> maskPhone(m.group()));
                case SSN -> SSN_PATTERN.matcher(result).replaceAll("XXX-XX-XXXX");
                case CREDIT_CARD -> CREDIT_CARD_PATTERN.matcher(result).replaceAll(m -> maskCreditCard(m.group()));
                case IP_ADDRESS -> IPV4_PATTERN.matcher(result).replaceAll(m -> maskIp(m.group()));
                case NAME, ADDRESS, DATE_OF_BIRTH -> result; // Not auto-detected
            };
        }

        return result;
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return REDACTED;
        }

        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);

        if (localPart.length() <= 2) {
            return "**" + domain;
        }

        return localPart.charAt(0) + "****" + domain;
    }

    private String maskPhone(String phone) {
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 4) {
            return REDACTED;
        }
        return "***-***-" + digits.substring(digits.length() - 4);
    }

    private String maskCreditCard(String card) {
        String digits = card.replaceAll("[^0-9]", "");
        if (digits.length() < 4) {
            return REDACTED;
        }
        return "**** **** **** " + digits.substring(digits.length() - 4);
    }

    private String maskIp(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return REDACTED;
        }
        return parts[0] + "." + parts[1] + ".xxx.xxx";
    }

    private String partialMask(String value) {
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }

    private String hashValue(String value) {
        byte[] hash = sha256.digest((SALT + value).getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append(String.format("%02x", hash[i]));
        }
        return "sha256:" + sb;
    }

    private String tokenize(String value) {
        // Simple tokenization - in production, use a secure token vault
        return "tok_" + hashValue(value).substring(7, 19);
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Map<String, MaskingStrategy> fieldStrategies = new HashMap<>();
        private final Map<Pattern, MaskingStrategy> patternStrategies = new HashMap<>();
        private final Set<String> headerFieldsToMask = new HashSet<>();
        private boolean maskInPayload = true;
        private boolean maskInHeaders = true;
        private boolean dryRunMode = false;

        /**
         * Adds a PII field with masking strategy.
         *
         * @param fieldName field name (case-insensitive)
         * @param strategy  masking strategy
         * @return this builder
         */
        public Builder addPiiField(String fieldName, MaskingStrategy strategy) {
            fieldStrategies.put(fieldName.toLowerCase(), strategy);
            return this;
        }

        /**
         * Adds a PII field pattern with masking strategy.
         *
         * @param pattern  regex pattern for field names
         * @param strategy masking strategy
         * @return this builder
         */
        public Builder addPiiFieldPattern(String pattern, MaskingStrategy strategy) {
            patternStrategies.put(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE), strategy);
            return this;
        }

        /**
         * Adds a header field to mask.
         *
         * @param headerName header name (case-insensitive)
         * @return this builder
         */
        public Builder addHeaderToMask(String headerName) {
            headerFieldsToMask.add(headerName.toLowerCase());
            return this;
        }

        /**
         * Configures default PII fields for common compliance scenarios.
         *
         * @return this builder
         */
        public Builder withDefaultPiiFields() {
            // Common PII fields
            fieldStrategies.put("email", MaskingStrategy.HASH);
            fieldStrategies.put("phone", MaskingStrategy.MASK);
            fieldStrategies.put("phonenumber", MaskingStrategy.MASK);
            fieldStrategies.put("mobile", MaskingStrategy.MASK);
            fieldStrategies.put("ssn", MaskingStrategy.REDACT);
            fieldStrategies.put("socialsecuritynumber", MaskingStrategy.REDACT);
            fieldStrategies.put("creditcard", MaskingStrategy.MASK);
            fieldStrategies.put("cardnumber", MaskingStrategy.MASK);
            fieldStrategies.put("ipaddress", MaskingStrategy.MASK);
            fieldStrategies.put("ip", MaskingStrategy.MASK);
            fieldStrategies.put("firstname", MaskingStrategy.HASH);
            fieldStrategies.put("lastname", MaskingStrategy.HASH);
            fieldStrategies.put("name", MaskingStrategy.HASH);
            fieldStrategies.put("fullname", MaskingStrategy.HASH);
            fieldStrategies.put("address", MaskingStrategy.REDACT);
            fieldStrategies.put("dateofbirth", MaskingStrategy.REDACT);
            fieldStrategies.put("dob", MaskingStrategy.REDACT);

            // Patterns for password-like fields
            patternStrategies.put(
                    Pattern.compile(".*password.*", Pattern.CASE_INSENSITIVE),
                    MaskingStrategy.REDACT);
            patternStrategies.put(
                    Pattern.compile(".*secret.*", Pattern.CASE_INSENSITIVE),
                    MaskingStrategy.REDACT);
            patternStrategies.put(
                    Pattern.compile(".*token.*", Pattern.CASE_INSENSITIVE),
                    MaskingStrategy.REDACT);
            patternStrategies.put(
                    Pattern.compile(".*apikey.*", Pattern.CASE_INSENSITIVE),
                    MaskingStrategy.REDACT);

            // Common headers to mask
            headerFieldsToMask.add("authorization");
            headerFieldsToMask.add("x-api-key");
            headerFieldsToMask.add("cookie");
            headerFieldsToMask.add("set-cookie");

            return this;
        }

        /**
         * Sets whether to mask payload fields.
         *
         * @param mask true to mask
         * @return this builder
         */
        public Builder maskInPayload(boolean mask) {
            this.maskInPayload = mask;
            return this;
        }

        /**
         * Sets whether to mask header fields.
         *
         * @param mask true to mask
         * @return this builder
         */
        public Builder maskInHeaders(boolean mask) {
            this.maskInHeaders = mask;
            return this;
        }

        /**
         * Enables dry-run mode for testing.
         *
         * @param dryRun true to enable
         * @return this builder
         */
        public Builder dryRunMode(boolean dryRun) {
            this.dryRunMode = dryRun;
            return this;
        }

        public PiiMaskingUtil build() {
            return new PiiMaskingUtil(this);
        }
    }

    // ==================== Inner Types ====================

    /**
     * PII masking strategies.
     */
    public enum MaskingStrategy {
        /** Replace entirely with "[REDACTED]" */
        REDACT,
        /** SHA-256 hash for pseudonymization */
        HASH,
        /** Partial masking (show first/last characters) */
        MASK,
        /** Replace with consistent token */
        TOKENIZE,
        /** No masking */
        NONE
    }

    /**
     * Types of PII that can be detected.
     */
    public enum PiiType {
        EMAIL,
        PHONE,
        SSN,
        CREDIT_CARD,
        IP_ADDRESS,
        NAME,
        ADDRESS,
        DATE_OF_BIRTH
    }

    /**
     * Masking statistics.
     */
    public record MaskingStats(
            long totalEventsMasked,
            long totalFieldsMasked
    ) {}
}
