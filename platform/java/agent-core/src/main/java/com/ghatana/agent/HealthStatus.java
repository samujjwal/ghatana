/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.agent;

/**
 * Operational status of an agent during its lifecycle.
 *
 * @doc.type enum
 * @doc.purpose Agent lifecycle status
 * @doc.layer core
 * @doc.pattern ValueObject
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public enum HealthStatus {

    /** Agent is fully operational and accepting input. */
    HEALTHY,

    /** Agent is operational but experiencing degraded performance. */
    DEGRADED,

    /** Agent is not operational. All processing requests will fail. */
    UNHEALTHY,

    /** Agent is initializing — not yet accepting input. */
    STARTING,

    /** Agent is shutting down — will stop accepting new input. */
    STOPPING,

    /** Agent status is unknown (e.g., health check timed out). */
    UNKNOWN;

    /**
     * Maps this agent lifecycle state onto the canonical platform health contract.
     */
    public com.ghatana.platform.health.HealthStatus toPlatformHealthStatus() {
        return switch (this) {
            case HEALTHY -> com.ghatana.platform.health.HealthStatus.healthy("Agent is healthy");
            case DEGRADED -> com.ghatana.platform.health.HealthStatus.degraded("Agent is degraded");
            case UNHEALTHY -> com.ghatana.platform.health.HealthStatus.unhealthy("Agent is unhealthy");
            case STARTING -> com.ghatana.platform.health.HealthStatus.degraded("Agent is starting");
            case STOPPING -> com.ghatana.platform.health.HealthStatus.degraded("Agent is stopping");
            case UNKNOWN -> com.ghatana.platform.health.HealthStatus.unknown("Agent status is unknown");
        };
    }

    /**
     * Maps the canonical platform health contract onto the closest agent lifecycle state.
     */
    public static HealthStatus fromPlatformHealthStatus(com.ghatana.platform.health.HealthStatus status) {
        java.util.Objects.requireNonNull(status, "status cannot be null");
        return switch (status.getStatus()) {
            case HEALTHY -> HEALTHY;
            case DEGRADED -> DEGRADED;
            case UNHEALTHY -> UNHEALTHY;
            case UNKNOWN -> UNKNOWN;
        };
    }
}
