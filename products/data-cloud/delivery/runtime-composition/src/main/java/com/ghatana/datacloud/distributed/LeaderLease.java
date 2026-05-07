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

package com.ghatana.datacloud.distributed;

import io.activej.promise.Promise;

import java.time.Duration;
import java.time.Instant;

/**
 * Leader election lease for a resource group.
 *
 * <p>Represents the result of a leader election attempt.
 * If elected, provides methods to maintain and release leadership.
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * LeaderLease lease = coordinator.electLeader("partition-0")
 *     .getResult();
 *
 * if (lease.isLeader()) {
 *     try {
 *         // Perform leader-only operations
 *         processPartition();
 *     } finally {
 *         lease.release();
 *     }
 * }
 * }</pre>
 *
 * @see ClusterCoordinator#electLeader(String)
 * @doc.type interface
 * @doc.purpose Leader election lease
 * @doc.layer core
 * @doc.pattern Handle
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public interface LeaderLease {

    /**
     * Returns true if this node is the leader.
     *
     * @return true if leader
     */
    boolean isLeader();

    /**
     * Returns the resource group this lease is for.
     *
     * @return resource group name
     */
    String resourceGroup();

    /**
     * Returns the leader node ID.
     *
     * @return leader node ID
     */
    String leaderId();

    /**
     * Returns when this lease expires.
     *
     * @return expiration time
     */
    Instant expiresAt();

    /**
     * Returns the time-to-live for this lease.
     *
     * @return time remaining
     */
    default Duration ttl() {
        return Duration.between(Instant.now(), expiresAt());
    }

    /**
     * Returns true if the lease is still valid.
     *
     * @return true if not expired
     */
    default boolean isValid() {
        return Instant.now().isBefore(expiresAt());
    }

    /**
     * Renews the lease, extending the expiration.
     *
     * <p>Only the current leader can renew.
     *
     * @return renewed lease
     */
    Promise<LeaderLease> renew();

    /**
     * Releases the lease, giving up leadership.
     *
     * @return promise that completes when released
     */
    Promise<Void> release();

    /**
     * Creates a non-leader lease (for election losers).
     *
     * @param resourceGroup the resource group
     * @param leaderId the current leader's node ID
     * @return non-leader lease
     */
    static LeaderLease notLeader(String resourceGroup, String leaderId) {
        return new LeaderLease() {
            @Override
            public boolean isLeader() {
                return false;
            }

            @Override
            public String resourceGroup() {
                return resourceGroup;
            }

            @Override
            public String leaderId() {
                return leaderId;
            }

            @Override
            public Instant expiresAt() {
                return Instant.MIN;
            }

            @Override
            public Promise<LeaderLease> renew() {
                return Promise.ofException(
                        new IllegalStateException("Cannot renew: not the leader"));
            }

            @Override
            public Promise<Void> release() {
                return Promise.complete();
            }
        };
    }
}
