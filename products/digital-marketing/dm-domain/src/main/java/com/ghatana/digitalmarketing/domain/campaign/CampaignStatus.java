package com.ghatana.digitalmarketing.domain.campaign;

/**
 * Lifecycle status of a DMOS campaign.
 *
 * <p>Valid transitions:</p>
 * <ul>
 *   <li>{@code DRAFT} → {@code LAUNCHED}</li>
 *   <li>{@code LAUNCHED} → {@code PAUSED}</li>
 *   <li>{@code PAUSED} → {@code LAUNCHED} (resume)</li>
 *   <li>{@code LAUNCHED} → {@code COMPLETED}</li>
 *   <li>{@code PAUSED} → {@code COMPLETED}</li>
 *   <li>{@code COMPLETED} → {@code ARCHIVED}</li>
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
    /** Campaign is actively running. */
    LAUNCHED,
    /** Campaign has been suspended mid-flight. */
    PAUSED,
    /** Campaign has concluded. */
    COMPLETED,
    /** Campaign has been archived and is no longer modifiable. */
    ARCHIVED
}
