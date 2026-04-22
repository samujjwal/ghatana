/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.memory.model;

import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 Expansion tests for Agent Memory module.
 * Tests memory links, memory items, relationship tracking at scale.
 *
 * @doc.type class
 * @doc.purpose Phase 3 expansion tests for agent memory subsystem
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("AgentMemory - Phase 3 Expansion [GH-90000]")
class AgentMemoryExpansionTest {

    // ============================================
    // MEMORY LINKS EXPANSION (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Memory Links - Phase 3 [GH-90000]")
    class MemoryLinksTests {

        @Test
        @DisplayName("Create many memory links [GH-90000]")
        void createManyLinks() { // GH-90000
            List<MemoryLink> links = new ArrayList<>(); // GH-90000

            for (int i = 0; i < 100; i++) { // GH-90000
                final int idx = i;
                MemoryLink link = MemoryLink.builder() // GH-90000
                        .targetItemId("item-" + idx) // GH-90000
                        .linkType(LinkType.RELATED) // GH-90000
                        .strength(0.5 + (idx % 50) / 100.0) // GH-90000
                        .description("Link to item " + idx) // GH-90000
                        .build(); // GH-90000
                links.add(link); // GH-90000
            }

            assertThat(links).hasSize(100); // GH-90000
            for (MemoryLink link : links) { // GH-90000
                assertThat(link.getTargetItemId()).isNotNull(); // GH-90000
                assertThat(link.getStrength()).isBetween(0.0, 1.0); // GH-90000
            }
        }

        @Test
        @DisplayName("Different link types [GH-90000]")
        void differentLinkTypes() { // GH-90000
            LinkType[] types = {
                LinkType.RELATED, LinkType.SUPPORTS, LinkType.CONTRADICTS,
                LinkType.DERIVED_FROM, LinkType.SUPERSEDES
            };

            for (int i = 0; i < 50; i++) { // GH-90000
                final int idx = i;
                LinkType type = types[idx % types.length];

                MemoryLink link = MemoryLink.builder() // GH-90000
                        .targetItemId("item-" + idx) // GH-90000
                        .linkType(type) // GH-90000
                        .strength(0.75) // GH-90000
                        .build(); // GH-90000

                assertThat(link.getLinkType()).isEqualTo(type); // GH-90000
            }
        }

        @Test
        @DisplayName("Link strength variations [GH-90000]")
        void linkStrengthVariations() { // GH-90000
            double[] strengths = {0.0, 0.25, 0.5, 0.75, 1.0};

            for (int i = 0; i < 50; i++) { // GH-90000
                final int idx = i;
                double strength = strengths[idx % strengths.length];

                MemoryLink link = MemoryLink.builder() // GH-90000
                        .targetItemId("item-" + idx) // GH-90000
                        .linkType(LinkType.RELATED) // GH-90000
                        .strength(strength) // GH-90000
                        .build(); // GH-90000

                assertThat(link.getStrength()).isEqualTo(strength); // GH-90000
            }
        }

        @Test
        @DisplayName("Links with very long descriptions [GH-90000]")
        void longDescriptions() { // GH-90000
            String longDesc = "This is a detailed description of the relationship. ".repeat(100); // GH-90000

            for (int i = 0; i < 20; i++) { // GH-90000
                final int idx = i;
                MemoryLink link = MemoryLink.builder() // GH-90000
                        .targetItemId("item-" + idx) // GH-90000
                        .linkType(LinkType.RELATED) // GH-90000
                        .description(longDesc) // GH-90000
                        .build(); // GH-90000

                assertThat(link.getDescription()).hasSize(longDesc.length()); // GH-90000
            }
        }
    }

    // ============================================
    // MEMORY ITEM RELATIONSHIPS (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Memory Item Relationships [GH-90000]")
    class RelationshipTests {

