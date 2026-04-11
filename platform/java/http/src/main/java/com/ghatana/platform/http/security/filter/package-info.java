/**
 * Security filters for HTTP request/response processing.
 *
 * <p>This package provides security-focused filters that integrate with the
 * {@link com.ghatana.platform.http.server.filter.FilterChain} infrastructure:
 *
 * <ul>
 *   <li>{@link PermissionEnforcerFilter} - Enforces @RequiresPermission annotations</li>
 *   <li>{@link RBACFilter} - Role-based access control filter</li>
 *   <li>{@link HstsHeaderFilter} - HSTS header injection for HTTPS enforcement</li>
 *   <li>{@link HttpsRedirectHandler} - HTTP to HTTPS redirect handler</li>
 *   <li>{@link RequestSizeLimitFilter} - Request body size limiting</li>
 *   <li>{@link TenantExtractor} - Tenant ID extraction from HTTP headers</li>
 * </ul>
 *
 * <p>These filters were migrated from {@code platform:java:security} to eliminate
 * HTTP dependency leakage in the security module.
 *
 * @doc.type package
 * @doc.purpose Security filters for HTTP processing
 * @doc.layer platform
 * @doc.pattern Filter Chain
 */
package com.ghatana.platform.http.security.filter;
