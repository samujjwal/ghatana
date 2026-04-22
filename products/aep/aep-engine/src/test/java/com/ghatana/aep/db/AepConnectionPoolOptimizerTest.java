/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * Unit tests for {@link AepConnectionPoolOptimizer} (AEP-004.4). // GH-90000
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("AepConnectionPoolOptimizer — AEP-004.4 [GH-90000]")
class AepConnectionPoolOptimizerTest {

    @Mock
    private DataSource mockDataSource;

    @Mock
    private Connection mockConnection;

    private AepConnectionPoolOptimizer optimizer;

    @BeforeEach
    void setUp() throws SQLException { // GH-90000
        optimizer = AepConnectionPoolOptimizer.builder(mockDataSource) // GH-90000
                .slowConnectionThreshold(Duration.ofMillis(50)) // GH-90000
                .build(); // GH-90000

        lenient().when(mockDataSource.getConnection()).thenReturn(mockConnection); // GH-90000
    }

    @Test
    @DisplayName("Successful borrows increment success counter [GH-90000]")
    void successfulBorrowsAreTracked() throws SQLException { // GH-90000
        optimizer.getConnection(); // GH-90000
        optimizer.getConnection(); // GH-90000

        AepConnectionPoolOptimizer.PoolStats stats = optimizer.stats(); // GH-90000
        assertThat(stats.successfulBorrows()).isEqualTo(2); // GH-90000
        assertThat(stats.totalBorrows()).isEqualTo(2); // GH-90000
        assertThat(stats.failedBorrows()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("Failed borrow increments failure counter [GH-90000]")
    void failedBorrowIsTracked() throws SQLException { // GH-90000
        when(mockDataSource.getConnection()).thenThrow(new SQLException("pool exhausted [GH-90000]"));

        assertThatThrownBy(() -> optimizer.getConnection()) // GH-90000
                .isInstanceOf(SQLException.class); // GH-90000

        AepConnectionPoolOptimizer.PoolStats stats = optimizer.stats(); // GH-90000
        assertThat(stats.failedBorrows()).isEqualTo(1); // GH-90000
        assertThat(stats.successfulBorrows()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("Efficiency = 1.0 when all borrows succeed [GH-90000]")
    void efficiencyIsOneWhenAllSucceed() throws SQLException { // GH-90000
        optimizer.getConnection(); // GH-90000
        optimizer.getConnection(); // GH-90000
        optimizer.getConnection(); // GH-90000

        AepConnectionPoolOptimizer.PoolStats stats = optimizer.stats(); // GH-90000
        assertThat(stats.efficiency()).isEqualTo(1.0); // GH-90000
        assertThat(stats.meetsTarget()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Efficiency < 1.0 when some borrows fail [GH-90000]")
    void efficiencyDropsWithFailures() throws SQLException { // GH-90000
        // 9 successes, 1 failure → efficiency = 0.90
        for (int i = 0; i < 9; i++) { // GH-90000
            optimizer.getConnection(); // GH-90000
        }
        when(mockDataSource.getConnection()).thenThrow(new SQLException("timeout [GH-90000]"));
        try { optimizer.getConnection(); } catch (SQLException ignored) {} // GH-90000

        AepConnectionPoolOptimizer.PoolStats stats = optimizer.stats(); // GH-90000
        assertThat(stats.efficiency()).isEqualTo(0.90); // GH-90000
        assertThat(stats.meetsTarget()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Empty stats return efficiency 1.0 and meet target [GH-90000]")
    void emptyStatsReturnDefault() { // GH-90000
        AepConnectionPoolOptimizer.PoolStats stats = optimizer.stats(); // GH-90000
        assertThat(stats.totalBorrows()).isEqualTo(0); // GH-90000
        assertThat(stats.efficiency()).isEqualTo(1.0); // GH-90000
        assertThat(stats.meetsTarget()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("asDataSource returns an instrumented DataSource [GH-90000]")
    void asDataSourceDelegates() throws SQLException { // GH-90000
        DataSource ds = optimizer.asDataSource(); // GH-90000
        ds.getConnection(); // GH-90000

        verify(mockDataSource).getConnection(); // GH-90000
        assertThat(optimizer.stats().successfulBorrows()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("Builder rejects null delegate [GH-90000]")
    void builderRejectsNullDelegate() { // GH-90000
        assertThatThrownBy(() -> AepConnectionPoolOptimizer.builder(null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("Builder rejects zero slow threshold [GH-90000]")
    void builderRejectsZeroThreshold() { // GH-90000
        assertThatThrownBy(() -> AepConnectionPoolOptimizer.builder(mockDataSource) // GH-90000
                .slowConnectionThreshold(Duration.ZERO)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }
}
