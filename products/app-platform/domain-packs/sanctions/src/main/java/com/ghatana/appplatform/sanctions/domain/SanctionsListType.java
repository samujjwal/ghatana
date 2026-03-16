package com.ghatana.appplatform.sanctions.domain;

/**
 * @doc.type    Enum
 * @doc.purpose Identifies the sanctions list source (D14-001, D14-009).
 * @doc.layer   Domain
 * @doc.pattern Value Object
 */
public enum SanctionsListType {
    OFAC_SDN,
    UN_CONSOLIDATED,
    EU_ASSET_FREEZE,
    NRB_LOCAL
}
