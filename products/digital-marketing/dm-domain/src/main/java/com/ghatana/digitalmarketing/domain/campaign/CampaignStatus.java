package com.ghatana.digitalmarketing.domain.campaign;

/**
 * Lifecycle status of a DMOS campaign.
 *
 * <p>Valid transitions:</p>
 * <ul>
 *   <li>{@code DRAFT} → {@code PENDING_APPROVAL} → {@code APPROVED}</li>
 *   <li>{@code APPROVED} → {@code PENDING_LAUNCH} → {@code LAUNCH_RUNNING} → {@code LAUNCHED}</li>
 *   <li>{@code PENDING_LAUNCH} → {@code EXTERNAL_EXECUTION_BLOCKED}</li>
 *   <li>{@code PENDING_LAUNCH} → {@code LAUNCH_FAILED}</li>
 *   <li>{@code LAUNCHED} → {@code PAUSED}</li>
 *   <li>{@code LAUNCHED} → {@code COMPLETED}</li>
 *   <li>{@code PAUSED} → {@code COMPLETED}</li>
 *   <li>{@code COMPLETED} → {@code ARCHIVED}</li>
 *   <li>{@code LAUNCH_FAILED} → {@code ROLLED_BACK}</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose DMOS campaign lifecycle status enum
 * @doc.layer product
 * @doc.pattern Enum
 */
public enum CampaignStatus {
    /** Campaign is being configured; not yet approved or launched. */
    DRAFT,
    /** Campaign is waiting for human approval before launch can proceed. */
    PENDING_APPROVAL,
    /** Campaign has the required human approval for launch. */
    APPROVED,
    /** Launch request passed preflight and is waiting for durable execution. */
    PENDING_LAUNCH,
    /** Durable launch execution is currently running. */
    LAUNCH_RUNNING,
    /** Launch execution failed after preflight or command creation. */
    LAUNCH_FAILED,
    /** External execution is explicitly blocked by a kill switch or connector gate. */
    EXTERNAL_EXECUTION_BLOCKED,
    /** Campaign is actively running. */
    LAUNCHED,
    /** Campaign has been suspended mid-flight. */
    PAUSED,
    /** Campaign has concluded. */
    COMPLETED,
    /** Campaign has been archived and is no longer modifiable. */
    ARCHIVED,
    /** Campaign execution was compensated/rolled back after a failed or blocked launch. */
    ROLLED_BACK
}
