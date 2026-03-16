package com.ghatana.appplatform.compliance.domain;

/**
 * @doc.type    Enum
 * @doc.purpose Restricted or watch list classification type (D07-011).
 * @doc.layer   Domain
 * @doc.pattern Value Object
 */
public enum RestrictedListType {
    /** Instrument fully restricted for the entity group — trades are blocked. */
    RESTRICTED,
    /** Instrument under watch — trades are flagged for enhanced monitoring. */
    WATCH,
    /** Instrument under grey listing — elevated scrutiny required. */
    GREY
}
