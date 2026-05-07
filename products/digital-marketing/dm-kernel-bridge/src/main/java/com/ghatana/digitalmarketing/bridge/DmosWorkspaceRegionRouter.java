package com.ghatana.digitalmarketing.bridge;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;

/**
 * KERNEL-P2-2: Geo-distributed workspace routing for DMOS multi-region failover.
 *
 * <p>Routes workspace API calls to the nearest regional endpoint based on an explicit
 * {@code regionHint} supplied by the workspace configuration. If the preferred region
 * is unknown or absent, the router falls back to the primary region.
 *
 * <p><b>Usage</b>
 * <pre>{@code
 * DmosWorkspaceRegionRouter router = DmosWorkspaceRegionRouter.builder()
 *     .primaryRegion("us-east-1")
 *     .addRegion("us-east-1", URI.create("https://api-us-east-1.dmos.example.com"))
 *     .addRegion("eu-west-1", URI.create("https://api-eu-west-1.dmos.example.com"))
 *     .addRegion("ap-southeast-1", URI.create("https://api-ap-se-1.dmos.example.com"))
 *     .build();
 *
 * URI endpoint = router.resolveEndpoint(workspaceId, "eu-west-1").get();
 * }</pre>
 *
 * <p>Failover strategy: unknown or null {@code regionHint} → primary region endpoint.
 * If primary is also absent (misconfiguration), the Promise is failed with
 * {@link IllegalStateException}.
 *
 * @doc.type class
 * @doc.purpose Geo-distributed workspace router for DMOS multi-region failover
 * @doc.layer product
 * @doc.pattern Adapter, Strategy, Failover
 */
public final class DmosWorkspaceRegionRouter {

    private static final Logger LOG = LoggerFactory.getLogger(DmosWorkspaceRegionRouter.class);

    private final String primaryRegion;
    private final Map<String, URI> regionEndpoints;

    private DmosWorkspaceRegionRouter(Builder builder) {
        this.primaryRegion = Objects.requireNonNull(builder.primaryRegion, "primaryRegion required");
        this.regionEndpoints = Map.copyOf(builder.regionEndpoints);
        if (!regionEndpoints.containsKey(primaryRegion)) {
            throw new IllegalArgumentException(
                    "primaryRegion '" + primaryRegion + "' must be registered in regionEndpoints");
        }
    }

    /**
     * Resolves the API base {@link URI} for the given workspace, using the supplied
     * {@code regionHint}. Falls back to the primary region if {@code regionHint} is
     * {@code null}, blank, or not in the endpoint registry.
     *
     * @param workspaceId the workspace requesting routing
     * @param regionHint  preferred region code, may be {@code null}
     * @return Promise of the resolved base URI
     */
    public Promise<URI> resolveEndpoint(DmWorkspaceId workspaceId, String regionHint) {
        Objects.requireNonNull(workspaceId, "workspaceId required");

        String region = effectiveRegion(regionHint);
        URI endpoint = regionEndpoints.get(region);

        if (endpoint == null) {
            // Should only happen if primary region is misconfigured after construction
            LOG.error("[DMOS][Region] Primary region endpoint missing: primaryRegion={}", primaryRegion);
            return Promise.ofException(new IllegalStateException(
                    "Primary region endpoint not configured: " + primaryRegion));
        }

        if (region.equals(effectiveRegion(regionHint)) && !region.equals(primaryRegion)) {
            LOG.debug("[DMOS][Region] Routing workspace={} to region={} endpoint={}",
                    workspaceId.getValue(), region, endpoint);
        } else {
            LOG.debug("[DMOS][Region] Workspace={} regionHint={} fell back to primaryRegion={}",
                    workspaceId.getValue(), regionHint, primaryRegion);
        }

        return Promise.of(endpoint);
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
     * Builder for {@link DmosWorkspaceRegionRouter}.
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
         * @param baseUri    HTTPS base URI for this region's DMOS API cluster
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

        public DmosWorkspaceRegionRouter build() {
            return new DmosWorkspaceRegionRouter(this);
        }
    }
}
