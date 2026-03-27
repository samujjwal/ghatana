/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime.recertification;

/**
 * Lifecycle state of a {@link RecertificationCampaign}.
 *
 * @doc.type enum
 * @doc.purpose Track the lifecycle of a recertification campaign
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum CampaignStatus {

    /** Campaign created; items are being populated. */
    INITIALIZING,

    /** All items populated; certifiers are reviewing. */
    IN_PROGRESS,

    /** All items have been certified or revoked; awaiting report generation. */
    COMPLETED,

    /** Campaign was cancelled before completion. */
    CANCELLED
}
