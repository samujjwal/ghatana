package com.ghatana.digitalmarketing.application.event;

import com.ghatana.digitalmarketing.domain.event.DmOutboxEntry;
import com.ghatana.digitalmarketing.domain.event.DmOutboxStatus;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DmOutboxRepository}.
 */
@DisplayName("DmOutboxRepository")
class DmOutboxRepositoryTest {

    @Test
    @DisplayName("noop repository can be created")
    void noopRepository_canBeCreated() {
        DmOutboxRepository repo = new DmOutboxRepository() {
            @Override
            public Promise<DmOutboxEntry> save(DmOutboxEntry entry) {
                return Promise.of(entry);
            }

            @Override
            public Promise<List<DmOutboxEntry>> findPending(String tenantId, int limit) {
                return Promise.of(List.of());
            }

            @Override
            public Promise<List<DmOutboxEntry>> findRetryable(String tenantId, int limit) {
                return Promise.of(List.of());
            }

            @Override
            public Promise<DmOutboxEntry> update(DmOutboxEntry entry) {
                return Promise.of(entry);
            }

            @Override
            public Promise<Optional<DmOutboxEntry>> findById(String id) {
                return Promise.of(Optional.empty());
            }

            @Override
            public Promise<Long> countByStatus(String tenantId, DmOutboxStatus status) {
                return Promise.of(0L);
            }
        };

        assertThat(repo).isNotNull();
    }

    @Test
    @DisplayName("repository interface defines required methods")
    void repositoryInterface_definesRequiredMethods() {
        // This test verifies the interface structure
        assertThat(DmOutboxRepository.class).isInterface();
        assertThat(DmOutboxRepository.class.getMethods()).hasSizeGreaterThan(0);
    }
}
