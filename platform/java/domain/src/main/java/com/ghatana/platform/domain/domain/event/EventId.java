package com.ghatana.platform.domain.domain.event;

/**
 * Unique identifier for events with tenant, type, and version information.
 *
 * <p>
 * Type-safe identifier representing a specific event's identity in the system.
 * Event IDs are unique within a tenant and include sufficient information to
 * route and validate events through the processing pipeline. Historically this
 * type was an interface; several modules implement or mock it directly. To
 * preserve backward compatibility we keep it as an interface with commonly used
 * accessors.
 * </p>
 *
 * <h2>Identity Components</h2>
 * <dl>
 * <dt><b>id</b>: {@link #getId()}</dt>
 * <dd>Unique identifier string (UUID or generated ID unique within tenant)</dd>
 *
 * <dt><b>eventType</b>: {@link #getEventType()}</dt>
 * <dd>Event type name (e.g., "com.ghatana.InfrastructureAlert",
 * "ApplicationError")</dd>
 *
 * <dt><b>version</b>: {@link #getVersion()}</dt>
 * <dd>Schema version for backward compatibility (e.g., "v1", "v2.1")</dd>
 *
 * <dt><b>tenantId</b>: {@link #getTenantId()}</dt>
 * <dd>Tenant identifier for multi-tenant isolation (default: null for
 * single-tenant)</dd>
 * </dl>
 *
 * <h2>Architecture Role</h2>
 * <p>
 * Event IDs are used throughout the platform:
 * <ul>
 * <li><b>Event Identification</b>: Unique identification within event
 * streams</li>
 * <li><b>Event Routing</b>: Routes to appropriate event processors based on
 * type</li>
 * <li><b>Event Tracking</b>: Enables tracing and correlation across
 * services</li>
 * <li><b>Event Relations</b>: References in causation and correlation
 * graphs</li>
 * <li><b>Multi-Tenancy</b>: Tenant scoping for data isolation</li>
 * <li><b>Schema Versioning</b>: Supports schema evolution and
 * compatibility</li>
 * </ul>
 * </p>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * EventId id = event.getId();
 * String uniqueId = id.getId();          // "550e8400-e29b-41d4-a716-446655440000"
 * String type = id.getEventType();       // "InfrastructureAlert"
 * String version = id.getVersion();      // "v1"
 * String tenant = id.getTenantId();      // "acme-corp" or null
 *
 * // Use in correlation
 * eventRelations.addCause(id);
 * graph.addNode(id);
 *
 * // Use in routing
 * router.route(event, id.getEventType());
 * }</pre>
 *
 * @doc.type interface
 * @doc.layer domain
 * @doc.purpose type-safe event identifier with routing information
 * @doc.pattern value-object (immutable identifier with multiple components)
 * @doc.test-hints verify ID uniqueness, test event type routing, validate
 * version handling, check tenant scoping
 *
 * @see com.ghatana.platform.domain.domain.event.Event (contains EventId)
 * @see com.ghatana.platform.domain.domain.event.EventRelations (uses EventIds for
 * relationships)
 */
public interface EventId {

    /**
     * Gets the unique identifier string for this event.
     *
     * @return The event ID, never null
     */
    String getId();

    /**
     * Gets the event type name.
     *
     * @return The event type, never null
     */
    String getEventType();

    /**
     * Gets the schema version of the event type.
     *
     * @return The version, never null
     */
    String getVersion();

    /**
     * Gets the tenant ID that owns this event.
     *
     * @return The tenant ID, or null if not applicable
     */
    default String getTenantId() {
        return null;
    }

    /**
     * Creates a simple EventId instance from the provided components. This
     * static factory provides a convenient way for callers to construct EventId
     * instances without depending on concrete implementations.
     */
    static EventId create(String id, String eventType, String version, String tenantId) {
        return new SimpleEventId(id, eventType, version, tenantId);
    }

    /**
     * Small immutable implementation used by the static factory above.
     */
    record SimpleEventId(String id, String eventType, String version, String tenantId) implements EventId {
        @Override
        public String getId
            
        () {
            return id;
        }

        @Override
        public String getEventType
            
        () {
            return eventType;
        }

        @Override
        public String getVersion
            
        () {
            return version;
        }

        @Override
        public String getTenantId
            
        () {
            return tenantId;
        }
    }
    }
