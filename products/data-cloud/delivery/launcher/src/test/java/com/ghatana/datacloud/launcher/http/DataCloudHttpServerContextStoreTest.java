/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.launcher.http.handlers.InMemoryContextStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DataCloudHttpServer ContextStore production validation")
class DataCloudHttpServerContextStoreTest {

    @Test
    @DisplayName("rejects InMemoryContextStore in production-like deployment modes")
    void rejectsInMemoryContextStoreInProduction() {
        assertThatThrownBy(() ->
                DataCloudHttpServer.validateContextStoreConfiguration("production", new InMemoryContextStore()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("durable ContextStore");

        assertThatThrownBy(() ->
                DataCloudHttpServer.validateContextStoreConfiguration("staging", new InMemoryContextStore()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("durable ContextStore");

        assertThatThrownBy(() ->
                DataCloudHttpServer.validateContextStoreConfiguration("sovereign", new InMemoryContextStore()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("durable ContextStore");
    }

    @Test
    @DisplayName("allows InMemoryContextStore only in local deployment mode")
    void allowsInMemoryContextStoreLocally() {
        assertThatCode(() ->
                DataCloudHttpServer.validateContextStoreConfiguration("local", new InMemoryContextStore()))
            .doesNotThrowAnyException();
    }
}
