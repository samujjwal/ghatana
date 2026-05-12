/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.obsolescence;

import com.ghatana.agent.obsolescence.ObsolescenceEvent;
import com.ghatana.agent.obsolescence.ObsolescenceSignalType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for DataCloudObsolescenceEventRepository.
 *
 * @doc.type class
 * @doc.purpose Tests for DataCloudObsolescenceEventRepository
 * @doc.layer data-cloud
 * @doc.pattern Test
 */
@DisplayName("DataCloudObsolescenceEventRepository Tests")
class DataCloudObsolescenceEventRepositoryTest {

    @Test
    @DisplayName("Should save and retrieve obsolescence event")
    void shouldSaveAndRetrieveObsolescenceEvent() {
        DataCloudObsolescenceEventRepository repository = new DataCloudObsolescenceEventRepository();

        ObsolescenceEvent event = new ObsolescenceEvent(
                "event-123",
                "mastery-123",
                ObsolescenceSignalType.API_CONTRACT_CHANGE,
                "API contract changed",
                Map.of("oldVersion", "1.0.0", "newVersion", "2.0.0"),
                Instant.now()
        );

        repository.save(event).await();
        Optional<ObsolescenceEvent> result = repository.findById("event-123").await();

        assertThat(result).isPresent();
        assertThat(result.get().eventId()).isEqualTo("event-123");
        assertThat(result.get().masteryId()).isEqualTo("mastery-123");
    }

    @Test
    @DisplayName("Should find obsolescence events by mastery ID")
    void shouldFindByMasteryId() {
        DataCloudObsolescenceEventRepository repository = new DataCloudObsolescenceEventRepository();

        ObsolescenceEvent event1 = new ObsolescenceEvent(
                "event-1", "mastery-123", ObsolescenceSignalType.API_CONTRACT_CHANGE,
                "API changed", Map.of(), Instant.now()
        );

        ObsolescenceEvent event2 = new ObsolescenceEvent(
                "event-2", "mastery-456", ObsolescenceSignalType.DOCUMENTATION_CONTRADICTION,
                "Docs conflict", Map.of(), Instant.now()
        );

        repository.save(event1).await();
        repository.save(event2).await();

        List<ObsolescenceEvent> results = repository.findByMasteryId("mastery-123").await();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).masteryId()).isEqualTo("mastery-123");
    }

    @Test
    @DisplayName("Should find recent obsolescence events")
    void shouldFindRecentEvents() {
        DataCloudObsolescenceEventRepository repository = new DataCloudObsolescenceEventRepository();

        Instant now = Instant.now();
        Instant past = now.minus(java.time.Duration.ofHours(2));

        ObsolescenceEvent recentEvent = new ObsolescenceEvent(
                "event-1", "mastery-123", ObsolescenceSignalType.API_CONTRACT_CHANGE,
                "API changed", Map.of(), now
        );

        ObsolescenceEvent oldEvent = new ObsolescenceEvent(
                "event-2", "mastery-456", ObsolescenceSignalType.DOCUMENTATION_CONTRADICTION,
                "Docs conflict", Map.of(), past
        );

        repository.save(recentEvent).await();
        repository.save(oldEvent).await();

        Instant threshold = now.minus(java.time.Duration.ofHours(1));
        List<ObsolescenceEvent> results = repository.findRecent(threshold).await();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).eventId()).isEqualTo("event-1");
    }
}
