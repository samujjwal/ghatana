package com.ghatana.appplatform.risk.domain;

/**
 * @doc.type    Domain Object (Enum)
 * @doc.purpose Classifies the type of financial instrument for margin rate lookup (D06-001).
 * @doc.layer   Domain
 * @doc.pattern Value Object
 */
public enum MarginType {
    /** Exchange-traded equity (50% margin rate). */
    EQUITY,
    /** Government or corporate bond (10% margin rate). */
    BOND,
    /** Exchange-traded fund (30% margin rate). */
    ETF,
    /** Money market / treasury bill (5% margin rate). */
    MONEY_MARKET
}
