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

@DisplayName("MemoryController")
class MemoryControllerTest {

    @Test
    @DisplayName("returns routed entries ordered by tier priority")
    void returnsRoutedEntriesOrderedByTierPriority() { 
        DefaultMemoryTierRouter<EntityRecord> router = new DefaultMemoryTierRouter<>(); 
        MemoryController controller = new MemoryController(router); 

        router.route(record("tenant-hot", "hot-record"), SalienceScore.of(0.95)).getResult(); 
        router.route(record("tenant-cold", "cold-record"), SalienceScore.of(0.25)).getResult(); 

        List<TierEntry> entries = controller.getEntries().getResult(); 

        assertThat(entries).hasSize(2); 
        assertThat(entries).extracting(TierEntry::getCurrentTier) 
                .containsExactly(MemoryTier.HOT, MemoryTier.COLD); 
        assertThat(entries).extracting(TierEntry::getTenantId) 
                .containsExactly("tenant-hot", "tenant-cold"); 
    }

    private static EntityRecord record(String tenantId, String recordId) { 
        return EntityRecord.builder() 
                .id(UUID.nameUUIDFromBytes(recordId.getBytes())) 
                .tenantId(tenantId) 
                .collectionName("memory-test")
                .metadata(Map.of("tenantId", tenantId + "-metadata")) 
                .createdBy("test")
                .build(); 
    }
}
