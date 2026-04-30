/**
 * YAPPC Domain — Cloud Security Bounded Context (Canonical Source).
 *
 * <p>This is the <strong>canonical domain model library</strong> for YAPPC.
 * All shared domain types should be defined here.</p>
 *
 * <p><b>Packages</b></p>
 * <ul>
 *   <li>{@code model/} — JPA entities (Incident, Project, ScanJob, etc.)</li>
 *   <li>{@code enums/} — Shared enumerations (ScanType, CloudProvider, etc.)</li>
 *   <li>{@code dto/}   — Data transfer objects for API boundaries</li>
 *   <li>{@code repository/} — Spring Data JPA repository interfaces</li>
 * </ul>
 *
 * <p><b>Usage Rules</b></p>
 * <ol>
 *   <li>New domain models MUST be added here, not in backend/api/domain/</li>
 *   <li>Cross-module dependencies MUST use this lib</li>
 *   <li>Overlapping entities in backend/api/domain/ are deprecated</li>
 * </ol>
 *
 * @doc.type package
 * @doc.purpose Canonical YAPPC domain models (JPA entities, DTOs, enums)
 * @doc.layer product
 * @doc.pattern SharedKernel
 */
package com.ghatana.yappc.domain;
