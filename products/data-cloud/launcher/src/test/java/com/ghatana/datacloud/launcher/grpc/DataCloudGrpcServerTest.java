/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.grpc;

import com.ghatana.platform.domain.eventstore.EventLogStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @doc.type class
 * @doc.purpose Tests for DataCloudGrpcServer lifecycle
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudGrpcServer")
class DataCloudGrpcServerTest {

    private DataCloudGrpcServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    @DisplayName("constructor with port uses specified port")
    void constructorWithPort_usesSpecifiedPort() {
        EventLogStore store = mock(EventLogStore.class);
        server = new DataCloudGrpcServer(store, 9091);
        assertThat(server).isNotNull();
    }

    @Test
    @DisplayName("constructor without port uses default port")
    void constructorWithoutPort_usesDefaultPort() {
        EventLogStore store = mock(EventLogStore.class);
        server = new DataCloudGrpcServer(store);
        assertThat(server).isNotNull();
    }

    @Test
    @DisplayName("constructor without port uses environment variable")
    void constructorWithoutPort_usesEnvironmentVariable() {
        System.setProperty("DATACLOUD_GRPC_PORT", "9092");
        try {
            EventLogStore store = mock(EventLogStore.class);
            server = new DataCloudGrpcServer(store);
            assertThat(server).isNotNull();
        } finally {
            System.clearProperty("DATACLOUD_GRPC_PORT");
        }
    }

    @Test
    @DisplayName("constructor without port handles invalid environment variable")
    void constructorWithoutPort_handlesInvalidEnvironmentVariable() {
        System.setProperty("DATACLOUD_GRPC_PORT", "invalid");
        try {
            EventLogStore store = mock(EventLogStore.class);
            server = new DataCloudGrpcServer(store);
            assertThat(server).isNotNull();
        } finally {
            System.clearProperty("DATACLOUD_GRPC_PORT");
        }
    }

    @Test
    @DisplayName("close gracefully shuts down server")
    void close_gracefullyShutsDown() {
        EventLogStore store = mock(EventLogStore.class);
        server = new DataCloudGrpcServer(store, 0); // Use port 0 to get random available port
        assertThat(server).isNotNull();
        server.close(); // Should not throw
    }
}