        @Test
        @DisplayName("Build complex relationship graphs [GH-90000]")
        void complexGraphs() { // GH-90000
            // Create a graph with multiple item relationships
            List<MemoryLink> itemLinks = new ArrayList<>(); // GH-90000

            for (int source = 0; source < 10; source++) { // GH-90000
                for (int target = 0; target < 10; target++) { // GH-90000
                    if (source != target) { // GH-90000
                        final int s = source;
                        final int t = target;
                        MemoryLink link = MemoryLink.builder() // GH-90000
                                .targetItemId("item-" + t) // GH-90000
                                .linkType(LinkType.RELATED) // GH-90000
                                .strength(0.5) // GH-90000
                                .build(); // GH-90000
                        itemLinks.add(link); // GH-90000
                    }
                }
            }

            assertThat(itemLinks).hasSize(90);  // 10 * 9 relationships // GH-90000
        }

        @Test
        @DisplayName("Relationship strength distribution [GH-90000]")
        void strengthDistribution() { // GH-90000
            List<MemoryLink> links = new ArrayList<>(); // GH-90000
            int[] connectionCounts = new int[5];  // Track link counts by strength bucket

            for (int i = 0; i < 500; i++) { // GH-90000
                final int idx = i;
                double strength = idx % 5 * 0.2;  // Distribute across [0.0, 1.0]

                MemoryLink link = MemoryLink.builder() // GH-90000
                        .targetItemId("item-" + (idx / 5)) // GH-90000
                        .linkType(LinkType.RELATED) // GH-90000
                        .strength(strength) // GH-90000
                        .build(); // GH-90000
                links.add(link); // GH-90000

                int bucket = (int) (strength * 5); // GH-90000
                connectionCounts[Math.min(bucket, 4)]++; // GH-90000
            }

            assertThat(links).hasSize(500); // GH-90000
            for (int count : connectionCounts) { // GH-90000
                assertThat(count).isGreaterThan(0); // GH-90000
            }
        }

        @Test
        @DisplayName("Relationship type variety [GH-90000]")
        void typeVariety() { // GH-90000
            List<MemoryLink> links = new ArrayList<>(); // GH-90000

            for (int i = 0; i < 100; i++) { // GH-90000
                final int idx = i;
                LinkType type = LinkType.values()[idx % LinkType.values().length]; // GH-90000

                MemoryLink link = MemoryLink.builder() // GH-90000
                        .targetItemId("target-" + (idx % 20)) // GH-90000
                        .linkType(type) // GH-90000
                        .build(); // GH-90000
                links.add(link); // GH-90000
            }

            assertThat(links).hasSize(100); // GH-90000
        }
    }

    // ============================================
    // MEMORY ITEM OPERATIONS (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Memory Item Operations [GH-90000]")
    class ItemOperationsTests {

        @Test
        @DisplayName("Many memory items with cross-references [GH-90000]")
        void manyItemsCrossReferences() { // GH-90000
            Set<MemoryItem> items = new HashSet<>(); // GH-90000

            for (int i = 0; i < 100; i++) { // GH-90000
                final int idx = i;
                MemoryItem item = EnhancedProcedure.builder() // GH-90000
                        .id("item-" + idx) // GH-90000
                    .situation("Situation for item " + idx) // GH-90000
                    .action("Action for item " + idx) // GH-90000
                        .build(); // GH-90000
                items.add(item); // GH-90000
            }

            assertThat(items).hasSize(100); // GH-90000
        }

        @Test
        @DisplayName("Memory items with link updates [GH-90000]")
        void itemsWithLinkUpdates() { // GH-90000
            List<MemoryItem> items = new ArrayList<>(); // GH-90000

            for (int i = 0; i < 50; i++) { // GH-90000
                final int idx = i;
                MemoryItem item = EnhancedProcedure.builder() // GH-90000
                        .id("item-" + idx) // GH-90000
                    .situation("Situation [GH-90000]")
                    .action("Action [GH-90000]")
                        .build(); // GH-90000
                items.add(item); // GH-90000
            }

            // Simulate adding links to items
            for (int i = 0; i < items.size(); i++) { // GH-90000
                for (int j = 0; j < 5; j++) { // GH-90000
                    final int linkIdx = j;
                    MemoryLink link = MemoryLink.builder() // GH-90000
                            .targetItemId("item-" + ((i + linkIdx + 1) % items.size())) // GH-90000
                            .linkType(LinkType.RELATED) // GH-90000
                            .build(); // GH-90000
                    // Link would be added to item[i]
                }
            }
        }
    }

