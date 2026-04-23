/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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

    private static final List<Map<String, Object>> SALES_DATA = List.of( // GH-90000
        Map.of("id", "sale-001", "product", "Widget", "quantity", 10, "price", 25.00, "region", "North", "date", "2026-01-01"), // GH-90000
        Map.of("id", "sale-002", "product", "Gadget", "quantity", 5, "price", 50.00, "region", "South", "date", "2026-01-01"), // GH-90000
        Map.of("id", "sale-003", "product", "Widget", "quantity", 15, "price", 25.00, "region", "North", "date", "2026-01-02"), // GH-90000
        Map.of("id", "sale-004", "product", "Tool", "quantity", 8, "price", 75.00, "region", "East", "date", "2026-01-02"), // GH-90000
        Map.of("id", "sale-005", "product", "Gadget", "quantity", 12, "price", 50.00, "region", "South", "date", "2026-01-03") // GH-90000
    );

    // ─────────────────────────────────────────────────────────────────────────
    // SUM Aggregation Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[SUM]: total_quantity_across_all_sales")
    void totalQuantityAcrossAllSales() { // GH-90000
        int totalQuantity = SALES_DATA.stream() // GH-90000
            .mapToInt(s -> (Integer) s.get("quantity"))
            .sum(); // GH-90000

        assertThat(totalQuantity).isEqualTo(50); // 10 + 5 + 15 + 8 + 12 // GH-90000
    }

    @Test
    @DisplayName("[SUM]: total_revenue_calculated_correctly")
    void totalRevenueCalculatedCorrectly() { // GH-90000
        double totalRevenue = SALES_DATA.stream() // GH-90000
            .mapToDouble(s -> (Integer) s.get("quantity") * (Double) s.get("price"))
            .sum(); // GH-90000

        // 10*25 + 5*50 + 15*25 + 8*75 + 12*50 = 250 + 250 + 375 + 600 + 600 = 2075
        assertThat(totalRevenue).isEqualTo(2075.00); // GH-90000
    }

    @Test
    @DisplayName("[SUM]: revenue_by_product_grouped_correctly")
    void revenueByProductGroupedCorrectly() { // GH-90000
        Map<String, Double> revenueByProduct = SALES_DATA.stream() // GH-90000
            .collect(Collectors.groupingBy( // GH-90000
                s -> (String) s.get("product"),
                Collectors.summingDouble(s -> (Integer) s.get("quantity") * (Double) s.get("price"))
            ));

        assertThat(revenueByProduct).containsEntry("Widget", 625.00); // (10+15)*25 // GH-90000
        assertThat(revenueByProduct).containsEntry("Gadget", 850.00); // (5+12)*50 // GH-90000
        assertThat(revenueByProduct).containsEntry("Tool", 600.00); // 8*75 // GH-90000
    }

    @Test
    @DisplayName("[SUM]: revenue_by_region_grouped_correctly")
    void revenueByRegionGroupedCorrectly() { // GH-90000
        Map<String, Double> revenueByRegion = SALES_DATA.stream() // GH-90000
            .collect(Collectors.groupingBy( // GH-90000
                s -> (String) s.get("region"),
                Collectors.summingDouble(s -> (Integer) s.get("quantity") * (Double) s.get("price"))
            ));

        assertThat(revenueByRegion).containsEntry("North", 625.00); // GH-90000
        assertThat(revenueByRegion).containsEntry("South", 850.00); // GH-90000
        assertThat(revenueByRegion).containsEntry("East", 600.00); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // COUNT Aggregation Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[COUNT]: total_number_of_sales")
    void totalNumberOfSales() { // GH-90000
        long count = SALES_DATA.size(); // GH-90000
        assertThat(count).isEqualTo(5); // GH-90000
    }

    @Test
    @DisplayName("[COUNT]: count_by_product")
    void countByProduct() { // GH-90000
        Map<String, Long> countByProduct = SALES_DATA.stream() // GH-90000
            .collect(Collectors.groupingBy( // GH-90000
                s -> (String) s.get("product"),
                Collectors.counting() // GH-90000
            ));

        assertThat(countByProduct).containsEntry("Widget", 2L); // GH-90000
        assertThat(countByProduct).containsEntry("Gadget", 2L); // GH-90000
        assertThat(countByProduct).containsEntry("Tool", 1L); // GH-90000
    }

    @Test
    @DisplayName("[COUNT]: distinct_products_count")
    void distinctProductsCount() { // GH-90000
        long distinctProducts = SALES_DATA.stream() // GH-90000
            .map(s -> (String) s.get("product"))
            .distinct() // GH-90000
            .count(); // GH-90000

        assertThat(distinctProducts).isEqualTo(3); // GH-90000
    }

    @Test
    @DisplayName("[COUNT]: distinct_regions_count")
    void distinctRegionsCount() { // GH-90000
        long distinctRegions = SALES_DATA.stream() // GH-90000
            .map(s -> (String) s.get("region"))
            .distinct() // GH-90000
            .count(); // GH-90000

        assertThat(distinctRegions).isEqualTo(3); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AVG (Average) Aggregation Tests // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[AVG]: average_quantity_per_sale")
    void averageQuantityPerSale() { // GH-90000
        double avgQuantity = SALES_DATA.stream() // GH-90000
            .mapToInt(s -> (Integer) s.get("quantity"))
            .average() // GH-90000
            .orElse(0.0); // GH-90000

        assertThat(avgQuantity).isEqualTo(10.0); // 50/5 // GH-90000
    }

    @Test
    @DisplayName("[AVG]: average_price_across_all_products")
    void averagePriceAcrossAllProducts() { // GH-90000
        double avgPrice = SALES_DATA.stream() // GH-90000
            .mapToDouble(s -> (Double) s.get("price"))
            .average() // GH-90000
            .orElse(0.0); // GH-90000

        assertThat(avgPrice).isEqualTo(45.00); // (25+50+25+75+50)/5 // GH-90000
    }

    @Test
    @DisplayName("[AVG]: average_revenue_per_sale")
    void averageRevenuePerSale() { // GH-90000
        double avgRevenue = SALES_DATA.stream() // GH-90000
            .mapToDouble(s -> (Integer) s.get("quantity") * (Double) s.get("price"))
            .average() // GH-90000
            .orElse(0.0); // GH-90000

        assertThat(avgRevenue).isEqualTo(415.00); // 2075/5 // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MIN/MAX Aggregation Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[MIN]: minimum_quantity_in_sales")
    void minimumQuantityInSales() { // GH-90000
        int minQuantity = SALES_DATA.stream() // GH-90000
            .mapToInt(s -> (Integer) s.get("quantity"))
            .min() // GH-90000
            .orElse(0); // GH-90000

        assertThat(minQuantity).isEqualTo(5); // GH-90000
    }

    @Test
    @DisplayName("[MAX]: maximum_quantity_in_sales")
    void maximumQuantityInSales() { // GH-90000
        int maxQuantity = SALES_DATA.stream() // GH-90000
            .mapToInt(s -> (Integer) s.get("quantity"))
            .max() // GH-90000
            .orElse(0); // GH-90000

        assertThat(maxQuantity).isEqualTo(15); // GH-90000
    }

    @Test
    @DisplayName("[MIN]: minimum_price_in_sales")
    void minimumPriceInSales() { // GH-90000
        double minPrice = SALES_DATA.stream() // GH-90000
            .mapToDouble(s -> (Double) s.get("price"))
            .min() // GH-90000
            .orElse(0.0); // GH-90000

        assertThat(minPrice).isEqualTo(25.00); // GH-90000
    }

    @Test
    @DisplayName("[MAX]: maximum_price_in_sales")
    void maximumPriceInSales() { // GH-90000
        double maxPrice = SALES_DATA.stream() // GH-90000
            .mapToDouble(s -> (Double) s.get("price"))
            .max() // GH-90000
            .orElse(0.0); // GH-90000

        assertThat(maxPrice).isEqualTo(75.00); // GH-90000
    }

    @Test
    @DisplayName("[MIN_MAX]: price_range_calculated_correctly")
    void priceRangeCalculatedCorrectly() { // GH-90000
        DoubleSummary stats = SALES_DATA.stream() // GH-90000
            .mapToDouble(s -> (Double) s.get("price"))
            .collect(DoubleSummary::new, // GH-90000
                (acc, v) -> { // GH-90000
                    acc.min = Math.min(acc.min, v); // GH-90000
                    acc.max = Math.max(acc.max, v); // GH-90000
                },
                (a, b) -> { // GH-90000
                    a.min = Math.min(a.min, b.min); // GH-90000
                    a.max = Math.max(a.max, b.max); // GH-90000
                });

        assertThat(stats.min).isEqualTo(25.00); // GH-90000
        assertThat(stats.max).isEqualTo(75.00); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Filter Correctness Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Filter]: equals_filter_returns_matching_records")
    void equalsFilterReturnsMatchingRecords() { // GH-90000
        List<Map<String, Object>> widgetSales = SALES_DATA.stream() // GH-90000
            .filter(s -> "Widget".equals(s.get("product")))
            .collect(Collectors.toList()); // GH-90000

        assertThat(widgetSales).hasSize(2); // GH-90000
        assertThat(widgetSales).allMatch(s -> "Widget".equals(s.get("product")));
    }

    @Test
    @DisplayName("[Filter]: range_filter_with_gt_returns_records_above_threshold")
    void rangeFilterWithGtReturnsRecordsAboveThreshold() { // GH-90000
        List<Map<String, Object>> highQuantitySales = SALES_DATA.stream() // GH-90000
            .filter(s -> (Integer) s.get("quantity") > 10)
            .collect(Collectors.toList()); // GH-90000

        assertThat(highQuantitySales).hasSize(2); // sale-003 (15), sale-005 (12) // GH-90000
    }

    @Test
    @DisplayName("[Filter]: range_filter_with_gte_returns_records_at_or_above_threshold")
    void rangeFilterWithGteReturnsRecordsAtOrAboveThreshold() { // GH-90000
        List<Map<String, Object>> highQuantitySales = SALES_DATA.stream() // GH-90000
            .filter(s -> (Integer) s.get("quantity") >= 10)
            .collect(Collectors.toList()); // GH-90000

        assertThat(highQuantitySales).hasSize(3); // sale-001 (10), sale-003 (15), sale-005 (12) // GH-90000
    }

    @Test
    @DisplayName("[Filter]: range_filter_with_lt_returns_records_below_threshold")
    void rangeFilterWithLtReturnsRecordsBelowThreshold() { // GH-90000
        List<Map<String, Object>> lowQuantitySales = SALES_DATA.stream() // GH-90000
            .filter(s -> (Integer) s.get("quantity") < 10)
            .collect(Collectors.toList()); // GH-90000

        assertThat(lowQuantitySales).hasSize(2); // sale-002 (5), sale-004 (8) // GH-90000
    }

    @Test
    @DisplayName("[Filter]: range_filter_with_lte_returns_records_at_or_below_threshold")
    void rangeFilterWithLteReturnsRecordsAtOrBelowThreshold() { // GH-90000
        List<Map<String, Object>> lowQuantitySales = SALES_DATA.stream() // GH-90000
            .filter(s -> (Integer) s.get("quantity") <= 10)
            .collect(Collectors.toList()); // GH-90000

        assertThat(lowQuantitySales).hasSize(3); // sale-001 (10), sale-002 (5), sale-004 (8) // GH-90000
    }

    @Test
    @DisplayName("[Filter]: set_membership_filter_returns_records_in_set")
    void setMembershipFilterReturnsRecordsInSet() { // GH-90000
        List<String> targetProducts = List.of("Widget", "Tool"); // GH-90000
        List<Map<String, Object>> filteredSales = SALES_DATA.stream() // GH-90000
            .filter(s -> targetProducts.contains(s.get("product")))
            .collect(Collectors.toList()); // GH-90000

        assertThat(filteredSales).hasSize(3); // GH-90000
    }

    @Test
    @DisplayName("[Filter]: date_range_filter_returns_records_in_range")
    void dateRangeFilterReturnsRecordsInRange() { // GH-90000
        List<Map<String, Object>> jan1Sales = SALES_DATA.stream() // GH-90000
            .filter(s -> "2026-01-01".equals(s.get("date")))
            .collect(Collectors.toList()); // GH-90000

        assertThat(jan1Sales).hasSize(2); // GH-90000
    }

    @Test
    @DisplayName("[Filter]: combined_filters_with_and_logic")
    void combinedFiltersWithAndLogic() { // GH-90000
        List<Map<String, Object>> filtered = SALES_DATA.stream() // GH-90000
            .filter(s -> "North".equals(s.get("region")))
            .filter(s -> (Integer) s.get("quantity") > 12)
            .collect(Collectors.toList()); // GH-90000

        assertThat(filtered).hasSize(1); // sale-003 // GH-90000
        assertThat(filtered.get(0).get("id")).isEqualTo("sale-003");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sort Correctness Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Sort]: ascending_sort_by_quantity")
    void ascendingSortByQuantity() { // GH-90000
        List<Map<String, Object>> sorted = SALES_DATA.stream() // GH-90000
            .sorted(Comparator.comparingInt(s -> (Integer) s.get("quantity")))
            .collect(Collectors.toList()); // GH-90000

        assertThat(sorted.get(0).get("quantity")).isEqualTo(5);
        assertThat(sorted.get(4).get("quantity")).isEqualTo(15);
    }

    @Test
    @DisplayName("[Sort]: descending_sort_by_quantity")
    void descendingSortByQuantity() { // GH-90000
        List<Map<String, Object>> sorted = SALES_DATA.stream() // GH-90000
            .sorted(Comparator.comparingInt((Map<String, Object> s) -> (Integer) s.get("quantity")).reversed())
            .collect(Collectors.toList()); // GH-90000

        assertThat(sorted.get(0).get("quantity")).isEqualTo(15);
        assertThat(sorted.get(4).get("quantity")).isEqualTo(5);
    }

    @Test
    @DisplayName("[Sort]: ascending_sort_by_price")
    void ascendingSortByPrice() { // GH-90000
        List<Map<String, Object>> sorted = SALES_DATA.stream() // GH-90000
            .sorted(Comparator.comparingDouble(s -> (Double) s.get("price")))
            .collect(Collectors.toList()); // GH-90000

        assertThat(sorted.get(0).get("price")).isEqualTo(25.00);
        assertThat(sorted.get(4).get("price")).isEqualTo(75.00);
    }

    @Test
    @DisplayName("[Sort]: multi_key_sort_by_region_then_quantity")
    void multiKeySortByRegionThenQuantity() { // GH-90000
        List<Map<String, Object>> sorted = SALES_DATA.stream() // GH-90000
            .sorted(Comparator.comparing((Map<String, Object> s) -> (String) s.get("region"))
                .thenComparingInt(s -> (Integer) s.get("quantity")))
            .collect(Collectors.toList()); // GH-90000

        // East: Tool(8) // GH-90000
        // North: Widget(10), Widget(15) // GH-90000
        // South: Gadget(5), Gadget(12) // GH-90000
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
    void offsetLimitsResults() { // GH-90000
        List<Map<String, Object>> page = SALES_DATA.stream() // GH-90000
            .sorted(Comparator.comparing(s -> (String) s.get("id")))
            .skip(2) // GH-90000
            .limit(2) // GH-90000
            .collect(Collectors.toList()); // GH-90000

        assertThat(page).hasSize(2); // GH-90000
        assertThat(page.get(0).get("id")).isEqualTo("sale-003");
        assertThat(page.get(1).get("id")).isEqualTo("sale-004");
    }

    @Test
    @DisplayName("[Pagination]: limit_restricts_page_size")
    void limitRestrictsPageSize() { // GH-90000
        List<Map<String, Object>> page = SALES_DATA.stream() // GH-90000
            .limit(3) // GH-90000
            .collect(Collectors.toList()); // GH-90000

        assertThat(page).hasSize(3); // GH-90000
    }

    @Test
    @DisplayName("[Pagination]: offset_beyond_total_returns_empty")
    void offsetBeyondTotalReturnsEmpty() { // GH-90000
        List<Map<String, Object>> page = SALES_DATA.stream() // GH-90000
            .skip(100) // GH-90000
            .limit(10) // GH-90000
            .collect(Collectors.toList()); // GH-90000

        assertThat(page).isEmpty(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reporting Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Report]: sales_summary_report_generated_correctly")
    void salesSummaryReportGeneratedCorrectly() { // GH-90000
        Map<String, Object> report = generateSalesSummary(SALES_DATA); // GH-90000

        assertThat(report).containsEntry("totalSales", 5L); // GH-90000
        assertThat(report).containsEntry("totalQuantity", 50); // GH-90000
        assertThat(report).containsEntry("totalRevenue", 2075.00); // GH-90000
        assertThat(report).containsEntry("averageOrderValue", 415.00); // GH-90000
        assertThat(report).containsKeys("revenueByProduct", "revenueByRegion"); // GH-90000
    }

    @Test
    @DisplayName("[Report]: product_performance_ranking")
    void productPerformanceRanking() { // GH-90000
        List<Map.Entry<String, Double>> rankedProducts = SALES_DATA.stream() // GH-90000
            .collect(Collectors.groupingBy( // GH-90000
                s -> (String) s.get("product"),
                Collectors.summingDouble(s -> (Integer) s.get("quantity") * (Double) s.get("price"))
            ))
            .entrySet().stream() // GH-90000
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed()) // GH-90000
            .collect(Collectors.toList()); // GH-90000

        assertThat(rankedProducts.get(0).getKey()).isEqualTo("Gadget"); // 850
        assertThat(rankedProducts.get(1).getKey()).isEqualTo("Widget"); // 625
        assertThat(rankedProducts.get(2).getKey()).isEqualTo("Tool"); // 600
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> generateSalesSummary(List<Map<String, Object>> sales) { // GH-90000
        long totalSales = sales.size(); // GH-90000
        int totalQuantity = sales.stream().mapToInt(s -> (Integer) s.get("quantity")).sum();
        double totalRevenue = sales.stream() // GH-90000
            .mapToDouble(s -> (Integer) s.get("quantity") * (Double) s.get("price"))
            .sum(); // GH-90000
        double averageOrderValue = totalRevenue / totalSales;

        Map<String, Double> revenueByProduct = sales.stream() // GH-90000
            .collect(Collectors.groupingBy( // GH-90000
                s -> (String) s.get("product"),
                Collectors.summingDouble(s -> (Integer) s.get("quantity") * (Double) s.get("price"))
            ));

        Map<String, Double> revenueByRegion = sales.stream() // GH-90000
            .collect(Collectors.groupingBy( // GH-90000
                s -> (String) s.get("region"),
                Collectors.summingDouble(s -> (Integer) s.get("quantity") * (Double) s.get("price"))
            ));

        return Map.of( // GH-90000
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
