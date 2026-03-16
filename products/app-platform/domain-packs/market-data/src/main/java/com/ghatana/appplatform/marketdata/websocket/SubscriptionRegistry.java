package com.ghatana.appplatform.marketdata.websocket;

import com.ghatana.appplatform.marketdata.domain.L1Quote;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @doc.type       Infrastructure Component
 * @doc.purpose    Tracks which instruments each WebSocket connection is subscribed to.
 *                 Maintains two concurrent maps for O(1) lookups in both directions:
 *                   - connectionId → Set of subscribed instrument ids (fan-in)
 *                   - instrumentId → Set of connectionIds (fan-out)
 *                 D04-014: subscription_registry, max_subscriptions_per_connection (100).
 * @doc.layer      Infrastructure
 * @doc.pattern    Registry / Bipartite Index
 */
public class SubscriptionRegistry {

    /** Maximum number of instrument subscriptions allowed per connection. */
    public static final int MAX_SUBSCRIPTIONS_PER_CONNECTION = 100;

    /** connectionId → set of subscribed instrumentIds */
    private final Map<String, Set<String>> byConnection = new ConcurrentHashMap<>();
    /** instrumentId  → set of subscribed connectionIds */
    private final Map<String, Set<String>> byInstrument  = new ConcurrentHashMap<>();

    /**
     * Subscribe a connection to the given instruments.
     * Silently ignores instruments that are already subscribed.
     *
     * @throws SubscriptionLimitExceededException if adding these instruments would
     *         exceed {@value #MAX_SUBSCRIPTIONS_PER_CONNECTION} per connection.
     */
    public void subscribe(String connectionId, List<String> instrumentIds) {
        Set<String> connectionSubs = byConnection.computeIfAbsent(
                connectionId, id -> ConcurrentHashMap.newKeySet());

        int newTotal = connectionSubs.size() + instrumentIds.size();
        if (newTotal > MAX_SUBSCRIPTIONS_PER_CONNECTION) {
            throw new SubscriptionLimitExceededException(
                    connectionId, connectionSubs.size(), instrumentIds.size());
        }

        for (String instrumentId : instrumentIds) {
            connectionSubs.add(instrumentId);
            byInstrument.computeIfAbsent(instrumentId, id -> ConcurrentHashMap.newKeySet())
                        .add(connectionId);
        }
    }

    /**
     * Unsubscribe a connection from the given instruments.
     * Silently ignores instruments that were not subscribed.
     */
    public void unsubscribe(String connectionId, List<String> instrumentIds) {
        Set<String> connectionSubs = byConnection.get(connectionId);
        if (connectionSubs == null) return;
        for (String instrumentId : instrumentIds) {
            connectionSubs.remove(instrumentId);
            Set<String> connections = byInstrument.get(instrumentId);
            if (connections != null) connections.remove(connectionId);
        }
    }

    /**
     * Remove all subscriptions for a disconnected connection.
     * Called when a WebSocket connection is closed.
     */
    public void removeConnection(String connectionId) {
        Set<String> subs = byConnection.remove(connectionId);
        if (subs == null) return;
        for (String instrumentId : subs) {
            Set<String> connections = byInstrument.get(instrumentId);
            if (connections != null) connections.remove(connectionId);
        }
    }

    /**
     * Return the set of connectionIds that are subscribed to the given instrument.
     * Returns an empty set if no connections are subscribed.
     */
    public Set<String> getSubscribersOf(String instrumentId) {
        return byInstrument.getOrDefault(instrumentId, Collections.emptySet());
    }

    /** Return how many instruments the connection is currently subscribed to. */
    public int subscriptionCount(String connectionId) {
        Set<String> subs = byConnection.get(connectionId);
        return subs == null ? 0 : subs.size();
    }

    // -----------------------------------------------------------------------
    // Exception
    // -----------------------------------------------------------------------

    public static final class SubscriptionLimitExceededException extends RuntimeException {
        public SubscriptionLimitExceededException(String connectionId, int current, int adding) {
            super(String.format(
                    "Connection %s has %d subscriptions; adding %d would exceed limit of %d",
                    connectionId, current, adding, MAX_SUBSCRIPTIONS_PER_CONNECTION));
        }
    }
}
