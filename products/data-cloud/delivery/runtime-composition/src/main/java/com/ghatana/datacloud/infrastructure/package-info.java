/**
 * Infrastructure adapters for Data Cloud platform.
 *
 * <p>This package contains the infrastructure layer implementing ports
 * defined in the domain and application layers (hexagonal architecture).
 *
 * <h2>Key Subpackages</h2>
 * <ul>
 *   <li>{@code http} - HTTP adapters (REST controllers, handlers)</li>
 *   <li>{@code persistence} - JPA repositories and data access</li>
 *   <li>{@code cache} - Caching infrastructure</li>
 *   <li>{@code audit} - Audit trail infrastructure</li>
 *   <li>{@code search} - Search/indexing infrastructure</li>
 *   <li>{@code governance} - Governance and SLO monitoring</li>
 * </ul>
 *
 * <h2>Architecture</h2>
 * <p>Infrastructure adapters follow these patterns:
 * <ul>
 *   <li>Implement ports from domain/application layers</li>
 *   <li>Use ActiveJ Promise for async operations</li>
 *   <li>Never leak infrastructure details to inner layers</li>
 *   <li>Use {@code Promise.ofBlocking()} for blocking I/O</li>
 * </ul>
 *
 * @see com.ghatana.datacloud.entity
 * @see com.ghatana.datacloud.application
 */
package com.ghatana.datacloud.infrastructure;
