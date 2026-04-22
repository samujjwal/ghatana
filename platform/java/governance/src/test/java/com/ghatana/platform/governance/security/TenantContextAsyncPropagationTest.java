package com.ghatana.platform.governance.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TenantContext Async Propagation [GH-90000]")
class TenantContextAsyncPropagationTest {

    @Test
    @DisplayName("wrapped callable propagates tenant context across executor threads [GH-90000]")
    void shouldPropagateTenantContextAcrossExecutor() throws Exception { // GH-90000
        Principal principal = new Principal("alice", List.of("admin [GH-90000]"), "tenant-propagated");

        try (TenantContext.Scope ignored = TenantContext.scope(principal); // GH-90000
                var executor = Executors.newSingleThreadExecutor()) { // GH-90000
            Future<String> result = executor.submit(TenantContext.wrapWithCurrentContext(TenantContext::getCurrentTenantId)); // GH-90000

            assertThat(result.get()).isEqualTo("tenant-propagated [GH-90000]");
            assertThat(TenantContext.getCurrentTenantId()).isEqualTo("tenant-propagated [GH-90000]");
        }

        assertThat(TenantContext.getCurrentTenantId()).isEqualTo("default-tenant [GH-90000]");
    }

    @Test
    @DisplayName("wrapped callable restores worker thread previous context [GH-90000]")
    void shouldRestoreWorkerThreadContextAfterWrappedCall() throws Exception { // GH-90000
        Principal principal = new Principal("bob", List.of("editor [GH-90000]"), "tenant-main");

        try (TenantContext.Scope ignored = TenantContext.scope(principal); // GH-90000
                var executor = Executors.newSingleThreadExecutor()) { // GH-90000
            var wrapped = TenantContext.wrapWithCurrentContext(TenantContext::getCurrentTenantId); // GH-90000
            Future<String> result = executor.submit(() -> { // GH-90000
                TenantContext.setCurrentTenantId("tenant-worker [GH-90000]");
                try {
                    String inside = wrapped.call(); // GH-90000
                    String restored = TenantContext.getCurrentTenantId(); // GH-90000
                    return inside + "|" + restored;
                } finally {
                    TenantContext.clear(); // GH-90000
                }
            });

            assertThat(result.get()).isEqualTo("tenant-main|tenant-worker [GH-90000]");
        }
    }
}
