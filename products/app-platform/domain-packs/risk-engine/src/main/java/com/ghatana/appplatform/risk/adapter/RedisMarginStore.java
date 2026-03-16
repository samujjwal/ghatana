package com.ghatana.appplatform.risk.adapter;

import com.ghatana.appplatform.risk.domain.MarginRecord;
import com.ghatana.appplatform.risk.port.MarginStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;

/**
 * @doc.type    Adapter (Secondary)
 * @doc.purpose Redis-backed implementation of {@link MarginStore} (D06-001).
 *              Stores margin as a Redis Hash: {@code margin:{clientId}:{accountId}}
 *              with fields {@code deposited} and {@code used}.
 *              Uses a Redis WATCH + MULTI/EXEC optimistic-lock loop for atomic reservation.
 * @doc.layer   Infrastructure Adapter
 * @doc.pattern Hexagonal Architecture — secondary adapter; Redis optimistic locking
 */
public class RedisMarginStore implements MarginStore {

    private static final Logger log = LoggerFactory.getLogger(RedisMarginStore.class);
    private static final int MAX_RETRY = 3;
    private static final String FIELD_DEPOSITED = "deposited";
    private static final String FIELD_USED       = "used";

    private final JedisPool jedisPool;

    public RedisMarginStore(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    @Override
    public Optional<MarginRecord> find(String clientId, String accountId) {
        String key = key(clientId, accountId);
        try (var jedis = jedisPool.getResource()) {
            var fields = jedis.hmget(key, FIELD_DEPOSITED, FIELD_USED);
            if (fields.get(0) == null) return Optional.empty();
            return Optional.of(new MarginRecord(clientId, accountId,
                    new BigDecimal(fields.get(0)),
                    new BigDecimal(fields.get(1)),
                    Instant.now()));
        }
    }

    /**
     * Atomically reserve margin using Redis WATCH + MULTI/EXEC optimistic locking (D06-001).
     * Retries up to {@value #MAX_RETRY} times on conflict before returning empty.
     */
    @Override
    public Optional<MarginRecord> reserveAtomic(String clientId, String accountId,
                                                  BigDecimal amount) {
        String key = key(clientId, accountId);
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            try (var jedis = jedisPool.getResource()) {
                jedis.watch(key);
                var fields = jedis.hmget(key, FIELD_DEPOSITED, FIELD_USED);
                if (fields.get(0) == null) {
                    jedis.unwatch();
                    return Optional.empty();
                }

                BigDecimal deposited = new BigDecimal(fields.get(0));
                BigDecimal used      = new BigDecimal(fields.get(1));
                BigDecimal available = deposited.subtract(used);

                if (available.compareTo(amount) < 0) {
                    jedis.unwatch();
                    return Optional.empty();
                }

                BigDecimal newUsed = used.add(amount).setScale(2, RoundingMode.HALF_EVEN);
                Transaction tx = jedis.multi();
                tx.hset(key, FIELD_USED, newUsed.toPlainString());
                var results = tx.exec();

                if (results != null) {
                    // EXEC succeeded — no concurrent modification
                    return Optional.of(new MarginRecord(clientId, accountId,
                            deposited, newUsed, Instant.now()));
                }
                log.debug("Redis WATCH conflict on margin reservation, retry {}", attempt + 1);
            }
        }
        return Optional.empty();
    }

    @Override
    public void release(String clientId, String accountId, BigDecimal amount) {
        String key = key(clientId, accountId);
        try (var jedis = jedisPool.getResource()) {
            // HINCRBYFLOAT with negative value to decrement used
            BigDecimal decrement = amount.negate().setScale(2, RoundingMode.HALF_EVEN);
            jedis.hincrbyfloat(key, FIELD_USED, decrement.doubleValue());
        }
    }

    @Override
    public void upsert(MarginRecord record) {
        String key = key(record.clientId(), record.accountId());
        try (var jedis = jedisPool.getResource()) {
            jedis.hset(key, FIELD_DEPOSITED, record.deposited().toPlainString());
            jedis.hset(key, FIELD_USED,      record.used().toPlainString());
        }
    }

    private static String key(String clientId, String accountId) {
        return "margin:" + clientId + ":" + accountId;
    }
}
