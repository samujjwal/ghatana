package com.ghatana.appplatform.rules;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes policy evaluation requests to the correct OPA bundle based on the
 * tenant's jurisdiction.
 *
 * <p>Each tenant belongs to a jurisdiction (e.g. {@code EU}, {@code IN}, {@code US}).
 * Each jurisdiction maps to a named OPA policy bundle prefix (e.g. {@code eu/`, `india/`}).
 * The fully qualified OPA policy path is then {@code {bundlePrefix}/{policyCategory}}.
 *
 * <h2>Example</h2>
 * <pre>
 *   tenant "acme" → jurisdiction "EU" → bundle prefix "eu"
 *   policyCategory "authz/allow"
 *   OPA path evaluated: "eu/authz/allow"
 * </pre>
 *
 * <p>Routing tables are loaded at construction time and can be refreshed via
 * {@link #registerTenantJurisdiction(String, String)} and
 * {@link #registerJurisdictionBundle(String, String)} without downtime.
 *
 * @doc.type class
 * @doc.purpose Routes OPA evaluations to jurisdiction-specific policy bundles by tenant
 * @doc.layer product
 * @doc.pattern Service
 */
public final class JurisdictionPolicyRouter {

    private static final Logger log = LoggerFactory.getLogger(JurisdictionPolicyRouter.class);

    /**
     * Well-known jurisdiction codes understood by the platform.
     * Custom codes are also accepted as plain {@link String} values.
     */
    public enum Jurisdiction {
        EU, US, IN, SG, GB, AU, DEFAULT
    }

    // tenantId → jurisdictionCode (e.g. "EU")
    private final ConcurrentHashMap<String, String> tenantJurisdictions;
    // jurisdictionCode → OPA bundle prefix (e.g. "eu")
    private final ConcurrentHashMap<String, String> jurisdictionBundles;

    private final RuleCacheService cacheService;

    /**
     * Creates a router with empty routing tables. Callers must populate the tables
     * via {@link #registerTenantJurisdiction} and {@link #registerJurisdictionBundle}
     * before routing requests.
     *
     * @param cacheService caching OPA evaluation service
     */
    public JurisdictionPolicyRouter(RuleCacheService cacheService) {
        this.cacheService = cacheService;
        this.tenantJurisdictions = new ConcurrentHashMap<>();
        this.jurisdictionBundles = new ConcurrentHashMap<>();
        // Seed sensible defaults
        jurisdictionBundles.put("EU", "eu");
        jurisdictionBundles.put("US", "us");
        jurisdictionBundles.put("IN", "india");
        jurisdictionBundles.put("SG", "sg");
        jurisdictionBundles.put("GB", "gb");
        jurisdictionBundles.put("AU", "au");
        jurisdictionBundles.put("DEFAULT", "global");
    }

    /**
     * Creates a router pre-populated with the given routing tables.
     *
     * @param cacheService         caching OPA evaluation service
     * @param tenantJurisdictions  map of tenantId → jurisdictionCode
     * @param jurisdictionBundles  map of jurisdictionCode → OPA bundle prefix
     */
    public JurisdictionPolicyRouter(RuleCacheService cacheService,
                                    Map<String, String> tenantJurisdictions,
                                    Map<String, String> jurisdictionBundles) {
        this.cacheService = cacheService;
        this.tenantJurisdictions = new ConcurrentHashMap<>(tenantJurisdictions);
        this.jurisdictionBundles = new ConcurrentHashMap<>(jurisdictionBundles);
    }

    // ── Registration API (hot-reload capable) ─────────────────────────────────

    /**
     * Maps a tenant to a jurisdiction. Thread-safe; takes effect immediately.
     *
     * @param tenantId         tenant identifier
     * @param jurisdictionCode uppercase jurisdiction code (e.g. {@code "EU"})
     */
    public void registerTenantJurisdiction(String tenantId, String jurisdictionCode) {
        tenantJurisdictions.put(tenantId, jurisdictionCode.toUpperCase());
    }

    /**
     * Maps a jurisdiction code to an OPA bundle prefix. Thread-safe; takes effect immediately.
     *
     * @param jurisdictionCode jurisdiction code (case-insensitive, stored uppercase)
     * @param bundlePrefix     OPA path prefix for this jurisdiction's policies
     */
    public void registerJurisdictionBundle(String jurisdictionCode, String bundlePrefix) {
        jurisdictionBundles.put(jurisdictionCode.toUpperCase(), bundlePrefix);
    }

    // ── Core routing ──────────────────────────────────────────────────────────

    /**
     * Routes the policy evaluation for the given tenant to the correct OPA bundle,
     * then evaluates via the cached OPA service.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Look up the jurisdiction for {@code tenantId}.
     *   <li>If not found, fall back to jurisdiction {@code "DEFAULT"}.
     *   <li>Look up the OPA bundle prefix for the jurisdiction.
     *   <li>Construct the fully-qualified policy path: {@code {bundlePrefix}/{policyCategory}}.
     *   <li>Delegate to {@link RuleCacheService#getOrEvaluate}.
     * </ol>
     *
     * @param tenantId       tenant performing the request
     * @param policyCategory OPA policy category within the bundle (e.g. {@code authz/allow})
     * @param input          input document for the OPA evaluation
     * @return promise resolving to an {@link OpaEvaluationService.OpaResult}
     */
    public Promise<OpaEvaluationService.OpaResult> routeAndEvaluate(
            String tenantId, String policyCategory, Map<String, Object> input) {

        String jurisdiction = tenantJurisdictions.getOrDefault(tenantId, "DEFAULT");
        String bundlePrefix = jurisdictionBundles.getOrDefault(jurisdiction,
                jurisdictionBundles.getOrDefault("DEFAULT", "global"));
        String policyPath = bundlePrefix + "/" + policyCategory;

        log.debug("Routing tenantId={} → jurisdiction={} → policyPath={}", tenantId, jurisdiction, policyPath);
        return cacheService.getOrEvaluate(policyPath, input);
    }

    /**
     * Returns the resolved jurisdiction code for a tenant, or {@code "DEFAULT"} if unknown.
     *
     * @param tenantId tenant identifier
     * @return jurisdiction code
     */
    public String resolveJurisdiction(String tenantId) {
        return tenantJurisdictions.getOrDefault(tenantId, "DEFAULT");
    }

    /**
     * Returns the OPA bundle prefix for a jurisdiction code, or the global fallback.
     *
     * @param jurisdictionCode jurisdiction code
     * @return bundle prefix
     */
    public String resolveBundlePrefix(String jurisdictionCode) {
        return jurisdictionBundles.getOrDefault(jurisdictionCode.toUpperCase(), "global");
    }
}
