package com.ghatana.platform.domain.domain.pipeline;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * {@code ConnectorSpec} defines a declarative connector configuration that can
 * be referenced from pipeline specifications when building end-to-end
 * pipelines.
 *
 * <p>
 * It is intentionally runtime-agnostic; concrete connector factories in runtime
 * modules (for example, EventCloud sources/sinks, HTTP ingress/egress, queue
 * connectors) interpret these fields to construct operator chains.
 *
 * <p>
 * Minimal fields included for Phase 1:
 * <ul>
 * <li>Type enum with at least EVENT_CLOUD_SOURCE and EVENT_CLOUD_SINK</li>
 * <li>Endpoint/cluster identifier</li>
 * <li>Topic/stream name</li>
 * <li>Tenant identifier (string form)</li>
 * <li>Basic QoS hints (durable, ordered, maxInFlight)</li>
 * </ul>
 *
 * @doc.type value-object
 * @doc.layer domain
 * @doc.purpose declarative connector configuration for pipelines
 * @doc.pattern value-object, serializable, configuration
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConnectorSpec {

    public enum ConnectorType {
        EVENT_CLOUD_SOURCE,
        EVENT_CLOUD_SINK,
        QUEUE_SOURCE,
        QUEUE_SINK,
        HTTP_INGRESS,
        HTTP_EGRESS
    }

    /**
     * Logical connector identifier. Pipeline stages may reference this id.
     */
    private String id;

    private ConnectorType type;

    /**
     * Endpoint or cluster identifier (for example, EventCloud cluster name,
     * Kafka bootstrap servers, or base URL).
     */
    private String endpoint;

    /**
     * Topic or stream name associated with this connector.
     */
    private String topicOrStream;

    /**
     * Tenant identifier as a string. Concrete runtimes may convert this to a
     * strong TenantId type.
     */
    private String tenantId;

    /**
     * Basic QoS hints.
     */
    private Boolean durable;

    private Boolean ordered;

    private Integer maxInFlight;

    /**
     * Arbitrary connector-specific properties (auth, SSL, headers, etc.).
     */
    private Map<String, String> properties;

    /**
     * Optional tags for classification and discovery.
     */
    private List<String> tags;
}
