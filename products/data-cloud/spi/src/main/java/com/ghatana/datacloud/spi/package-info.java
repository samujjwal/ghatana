/**
 * Data-Cloud Service Provider Interface (SPI) - Storage Domain.
 *
 * <p>This package contains the core interfaces that define the storage domain,
 * which is owned by Data-Cloud. These interfaces can be implemented by:
 * <ul>
 *   <li>Data-Cloud's built-in implementations (PostgreSQL, Redis, S3)</li>
 *   <li>AEP plugins for event storage</li>
 *   <li>Third-party implementations</li>
 * </ul>
 *
 * <p><b>Key Interfaces:</b>
 * <ul>
 *   <li>{@link com.ghatana.datacloud.spi.EntityStore} - Entity CRUD operations</li>
 *   <li>{@link com.ghatana.datacloud.spi.EventLogStore} - Legacy append-only event log (deprecated; use platform contract)</li>
 *   <li>{@link com.ghatana.datacloud.spi.TenantContext} - Legacy tenant context (deprecated; use platform contract)</li>
 * </ul>
 *
 * <p><b>Canonical event-store contracts:</b>
 * <ul>
 *   <li>{@link com.ghatana.platform.domain.eventstore.EventLogStore}</li>
 *   <li>{@link com.ghatana.platform.domain.eventstore.TenantContext}</li>
 * </ul>
 * Use {@link com.ghatana.datacloud.spi.EventLogStoreAdapters} for incremental migration.
 *
 * @doc.type package
 * @doc.purpose Storage SPI owned by Data-Cloud
 * @doc.layer spi
 */
package com.ghatana.datacloud.spi;
