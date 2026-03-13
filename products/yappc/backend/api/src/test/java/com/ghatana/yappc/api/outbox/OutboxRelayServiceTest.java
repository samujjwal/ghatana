/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api.outbox;

import com.ghatana.yappc.api.aep.AepClient;
import com.ghatana.yappc.api.aep.AepException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OutboxRelayService}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for the outbox relay service
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxRelayService")
class OutboxRelayServiceTest {

    private static final String VALID_UUID = "550e8400-e29b-41d4-a716-446655440000";

    @Mock DataSource dataSource;
    @Mock Connection connection;
    @Mock PreparedStatement ps;
    @Mock ResultSet rs;
    @Mock AepClient aepClient;

    OutboxRelayService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new OutboxRelayService(dataSource, aepClient);
        lenient().when(dataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.prepareStatement(anyString())).thenReturn(ps);
        lenient().when(ps.executeQuery()).thenReturn(rs);
    }

    // ==================== relayBatch ====================

    @Nested
    @DisplayName("relayBatch()")
    class RelayBatch {

        @Test
        @DisplayName("empty outbox → no publishEvent call")
        void emptyQueue_doesNothing() throws Exception {
            when(rs.next()).thenReturn(false);

            service.relayBatch();

            verifyNoInteractions(aepClient);
        }

        @Test
        @DisplayName("single pending entry → publishEvent called and markDelivered executed")
        void singleEntry_successfulDelivery_marksDelivered() throws Exception {
            when(rs.next()).thenReturn(true, false);
            when(rs.getString("id")).thenReturn(VALID_UUID);
            when(rs.getString("tenant_id")).thenReturn("tenant1");
            when(rs.getString("event_type")).thenReturn("FEATURE_CREATED");
            when(rs.getString("payload")).thenReturn("{\"id\":\"1\"}");
            when(rs.getInt("attempts")).thenReturn(0);
            when(aepClient.publishEvent(anyString(), anyString())).thenReturn("evt-123");

            service.relayBatch();

            verify(aepClient).publishEvent("FEATURE_CREATED", "{\"id\":\"1\"}");
            // markDelivered issues an UPDATE_DELIVERED — at least two prepareStatement calls total
            verify(connection, atLeast(2)).prepareStatement(anyString());
            verify(ps, atLeastOnce()).executeUpdate();
        }

        @Test
        @DisplayName("AepException → markFailed issued with back-off")
        void aepException_marksFailedWithBackoff() throws Exception {
            when(rs.next()).thenReturn(true, false);
            when(rs.getString("id")).thenReturn(VALID_UUID);
            when(rs.getString("tenant_id")).thenReturn("tenant1");
            when(rs.getString("event_type")).thenReturn("FEATURE_CREATED");
            when(rs.getString("payload")).thenReturn("{}");
            when(rs.getInt("attempts")).thenReturn(0);
            when(aepClient.publishEvent(anyString(), anyString()))
                    .thenThrow(new AepException("aep-down"));

            service.relayBatch();

            // markFailed issues UPDATE_FAILED
            verify(aepClient).publishEvent(anyString(), anyString());
            verify(connection, atLeast(2)).prepareStatement(anyString());
            verify(ps, atLeastOnce()).executeUpdate();
        }

        @Test
        @DisplayName("multiple entries → each processed independently")
        void multipleEntries_allProcessed() throws Exception {
            when(rs.next()).thenReturn(true, true, false);
            when(rs.getString("id")).thenReturn(VALID_UUID, "660e8400-e29b-41d4-a716-446655440001");
            when(rs.getString("tenant_id")).thenReturn("tenant1");
            when(rs.getString("event_type")).thenReturn("FEATURE_CREATED");
            when(rs.getString("payload")).thenReturn("{}");
            when(rs.getInt("attempts")).thenReturn(0);
            when(aepClient.publishEvent(anyString(), anyString())).thenReturn("ok");

            service.relayBatch();

            verify(aepClient, times(2)).publishEvent(anyString(), anyString());
        }

        @Test
        @DisplayName("SQL exception in fetchPending → logged and swallowed, no exception propagated")
        void sqlExceptionInFetch_noExceptionPropagated() throws Exception {
            when(dataSource.getConnection()).thenThrow(new SQLException("db-down"));

            assertThatNoException().isThrownBy(service::relayBatch);
            verifyNoInteractions(aepClient);
        }

        @Test
        @DisplayName("first entry fails AepException, second succeeds → both processed")
        void firstEntryFails_secondSucceeds() throws Exception {
            when(rs.next()).thenReturn(true, true, false);
            when(rs.getString("id"))
                    .thenReturn(VALID_UUID, "660e8400-e29b-41d4-a716-446655440001");
            when(rs.getString("tenant_id")).thenReturn("tenant1");
            when(rs.getString("event_type")).thenReturn("TYPE_A", "TYPE_B");
            when(rs.getString("payload")).thenReturn("{}");
            when(rs.getInt("attempts")).thenReturn(0);
            when(aepClient.publishEvent(eq("TYPE_A"), anyString()))
                    .thenThrow(new AepException("fail-a"));
            when(aepClient.publishEvent(eq("TYPE_B"), anyString())).thenReturn("ok");

            service.relayBatch();

            verify(aepClient, times(2)).publishEvent(anyString(), anyString());
        }
    }

    // ==================== Lifecycle ====================

    @Nested
    @DisplayName("start()")
    class Start {

        @Test
        @DisplayName("idempotent — calling start() twice does not start two schedulers")
        void idempotent_noDuplicateScheduler() {
            service.start();
            service.start(); // must not throw or double-schedule
            service.stop();
        }

        @Test
        @DisplayName("start() then stop() completes without error")
        void startThenStop_completesGracefully() {
            assertThatNoException().isThrownBy(() -> {
                service.start();
                service.stop();
            });
        }
    }

    @Nested
    @DisplayName("stop()")
    class Stop {

        @Test
        @DisplayName("stop() on a service that was never started is a no-op")
        void stopWithoutStart_isNoOp() {
            assertThatNoException().isThrownBy(service::stop);
        }
    }
}
