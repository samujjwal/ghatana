/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Services Platform — SecretAccessLogger Tests
 */
package com.ghatana.yappc.services.security;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SecretAccessLogger}.
 *
 * <p>Tests use the package-private {@code persistPromise} to await the async write
 * reliably on the eventloop, then verify the PreparedStatement parameters.
 * The public fire-and-forget methods are lightly smoke-tested for constructor validation.
 */
@DisplayName("SecretAccessLogger")
@ExtendWith(MockitoExtension.class)
class SecretAccessLoggerTest extends EventloopTestBase {

    @Mock private DataSource       dataSource;
    @Mock private Connection       connection;
    @Mock private PreparedStatement ps;

    private SecretAccessLogger logger;

    @BeforeEach
    void setUp() throws SQLException {
        lenient().when(dataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.prepareStatement(anyString())).thenReturn(ps);
        lenient().when(ps.executeUpdate()).thenReturn(1);

        logger = new SecretAccessLogger(dataSource, eventloop());
    }

    // ── ENCRYPT / SUCCESS ────────────────────────────────────────────────────

    @Test
    @DisplayName("recordEncrypt: persists ENCRYPT / SUCCESS row")
    void recordEncrypt_persistsCorrectRow() throws SQLException {
        runPromise(() -> logger.persistPromise(
                "user-1", "tenant-A", "project.environmentVariables",
                SecretAccessLogger.Action.ENCRYPT, SecretAccessLogger.Outcome.SUCCESS, null));

        verify(connection).prepareStatement(contains("INSERT INTO secret_access_audit"));
        verify(ps).setString(2, "tenant-A");
        verify(ps).setString(3, "user-1");
        verify(ps).setString(4, "project.environmentVariables");
        verify(ps).setString(5, "ENCRYPT");
        verify(ps).setString(6, "SUCCESS");
        verify(ps).setString(7, null);
        verify(ps).executeUpdate();
    }

    @Test
    @DisplayName("recordEncrypt: sets all supplied fields correctly")
    void recordEncrypt_allFields() throws SQLException {
        runPromise(() -> logger.persistPromise(
                "agent-42", "tenant-B", "approval.sensitivePayload",
                SecretAccessLogger.Action.ENCRYPT, SecretAccessLogger.Outcome.SUCCESS, null));

        verify(ps).setString(3, "agent-42");
        verify(ps).setString(4, "approval.sensitivePayload");
    }

    // ── ENCRYPT / FAILURE ────────────────────────────────────────────────────

    @Test
    @DisplayName("recordEncryptFailure: persists ENCRYPT / FAILURE row with detail")
    void recordEncryptFailure_persistsDetail() throws SQLException {
        runPromise(() -> logger.persistPromise(
                "user-1", "tenant-A", "project.environmentVariables",
                SecretAccessLogger.Action.ENCRYPT, SecretAccessLogger.Outcome.FAILURE,
                "key not configured"));

        verify(ps).setString(5, "ENCRYPT");
        verify(ps).setString(6, "FAILURE");
        verify(ps).setString(7, "key not configured");
    }

    // ── DECRYPT / SUCCESS ────────────────────────────────────────────────────

    @Test
    @DisplayName("recordDecrypt: persists DECRYPT / SUCCESS row")
    void recordDecrypt_persistsDecryptRow() throws SQLException {
        runPromise(() -> logger.persistPromise(
                "user-2", "tenant-C", "project.environmentVariables",
                SecretAccessLogger.Action.DECRYPT, SecretAccessLogger.Outcome.SUCCESS, null));

        verify(ps).setString(5, "DECRYPT");
        verify(ps).setString(6, "SUCCESS");
        verify(ps).setString(7, null);
    }

    // ── DECRYPT / FAILURE ────────────────────────────────────────────────────

    @Test
    @DisplayName("recordDecryptFailure: persists DECRYPT / FAILURE row with detail")
    void recordDecryptFailure_persistsDetail() throws SQLException {
        runPromise(() -> logger.persistPromise(
                "user-3", "tenant-D", "approval.attachments",
                SecretAccessLogger.Action.DECRYPT, SecretAccessLogger.Outcome.FAILURE,
                "GCM tag mismatch"));

        verify(ps).setString(5, "DECRYPT");
        verify(ps).setString(6, "FAILURE");
        verify(ps).setString(7, "GCM tag mismatch");
    }

    // ── JDBC failure propagates through promise ──────────────────────────────

    @Test
    @DisplayName("JDBC connection failure propagates through persistPromise")
    void jdbcConnectionFailure_propagatesThroughPromise() throws Exception {
        when(connection.prepareStatement(anyString()))
                .thenThrow(new SQLException("connection lost"));

        // persistAsync (public API) swallows this; persistPromise surfaces it in the promise
        // Here we verify the exception is thrown so outer fire-and-forget can log it
        org.assertj.core.api.ThrowableAssert.ThrowingCallable callable = () ->
            runPromise(() -> logger.persistPromise(
                    "user-1", "tenant-A", "project.environmentVariables",
                    SecretAccessLogger.Action.ENCRYPT, SecretAccessLogger.Outcome.SUCCESS, null));

        assertThatThrownBy(callable)
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("executeUpdate failure propagates through persistPromise")
    void executeUpdateFailure_propagatesThroughPromise() throws Exception {
        when(ps.executeUpdate()).thenThrow(new SQLException("statement failed"));

        org.assertj.core.api.ThrowableAssert.ThrowingCallable callable = () ->
            runPromise(() -> logger.persistPromise(
                    "user-2", "tenant-B", "project.environmentVariables",
                    SecretAccessLogger.Action.DECRYPT, SecretAccessLogger.Outcome.SUCCESS, null));

        assertThatThrownBy(callable)
                .isInstanceOf(Exception.class);
    }

    // ── Constructor validation ────────────────────────────────────────────────

    @Test
    @DisplayName("null dataSource throws NullPointerException")
    void nullDataSource_throws() {
        assertThatThrownBy(() -> new SecretAccessLogger(null, eventloop()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("dataSource");
    }

    @Test
    @DisplayName("null eventloop throws NullPointerException")
    void nullEventloop_throws() {
        assertThatThrownBy(() -> new SecretAccessLogger(dataSource, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("eventloop");
    }
}
