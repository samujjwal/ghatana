/**
 * Platform-agnostic pagination abstractions.
 *
 * <p>
 * This package provides pagination types that are independent of Spring Data,
 * JPA, or any other framework. Use these types in repository interfaces and
 * service methods to avoid framework coupling.
 *
 * <p>
 * <b>Key Types:</b>
 * <ul>
 * <li>{@link com.ghatana.core.common.pagination.Page} - Page result with
 * metadata</li>
 * <li>{@link com.ghatana.core.common.pagination.PageRequest} - Pagination
 * request parameters</li>
 * <li>{@link com.ghatana.core.common.pagination.Sort} - Sort specification</li>
 * </ul>
 *
 * <p>
 * <b>Example Usage:</b>
 * <pre>{@code
 * // In repository interface:
 * Page<User> findByWorkspaceId(UUID workspaceId, PageRequest pageRequest);
 *
 * // In service:
 * PageRequest request = PageRequest.of(0, 20, Sort.by("name").ascending());
 * Page<User> page = repository.findByWorkspaceId(workspaceId, request);
 * }</pre>
 *
 * @doc.type package
 * @doc.purpose Platform-agnostic pagination abstractions
 * @doc.layer core
 * @doc.pattern Value Object
 */
package com.ghatana.platform.core.common.pagination;
