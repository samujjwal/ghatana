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

package com.ghatana.datacloud.record;

/**
 * Trait for records with audit trail (who created/modified).
 *
 * <p>Records implementing this trait track:
 * <ul>
 *   <li>Creator identity</li>
 *   <li>Last modifier identity</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * interface AuditedEntity extends Record, Timestamped, Auditable {}
 *
 * // Check who made changes
 * if (record.modifiedBy().equals(currentUser)) {
 *     // User's own changes
 * }
 * }</pre>
 *
 * @see Record
 * @see Timestamped
 * @doc.type interface
 * @doc.purpose Audit trail trait
 * @doc.layer core
 * @doc.pattern Trait, Mixin
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public interface Auditable {

    /**
     * Returns the ID of the user who created this record.
     *
     * @return creator user ID (may be null for system-created)
     */
    String createdBy();

    /**
     * Returns the ID of the user who last modified this record.
     *
     * @return modifier user ID (may be null for system-modified)
     */
    String modifiedBy();

    /**
     * Returns true if this record was created by a user (not system).
     *
     * @return true if createdBy is not null
     */
    default boolean hasCreator() {
        return createdBy() != null;
    }

    /**
     * Returns true if this record was modified by the same user who created it.
     *
     * @return true if createdBy equals modifiedBy
     */
    default boolean modifiedByCreator() {
        return createdBy() != null && createdBy().equals(modifiedBy());
    }

    /**
     * Returns true if this record was created by the given user.
     *
     * @param userId the user ID to check
     * @return true if created by the user
     */
    default boolean wasCreatedBy(String userId) {
        return userId != null && userId.equals(createdBy());
    }

    /**
     * Returns true if this record was modified by the given user.
     *
     * @param userId the user ID to check
     * @return true if modified by the user
     */
    default boolean wasModifiedBy(String userId) {
        return userId != null && userId.equals(modifiedBy());
    }
}
