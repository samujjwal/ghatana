/*
 * Copyright (c) 2026 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.ghatana.yappc.kernelvisibility;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("KernelLifecycleEventIngestService")
class KernelLifecycleEventIngestServiceTest extends EventloopTestBase {

    @Test
    @DisplayName("delegates reads to provided truth source")
    void delegatesReadsToProvidedTruthSource() {
        KernelLifecycleTruthSource truthSource = new KernelLifecycleTruthSource() {
            @Override
            public Promise<Map<String, Object>> getProductUnitLifecycleData(String productUnitId) {
                return Promise.of(Map.of("productUnitId", productUnitId, "status", "found", "truthSource", "test-truth-source"));
            }

            @Override
            public Promise<List<Map<String, Object>>> listAllProductUnitLifecycleData() {
                return Promise.of(List.of(Map.of("productUnitId", "alpha", "status", "found")));
            }

            @Override
            public Promise<List<String>> listProductUnitIds() {
                return Promise.of(List.of("alpha"));
            }

            @Override
            public Promise<Boolean> hasLifecycleData(String productUnitId) {
                return Promise.of("alpha".equals(productUnitId));
            }
        };

        KernelLifecycleEventIngestService service = new KernelLifecycleEventIngestService(truthSource);

        Map<String, Object> single = runPromise(() -> service.ingestProductUnitLifecycle("alpha"));
        List<Map<String, Object>> all = runPromise(service::ingestAllProductUnitLifecycles);
        Boolean hasAlpha = runPromise(() -> service.hasProductUnitLifecycleData("alpha"));
        Boolean hasBeta = runPromise(() -> service.hasProductUnitLifecycleData("beta"));

        assertThat(single.get("productUnitId")).isEqualTo("alpha");
        assertThat(single.get("truthSource")).isEqualTo("test-truth-source");
        assertThat(all).hasSize(1);
        assertThat(hasAlpha).isTrue();
        assertThat(hasBeta).isFalse();
    }

    @Test
    @DisplayName("closes custom provider when service closes")
    void closesCustomProviderWhenServiceCloses() {
        AtomicBoolean closed = new AtomicBoolean(false);

        KernelLifecycleEventIngestService.KernelLifecycleEventProvider provider =
                new KernelLifecycleEventIngestService.KernelLifecycleEventProvider() {
                    @Override
                    public Promise<Map<String, Object>> ingestProductUnitLifecycle(String productUnitId) {
                        return Promise.of(Map.of("productUnitId", productUnitId, "status", "found"));
                    }

                    @Override
                    public Promise<List<Map<String, Object>>> ingestAllProductUnitLifecycles() {
                        return Promise.of(List.of());
                    }

                    @Override
                    public Promise<Boolean> hasProductUnitLifecycleData(String productUnitId) {
                        return Promise.of(false);
                    }

                    @Override
                    public void close() {
                        closed.set(true);
                    }
                };

        KernelLifecycleEventIngestService service = new KernelLifecycleEventIngestService(provider);
        service.close();

        assertThat(closed.get()).isTrue();
    }

    @Test
    @DisplayName("production runtime rejects local filesystem provider")
    void productionRuntimeRejectsLocalFilesystemProvider() {
        String previousProfile = System.getProperty("yappc.runtime.profile");
        try {
            System.setProperty("yappc.runtime.profile", "production");

            assertThatThrownBy(KernelLifecycleEventIngestService::new)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("production must inject DataCloudKernelLifecycleTruthSource");
        } finally {
            if (previousProfile == null) {
                System.clearProperty("yappc.runtime.profile");
            } else {
                System.setProperty("yappc.runtime.profile", previousProfile);
            }
        }
    }
}
