/**
 * Global workspace for shared cognitive spotlight.
 *
 * <p>This package provides the global workspace abstraction for the organizational brain,
 * enabling cross-system communication of high-salience items:
 * <ul>
 *   <li>Spotlight items - High-priority items requiring attention</li>
 *   <li>Broadcast mechanism - Emergency notifications to all subscribers</li>
 *   <li>TTL-based eviction - Automatic cleanup of stale items</li>
 *   <li>Pub-sub integration - Distributed notification via Redis</li>
 * </ul>
 *
 * <p><b>Key Components:</b>
 * <ul>
 *   <li>{@link com.ghatana.datacloud.workspace.SpotlightItem} - Item in the spotlight</li>
 *   <li>{@link com.ghatana.datacloud.workspace.GlobalWorkspace} - Workspace manager</li>
 * </ul>
 *
 * @doc.type package
 * @doc.purpose Global cognitive workspace
 * @doc.layer core
 * @doc.pattern Publish-Subscribe, Observer
 */
package com.ghatana.datacloud.workspace;
