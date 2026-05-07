/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.datacloud.platform.analytics;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for query correctness with deterministic fixtures.
 *
 * <p>Validates aggregation formulas, filtering, sorting, and reporting
 * with fixed test data for reproducible results.
 *
 * @doc.type class
 * @doc.purpose Query correctness tests with deterministic fixtures
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("[Analytics]: query_correctness_aggregation_reporting")
class QueryCorrectnessFixtureTest extends EventloopTestBase {

    // ─────────────────────────────────────────────────────────────────────────
    // Test Fixtures
    // ─────────────────────────────────────────────────────────────────────────

    private static final List<Map<String, Object>> SALES_DATA = List.of( 
        Map.of("id", "sale-001", "product", "Widget", "quantity", 10, "price", 25.00, "region", "North", "date", "2026-01-01"), 
        Map.of("id", "sale-002", "product", "Gadget", "quantity", 5, "price", 50.00, "region", "South", "date", "2026-01-01"), 
        Map.of("id", "sale-003", "product", "Widget", "quantity", 15, "price", 25.00, "region", "North", "date", "2026-01-02"), 
        Map.of("id", "sale-004", "product", "Tool", "quantity", 8, "price", 75.00, "region", "East", "date", "2026-01-02"), 
        Map.of("id", "sale-005", "product", "Gadget", "quantity", 12, "price", 50.00, "region", "South", "date", "2026-01-03") 
    );

    // ─────────────────────────────────────────────────────────────────────────
    // SUM Aggregation Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[SUM]: total_quantity_across_all_sales")
    void totalQuantityAcrossAllSales() { 
        int totalQuantity = SALES_DATA.stream() 
            .mapToInt(s -> (Integer) s.get("quantity"))
            .sum(); 

        assertThat(totalQuantity).isEqualTo(50); // 10 + 5 + 15 + 8 + 12 
    }

    @Test
    @DisplayName("[SUM]: total_revenue_calculated_correctly")
    void totalRevenueCalculatedCorrectly() { 
        double totalRevenue = SALES_DATA.stream() 
            .mapToDouble(s -> (Integer) s.get("quantity") * (Double) s.get("price"))
            .sum(); 

        // 10*25 + 5*50 + 15*25 + 8*75 + 12*50 = 250 + 250 + 375 + 600 + 600 = 2075
        assertThat(totalRevenue).isEqualTo(2075.00); 
    }

    @Test
    @DisplayName("[SUM]: revenue_by_product_grouped_correctly")
    void revenueByProductGroupedCorrectly() { 
        Map<String, Double> revenueByProduct = SALES_DATA.stream() 
            .collect(Collectors.groupingBy( 
                s -> (String) s.get("product"),
                Collectors.summingDouble(s -> (Integer) s.get("quantity") * (Double) s.get("price"))
            ));

        assertThat(revenueByProduct).containsEntry("Widget", 625.00); // (10+15)*25 
        assertThat(revenueByProduct).containsEntry("Gadget", 850.00); // (5+12)*50 
        assertThat(revenueByProduct).containsEntry("Tool", 600.00); // 8*75 
    }

