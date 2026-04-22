package com.ghatana.datacloud.api.controller;

import com.ghatana.datacloud.EntityRecord;
import com.ghatana.datacloud.memory.DefaultMemoryTierRouter;
import com.ghatana.datacloud.memory.MemoryTier;
import com.ghatana.datacloud.memory.TierEntry;
import com.ghatana.datacloud.attention.SalienceScore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MemoryController [GH-90000]")
class MemoryControllerTest {

    @Test
    @DisplayName("returns routed entries ordered by tier priority [GH-90000]")
    void returnsRoutedEntriesOrderedByTierPriority() { // GH-90000
        DefaultMemoryTierRouter<EntityRecord> router = new DefaultMemoryTierRouter<>(); // GH-90000
        MemoryController controller = new MemoryController(router); // GH-90000

        router.route(record("tenant-hot", "hot-record"), SalienceScore.of(0.95)).getResult(); // GH-90000
        router.route(record("tenant-cold", "cold-record"), SalienceScore.of(0.25)).getResult(); // GH-90000

        List<TierEntry> entries = controller.getEntries().getResult(); // GH-90000

        assertThat(entries).hasSize(2); // GH-90000
        assertThat(entries).extracting(TierEntry::getCurrentTier) // GH-90000
                .containsExactly(MemoryTier.HOT, MemoryTier.COLD); // GH-90000
        assertThat(entries).extracting(TierEntry::getTenantId) // GH-90000
                .containsExactly("tenant-hot", "tenant-cold"); // GH-90000
    }

    private static EntityRecord record(String tenantId, String recordId) { // GH-90000
        return EntityRecord.builder() // GH-90000
                .id(UUID.nameUUIDFromBytes(recordId.getBytes())) // GH-90000
                .tenantId(tenantId) // GH-90000
                .collectionName("memory-test [GH-90000]")
                .metadata(Map.of("tenantId", tenantId + "-metadata")) // GH-90000
                .createdBy("test [GH-90000]")
                .build(); // GH-90000
    }
}
