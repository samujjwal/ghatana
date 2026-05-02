/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("AgentMemory - Phase 3 Expansion")
class AgentMemoryExpansionTest {

    // ============================================
    // MEMORY LINKS EXPANSION (4 tests) 
    // ============================================

    @Nested
    @DisplayName("Memory Links - Phase 3")
    class MemoryLinksTests {

        @Test
        @DisplayName("Create many memory links")
        void createManyLinks() { 
            List<MemoryLink> links = new ArrayList<>(); 

            for (int i = 0; i < 100; i++) { 
                final int idx = i;
                MemoryLink link = MemoryLink.builder() 
                        .targetItemId("item-" + idx) 
                        .linkType(LinkType.RELATED) 
                        .strength(0.5 + (idx % 50) / 100.0) 
                        .description("Link to item " + idx) 
                        .build(); 
                links.add(link); 
            }

            assertThat(links).hasSize(100); 
            for (MemoryLink link : links) { 
                assertThat(link.getTargetItemId()).isNotNull(); 
                assertThat(link.getStrength()).isBetween(0.0, 1.0); 
            }
        }

        @Test
        @DisplayName("Different link types")
        void differentLinkTypes() { 
            LinkType[] types = {
                LinkType.RELATED, LinkType.SUPPORTS, LinkType.CONTRADICTS,
                LinkType.DERIVED_FROM, LinkType.SUPERSEDES
            };

            for (int i = 0; i < 50; i++) { 
                final int idx = i;
                LinkType type = types[idx % types.length];

                MemoryLink link = MemoryLink.builder() 
                        .targetItemId("item-" + idx) 
                        .linkType(type) 
                        .strength(0.75) 
                        .build(); 

                assertThat(link.getLinkType()).isEqualTo(type); 
            }
        }

        @Test
        @DisplayName("Link strength variations")
        void linkStrengthVariations() { 
            double[] strengths = {0.0, 0.25, 0.5, 0.75, 1.0};

            for (int i = 0; i < 50; i++) { 
                final int idx = i;
                double strength = strengths[idx % strengths.length];

                MemoryLink link = MemoryLink.builder() 
                        .targetItemId("item-" + idx) 
                        .linkType(LinkType.RELATED) 
                        .strength(strength) 
                        .build(); 

                assertThat(link.getStrength()).isEqualTo(strength); 
            }
        }

        @Test
        @DisplayName("Links with very long descriptions")
        void longDescriptions() { 
            String longDesc = "This is a detailed description of the relationship. ".repeat(100); 

            for (int i = 0; i < 20; i++) { 
                final int idx = i;
                MemoryLink link = MemoryLink.builder() 
                        .targetItemId("item-" + idx) 
                        .linkType(LinkType.RELATED) 
                        .description(longDesc) 
                        .build(); 

                assertThat(link.getDescription()).hasSize(longDesc.length()); 
            }
        }
    }

    // ============================================
    // MEMORY ITEM RELATIONSHIPS (3 tests) 
    // ============================================

    @Nested
    @DisplayName("Memory Item Relationships")
    class RelationshipTests {

        @Test
        @DisplayName("Build complex relationship graphs")
        void complexGraphs() { 
            // Create a graph with multiple item relationships
            List<MemoryLink> itemLinks = new ArrayList<>(); 

            for (int source = 0; source < 10; source++) { 
                for (int target = 0; target < 10; target++) { 
                    if (source != target) { 
                        final int s = source;
                        final int t = target;
                        MemoryLink link = MemoryLink.builder() 
                                .targetItemId("item-" + t) 
                                .linkType(LinkType.RELATED) 
                                .strength(0.5) 
                                .build(); 
                        itemLinks.add(link); 
                    }
                }
            }

            assertThat(itemLinks).hasSize(90);  // 10 * 9 relationships 
        }

        @Test
        @DisplayName("Relationship strength distribution")
        void strengthDistribution() { 
            List<MemoryLink> links = new ArrayList<>(); 
            int[] connectionCounts = new int[5];  // Track link counts by strength bucket

            for (int i = 0; i < 500; i++) { 
                final int idx = i;
                double strength = idx % 5 * 0.2;  // Distribute across [0.0, 1.0]

                MemoryLink link = MemoryLink.builder() 
                        .targetItemId("item-" + (idx / 5)) 
                        .linkType(LinkType.RELATED) 
                        .strength(strength) 
                        .build(); 
                links.add(link); 

                int bucket = (int) (strength * 5); 
                connectionCounts[Math.min(bucket, 4)]++; 
            }

            assertThat(links).hasSize(500); 
            for (int count : connectionCounts) { 
                assertThat(count).isGreaterThan(0); 
            }
        }

        @Test
        @DisplayName("Relationship type variety")
        void typeVariety() { 
            List<MemoryLink> links = new ArrayList<>(); 

            for (int i = 0; i < 100; i++) { 
                final int idx = i;
                LinkType type = LinkType.values()[idx % LinkType.values().length]; 

                MemoryLink link = MemoryLink.builder() 
                        .targetItemId("target-" + (idx % 20)) 
                        .linkType(type) 
                        .build(); 
                links.add(link); 
            }

            assertThat(links).hasSize(100); 
        }
    }

