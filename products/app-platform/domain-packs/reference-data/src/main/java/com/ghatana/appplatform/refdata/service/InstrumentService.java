package com.ghatana.appplatform.refdata.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.appplatform.refdata.domain.Instrument;
import com.ghatana.appplatform.refdata.domain.InstrumentStatus;
import com.ghatana.appplatform.refdata.domain.InstrumentType;
import com.ghatana.appplatform.refdata.port.InstrumentStore;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type       Application Service
 * @doc.purpose    CRUD and temporal-validity service for the instrument master.
 *                 Handles D11-001 (CRUD) and D11-002 (point-in-time queries).
 *                 New instruments are created in PENDING_APPROVAL state and
 *                 require maker-checker approval before activation (D11-003).
 *                 Redis provides a 5-minute TTL hot cache to achieve sub-1ms
 *                 lookups for frequently accessed instruments.
 * @doc.layer      Application Service
 * @doc.pattern    Hexagonal Application Service
 */
public class InstrumentService {

    private static final Logger log = LoggerFactory.getLogger(InstrumentService.class);
    private static final int CACHE_TTL_SECONDS = 300; // 5 minutes

    private final InstrumentStore store;
    private final JedisPool jedisPool;
    private final Executor executor;
    private final Eventloop eventloop;
    private final ObjectMapper objectMapper;

    public InstrumentService(InstrumentStore store, JedisPool jedisPool,
                             Executor executor, Eventloop eventloop) {
        this.store = store;
        this.jedisPool = jedisPool;
        this.executor = executor;
        this.eventloop = eventloop;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Create a new instrument in PENDING_APPROVAL state.
     * Rejects with {@link DuplicateInstrumentException} if symbol+exchange already exists.
     */
    public Promise<Instrument> create(String symbol, String exchange, String isin,
                                      String name, InstrumentType type, String sector,
                                      int lotSize, BigDecimal tickSize, String currency,
                                      LocalDate effectiveFrom,
                                      Map<String, Object> metadata,
                                      String calendarDateBs) {
        return Promise.ofBlocking(executor, () -> {
            boolean exists = store.existsBySymbolAndExchange(symbol, exchange)
                    .get();
            if (exists) {
                throw new DuplicateInstrumentException(symbol, exchange);
            }
            Instrument instrument = new Instrument(
                    UUID.randomUUID(), symbol, exchange, isin, name, type,
                    InstrumentStatus.PENDING_APPROVAL,
                    sector, lotSize, tickSize, currency,
                    effectiveFrom == null ? LocalDate.now() : effectiveFrom,
                    null, Instant.now(), calendarDateBs, metadata);
            store.save(instrument).get();
            log.info("instrument.created symbol={} exchange={} id={}", symbol, exchange, instrument.id());
            return instrument;
        });
    }

    /** Return the current version of an instrument, checking cache first. */
    public Promise<Optional<Instrument>> findById(UUID id) {
        return Promise.ofBlocking(executor, () -> {
            String cacheKey = "refdata:instrument:" + id;
            try (var jedis = jedisPool.getResource()) {
                String cached = jedis.get(cacheKey);
                if (cached != null) {
                    Instrument fromCache = deserialize(cached);
                    if (fromCache != null) {
                        return Optional.of(fromCache);
                    }
                    // Deserialization failed — invalidate the bad entry and fall through to DB
                    jedis.del(cacheKey);
                }
            }
            Optional<Instrument> found = store.findCurrentById(id).get();
            found.ifPresent(i -> {
                String serialized = serialize(i);
                if (serialized != null) {
                    try (var jedis = jedisPool.getResource()) {
                        jedis.setex(cacheKey, CACHE_TTL_SECONDS, serialized);
                    }
                }
            });
            return found;
        });
    }

    /**
     * Point-in-time (as-of) query.  Returns the instrument version valid on
     * the given date (covers D11-002: temporal validity).
     */
    public Promise<Optional<Instrument>> findByIdAsOf(UUID id, LocalDate asOf) {
        return Promise.ofBlocking(executor, () -> store.findByIdAsOf(id, asOf).get());
    }

    /** List all current instruments, optionally filtered by status. */
    public Promise<List<Instrument>> list(InstrumentStatus statusFilter) {
        return Promise.ofBlocking(executor, () -> store.listCurrent(statusFilter).get());
    }

    /** Full-text search on symbol, ISIN, or name. */
    public Promise<List<Instrument>> search(String query, int limit) {
        return Promise.ofBlocking(executor, () -> store.search(query, limit).get());
    }

    /** Invalidate the Redis cache entry for the given instrument. */
    private void invalidateCache(UUID id) {
        try (var jedis = jedisPool.getResource()) {
            jedis.del("refdata:instrument:" + id);
        }
    }

    // ── Minimal JSON serialisation backed by Jackson ──────────────────────────

    private String serialize(Instrument i) {
        try {
            return objectMapper.writeValueAsString(new InstrumentCacheDto(i));
        } catch (Exception e) {
            log.warn("instrument.cache.serialize.failed id={}: {}", i.id(), e.getMessage());
            // Fall through — caller will retrieve from DB on next access
            return null;
        }
    }

    private Instrument deserialize(String json) {
        try {
            InstrumentCacheDto dto = objectMapper.readValue(json, InstrumentCacheDto.class);
            return dto.toInstrument();
        } catch (Exception e) {
            log.warn("instrument.cache.deserialize.failed — falling back to DB: {}", e.getMessage());
            // Return null to force DB fallback in findById
            return null;
        }
    }

    /**
     * DTO used for Redis cache serialisation. Mirrors all Instrument fields
     * using Jackson so the full object round-trips correctly.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class InstrumentCacheDto {
        public String id;
        public String symbol;
        public String exchange;
        public String isin;
        public String name;
        public String type;
        public String status;
        public String sector;
        public int lotSize;
        public BigDecimal tickSize;
        public String currency;
        public LocalDate effectiveFrom;
        public LocalDate effectiveTo;
        public Instant createdAt;
        public String calendarDateBs;
        public Map<String, Object> metadata;

        // Default constructor for Jackson
        public InstrumentCacheDto() {}

        public InstrumentCacheDto(Instrument i) {
            this.id             = i.id().toString();
            this.symbol         = i.symbol();
            this.exchange       = i.exchange();
            this.isin           = i.isin();
            this.name           = i.name();
            this.type           = i.type().name();
            this.status         = i.status().name();
            this.sector         = i.sector();
            this.lotSize        = i.lotSize();
            this.tickSize       = i.tickSize();
            this.currency       = i.currency();
            this.effectiveFrom  = i.effectiveFrom();
            this.effectiveTo    = i.effectiveTo();
            this.createdAt      = i.createdAt();
            this.calendarDateBs = i.calendarDateBs();
            this.metadata       = i.metadata();
        }

        public Instrument toInstrument() {
            return new Instrument(
                    UUID.fromString(id),
                    symbol, exchange, isin, name,
                    InstrumentType.valueOf(type),
                    InstrumentStatus.valueOf(status),
                    sector, lotSize, tickSize, currency,
                    effectiveFrom, effectiveTo, createdAt,
                    calendarDateBs, metadata);
        }
    }

    // ── Exceptions ────────────────────────────────────────────────────────────

    public static final class DuplicateInstrumentException extends RuntimeException {
        public DuplicateInstrumentException(String symbol, String exchange) {
            super("Instrument already exists: " + symbol + "@" + exchange);
        }
    }
}
