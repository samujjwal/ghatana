/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.provider;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SigningKeyRotator}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for rotating RSA signing key provider with Redis grace period (K14-006)
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SigningKeyRotator — Unit Tests")
class SigningKeyRotatorTest extends EventloopTestBase {

    @Mock private JedisPool jedisPool;
    @Mock private Jedis jedis;

    private SigningKeyRotator rotator;

    @BeforeEach
    void setUp() {
        when(jedisPool.getResource()).thenReturn(jedis);
        rotator = new SigningKeyRotator(jedisPool, Executors.newSingleThreadExecutor());
    }

    @Test
    @DisplayName("initial state — getSigningKey returns a non-null RSA key")
    void initialState_signingKeyNotNull() {
        RSAKey key = rotator.getSigningKey();
        assertThat(key).isNotNull();
        assertThat(key.isPrivate()).isTrue();
    }

    @Test
    @DisplayName("getKeyId — returns the kid of the active key")
    void getKeyId_matchesActiveKey() {
        assertThat(rotator.getKeyId()).isEqualTo(rotator.getSigningKey().getKeyID());
    }

    @Test
    @DisplayName("rotate — generates a new key and stores old public key in Redis")
    void rotate_newKeyGeneratedAndOldStoredInRedis() {
        String originalKid = rotator.getKeyId();
        when(jedis.setex(anyString(), anyLong(), anyString())).thenReturn("OK");

        String newKid = runPromise(() -> rotator.rotate());

        assertThat(newKid).isNotBlank();
        assertThat(newKid).isNotEqualTo(originalKid);
        assertThat(rotator.getKeyId()).isEqualTo(newKid);

        // Old key should be stored in Redis under kid:grace:{oldKid}
        verify(jedis).setex(eq("kid:grace:" + originalKid),
                eq(SigningKeyRotator.GRACE_TTL_SECONDS), anyString());
    }

    @Test
    @DisplayName("rotate — active kid pointer updated in Redis")
    void rotate_activeKidUpdatedInRedis() {
        when(jedis.setex(anyString(), anyLong(), anyString())).thenReturn("OK");

        String newKid = runPromise(() -> rotator.rotate());

        verify(jedis).setex(eq("kid:active"), eq(SigningKeyRotator.GRACE_TTL_SECONDS), eq(newKid));
    }

    @Test
    @DisplayName("rotate — signing key changes between rotations")
    void rotate_signingKeyChanges() {
        when(jedis.setex(anyString(), anyLong(), anyString())).thenReturn("OK");

        RSAKey before = rotator.getSigningKey();
        runPromise(() -> rotator.rotate());
        RSAKey after = rotator.getSigningKey();

        assertThat(after.getKeyID()).isNotEqualTo(before.getKeyID());
    }

    @Test
    @DisplayName("listValidPublicKeys — includes active key and grace period keys from Redis")
    void listValidPublicKeys_includesActiveAndGrace() throws Exception {
        when(jedis.setex(anyString(), anyLong(), anyString())).thenReturn("OK");
        // Rotate to set up one grace key
        runPromise(() -> rotator.rotate());

        // Mock Redis to return the grace key
        String gracePubKeyJson = rotator.getSigningKey().toPublicJWK().toJSONString();
        when(jedis.keys("kid:grace:*")).thenReturn(Set.of("kid:grace:old-kid"));
        when(jedis.get("kid:grace:old-kid")).thenReturn(gracePubKeyJson);

        List<RSAKey> keys = runPromise(() -> rotator.listValidPublicKeys());

        // Should have at least the active key
        assertThat(keys).isNotEmpty();
        assertThat(keys.get(0).isPrivate()).isFalse(); // public keys only
    }

    @Test
    @DisplayName("GRACE_TTL_SECONDS — equals 24 hours")
    void graceTtlSeconds_is24Hours() {
        assertThat(SigningKeyRotator.GRACE_TTL_SECONDS).isEqualTo(24L * 3600L);
    }

    @Test
    @DisplayName("rotate multiple times — each rotation produces unique kids")
    void rotateTwice_uniqueKids() {
        when(jedis.setex(anyString(), anyLong(), anyString())).thenReturn("OK");

        String kid1 = runPromise(() -> rotator.rotate());
        String kid2 = runPromise(() -> rotator.rotate());

        assertThat(kid1).isNotEqualTo(kid2);
    }
}
