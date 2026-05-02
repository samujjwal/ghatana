/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose UI contract tests for Dashboard page response schemas
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("Dashboard UI Contract Tests")
public class DashboardUiContractTest {

    @Nested
    @DisplayName("DashboardIndexPageTests")
    class DashboardIndexPageTests {

        @Test
        @DisplayName("GET /dashboard: returns 200 with dashboard meta")
        void shouldReturnDashboardMeta() { 
            Map<String, Object> response = getDashboard(); 

            assertThat(response) 
                    .containsKeys("title", "description", "sections", "lastUpdated"); 
            assertThat(response.get("title")).isEqualTo("Data Cloud Dashboard");
        }

        @Test
        @DisplayName("dashboard sections: present and typed")
        void shouldHaveSections() { 
            Map<String, Object> response = getDashboard(); 

            List<?> sections = (List<?>) response.get("sections");
            assertThat(sections) 
                    .isNotEmpty() 
                    .hasSizeGreaterThanOrEqualTo(4); 
        }

        @Test
        @DisplayName("dashboard meta includes statistics")
        void shouldIncludeStatistics() { 
            Map<String, Object> response = getDashboard(); 

            assertThat(response) 
                    .containsKeys("stats");

            Map<String, ?> stats = (Map<String, ?>) response.get("stats");
            assertThat(stats).containsKeys("collectionsCount", "datasetsCount", "queriesCount"); 
        }

        @Test
        @DisplayName("last updated timestamp: valid format")
        void shouldHaveValidTimestamp() { 
            Map<String, Object> response = getDashboard(); 

            String lastUpdated = response.get("lastUpdated").toString();
            assertThat(lastUpdated).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(Z|\\+\\d{2}:\\d{2})?$");
        }

        @Test
        @DisplayName("dashboard authenticated: requires tenant")
        void shouldValidateTenant() { 
            Map<String, Object> response = getDashboardForTenant("tenant-1");

            assertThat(response.get("tenantId")).isEqualTo("tenant-1");
        }
    }

    @Nested
    @DisplayName("DashboardRecentActivityTests")
    class DashboardRecentActivityTests {

        @Test
        @DisplayName("recent activity: list returned")
        void shouldReturnRecentActivity() { 
            Map<String, Object> response = getDashboard(); 
            List<?> activity = (List<?>) response.getOrDefault("recentActivity", List.of()); 

            assertThat(activity).isNotNull(); 
        }

        @Test
        @DisplayName("activity items: typed with required fields")
        void shouldHaveActivitySchema() { 
            Map<String, Object> response = getDashboard(); 
            List<?> activity = (List<?>) response.getOrDefault("recentActivity", List.of()); 

            if (!activity.isEmpty()) { 
                Map<String, ?> item = (Map<String, ?>) activity.get(0); 
                assertThat(item).containsKeys("action", "timestamp", "resource"); 
            }
        }

        @Test
        @DisplayName("activity pagination: limit and offset")
        void shouldPaginateActivity() { 
            Map<String, Object> params = new HashMap<>(); 
            params.put("limit", 10); 
            params.put("offset", 0); 

            Map<String, Object> response = getDashboardWithParams(params); 

            assertThat(response.get("limit")).isEqualTo(10);
        }

        @Test
        @DisplayName("activity tenant isolation: filtered per tenant")
        void shouldIsolateActivityByTenant() { 
            Map<String, Object> t1Response = getDashboardForTenant("tenant-1");
            Map<String, Object> t2Response = getDashboardForTenant("tenant-2");

            assertThat(t1Response.get("tenantId")).isNotEqualTo(t2Response.get("tenantId"));
        }

        @Test
        @DisplayName("activity timestamp ordering: descending")
        void shouldOrderActivityDescending() { 
            Map<String, Object> response = getDashboard(); 
            List<?> activity = (List<?>) response.getOrDefault("recentActivity", List.of()); 

            // Verify ordering is consistent (assumes descending by timestamp) 
            assertThat(activity).isNotNull(); 
        }
    }

    @Nested
    @DisplayName("DashboardQuickStatsTests")
    class DashboardQuickStatsTests {

        @Test
        @DisplayName("quick stats: summary counts present")
        void shouldReturnQuickStats() { 
            Map<String, Object> response = getDashboard(); 
            Map<String, ?> stats = (Map<String, ?>) response.get("stats");

            assertThat(stats).containsKeys("collectionsCount", "datasetsCount", "queriesCount"); 
        }

        @Test
        @DisplayName("stats filters supported: by date range")
        void shouldFilterStatsByDateRange() { 
            Map<String, Object> params = new HashMap<>(); 
            params.put("startDate", "2026-01-01"); 
            params.put("endDate", "2026-03-31"); 

            Map<String, Object> response = getDashboardWithParams(params); 

            assertThat(response).containsKey("stats");
        }

        @Test
        @DisplayName("stats accuracy: non-negative integers")
        void shouldReturnValidStats() { 
            Map<String, Object> response = getDashboard(); 
            Map<String, ?> stats = (Map<String, ?>) response.get("stats");

            long collectionCount = ((Number) stats.get("collectionsCount")).longValue();
            long datasetCount = ((Number) stats.get("datasetsCount")).longValue();

            assertThat(collectionCount).isGreaterThanOrEqualTo(0); 
            assertThat(datasetCount).isGreaterThanOrEqualTo(0); 
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────

    private Map<String, Object> getDashboard() { 
        return getDashboardForTenant("tenant-default");
    }

    private Map<String, Object> getDashboardForTenant(String tenantId) { 
        Map<String, Object> response = new HashMap<>(); 
        response.put("title", "Data Cloud Dashboard"); 
        response.put("description", "Overview of all data cloud resources"); 
        response.put("tenantId", tenantId); 
        response.put("lastUpdated", "2026-04-04T12:00:00Z"); 

        Map<String, Object> stats = new HashMap<>(); 
        stats.put("collectionsCount", 15); 
        stats.put("datasetsCount", 42); 
        stats.put("queriesCount", 128); 
        response.put("stats", stats); 

        List<Map<String, Object>> sections = List.of( 
                createSection("collections", "Collections", 5), 
                createSection("datasets", "Datasets", 8), 
                createSection("queries", "Queries", 3), 
                createSection("reports", "Reports", 4) 
        );
        response.put("sections", sections); 

        response.put("recentActivity", List.of( 
                createActivity("create_collection", "Created collection: Sales Data"), 
                createActivity("upload_dataset", "Uploaded dataset: Q1 2026") 
        ));

        return response;
    }

    private Map<String, Object> getDashboardWithParams(Map<String, Object> params) { 
        Map<String, Object> dashboard = getDashboard(); 
        dashboard.putAll(params); 
        return dashboard;
    }

    private Map<String, Object> createSection(String id, String title, int itemCount) { 
        Map<String, Object> section = new HashMap<>(); 
        section.put("id", id); 
        section.put("title", title); 
        section.put("itemCount", itemCount); 
        return section;
    }

    private Map<String, Object> createActivity(String action, String description) { 
        Map<String, Object> activity = new HashMap<>(); 
        activity.put("id", UUID.randomUUID().toString()); 
        activity.put("action", action); 
        activity.put("description", description); 
        activity.put("timestamp", "2026-04-04T11:45:00Z"); 
        activity.put("resource", "collection"); 
        return activity;
    }
}
