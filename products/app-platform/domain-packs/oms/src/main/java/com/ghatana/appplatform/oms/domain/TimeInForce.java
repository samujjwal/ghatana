package com.ghatana.appplatform.oms.domain;

/**
 * @doc.type    Enum
 * @doc.purpose Order time-in-force instructions — how long the order stays active.
 * @doc.layer   Domain
 * @doc.pattern Value Object
 */
public enum TimeInForce {
    /** Order expires at end of trading day. */
    DAY,
    /** Good-till-Cancelled — remains active across sessions (max 30 days). */
    GTC,
    /** Immediate or Cancel — execute immediately or cancel remainder. */
    IOC,
    /** Fill or Kill — execute entire quantity immediately or cancel. */
    FOK
}
