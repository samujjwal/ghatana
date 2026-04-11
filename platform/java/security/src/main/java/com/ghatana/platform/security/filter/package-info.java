/**
 * HTTP security filters for ActiveJ HTTP servers.
 *
 * <p>This package provides security filters that integrate with the HTTP server:
 * <ul>
 *   <li>{@link PermissionEnforcerFilter} - Annotation-based permission enforcement</li>
 *   <li>{@link RBACFilter} - Role-based access control filter</li>
 * </ul>
 *
 * <h2>Migration Note</h2>
 *
 * <p>These filters were moved from {@code platform:java:security} to avoid HTTP
 * dependency leakage. The security module now provides security abstractions
 * (Principal, TenantContext, PolicyService) while this module provides the
 * HTTP-specific filter implementations.</p>
 *
 * @doc.type package
 * @doc.purpose HTTP security filters for ActiveJ HTTP servers
 * @doc.layer platform
 * @doc.pattern Package
 */
package com.ghatana.platform.security.filter;
