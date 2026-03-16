package com.ghatana.appplatform.refdata.domain;

/**
 * @doc.type       Enum
 * @doc.purpose    Classification of legal and market entities recorded in the entity master.
 * @doc.layer      Domain
 * @doc.pattern    Value Object
 */
public enum EntityType {
    ISSUER,
    BROKER,
    CUSTODIAN,
    EXCHANGE,
    REGULATOR,
    BANK
}
