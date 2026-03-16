package com.ghatana.appplatform.marketdata.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.appplatform.marketdata.domain.L1Quote;
import com.ghatana.appplatform.marketdata.port.L1Cache;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * @doc.type       Driven Adapter (Cache)
 * @doc.purpose    Jedis-based Redis implementation of L1Cache.
 *                 Stores quotes as JSON strings under key {@code l1:{instrumentId}}.
 *                 The entire L1 space is organised under a Redis Hash
 *                 {@code marketdata:l1} so getAllL1() is a single HGETALL call.
 *                 D04-004: l1_redis_write.
 * @doc.layer      Infrastructure
 * @doc.pattern    Hexagonal / Cache Adapter
 */
public class RedisL1Cache implements L1Cache {

    private static final Logger log = LoggerFactory.getLogger(RedisL1Cache.class);
    private static final String HASH_KEY = "marketdata:l1";

    private final JedisPool jedisPool;
    private final ObjectMapper mapper;
    private final Executor executor;

    public RedisL1Cache(JedisPool jedisPool, ObjectMapper mapper, Executor executor) {
        this.jedisPool = jedisPool;
        this.mapper = mapper;
        this.executor = executor;
    }

    @Override
    public Promise<Void> updateL1(L1Quote quote) {
        return Promise.ofBlocking(executor, () -> {
            try (var jedis = jedisPool.getResource()) {
                String json = mapper.writeValueAsString(new L1QuoteJson(
                        quote.instrumentId(),
                        quote.bestBid() != null ? quote.bestBid().toPlainString() : null,
                        quote.bestAsk() != null ? quote.bestAsk().toPlainString() : null,
                        quote.lastPrice() != null ? quote.lastPrice().toPlainString() : null,
                        quote.volume(),
                        quote.updatedAt().toEpochMilli()));
                jedis.hset(HASH_KEY, quote.instrumentId(), json);
            }
        });
    }

    @Override
    public Promise<Optional<L1Quote>> getL1(String instrumentId) {
        return Promise.ofBlocking(executor, () -> {
            try (var jedis = jedisPool.getResource()) {
                String json = jedis.hget(HASH_KEY, instrumentId);
                if (json == null) return Optional.empty();
                return Optional.of(fromJson(json));
            }
        });
    }

    @Override
    public Promise<List<L1Quote>> getAllL1() {
        return Promise.ofBlocking(executor, () -> {
            List<L1Quote> quotes = new ArrayList<>();
            try (var jedis = jedisPool.getResource()) {
                Map<String, String> all = jedis.hgetAll(HASH_KEY);
                for (String json : all.values()) {
                    quotes.add(fromJson(json));
                }
            }
            return quotes;
        });
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private L1Quote fromJson(String json) throws Exception {
        L1QuoteJson dto = mapper.readValue(json, L1QuoteJson.class);
        return new L1Quote(
                dto.instrumentId(),
                dto.bestBid() != null ? new BigDecimal(dto.bestBid()) : null,
                dto.bestAsk() != null ? new BigDecimal(dto.bestAsk()) : null,
                dto.lastPrice() != null ? new BigDecimal(dto.lastPrice()) : null,
                dto.volume(),
                Instant.ofEpochMilli(dto.updatedAtMs()));
    }

    /** JSON DTO — BigDecimal fields serialised as plain strings to avoid precision loss. */
    private record L1QuoteJson(
            String instrumentId,
            String bestBid,
            String bestAsk,
            String lastPrice,
            long volume,
            long updatedAtMs
    ) {}
}
