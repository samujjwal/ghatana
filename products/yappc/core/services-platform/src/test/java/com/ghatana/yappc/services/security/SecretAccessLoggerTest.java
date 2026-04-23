/*
 * Copyright (c) 2026 Ghatana Technologies // GH-90000
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
@ExtendWith(MockitoExtension.class) // GH-90000
class SecretAccessLoggerTest extends EventloopTestBase {

    @Mock private DataSource       dataSource;
    @Mock private Connection       connection;
    @Mock private PreparedStatement ps;

    private SecretAccessLogger logger;

    @BeforeEach
    void setUp() throws SQLException { // GH-90000
        lenient().when(dataSource.getConnection()).thenReturn(connection); // GH-90000
        lenient().when(connection.prepareStatement(anyString())).thenReturn(ps); // GH-90000
        lenient().when(ps.executeUpdate()).thenReturn(1); // GH-90000

        logger = new SecretAccessLogger(dataSource, eventloop()); // GH-90000
    }

    // ── ENCRYPT / SUCCESS ────────────────────────────────────────────────────

    @Test
    @DisplayName("recordEncrypt: persists ENCRYPT / SUCCESS row")
    void recordEncrypt_persistsCorrectRow() throws SQLException { // GH-90000
        runPromise(() -> logger.persistPromise( // GH-90000
                "user-1", "tenant-A", "project.environmentVariables",
                SecretAccessLogger.Action.ENCRYPT, SecretAccessLogger.Outcome.SUCCESS, null));

        verify(connection).prepareStatement(contains("INSERT INTO secret_access_audit"));
        verify(ps).setString(2, "tenant-A"); // GH-90000
        verify(ps).setString(3, "user-1"); // GH-90000
        verify(ps).setString(4, "project.environmentVariables"); // GH-90000
        verify(ps).setString(5, "ENCRYPT"); // GH-90000
        verify(ps).setString(6, "SUCCESS"); // GH-90000
        verify(ps).setString(7, null); // GH-90000
        verify(ps).executeUpdate(); // GH-90000
    }

    @Test
    @DisplayName("recordEncrypt: sets all supplied fields correctly")
    void recordEncrypt_allFields() throws SQLException { // GH-90000
        runPromise(() -> logger.persistPromise( // GH-90000
                "agent-42", "tenant-B", "approval.sensitivePayload",
                SecretAccessLogger.Action.ENCRYPT, SecretAccessLogger.Outcome.SUCCESS, null));

        verify(ps).setString(3, "agent-42"); // GH-90000
        verify(ps).setString(4, "approval.sensitivePayload"); // GH-90000
    }

    // ── ENCRYPT / FAILURE ────────────────────────────────────────────────────

    @Test
    @DisplayName("recordEncryptFailure: persists ENCRYPT / FAILURE row with detail")
    void recordEncryptFailure_persistsDetail() throws SQLException { // GH-90000
        runPromise(() -> logger.persistPromise( // GH-90000
                "user-1", "tenant-A", "project.environmentVariables",
                SecretAccessLogger.Action.ENCRYPT, SecretAccessLogger.Outcome.FAILURE,
                "key not configured"));

        verify(ps).setString(5, "ENCRYPT"); // GH-90000
        verify(ps).setString(6, "FAILURE"); // GH-90000
        verify(ps).setString(7, "key not configured"); // GH-90000
    }

    // ── DECRYPT / SUCCESS ────────────────────────────────────────────────────

    @Test
    @DisplayName("recordDecrypt: persists DECRYPT / SUCCESS row")
    void recordDecrypt_persistsDecryptRow() throws SQLException { // GH-90000
        runPromise(() -> logger.persistPromise( // GH-90000
                "user-2", "tenant-C", "project.environmentVariables",
                SecretAccessLogger.Action.DECRYPT, SecretAccessLogger.Outcome.SUCCESS, null));

        verify(ps).setString(5, "DECRYPT"); // GH-90000
        verify(ps).setString(6, "SUCCESS"); // GH-90000
        verify(ps).setString(7, null); // GH-90000
    }

    // ── DECRYPT / FAILURE ────────────────────────────────────────────────────

    @Test
    @DisplayName("recordDecryptFailure: persists DECRYPT / FAILURE row with detail")
    void recordDecryptFailure_persistsDetail() throws SQLException { // GH-90000
        runPromise(() -> logger.persistPromise( // GH-90000
                "user-3", "tenant-D", "approval.attachments",
                SecretAccessLogger.Action.DECRYPT, SecretAccessLogger.Outcome.FAILURE,
                "GCM tag mismatch"));

        verify(ps).setString(5, "DECRYPT"); // GH-90000
        verify(ps).setString(6, "FAILURE"); // GH-90000
        verify(ps).setString(7, "GCM tag mismatch"); // GH-90000
    }

    // ── JDBC failure propagates through promise ──────────────────────────────

    @Test
    @DisplayName("JDBC connection failure propagates through persistPromise")
    void jdbcConnectionFailure_propagatesThroughPromise() throws Exception { // GH-90000
        when(connection.prepareStatement(anyString())) // GH-90000
                .thenThrow(new SQLException("connection lost"));

        // persistAsync (public API) swallows this; persistPromise surfaces it in the promise // GH-90000
        // Here we verify the exception is thrown so outer fire-and-forget can log it
        org.assertj.core.api.ThrowableAssert.ThrowingCallable callable = () -> // GH-90000
            runPromise(() -> logger.persistPromise( // GH-90000
                    "user-1", "tenant-A", "project.environmentVariables",
                    SecretAccessLogger.Action.ENCRYPT, SecretAccessLogger.Outcome.SUCCESS, null));

        assertThatThrownBy(callable) // GH-90000
                .isInstanceOf(Exception.class); // GH-90000
    }

    @Test
    @DisplayName("executeUpdate failure propagates through persistPromise")
    void executeUpdateFailure_propagatesThroughPromise() throws Exception { // GH-90000
        when(ps.executeUpdate()).thenThrow(new SQLException("statement failed"));

        org.assertj.core.api.ThrowableAssert.ThrowingCallable callable = () -> // GH-90000
            runPromise(() -> logger.persistPromise( // GH-90000
                    "user-2", "tenant-B", "project.environmentVariables",
                    SecretAccessLogger.Action.DECRYPT, SecretAccessLogger.Outcome.SUCCESS, null));

        assertThatThrownBy(callable) // GH-90000
                .isInstanceOf(Exception.class); // GH-90000
    }

    // ── Constructor validation ────────────────────────────────────────────────

    @Test
    @DisplayName("null dataSource throws NullPointerException")
    void nullDataSource_throws() { // GH-90000
        assertThatThrownBy(() -> new SecretAccessLogger(null, eventloop())) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("dataSource");
    }

    @Test
    @DisplayName("null eventloop throws NullPointerException")
    void nullEventloop_throws() { // GH-90000
        assertThatThrownBy(() -> new SecretAccessLogger(dataSource, null)) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("eventloop");
    }
}
