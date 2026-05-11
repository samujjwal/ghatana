/*
 * Copyright (c) 2025 Ghatana Platform Contributors
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

package com.ghatana.yappc.facades.aep;

import com.ghatana.yappc.facades.common.TenantScopedRequest;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * YAPPC-facing facade for AEP (Audit, Event, Phase) event operations.
 *
 * Provides a typed interface for YAPPC to interact with AEP event systems
 * without direct dependencies on AEP internals.
 *
 * @doc.type interface
 * @doc.purpose YAPPC facade for AEP event operations
 * @doc.layer product
 * @doc.pattern Facade
 */
public interface AepEventFacade {

    /**
     * Publish an event to AEP.
     *
     * @param request The event publish request
     * @return Promise containing the event ID
     */
    Promise<String> publishEvent(EventPublishRequest request);

    /**
     * Retrieve an event from AEP.
     *
     * @param eventId The event ID
     * @param tenantId The tenant ID
     * @return Promise containing the event content
     */
    Promise<Optional<EventContent>> retrieveEvent(String eventId, String tenantId);

    /**
     * Query events by criteria.
     *
     * @param query The event query
     * @return Promise containing list of matching events
     */
    Promise<List<EventContent>> queryEvents(EventQuery query);

    /**
     * Subscribe to events matching criteria.
     *
     * @param subscription The event subscription
     * @return Promise containing the subscription ID
     */
    Promise<String> subscribeToEvents(EventSubscription subscription);

    /**
     * Unsubscribe from events.
     *
     * @param subscriptionId The subscription ID
     * @param tenantId The tenant ID
     * @return Promise indicating completion
     */
    Promise<Void> unsubscribeFromEvents(String subscriptionId, String tenantId);

    /**
     * Event publish request.
     */
    record EventPublishRequest(
        String eventType,
        String source,
        String tenantId,
        Map<String, Object> payload,
        Map<String, String> metadata
    ) implements TenantScopedRequest {
        @Override
        public String getTenantId() {
            return tenantId;
        }
    }

    /**
     * Event content.
     */
    record EventContent(
        String eventId,
        String eventType,
        String source,
        String tenantId,
        Map<String, Object> payload,
        Map<String, String> metadata,
        long timestamp
    ) {}

    /**
     * Event query.
     */
    record EventQuery(
        String tenantId,
        Optional<String> eventType,
        Optional<String> source,
        Optional<Long> afterTimestamp,
        Optional<Long> beforeTimestamp,
        Optional<Integer> limit
    ) implements TenantScopedRequest {
        @Override
        public String getTenantId() {
            return tenantId;
        }
    }

    /**
     * Event subscription.
     */
    record EventSubscription(
        String tenantId,
        String eventType,
        Optional<String> source,
        Map<String, String> filterCriteria
    ) implements TenantScopedRequest {
        @Override
        public String getTenantId() {
            return tenantId;
        }
    }
}
