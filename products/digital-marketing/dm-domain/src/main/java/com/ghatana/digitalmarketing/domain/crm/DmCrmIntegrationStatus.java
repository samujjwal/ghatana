package com.ghatana.digitalmarketing.domain.crm;

/**
 * Status of a CRM integration.
 *
 * @doc.type class
 * @doc.purpose Lifecycle status for DmCrmIntegration (DMOS-F4-002)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DmCrmIntegrationStatus {
    PENDING,
    ACTIVE,
    FAILED,
    DISCONNECTED
}
