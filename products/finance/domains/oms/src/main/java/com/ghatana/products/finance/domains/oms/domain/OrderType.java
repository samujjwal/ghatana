package com.ghatana.products.finance.domains.oms.domain;

/**
 * @doc.type    Enum
 * @doc.purpose Supported order execution types per D01-001 spec.
 * @doc.layer   Domain
 * @doc.pattern Value Object
 */
public enum OrderType {
    MARKET,
    LIMIT,
    STOP,
    STOP_LIMIT
}
