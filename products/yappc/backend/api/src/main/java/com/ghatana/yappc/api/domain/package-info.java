/**
 * YAPPC API Domain — Project Management Bounded Context.
 *
 * <p>Contains domain entities for project lifecycle management:
 * stories, sprints, requirements, workspaces, and related concepts.</p>
 *
 * <p>Security-related entities (Incident, Alert, SecurityScan, Compliance, Project)
 * have been migrated to {@code com.ghatana.products.yappc.domain.model} (yappc-domain lib).</p>
 *
 * <h3>Entities in this package</h3>
 * <p>Story, Sprint, Requirement, Workspace, Team, etc. are unique to
 * the project management context and remain canonical here until
 * extracted to a dedicated {@code yappc-project-domain} lib.</p>
 *
 * @doc.type package
 * @doc.purpose Project management domain entities (ActiveJ POJOs)
 * @doc.layer product
 * @doc.pattern BoundedContext
 *
 * @see com.ghatana.products.yappc.domain.model
 */
package com.ghatana.yappc.api.domain;
