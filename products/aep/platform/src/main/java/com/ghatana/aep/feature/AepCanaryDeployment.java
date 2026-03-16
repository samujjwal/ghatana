/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.feature;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Canary deployment descriptor for AEP model rollouts.
 *
 * <p>A canary deployment routes a configurable fraction of live inference
 * traffic to a new model version while the remainder continues to hit the
 * incumbent production version. The deployment is promoted, rolled back, or
 * paused based on live metrics collected during the canary window.
 *
 * <h3>Traffic routing</h3>
 * <pre>
 *   canaryTrafficPct = 10  →  10% to canary, 90% to production
 *   canaryTrafficPct = 100 →  fully promoted to canary
 * </pre>
 *
 * @param id                 surrogate identifier
 * @param tenantId           owning tenant
 * @param modelName          logical model name
 * @param productionVersion  currently stable version (control)
 * @param canaryVersion      version under evaluation (treatment)
 * @param canaryTrafficPct   integer percentage [0, 100] of traffic routed to
 *                           the canary; 0 = paused/rolled back, 100 = full canary
 * @param status             current lifecycle status
 * @param startedAt          when the canary was initiated
 * @param concludedAt        when the canary was promoted, rolled back, or failed
 *
 * @doc.type record
 * @doc.purpose Canary deployment configuration for AEP model rollouts
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record AepCanaryDeployment(
        UUID id,
        String tenantId,
        String modelName,
        String productionVersion,
        String canaryVersion,
        int canaryTrafficPct,
        Status status,
        Instant startedAt,
        Instant concludedAt
) {

    /** Lifecycle status of a canary deployment. */
    public enum Status {
        /** Traffic is being routed — metrics are being collected. */
        ACTIVE,
        /** Canary version was promoted to production. */
        PROMOTED,
        /** Traffic was returned to production; canary rolled back. */
        ROLLED_BACK,
        /** Traffic routing paused pending manual review. */
        PAUSED,
        /** Canary ended due to an unexpected error. */
        FAILED
    }

    public AepCanaryDeployment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(modelName, "modelName");
        Objects.requireNonNull(productionVersion, "productionVersion");
        Objects.requireNonNull(canaryVersion, "canaryVersion");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(startedAt, "startedAt");
        if (canaryTrafficPct < 0 || canaryTrafficPct > 100) {
            throw new IllegalArgumentException(
                    "canaryTrafficPct must be [0, 100] but was " + canaryTrafficPct);
        }
    }

    /**
     * Creates a new ACTIVE canary with the given initial traffic percentage.
     *
     * @param tenantId          owning tenant
     * @param modelName         logical model name
     * @param productionVersion incumbent production version
     * @param canaryVersion     version being canary-tested
     * @param initialTrafficPct starting traffic fraction [1, 99]
     * @return new ACTIVE canary deployment
     */
    public static AepCanaryDeployment start(
            String tenantId, String modelName,
            String productionVersion, String canaryVersion,
            int initialTrafficPct) {
        return new AepCanaryDeployment(
                UUID.randomUUID(), tenantId, modelName,
                productionVersion, canaryVersion,
                initialTrafficPct, Status.ACTIVE,
                Instant.now(), null);
    }

    /** Returns a copy of this canary with an updated traffic percentage. */
    public AepCanaryDeployment withTrafficPct(int newPct) {
        return new AepCanaryDeployment(id, tenantId, modelName,
                productionVersion, canaryVersion,
                newPct, status, startedAt, concludedAt);
    }

    /** Returns a copy of this canary with the given status and conclusion time. */
    public AepCanaryDeployment withStatus(Status newStatus) {
        Instant concluded = (newStatus == Status.ACTIVE || newStatus == Status.PAUSED)
                ? concludedAt : Instant.now();
        return new AepCanaryDeployment(id, tenantId, modelName,
                productionVersion, canaryVersion,
                canaryTrafficPct, newStatus, startedAt, concluded);
    }
}
