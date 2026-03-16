package com.ghatana.appplatform.marketdata.domain;

/**
 * @doc.type    Domain Object (Enum)
 * @doc.purpose NEPSE market trading session states (D04-010).
 *              Sessions progress in order during each trading day.
 *              {@link TradingSessionService} emits a {@code SessionStateChangedEvent}
 *              on each transition and publishes it to K-05.
 * @doc.layer   Domain
 * @doc.pattern Value Object (state enum)
 */
public enum TradingSession {

    /** Market closed; no orders accepted. Pre-market data collection. */
    PRE_OPEN,

    /** Opening auction: order entry only, no matching yet. */
    OPEN_AUCTION,

    /** Normal continuous two-way trading with order matching. */
    CONTINUOUS_TRADING,

    /** Closing auction: matching at single equilibrium price. */
    CLOSE_AUCTION,

    /** Post-close period: trade reporting only. */
    POST_CLOSE,

    /** Market fully closed for the day. */
    CLOSED;

    /** Returns true if the session allows new order submissions. */
    public boolean acceptsOrders() {
        return this == OPEN_AUCTION || this == CONTINUOUS_TRADING;
    }

    /** Returns true if the session allows order matching. */
    public boolean allowsMatching() {
        return this == OPEN_AUCTION || this == CONTINUOUS_TRADING || this == CLOSE_AUCTION;
    }
}
