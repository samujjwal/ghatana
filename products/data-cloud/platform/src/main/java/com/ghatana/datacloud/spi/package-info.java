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
 *   <li>{@link com.ghatana.datacloud.spi.EventLogStore} - Append-only event log</li>
 *   <li>{@link com.ghatana.datacloud.spi.TenantContext} - Multi-tenant context</li>
 * </ul>
 *
 * @doc.type package
 * @doc.purpose Storage SPI owned by Data-Cloud
 * @doc.layer spi
 */
package com.ghatana.datacloud.spi;
