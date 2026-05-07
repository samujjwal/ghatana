package com.ghatana.kernel.routing;

import com.ghatana.kernel.context.KernelContext;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;

/**
 * Generic geo-distributed routing for multi-region failover across platform products.
 *
 * <p>Routes requests to the nearest regional endpoint based on an explicit
 * {@code regionHint} supplied by the workspace or tenant configuration. If the preferred region
 * is unknown or absent, the router falls back to the primary region.
 *
 * <p><b>Usage</b>
 * <pre>{@code
 * RegionRouter router = RegionRouter.builder()
 *     .primaryRegion("us-east-1")
 *     .addRegion("us-east-1", URI.create("https://api-us-east-1.platform.example.com"))
 *     .addRegion("eu-west-1", URI.create("https://api-eu-west-1.platform.example.com"))
 *     .addRegion("ap-southeast-1", URI.create("https://api-ap-se-1.platform.example.com"))
 *     .build();
 *
 * URI endpoint = router.resolveEndpoint(tenantId, workspaceId, "eu-west-1").get();
 * }</pre>
 *
 * <p>Failover strategy: unknown or null {@code regionHint} → primary region endpoint.
 * If primary is also absent (misconfiguration), the Promise is failed with
 * {@link IllegalStateException}.
 *
 * @doc.type class
 * @doc.purpose Generic geo-distributed router for platform multi-region failover (KERNEL-P2)
 * @doc.layer core
 * @doc.pattern Strategy, Failover
 */
public final class RegionRouter {

    private static final Logger LOG = LoggerFactory.getLogger(RegionRouter.class);

    private final String primaryRegion;
    private final Map<String, URI> regionEndpoints;

    private RegionRouter(Builder builder) {
        this.primaryRegion = Objects.requireNonNull(builder.primaryRegion, "primaryRegion required");
        this.regionEndpoints = Map.copyOf(builder.regionEndpoints);
        if (!regionEndpoints.containsKey(primaryRegion)) {
            throw new IllegalArgumentException(
                    "primaryRegion '" + primaryRegion + "' must be registered in regionEndpoints");
        }
        // Validate all registered endpoints use HTTPS
        for (Map.Entry<String, URI> entry : regionEndpoints.entrySet()) {
            if (!"https".equalsIgnoreCase(entry.getValue().getScheme())) {
                throw new IllegalArgumentException(
                    "Region endpoint must use HTTPS: region=" + entry.getKey() + ", uri=" + entry.getValue());
            }
        }
    }

    /**
     * Resolves the API base {@link URI} for the given tenant/workspace, using the supplied
     * {@code regionHint}. Falls back to the primary region if {@code regionHint} is
     * {@code null}, blank, or not in the endpoint registry.
     *
     * @param tenantId the tenant requesting routing
     * @param workspaceId the workspace requesting routing
     * @param regionHint preferred region code, may be {@code null}
     * @return Promise of the resolved base URI
     */
    public Promise<URI> resolveEndpoint(String tenantId, String workspaceId, String regionHint) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(workspaceId, "workspaceId required");

        String region = effectiveRegion(regionHint);
        URI endpoint = regionEndpoints.get(region);

        if (endpoint == null) {
            // Should only happen if primary region is misconfigured after construction
            LOG.error("[Kernel][Region] Primary region endpoint missing: primaryRegion={}", primaryRegion);
            return Promise.ofException(new IllegalStateException(
                    "Primary region endpoint not configured: " + primaryRegion));
        }

        if (region.equals(effectiveRegion(regionHint)) && !region.equals(primaryRegion)) {
            LOG.debug("[Kernel][Region] Routing tenant={} workspace={} to region={} endpoint={}",
                    tenantId, workspaceId, region, endpoint);
        } else {
            LOG.debug("[Kernel][Region] tenant={} workspace={} regionHint={} fell back to primaryRegion={}",
                    tenantId, workspaceId, regionHint, primaryRegion);
        }

        return Promise.of(endpoint);
    }

    /**
     * Resolves the API base {@link URI} for the given context, using the supplied
     * {@code regionHint}. Falls back to the primary region if {@code regionHint} is
     * {@code null}, blank, or not in the endpoint registry.
     *
     * @param context kernel context containing tenant and workspace
     * @param regionHint preferred region code, may be {@code null}
     * @return Promise of the resolved base URI
     */
    public Promise<URI> resolveEndpoint(KernelContext context, String regionHint) {
        Objects.requireNonNull(context, "context required");
        return resolveEndpoint(
            context.getTenantId().getValue(),
            context.getWorkspaceId().getValue(),
            regionHint
        );
    }

    /**
     * Returns all registered region codes, ordered alphabetically.
     */
    public List<String> registeredRegions() {
        List<String> regions = new ArrayList<>(regionEndpoints.keySet());
        Collections.sort(regions);
        return Collections.unmodifiableList(regions);
    }

    /**
     * Returns the primary (fallback) region code.
     */
    public String getPrimaryRegion() {
        return primaryRegion;
    }

    /**
     * Checks whether a region code is registered.
     */
    public boolean isRegionRegistered(String regionCode) {
        return regionEndpoints.containsKey(regionCode);
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private String effectiveRegion(String regionHint) {
        if (regionHint == null || regionHint.isBlank()) {
            return primaryRegion;
        }
        return regionEndpoints.containsKey(regionHint) ? regionHint : primaryRegion;
    }

    // ─── Builder ─────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link RegionRouter}.
     */
    public static final class Builder {

        private String primaryRegion;
        private final Map<String, URI> regionEndpoints = new LinkedHashMap<>();

        private Builder() {}

        /**
         * Sets the primary (fallback) region code.
         */
        public Builder primaryRegion(String region) {
            this.primaryRegion = Objects.requireNonNull(region, "region required");
            return this;
        }

        /**
         * Registers a region code with its API base URI.
         *
         * @param regionCode IANA/AWS-style region code, e.g. {@code "eu-west-1"}
         * @param baseUri    HTTPS base URI for this region's API cluster
         */
        public Builder addRegion(String regionCode, URI baseUri) {
            Objects.requireNonNull(regionCode, "regionCode required");
            Objects.requireNonNull(baseUri, "baseUri required");
            if (regionCode.isBlank()) {
                throw new IllegalArgumentException("regionCode cannot be blank");
            }
            regionEndpoints.put(regionCode, baseUri);
            return this;
        }

        public RegionRouter build() {
            return new RegionRouter(this);
        }
    }
}
