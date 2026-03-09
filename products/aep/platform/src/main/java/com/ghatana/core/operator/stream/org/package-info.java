/**
 * Organization event stream operators.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides specialized stream operators for processing organization events in
 * unified pipelines.
 *
 * <p>
 * <b>Operators</b><br> - {@link OrganizationEventFilterOperator} - Filter
 * organization events by type/status - {@link OrganizationEventEnricher} -
 * Enrich events with context and metadata - {@link OrganizationEventRouter} -
 * Route events to appropriate processing handlers
 *
 * <p>
 * <b>Usage Example</b><br>
 * <pre>{@code
 * // Create an organization event processing pipeline
 * OrganizationEventFilterOperator filter = OrganizationEventFilterOperator.builder()
 *     .id(OperatorId.of("org:filter:active"))
 *     .name("Active Organizations")
 *     .filterByStatus("ACTIVE")
 *     .metricsCollector(metrics)
 *     .build();
 *
 * OrganizationEventRouter router = OrganizationEventRouter.builder()
 *     .id(OperatorId.of("org:route:type"))
 *     .name("Event Type Router")
 *     .routeByEventType()
 *     .metricsCollector(metrics)
 *     .build();
 *
 * // Chain operators together
 * OperatorChain chain = OperatorChain.create(filter)
 *     .then(router);
 * }</pre>
 *
 * @see OrganizationEventFilterOperator
 * @see OrganizationEventEnricher
 * @see OrganizationEventRouter
 */
package com.ghatana.core.operator.stream.org;
