/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.db;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AepConnectionPoolOptimizer} (AEP-004.4).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AepConnectionPoolOptimizer — AEP-004.4")
class AepConnectionPoolOptimizerTest {

    @Mock
    private DataSource mockDataSource;

    @Mock
    private Connection mockConnection;

    private AepConnectionPoolOptimizer optimizer;

    @BeforeEach
    void setUp() throws SQLException {
        optimizer = AepConnectionPoolOptimizer.builder(mockDataSource)
                .slowConnectionThreshold(Duration.ofMillis(50))
                .build();

        lenient().when(mockDataSource.getConnection()).thenReturn(mockConnection);
    }

    @Test
    @DisplayName("Successful borrows increment success counter")
    void successfulBorrowsAreTracked() throws SQLException {
        optimizer.getConnection();
        optimizer.getConnection();

        AepConnectionPoolOptimizer.PoolStats stats = optimizer.stats();
        assertThat(stats.successfulBorrows()).isEqualTo(2);
        assertThat(stats.totalBorrows()).isEqualTo(2);
        assertThat(stats.failedBorrows()).isEqualTo(0);
    }

    @Test
    @DisplayName("Failed borrow increments failure counter")
    void failedBorrowIsTracked() throws SQLException {
        when(mockDataSource.getConnection()).thenThrow(new SQLException("pool exhausted"));

        assertThatThrownBy(() -> optimizer.getConnection())
                .isInstanceOf(SQLException.class);

        AepConnectionPoolOptimizer.PoolStats stats = optimizer.stats();
        assertThat(stats.failedBorrows()).isEqualTo(1);
        assertThat(stats.successfulBorrows()).isEqualTo(0);
    }

    @Test
    @DisplayName("Efficiency = 1.0 when all borrows succeed")
    void efficiencyIsOneWhenAllSucceed() throws SQLException {
        optimizer.getConnection();
        optimizer.getConnection();
        optimizer.getConnection();

        AepConnectionPoolOptimizer.PoolStats stats = optimizer.stats();
        assertThat(stats.efficiency()).isEqualTo(1.0);
        assertThat(stats.meetsTarget()).isTrue();
    }

    @Test
    @DisplayName("Efficiency < 1.0 when some borrows fail")
    void efficiencyDropsWithFailures() throws SQLException {
        // 9 successes, 1 failure → efficiency = 0.90
        for (int i = 0; i < 9; i++) {
            optimizer.getConnection();
        }
        when(mockDataSource.getConnection()).thenThrow(new SQLException("timeout"));
        try { optimizer.getConnection(); } catch (SQLException ignored) {}

        AepConnectionPoolOptimizer.PoolStats stats = optimizer.stats();
        assertThat(stats.efficiency()).isEqualTo(0.90);
        assertThat(stats.meetsTarget()).isTrue();
    }

    @Test
    @DisplayName("Empty stats return efficiency 1.0 and meet target")
    void emptyStatsReturnDefault() {
        AepConnectionPoolOptimizer.PoolStats stats = optimizer.stats();
        assertThat(stats.totalBorrows()).isEqualTo(0);
        assertThat(stats.efficiency()).isEqualTo(1.0);
        assertThat(stats.meetsTarget()).isTrue();
    }

    @Test
    @DisplayName("asDataSource returns an instrumented DataSource")
    void asDataSourceDelegates() throws SQLException {
        DataSource ds = optimizer.asDataSource();
        ds.getConnection();

        verify(mockDataSource).getConnection();
        assertThat(optimizer.stats().successfulBorrows()).isEqualTo(1);
    }

    @Test
    @DisplayName("Builder rejects null delegate")
    void builderRejectsNullDelegate() {
        assertThatThrownBy(() -> AepConnectionPoolOptimizer.builder(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Builder rejects zero slow threshold")
    void builderRejectsZeroThreshold() {
        assertThatThrownBy(() -> AepConnectionPoolOptimizer.builder(mockDataSource)
                .slowConnectionThreshold(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

