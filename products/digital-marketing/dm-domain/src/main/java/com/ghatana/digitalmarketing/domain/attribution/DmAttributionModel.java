package com.ghatana.digitalmarketing.domain.attribution;

/**
 * Attribution model types supported by the system.
 *
 * @doc.type class
 * @doc.purpose Identifies the attribution model used for a conversion (DMOS-F2-017)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DmAttributionModel {
    LAST_CLICK,
    FIRST_CLICK,
    LINEAR,
    TIME_DECAY,
    POSITION_BASED
}