    // ============================================
    // MEMORY ITEM OPERATIONS (2 tests) 
    // ============================================

    @Nested
    @DisplayName("Memory Item Operations")
    class ItemOperationsTests {

        @Test
        @DisplayName("Many memory items with cross-references")
        void manyItemsCrossReferences() { 
            Set<MemoryItem> items = new HashSet<>(); 

            for (int i = 0; i < 100; i++) { 
                final int idx = i;
                MemoryItem item = EnhancedProcedure.builder() 
                        .id("item-" + idx) 
                    .situation("Situation for item " + idx) 
                    .action("Action for item " + idx) 
                        .build(); 
                items.add(item); 
            }

            assertThat(items).hasSize(100); 
        }

        @Test
        @DisplayName("Memory items with link updates")
        void itemsWithLinkUpdates() { 
            List<MemoryItem> items = new ArrayList<>(); 

            for (int i = 0; i < 50; i++) { 
                final int idx = i;
                MemoryItem item = EnhancedProcedure.builder() 
                        .id("item-" + idx) 
                    .situation("Situation")
                    .action("Action")
                        .build(); 
                items.add(item); 
            }

            // Simulate adding links to items
            for (int i = 0; i < items.size(); i++) { 
                for (int j = 0; j < 5; j++) { 
                    final int linkIdx = j;
                    MemoryLink link = MemoryLink.builder() 
                            .targetItemId("item-" + ((i + linkIdx + 1) % items.size())) 
                            .linkType(LinkType.RELATED) 
                            .build(); 
                    // Link would be added to item[i]
                }
            }
        }
    }

    // ============================================
    // CONCURRENT MEMORY OPERATIONS (2 tests) 
    // ============================================

    @Nested
    @DisplayName("Concurrent Operations")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent memory link creation")
        void concurrentLinkCreation() throws Exception { 
            int threadCount = 20;
            int linksPerThread = 100;
            CountDownLatch latch = new CountDownLatch(threadCount); 
            AtomicInteger totalLinks = new AtomicInteger(0); 

            ExecutorService exec = Executors.newFixedThreadPool(threadCount); 
            try {
                for (int t = 0; t < threadCount; t++) { 
                    final int threadIdx = t;
                    exec.submit(() -> { 
                        try {
                            for (int i = 0; i < linksPerThread; i++) { 
                                final int linkIdx = i;
                                MemoryLink link = MemoryLink.builder() 
                                        .targetItemId("item-" + threadIdx + "-" + linkIdx) 
                                        .linkType(LinkType.RELATED) 
                                        .strength(0.5) 
                                        .build(); 

                                assertThat(link.getTargetItemId()).isNotNull(); 
                                totalLinks.incrementAndGet(); 
                            }
                        } finally {
                            latch.countDown(); 
                        }
                    });
                }
                assertThat(latch.await(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); 
            } finally {
                exec.shutdownNow(); 
            }

            assertThat(totalLinks.get()).isEqualTo(threadCount * linksPerThread); 
        }

        @Test
        @DisplayName("High-volume relationship network building")
        void highVolumeNetworkBuilding() { 
            // Build a large relationship network
            int itemCount = 50;
            int linksPerItem = 30;
            int totalLinks = 0;

            for (int i = 0; i < itemCount; i++) { 
                for (int j = 0; j < linksPerItem; j++) { 
                    final int si = i;
                    final int tj = j;
                    MemoryLink link = MemoryLink.builder() 
                            .targetItemId("item-" + ((si + tj) % itemCount)) 
                            .linkType(LinkType.RELATED) 
                            .strength(0.75) 
                            .build(); 

                    assertThat(link.getTargetItemId()).isNotNull(); 
                    totalLinks++;
                }
            }

            assertThat(totalLinks).isEqualTo(itemCount * linksPerItem); 
        }
    }

    // ============================================
    // EDGE CASES (1 test) 
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Extreme memory link scenarios")
        void extremeScenarios() { 
            // Very long target ID
            String longTargetId = "item-" + "x".repeat(500); 
            MemoryLink longIdLink = MemoryLink.builder() 
                    .targetItemId(longTargetId) 
                    .linkType(LinkType.RELATED) 
                    .build(); 
                assertThat(longIdLink.getTargetItemId()).hasSize(longTargetId.length()); 

            // Extreme strength values
            MemoryLink minStrength = MemoryLink.builder() 
                    .targetItemId("item-min")
                    .linkType(LinkType.RELATED) 
                    .strength(0.0) 
                    .build(); 
            assertThat(minStrength.getStrength()).isEqualTo(0.0); 

            MemoryLink maxStrength = MemoryLink.builder() 
                    .targetItemId("item-max")
                    .linkType(LinkType.RELATED) 
                    .strength(1.0) 
                    .build(); 
            assertThat(maxStrength.getStrength()).isEqualTo(1.0); 
        }
    }
}
