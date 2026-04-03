package com.ghatana.platform.governance.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TenantContext Async Propagation")
class TenantContextAsyncPropagationTest {

    @Test
    @DisplayName("wrapped callable propagates tenant context across executor threads")
    void shouldPropagateTenantContextAcrossExecutor() throws Exception {
        Principal principal = new Principal("alice", List.of("admin"), "tenant-propagated");

        try (TenantContext.Scope ignored = TenantContext.scope(principal);
                var executor = Executors.newSingleThreadExecutor()) {
            Future<String> result = executor.submit(TenantContext.wrapWithCurrentContext(TenantContext::getCurrentTenantId));

            assertThat(result.get()).isEqualTo("tenant-propagated");
            assertThat(TenantContext.getCurrentTenantId()).isEqualTo("tenant-propagated");
        }

        assertThat(TenantContext.getCurrentTenantId()).isEqualTo("default-tenant");
    }

    @Test
    @DisplayName("wrapped callable restores worker thread previous context")
    void shouldRestoreWorkerThreadContextAfterWrappedCall() throws Exception {
        Principal principal = new Principal("bob", List.of("editor"), "tenant-main");

        try (TenantContext.Scope ignored = TenantContext.scope(principal);
                var executor = Executors.newSingleThreadExecutor()) {
            var wrapped = TenantContext.wrapWithCurrentContext(TenantContext::getCurrentTenantId);
            Future<String> result = executor.submit(() -> {
                TenantContext.setCurrentTenantId("tenant-worker");
                try {
                    String inside = wrapped.call();
                    String restored = TenantContext.getCurrentTenantId();
                    return inside + "|" + restored;
                } finally {
                    TenantContext.clear();
                }
            });

            assertThat(result.get()).isEqualTo("tenant-main|tenant-worker");
        }
    }
}
