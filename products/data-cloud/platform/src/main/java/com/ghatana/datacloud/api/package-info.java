/**
 * Public API definitions for Data Cloud platform.
 *
 * <p>This package contains the public API interfaces, DTOs, and contracts
 * exposed to external consumers.
 *
 * <h2>Contents</h2>
 * <ul>
 *   <li>{@code dto} - Data Transfer Objects for API requests/responses</li>
 *   <li>API interfaces and contracts</li>
 * </ul>
 *
 * <h2>Architecture</h2>
 * <p>The API layer serves as the entry point for external consumers:
 * <ul>
 *   <li>All DTOs are immutable records</li>
 *   <li>Validation annotations for input validation</li>
 *   <li>Jackson annotations for serialization</li>
 * </ul>
 *
 * @see com.ghatana.datacloud.api.dto
 */
package com.ghatana.datacloud.api;
