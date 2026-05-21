/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JdbcContextStore")
class JdbcContextStoreTest extends EventloopTestBase {

    @Test
    @DisplayName("persists, snapshots, reads, and deletes tenant context entries")
    void persistsContextEntries() {
        JdbcContextStore store = new JdbcContextStore(dataSource());

        long firstVersion = runPromise(() -> store.putEntries("tenant-a", Map.of(
                "region", "us-west",
                "limits", Map.of("daily", 25))));
        ContextStore.ContextSnapshot snapshot = runPromise(() -> store.getSnapshot("tenant-a"));
        Object region = runPromise(() -> store.getEntry("tenant-a", "region")).orElseThrow();
        boolean deleted = runPromise(() -> store.deleteEntry("tenant-a", "region"));
        Map<String, Object> remaining = runPromise(() -> store.getAllEntries("tenant-a"));

        assertThat(firstVersion).isEqualTo(1L);
        assertThat(snapshot.entries()).containsKeys("region", "limits");
        assertThat(snapshot.version()).isEqualTo(1L);
        assertThat(region).isEqualTo("us-west");
        assertThat(deleted).isTrue();
        assertThat(remaining).containsOnlyKeys("limits");
    }

    private static JdbcDataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:context-store-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