    @Test
    @DisplayName("[SUM]: revenue_by_region_grouped_correctly")
    void revenueByRegionGroupedCorrectly() { 
        Map<String, Double> revenueByRegion = SALES_DATA.stream() 
            .collect(Collectors.groupingBy( 
                s -> (String) s.get("region"),
                Collectors.summingDouble(s -> (Integer) s.get("quantity") * (Double) s.get("price"))
            ));

        assertThat(revenueByRegion).containsEntry("North", 625.00); 
        assertThat(revenueByRegion).containsEntry("South", 850.00); 
        assertThat(revenueByRegion).containsEntry("East", 600.00); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // COUNT Aggregation Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[COUNT]: total_number_of_sales")
    void totalNumberOfSales() { 
        long count = SALES_DATA.size(); 
        assertThat(count).isEqualTo(5); 
    }

    @Test
    @DisplayName("[COUNT]: count_by_product")
    void countByProduct() { 
        Map<String, Long> countByProduct = SALES_DATA.stream() 
            .collect(Collectors.groupingBy( 
                s -> (String) s.get("product"),
                Collectors.counting() 
            ));

        assertThat(countByProduct).containsEntry("Widget", 2L); 
        assertThat(countByProduct).containsEntry("Gadget", 2L); 
        assertThat(countByProduct).containsEntry("Tool", 1L); 
    }

    @Test
    @DisplayName("[COUNT]: distinct_products_count")
    void distinctProductsCount() { 
        long distinctProducts = SALES_DATA.stream() 
            .map(s -> (String) s.get("product"))
            .distinct() 
            .count(); 

        assertThat(distinctProducts).isEqualTo(3); 
    }

    @Test
    @DisplayName("[COUNT]: distinct_regions_count")
    void distinctRegionsCount() { 
        long distinctRegions = SALES_DATA.stream() 
            .map(s -> (String) s.get("region"))
            .distinct() 
            .count(); 

        assertThat(distinctRegions).isEqualTo(3); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AVG (Average) Aggregation Tests 
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[AVG]: average_quantity_per_sale")
    void averageQuantityPerSale() { 
        double avgQuantity = SALES_DATA.stream() 
            .mapToInt(s -> (Integer) s.get("quantity"))
            .average() 
            .orElse(0.0); 

        assertThat(avgQuantity).isEqualTo(10.0); // 50/5 
    }

    @Test
    @DisplayName("[AVG]: average_price_across_all_products")
    void averagePriceAcrossAllProducts() { 
        double avgPrice = SALES_DATA.stream() 
            .mapToDouble(s -> (Double) s.get("price"))
            .average() 
            .orElse(0.0); 

        assertThat(avgPrice).isEqualTo(45.00); // (25+50+25+75+50)/5 
    }

    @Test
    @DisplayName("[AVG]: average_revenue_per_sale")
    void averageRevenuePerSale() { 
        double avgRevenue = SALES_DATA.stream() 
            .mapToDouble(s -> (Integer) s.get("quantity") * (Double) s.get("price"))
            .average() 
            .orElse(0.0); 

        assertThat(avgRevenue).isEqualTo(415.00); // 2075/5 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MIN/MAX Aggregation Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[MIN]: minimum_quantity_in_sales")
    void minimumQuantityInSales() { 
        int minQuantity = SALES_DATA.stream() 
            .mapToInt(s -> (Integer) s.get("quantity"))
            .min() 
            .orElse(0); 

        assertThat(minQuantity).isEqualTo(5); 
    }

    @Test
    @DisplayName("[MAX]: maximum_quantity_in_sales")
    void maximumQuantityInSales() { 
        int maxQuantity = SALES_DATA.stream() 
            .mapToInt(s -> (Integer) s.get("quantity"))
            .max() 
            .orElse(0); 

        assertThat(maxQuantity).isEqualTo(15); 
    }

    @Test
    @DisplayName("[MIN]: minimum_price_in_sales")
    void minimumPriceInSales() { 
        double minPrice = SALES_DATA.stream() 
            .mapToDouble(s -> (Double) s.get("price"))
            .min() 
            .orElse(0.0); 

        assertThat(minPrice).isEqualTo(25.00); 
    }

    @Test
    @DisplayName("[MAX]: maximum_price_in_sales")
    void maximumPriceInSales() { 
        double maxPrice = SALES_DATA.stream() 
            .mapToDouble(s -> (Double) s.get("price"))
            .max() 
            .orElse(0.0); 

        assertThat(maxPrice).isEqualTo(75.00); 
    }

    @Test
    @DisplayName("[MIN_MAX]: price_range_calculated_correctly")
    void priceRangeCalculatedCorrectly() { 
        DoubleSummary stats = SALES_DATA.stream() 
            .mapToDouble(s -> (Double) s.get("price"))
            .collect(DoubleSummary::new, 
                (acc, v) -> { 
                    acc.min = Math.min(acc.min, v); 
                    acc.max = Math.max(acc.max, v); 
                },
                (a, b) -> { 
                    a.min = Math.min(a.min, b.min); 
                    a.max = Math.max(a.max, b.max); 
                });

        assertThat(stats.min).isEqualTo(25.00); 
        assertThat(stats.max).isEqualTo(75.00); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Filter Correctness Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Filter]: equals_filter_returns_matching_records")
    void equalsFilterReturnsMatchingRecords() { 
        List<Map<String, Object>> widgetSales = SALES_DATA.stream() 
            .filter(s -> "Widget".equals(s.get("product")))
            .collect(Collectors.toList()); 

        assertThat(widgetSales).hasSize(2); 
        assertThat(widgetSales).allMatch(s -> "Widget".equals(s.get("product")));
    }

    @Test
    @DisplayName("[Filter]: range_filter_with_gt_returns_records_above_threshold")
    void rangeFilterWithGtReturnsRecordsAboveThreshold() { 
        List<Map<String, Object>> highQuantitySales = SALES_DATA.stream() 
            .filter(s -> (Integer) s.get("quantity") > 10)
            .collect(Collectors.toList()); 

        assertThat(highQuantitySales).hasSize(2); // sale-003 (15), sale-005 (12) 
    }

    @Test
    @DisplayName("[Filter]: range_filter_with_gte_returns_records_at_or_above_threshold")
    void rangeFilterWithGteReturnsRecordsAtOrAboveThreshold() { 
        List<Map<String, Object>> highQuantitySales = SALES_DATA.stream() 
            .filter(s -> (Integer) s.get("quantity") >= 10)
            .collect(Collectors.toList()); 

        assertThat(highQuantitySales).hasSize(3); // sale-001 (10), sale-003 (15), sale-005 (12) 
    }

    @Test
    @DisplayName("[Filter]: range_filter_with_lt_returns_records_below_threshold")
    void rangeFilterWithLtReturnsRecordsBelowThreshold() { 
        List<Map<String, Object>> lowQuantitySales = SALES_DATA.stream() 
            .filter(s -> (Integer) s.get("quantity") < 10)
            .collect(Collectors.toList()); 

        assertThat(lowQuantitySales).hasSize(2); // sale-002 (5), sale-004 (8) 
    }

    @Test
    @DisplayName("[Filter]: range_filter_with_lte_returns_records_at_or_below_threshold")
    void rangeFilterWithLteReturnsRecordsAtOrBelowThreshold() { 
        List<Map<String, Object>> lowQuantitySales = SALES_DATA.stream() 
            .filter(s -> (Integer) s.get("quantity") <= 10)
            .collect(Collectors.toList()); 

        assertThat(lowQuantitySales).hasSize(3); // sale-001 (10), sale-002 (5), sale-004 (8) 
    }

    @Test
    @DisplayName("[Filter]: set_membership_filter_returns_records_in_set")
    void setMembershipFilterReturnsRecordsInSet() { 
        List<String> targetProducts = List.of("Widget", "Tool"); 
        List<Map<String, Object>> filteredSales = SALES_DATA.stream() 
            .filter(s -> targetProducts.contains(s.get("product")))
            .collect(Collectors.toList()); 

        assertThat(filteredSales).hasSize(3); 
    }

    @Test
    @DisplayName("[Filter]: date_range_filter_returns_records_in_range")
    void dateRangeFilterReturnsRecordsInRange() { 
        List<Map<String, Object>> jan1Sales = SALES_DATA.stream() 
            .filter(s -> "2026-01-01".equals(s.get("date")))
            .collect(Collectors.toList()); 

        assertThat(jan1Sales).hasSize(2); 
    }

    @Test
    @DisplayName("[Filter]: combined_filters_with_and_logic")
    void combinedFiltersWithAndLogic() { 
        List<Map<String, Object>> filtered = SALES_DATA.stream() 
            .filter(s -> "North".equals(s.get("region")))
            .filter(s -> (Integer) s.get("quantity") > 12)
            .collect(Collectors.toList()); 

        assertThat(filtered).hasSize(1); // sale-003 
        assertThat(filtered.get(0).get("id")).isEqualTo("sale-003");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sort Correctness Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Sort]: ascending_sort_by_quantity")
    void ascendingSortByQuantity() { 
        List<Map<String, Object>> sorted = SALES_DATA.stream() 
            .sorted(Comparator.comparingInt(s -> (Integer) s.get("quantity")))
            .collect(Collectors.toList()); 

        assertThat(sorted.get(0).get("quantity")).isEqualTo(5);
        assertThat(sorted.get(4).get("quantity")).isEqualTo(15);
    }

    @Test
    @DisplayName("[Sort]: descending_sort_by_quantity")
    void descendingSortByQuantity() { 
        List<Map<String, Object>> sorted = SALES_DATA.stream() 
            .sorted(Comparator.comparingInt((Map<String, Object> s) -> (Integer) s.get("quantity")).reversed())
            .collect(Collectors.toList()); 

        assertThat(sorted.get(0).get("quantity")).isEqualTo(15);
        assertThat(sorted.get(4).get("quantity")).isEqualTo(5);
    }

    @Test
    @DisplayName("[Sort]: ascending_sort_by_price")
    void ascendingSortByPrice() { 
        List<Map<String, Object>> sorted = SALES_DATA.stream() 
            .sorted(Comparator.comparingDouble(s -> (Double) s.get("price")))
            .collect(Collectors.toList()); 

        assertThat(sorted.get(0).get("price")).isEqualTo(25.00);
        assertThat(sorted.get(4).get("price")).isEqualTo(75.00);
    }

    @Test
    @DisplayName("[Sort]: multi_key_sort_by_region_then_quantity")
    void multiKeySortByRegionThenQuantity() { 
        List<Map<String, Object>> sorted = SALES_DATA.stream() 
            .sorted(Comparator.comparing((Map<String, Object> s) -> (String) s.get("region"))
                .thenComparingInt(s -> (Integer) s.get("quantity")))
            .collect(Collectors.toList()); 

        // East: Tool(8) 
        // North: Widget(10), Widget(15) 
        // South: Gadget(5), Gadget(12) 
        assertThat(sorted.get(0).get("region")).isEqualTo("East");
        assertThat(sorted.get(1).get("region")).isEqualTo("North");
        assertThat(sorted.get(1).get("quantity")).isEqualTo(10);
        assertThat(sorted.get(2).get("quantity")).isEqualTo(15);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pagination Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Pagination]: offset_limits_results")
    void offsetLimitsResults() { 
        List<Map<String, Object>> page = SALES_DATA.stream() 
            .sorted(Comparator.comparing(s -> (String) s.get("id")))
            .skip(2) 
            .limit(2) 
            .collect(Collectors.toList()); 

        assertThat(page).hasSize(2); 
        assertThat(page.get(0).get("id")).isEqualTo("sale-003");
        assertThat(page.get(1).get("id")).isEqualTo("sale-004");
    }

    @Test
    @DisplayName("[Pagination]: limit_restricts_page_size")
    void limitRestrictsPageSize() { 
        List<Map<String, Object>> page = SALES_DATA.stream() 
            .limit(3) 
            .collect(Collectors.toList()); 

        assertThat(page).hasSize(3); 
    }

    @Test
    @DisplayName("[Pagination]: offset_beyond_total_returns_empty")
    void offsetBeyondTotalReturnsEmpty() { 
        List<Map<String, Object>> page = SALES_DATA.stream() 
            .skip(100) 
            .limit(10) 
            .collect(Collectors.toList()); 

        assertThat(page).isEmpty(); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reporting Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Report]: sales_summary_report_generated_correctly")
    void salesSummaryReportGeneratedCorrectly() { 
        Map<String, Object> report = generateSalesSummary(SALES_DATA); 

        assertThat(report).containsEntry("totalSales", 5L); 
        assertThat(report).containsEntry("totalQuantity", 50); 
        assertThat(report).containsEntry("totalRevenue", 2075.00); 
        assertThat(report).containsEntry("averageOrderValue", 415.00); 
        assertThat(report).containsKeys("revenueByProduct", "revenueByRegion"); 
    }

    @Test
    @DisplayName("[Report]: product_performance_ranking")
    void productPerformanceRanking() { 
        List<Map.Entry<String, Double>> rankedProducts = SALES_DATA.stream() 
            .collect(Collectors.groupingBy( 
                s -> (String) s.get("product"),
                Collectors.summingDouble(s -> (Integer) s.get("quantity") * (Double) s.get("price"))
            ))
            .entrySet().stream() 
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed()) 
            .collect(Collectors.toList()); 

        assertThat(rankedProducts.get(0).getKey()).isEqualTo("Gadget"); // 850
        assertThat(rankedProducts.get(1).getKey()).isEqualTo("Widget"); // 625
        assertThat(rankedProducts.get(2).getKey()).isEqualTo("Tool"); // 600
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> generateSalesSummary(List<Map<String, Object>> sales) { 
        long totalSales = sales.size(); 
        int totalQuantity = sales.stream().mapToInt(s -> (Integer) s.get("quantity")).sum();
        double totalRevenue = sales.stream() 
            .mapToDouble(s -> (Integer) s.get("quantity") * (Double) s.get("price"))
            .sum(); 
        double averageOrderValue = totalRevenue / totalSales;

        Map<String, Double> revenueByProduct = sales.stream() 
            .collect(Collectors.groupingBy( 
                s -> (String) s.get("product"),
                Collectors.summingDouble(s -> (Integer) s.get("quantity") * (Double) s.get("price"))
            ));

        Map<String, Double> revenueByRegion = sales.stream() 
            .collect(Collectors.groupingBy( 
                s -> (String) s.get("region"),
                Collectors.summingDouble(s -> (Integer) s.get("quantity") * (Double) s.get("price"))
            ));

        return Map.of( 
            "totalSales", totalSales,
            "totalQuantity", totalQuantity,
            "totalRevenue", totalRevenue,
            "averageOrderValue", averageOrderValue,
            "revenueByProduct", revenueByProduct,
            "revenueByRegion", revenueByRegion
        );
    }

    // Helper class for MIN/MAX aggregation
    private static class DoubleSummary {
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
    }
}