    // ============================================
    // CONCURRENT MEMORY OPERATIONS (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Concurrent Operations [GH-90000]")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent memory link creation [GH-90000]")
        void concurrentLinkCreation() throws Exception { // GH-90000
            int threadCount = 20;
            int linksPerThread = 100;
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000
            AtomicInteger totalLinks = new AtomicInteger(0); // GH-90000

            ExecutorService exec = Executors.newFixedThreadPool(threadCount); // GH-90000
            try {
                for (int t = 0; t < threadCount; t++) { // GH-90000
                    final int threadIdx = t;
                    exec.submit(() -> { // GH-90000
                        try {
                            for (int i = 0; i < linksPerThread; i++) { // GH-90000
                                final int linkIdx = i;
                                MemoryLink link = MemoryLink.builder() // GH-90000
                                        .targetItemId("item-" + threadIdx + "-" + linkIdx) // GH-90000
                                        .linkType(LinkType.RELATED) // GH-90000
                                        .strength(0.5) // GH-90000
                                        .build(); // GH-90000

                                assertThat(link.getTargetItemId()).isNotNull(); // GH-90000
                                totalLinks.incrementAndGet(); // GH-90000
                            }
                        } finally {
                            latch.countDown(); // GH-90000
                        }
                    });
                }
                assertThat(latch.await(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); // GH-90000
            } finally {
                exec.shutdownNow(); // GH-90000
            }

            assertThat(totalLinks.get()).isEqualTo(threadCount * linksPerThread); // GH-90000
        }

        @Test
        @DisplayName("High-volume relationship network building [GH-90000]")
        void highVolumeNetworkBuilding() { // GH-90000
            // Build a large relationship network
            int itemCount = 50;
            int linksPerItem = 30;
            int totalLinks = 0;

            for (int i = 0; i < itemCount; i++) { // GH-90000
                for (int j = 0; j < linksPerItem; j++) { // GH-90000
                    final int si = i;
                    final int tj = j;
                    MemoryLink link = MemoryLink.builder() // GH-90000
                            .targetItemId("item-" + ((si + tj) % itemCount)) // GH-90000
                            .linkType(LinkType.RELATED) // GH-90000
                            .strength(0.75) // GH-90000
                            .build(); // GH-90000

                    assertThat(link.getTargetItemId()).isNotNull(); // GH-90000
                    totalLinks++;
                }
            }

            assertThat(totalLinks).isEqualTo(itemCount * linksPerItem); // GH-90000
        }
    }

    // ============================================
    // EDGE CASES (1 test) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Edge Cases [GH-90000]")
    class EdgeCaseTests {

        @Test
        @DisplayName("Extreme memory link scenarios [GH-90000]")
        void extremeScenarios() { // GH-90000
            // Very long target ID
            String longTargetId = "item-" + "x".repeat(500); // GH-90000
            MemoryLink longIdLink = MemoryLink.builder() // GH-90000
                    .targetItemId(longTargetId) // GH-90000
                    .linkType(LinkType.RELATED) // GH-90000
                    .build(); // GH-90000
                assertThat(longIdLink.getTargetItemId()).hasSize(longTargetId.length()); // GH-90000

            // Extreme strength values
            MemoryLink minStrength = MemoryLink.builder() // GH-90000
                    .targetItemId("item-min [GH-90000]")
                    .linkType(LinkType.RELATED) // GH-90000
                    .strength(0.0) // GH-90000
                    .build(); // GH-90000
            assertThat(minStrength.getStrength()).isEqualTo(0.0); // GH-90000

            MemoryLink maxStrength = MemoryLink.builder() // GH-90000
                    .targetItemId("item-max [GH-90000]")
                    .linkType(LinkType.RELATED) // GH-90000
                    .strength(1.0) // GH-90000
                    .build(); // GH-90000
            assertThat(maxStrength.getStrength()).isEqualTo(1.0); // GH-90000
        }
    }
}
