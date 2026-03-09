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

import java.time.Instant;

/**
 * Distributed lock for cross-node synchronization.
 *
 * <p>Provides mutual exclusion across cluster nodes for
 * coordinated operations like:
 * <ul>
 *   <li>Schema migrations</li>
 *   <li>Partition rebalancing</li>
 *   <li>Resource cleanup</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * DistributedLock lock = coordinator.acquireLock("migration-lock", Duration.ofMinutes(5))
 *     .getResult();
 *
 * try {
 *     // Critical section - only one node at a time
 *     performMigration();
 * } finally {
 *     lock.release().getResult();
 * }
 * }</pre>
 *
 * @see ClusterCoordinator#acquireLock
 * @doc.type interface
 * @doc.purpose Distributed locking
 * @doc.layer core
 * @doc.pattern Handle
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public interface DistributedLock {

    /**
     * Returns the lock name.
     *
     * @return lock name
     */
    String lockName();

    /**
     * Returns the node ID that holds this lock.
     *
     * @return holder node ID
     */
    String holderId();

    /**
     * Returns when this lock expires.
     *
     * @return expiration time
     */
    Instant expiresAt();

    /**
     * Returns true if the lock is still held.
     *
     * @return true if held and not expired
     */
    default boolean isHeld() {
        return Instant.now().isBefore(expiresAt());
    }

    /**
     * Extends the lock timeout.
     *
     * @return renewed lock
     */
    Promise<DistributedLock> extend();

    /**
     * Releases the lock.
     *
     * @return promise that completes when released
     */
    Promise<Void> release();

    /**
     * Creates an empty lock (for failed acquisition).
     *
     * @param lockName the lock name
     * @return empty lock
     */
    static DistributedLock empty(String lockName) {
        return new DistributedLock() {
            @Override
            public String lockName() {
                return lockName;
            }

            @Override
            public String holderId() {
                return null;
            }

            @Override
            public Instant expiresAt() {
                return Instant.MIN;
            }

            @Override
            public boolean isHeld() {
                return false;
            }

            @Override
            public Promise<DistributedLock> extend() {
                return Promise.ofException(
                        new IllegalStateException("Lock not held"));
            }

            @Override
            public Promise<Void> release() {
                return Promise.complete();
            }
        };
    }
}
