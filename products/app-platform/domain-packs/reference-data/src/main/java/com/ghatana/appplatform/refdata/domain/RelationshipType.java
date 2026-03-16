package com.ghatana.appplatform.refdata.domain;

/**
 * @doc.type       Enum
 * @doc.purpose    Structural relationship type between two market entities.
 *                 Used in the entity relationship graph to represent ownership,
 *                 custodianship, market-making, and issuer-of-security links.
 * @doc.layer      Domain
 * @doc.pattern    Value Object
 */
public enum RelationshipType {
    SUBSIDIARY,
    CUSTODIAN_FOR,
    MARKET_MAKER,
    ISSUER_OF
}
