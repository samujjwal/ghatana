package com.ghatana.core.domain.pipeline;

/**
 * Types of connectors in the pipeline system.
 * 
 * <p>Defines the role and behavior of connectors within event processing pipelines.
 * Each connector type has specific characteristics and constraints.
 * 
 * @doc.type enum
 * @doc.purpose Pipeline connector classification
 * @doc.layer domain
 */
public enum ConnectorType {
    
    /**
     * Input connector for ingesting events into the pipeline.
     * 
     * <p>Input connectors are responsible for:
     * <ul>
     *   <li>Receiving events from external sources</li>
     *   <li>Validating incoming event format</li>
     *   <li>Converting events to internal format</li>
     *   <li>Providing source metadata</li>
     * </ul>
     */
    INPUT,
    
    /**
     * Output connector for publishing processed events.
     * 
     * <p>Output connectors are responsible for:
     * <ul>
     *   <li>Publishing events to external destinations</li>
     *   <li>Handling delivery guarantees</li>
     *   <li>Managing connection state</li>
     *   <li>Providing delivery feedback</li>
     * </ul>
     */
    OUTPUT,
    
    /**
     * Transform connector for processing and modifying events.
     * 
     * <p>Transform connectors are responsible for:
     * <ul>
     *   <li>Applying business logic to events</li>
     *   <li>Enriching events with additional data</li>
     *   <li>Filtering events based on conditions</li>
     *   <li>Aggregating events over time windows</li>
     * </ul>
     */
    TRANSFORM,
    
    /**
     * Filter connector for conditional event routing.
     * 
     * <p>Filter connectors are responsible for:
     * <ul>
     *   <li>Evaluating conditions on events</li>
     *   <li>Routing events to different paths</li>
     *   <li>Implementing complex filtering logic</li>
     *   <li>Supporting dynamic rule evaluation</li>
     * </ul>
     */
    FILTER,
    
    /**
     * Join connector for combining multiple event streams.
     * 
     * <p>Join connectors are responsible for:
     * <ul>
     *   <li>Combining events from multiple sources</li>
     *   <li>Handling temporal correlations</li>
     *   <li>Managing join strategies</li>
     *   <li>Resolving key conflicts</li>
     * </ul>
     */
    JOIN,
    
    /**
     * Aggregate connector for event aggregation operations.
     * 
     * <p>Aggregate connectors are responsible for:
     * <ul>
     *   <li>Grouping events by keys</li>
     *   <li>Computing aggregate functions</li>
     *   <li>Managing time windows</li>
     *   <li>Handling state retention</li>
     * </ul>
     */
    AGGREGATE,
    
    /**
     * Split connector for event stream division.
     * 
     * <p>Split connectors are responsible for:
     * <ul>
     *   <li>Dividing event streams</li>
     *   <li>Implementing conditional routing</li>
     *   <li>Managing multiple output paths</li>
     *   <li>Preserving event order</li>
     * </ul>
     */
    SPLIT,
    
    /**
     * Custom connector for specialized processing.
     * 
     * <p>Custom connectors allow for:
     * <ul>
     *   <li>User-defined processing logic</li>
     *   <li>Integration with external systems</li>
     *   <li>Specialized protocol handling</li>
     *   <li>Advanced transformation capabilities</li>
     * </ul>
     */
    CUSTOM
}
