package com.ghatana.digitalmarketing.domain.playbook;

/**
 * Status for playbook versions.
 *
 * @doc.type class
 * @doc.purpose Lifecycle status for DmPlaybookVersion (DMOS-F3-004)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DmPlaybookVersionStatus {
    DRAFT,
    ACTIVE,
    ARCHIVED
}
