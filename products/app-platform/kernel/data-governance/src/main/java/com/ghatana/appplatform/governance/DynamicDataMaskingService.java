package com.ghatana.appplatform.governance;

import com.ghatana.appplatform.governance.port.MaskingRuleStore;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Dynamic data masking applied transparently at the query layer before data
 *              is returned to callers. Supports four masking types: FULL (replaces with
 *              "****"), PARTIAL (exposes first/last 3 chars), HASH (SHA-256 hex), and
 *              TOKENIZE (reversible via TokenStorePort). Masking rules are configured per
 *              field-pattern and classification level. Users with the exempt role (e.g.
 *              COMPLIANCE) see plaintext. Satisfies STORY-K08-014.
 * @doc.layer   Kernel
 * @doc.pattern Role-based masking bypass; field-pattern rule lookup; SHA-256 for HASH type;
 *              reversible tokenization delegated to TokenStorePort; operations Counter.
 */
public class DynamicDataMaskingService {

    private final MaskingRuleStore ruleStore;
    private final Executor         executor;
    private final TokenStorePort   tokenStorePort;
    private final RbacPort         rbacPort;
    private final Counter          maskingOpsCounter;

    public DynamicDataMaskingService(MaskingRuleStore ruleStore, Executor executor,
                                      TokenStorePort tokenStorePort,
                                      RbacPort rbacPort,
                                      MeterRegistry registry) {
        this.ruleStore        = ruleStore;
        this.executor         = executor;
        this.tokenStorePort   = tokenStorePort;
        this.rbacPort         = rbacPort;
        this.maskingOpsCounter = Counter.builder("governance.masking.operations_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** Reversible tokenization store — produces opaque tokens that can be resolved back. */
    public interface TokenStorePort {
        String storeToken(String plaintext);
        String resolveToken(String token);
    }

    /** K-01 RBAC: check whether caller has an exempt (unmasked) role. */
    public interface RbacPort {
        boolean hasRole(String userId, String role);
    }

    // ─── Domain enums and records ─────────────────────────────────────────────

    public enum MaskingType { FULL, PARTIAL, HASH, TOKENIZE }

    public record MaskingRule(
        String ruleId, String fieldPattern, String classificationLevel,
        MaskingType maskingType, List<String> exemptRoles
    ) {}

    public record MaskResult(String masked, boolean wasExempt, MaskingType appliedType) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Apply masking to a single field value. If the caller has an exempt role, returns
     * the plaintext value unchanged. Otherwise applies the configured masking type.
     */
    public Promise<MaskResult> maskField(String value, String fieldPattern,
                                          String classificationLevel, String userId) {
        return Promise.ofBlocking(executor, () -> {
            MaskingRule rule = ruleStore.fetchRule(fieldPattern, classificationLevel);
            if (rule == null) {
                // No rule → return as-is
                return new MaskResult(value, true, null);
            }

            for (String exemptRole : rule.exemptRoles()) {
                if (rbacPort.hasRole(userId, exemptRole)) {
                    return new MaskResult(value, true, rule.maskingType());
                }
            }

            String masked = applyMask(value, rule.maskingType());
            maskingOpsCounter.increment();
            return new MaskResult(masked, false, rule.maskingType());
        });
    }

    /**
     * Batch-mask a map of field values. Each field key is matched against rule patterns.
     */
    public Promise<Map<String, MaskResult>> maskRecord(Map<String, String> fieldValues,
                                                        String classificationLevel, String userId) {
        return Promise.ofBlocking(executor, () -> {
            Map<String, MaskResult> results = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : fieldValues.entrySet()) {
                MaskingRule rule = ruleStore.fetchRule(entry.getKey(), classificationLevel);
                if (rule == null) {
                    results.put(entry.getKey(), new MaskResult(entry.getValue(), true, null));
                    continue;
                }
                boolean exempt = rule.exemptRoles().stream().anyMatch(r -> rbacPort.hasRole(userId, r));
                if (exempt) {
                    results.put(entry.getKey(), new MaskResult(entry.getValue(), true, rule.maskingType()));
                } else {
                    String masked = applyMask(entry.getValue(), rule.maskingType());
                    maskingOpsCounter.increment();
                    results.put(entry.getKey(), new MaskResult(masked, false, rule.maskingType()));
                }
            }
            return results;
        });
    }

    /**
     * Resolve a token back to its plaintext (only for TOKENIZE type; caller must be exempt).
     */
    public Promise<String> resolveToken(String token, String userId) {
        return Promise.ofBlocking(executor, () -> {
            if (!rbacPort.hasRole(userId, "COMPLIANCE")) {
                throw new SecurityException("Only COMPLIANCE role can resolve tokens");
            }
            return tokenStorePort.resolveToken(token);
        });
    }

    /**
     * Register or update a masking rule. Only COMPLIANCE admins may do this.
     */
    public Promise<Void> upsertMaskingRule(MaskingRule rule, String adminUserId) {
        return Promise.ofBlocking(executor, () -> {
            if (!rbacPort.hasRole(adminUserId, "COMPLIANCE")) {
                throw new SecurityException("Only COMPLIANCE role can modify masking rules");
            }
            MaskingRule toStore = rule.ruleId() != null ? rule
                    : new MaskingRule(UUID.randomUUID().toString(), rule.fieldPattern(),
                            rule.classificationLevel(), rule.maskingType(), rule.exemptRoles());
            ruleStore.upsertRule(toStore);
            return null;
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private String applyMask(String value, MaskingType type) {
        if (value == null || value.isEmpty()) return value;
        return switch (type) {
            case FULL     -> "****";
            case PARTIAL  -> {
                int len = value.length();
                if (len <= 6) yield "***";
                yield value.substring(0, 3) + "***" + value.substring(len - 3);
            }
            case HASH     -> sha256Hex(value);
            case TOKENIZE -> tokenStorePort.storeToken(value);
        };
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

}
