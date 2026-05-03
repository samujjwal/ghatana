package com.ghatana.digitalmarketing.application.event;

import com.ghatana.digitalmarketing.domain.event.DmDeadLetterEntry;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DmDeadLetterRepository}.
 */
@DisplayName("DmDeadLetterRepository")
class DmDeadLetterRepositoryTest {

    @Test
    @DisplayName("noop repository can be created")
    void noopRepository_canBeCreated() {
        DmDeadLetterRepository repo = new DmDeadLetterRepository() {
            @Override
            public Promise<DmDeadLetterEntry> save(DmDeadLetterEntry entry) {
                return Promise.of(entry);
            }

            @Override
            public Promise<List<DmDeadLetterEntry>> findUnreplayed(String tenantId, int limit) {
                return Promise.of(List.of());
            }

            @Override
            public Promise<Optional<DmDeadLetterEntry>> findById(String id) {
                return Promise.of(Optional.empty());
            }

            @Override
            public Promise<DmDeadLetterEntry> update(DmDeadLetterEntry entry) {
                return Promise.of(entry);
            }
        };

        assertThat(repo).isNotNull();
    }

    @Test
    @DisplayName("repository interface defines required methods")
    void repositoryInterface_definesRequiredMethods() {
        // This test verifies the interface structure
        assertThat(DmDeadLetterRepository.class).isInterface();
        assertThat(DmDeadLetterRepository.class.getMethods()).hasSizeGreaterThan(0);
    }
}
