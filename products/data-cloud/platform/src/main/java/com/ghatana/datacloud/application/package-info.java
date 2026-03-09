/**
 * Application services for Data Cloud platform.
 *
 * <p>This package contains the application layer services that orchestrate
 * domain operations and implement business use cases.
 *
 * <h2>Core Services</h2>
 * <ul>
 *   <li>{@link com.ghatana.datacloud.application.CollectionService} - Collection management</li>
 *   <li>{@link com.ghatana.datacloud.application.EntityService} - Entity CRUD operations</li>
 *   <li>{@link com.ghatana.datacloud.application.ValidationService} - Field validation</li>
 *   <li>{@link com.ghatana.datacloud.application.NLQService} - Natural Language Query</li>
 * </ul>
 *
 * <h2>Query Services</h2>
 * <ul>
 *   <li>{@link com.ghatana.datacloud.application.DynamicQueryBuilder} - SQL generation</li>
 *   <li>{@link com.ghatana.datacloud.application.QueryExecutionService} - Query execution</li>
 * </ul>
 *
 * <h2>Architecture</h2>
 * <p>Application services follow these patterns:
 * <ul>
 *   <li>Use ActiveJ Promise for async operations</li>
 *   <li>Coordinate domain operations through ports</li>
 *   <li>Never expose infrastructure details</li>
 * </ul>
 *
 * @see com.ghatana.datacloud.entity
 * @see com.ghatana.datacloud.infrastructure
 */
package com.ghatana.datacloud.application;
