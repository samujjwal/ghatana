package com.ghatana.auth.adapter.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.domain.auth.ClientId;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.domain.auth.Token;
import com.ghatana.platform.domain.auth.TokenId;
import com.ghatana.platform.domain.auth.UserId;
import com.ghatana.platform.security.port.TokenStore;
import io.activej.promise.Promise;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

/**
 * Redis-backed TokenStore for distributed token management.
 *
 * @doc.type class
 * @doc.purpose Redis token store adapter for distributed token storage
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class RedisTokenStore implements TokenStore {

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;
    private static final ForkJoinPool REDIS_POOL = ForkJoinPool.commonPool();

    public RedisTokenStore(JedisPool jedisPool, ObjectMapper objectMapper) {
        this.jedisPool = Objects.requireNonNull(jedisPool);
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public RedisTokenStore(JedisPool jedisPool) {
        this(jedisPool, new ObjectMapper());
    }

    @Override
    public Promise<Void> store(Token token) {
        return Promise.ofBlocking(REDIS_POOL, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String key = tokenKey(token.getTenantId(), token.getTokenId());
                String value = objectMapper.writeValueAsString(tokenToMap(token));
                long ttlSeconds = java.time.Duration.between(Instant.now(), token.getExpiresAt()).getSeconds();
                if (ttlSeconds > 0) {
                    jedis.setex(key, (int) ttlSeconds, value);
                } else {
                    jedis.set(key, value);
                }
                // Index by value
                jedis.set(valueKey(token.getTenantId(), token.getTokenValue()), token.getTokenId().value());
                // Index by user
                jedis.sadd(userKey(token.getTenantId(), token.getUserId()), token.getTokenId().value());
                // Index by client
                jedis.sadd(clientKey(token.getTenantId(), token.getClientId()), token.getTokenId().value());
            }
            return (Void) null;
        });
    }

    @Override
    public Promise<Optional<Token>> findById(TenantId tenantId, TokenId tokenId) {
        return Promise.ofBlocking(REDIS_POOL, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String key = tokenKey(tenantId, tokenId);
                String json = jedis.get(key);
                if (json == null) return Optional.<Token>empty();
                Token token = mapToToken(json);
                if (isRevoked(jedis, tenantId, tokenId) || token.getExpiresAt().isBefore(Instant.now())) {
                    return Optional.<Token>empty();
                }
                return Optional.of(token);
            }
        });
    }

    @Override
    public Promise<Optional<Token>> findByValue(TenantId tenantId, String tokenValue) {
        return Promise.ofBlocking(REDIS_POOL, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String tokenIdStr = jedis.get(valueKey(tenantId, tokenValue));
                if (tokenIdStr == null) return Optional.<Token>empty();
                TokenId tokenId = TokenId.of(tokenIdStr);
                String json = jedis.get(tokenKey(tenantId, tokenId));
                if (json == null) return Optional.<Token>empty();
                Token token = mapToToken(json);
                if (isRevoked(jedis, tenantId, tokenId) || token.getExpiresAt().isBefore(Instant.now())) {
                    return Optional.<Token>empty();
                }
                return Optional.of(token);
            }
        });
    }

    public Promise<List<Token>> findByUserId(TenantId tenantId, UserId userId) {
        return Promise.ofBlocking(REDIS_POOL, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                Set<String> tokenIds = jedis.smembers(userKey(tenantId, userId));
                List<Token> tokens = new ArrayList<>();
                for (String id : tokenIds) {
                    TokenId tokenId = TokenId.of(id);
                    String json = jedis.get(tokenKey(tenantId, tokenId));
                    if (json != null) {
                        Token token = mapToToken(json);
                        if (!isRevoked(jedis, tenantId, tokenId) && token.getExpiresAt().isAfter(Instant.now())) {
                            tokens.add(token);
                        }
                    }
                }
                return tokens;
            }
        });
    }

    public Promise<List<Token>> findByClientId(TenantId tenantId, ClientId clientId) {
        return Promise.ofBlocking(REDIS_POOL, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                Set<String> tokenIds = jedis.smembers(clientKey(tenantId, clientId));
                List<Token> tokens = new ArrayList<>();
                for (String id : tokenIds) {
                    TokenId tokenId = TokenId.of(id);
                    String json = jedis.get(tokenKey(tenantId, tokenId));
                    if (json != null) {
                        Token token = mapToToken(json);
                        if (!isRevoked(jedis, tenantId, tokenId) && token.getExpiresAt().isAfter(Instant.now())) {
                            tokens.add(token);
                        }
                    }
                }
                return tokens;
            }
        });
    }

    @Override
    public Promise<Void> revoke(TenantId tenantId, TokenId tokenId) {
        return Promise.ofBlocking(REDIS_POOL, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.set(revokeKey(tenantId, tokenId), Instant.now().toString());
            }
            return (Void) null;
        });
    }

    public Promise<Void> revokeAllForUser(TenantId tenantId, UserId userId) {
        return Promise.ofBlocking(REDIS_POOL, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                Set<String> tokenIds = jedis.smembers(userKey(tenantId, userId));
                for (String id : tokenIds) {
                    jedis.set(revokeKey(tenantId, TokenId.of(id)), Instant.now().toString());
                }
            }
            return (Void) null;
        });
    }

    public Promise<Void> revokeAllForClient(TenantId tenantId, ClientId clientId) {
        return Promise.ofBlocking(REDIS_POOL, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                Set<String> tokenIds = jedis.smembers(clientKey(tenantId, clientId));
                for (String id : tokenIds) {
                    jedis.set(revokeKey(tenantId, TokenId.of(id)), Instant.now().toString());
                }
            }
            return (Void) null;
        });
    }

    @Override
    public Promise<Boolean> isValid(TenantId tenantId, TokenId tokenId) {
        return Promise.ofBlocking(REDIS_POOL, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                return !isRevoked(jedis, tenantId, tokenId);
            }
        });
    }

    @Override
    public Promise<Integer> deleteExpired(TenantId tenantId) {
        // Redis TTL handles expiration automatically
        return Promise.of(0);
    }

    // ===== Private helpers =====

    private boolean isRevoked(Jedis jedis, TenantId tenantId, TokenId tokenId) {
        return jedis.exists(revokeKey(tenantId, tokenId));
    }

    private String tokenKey(TenantId tenantId, TokenId tokenId) {
        return tenantId.value() + ":token:" + tokenId.value();
    }

    private String valueKey(TenantId tenantId, String tokenValue) {
        return tenantId.value() + ":token_value:" + tokenValue;
    }

    private String userKey(TenantId tenantId, UserId userId) {
        return tenantId.value() + ":user_tokens:" + userId.value();
    }

    private String clientKey(TenantId tenantId, ClientId clientId) {
        return tenantId.value() + ":client_tokens:" + clientId.value();
    }

    private String revokeKey(TenantId tenantId, TokenId tokenId) {
        return tenantId.value() + ":revoked:" + tokenId.value();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> tokenToMap(Token token) {
        Map<String, Object> map = new HashMap<>();
        map.put("tenantId", token.getTenantId().value());
        map.put("tokenId", token.getTokenId().value());
        map.put("tokenType", token.getTokenType().name());
        map.put("userId", token.getUserId().value());
        map.put("clientId", token.getClientId().value());
        map.put("tokenValue", token.getTokenValue());
        map.put("issuedAt", token.getIssuedAt().toString());
        map.put("expiresAt", token.getExpiresAt().toString());
        return map;
    }

    @SuppressWarnings("unchecked")
    private Token mapToToken(String json) {
        try {
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            return Token.builder()
                    .tenantId(TenantId.of((String) map.get("tenantId")))
                    .tokenId(TokenId.of((String) map.get("tokenId")))
                    .tokenType(com.ghatana.platform.domain.auth.TokenType.valueOf((String) map.get("tokenType")))
                    .userId(UserId.of((String) map.get("userId")))
                    .clientId(ClientId.of((String) map.get("clientId")))
                    .tokenValue((String) map.get("tokenValue"))
                    .issuedAt(Instant.parse((String) map.get("issuedAt")))
                    .expiresAt(Instant.parse((String) map.get("expiresAt")))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize token from Redis", e);
        }
    }
}
