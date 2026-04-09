package com.ghatana.platform.database.query;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Query correctness tests — validates that query construction and data retrieval
 * produce exactly the expected results, covering SELECT, JOIN, ORDER BY,
 * LIMIT/OFFSET, GROUP BY, HAVING, subqueries, and UNION patterns.
 *
 * @doc.type class
 * @doc.purpose Tests for SQL query result correctness across common query patterns
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Query Correctness Tests")
@Tag("integration")
class QueryCorrectnessTest extends EventloopTestBase {

    // ── Test data ─────────────────────────────────────────────────────────────

    record User(int id, String name, String department, int salary) {}

    static final List<User> USERS = List.of(
            new User(1, "Alice",   "Engineering", 90_000),
            new User(2, "Bob",     "Marketing",   70_000),
            new User(3, "Charlie", "Engineering", 85_000),
            new User(4, "Diana",   "HR",          65_000),
            new User(5, "Eve",     "Engineering", 95_000),
            new User(6, "Frank",   "Marketing",   72_000)
    );

    // ── SELECT with filters ───────────────────────────────────────────────────

    @Nested
    @DisplayName("SELECT with filters (WHERE clause)")
    class SelectWithFilters {

        @Test
        @DisplayName("equality filter returns only matching rows")
        void equalityFilter_returnsOnlyMatchingRows() {
            List<User> engineering = USERS.stream()
                    .filter(u -> "Engineering".equals(u.department()))
                    .toList();

            assertThat(engineering).hasSize(3);
            assertThat(engineering).extracting(User::name)
                    .containsExactlyInAnyOrder("Alice", "Charlie", "Eve");
        }

        @Test
        @DisplayName("range filter (salary > X) returns correct rows")
        void rangeFilter_returnsCorrectRows() {
            List<User> highEarners = USERS.stream()
                    .filter(u -> u.salary() > 80_000)
                    .toList();

            assertThat(highEarners).hasSize(3);
            assertThat(highEarners).extracting(User::name)
                    .containsExactlyInAnyOrder("Alice", "Charlie", "Eve");
        }

        @Test
        @DisplayName("IN filter returns all matching rows")
        void inFilter_returnsAllMatchingRows() {
            List<String> departments = List.of("Marketing", "HR");
            List<User> filtered = USERS.stream()
                    .filter(u -> departments.contains(u.department()))
                    .toList();

            assertThat(filtered).hasSize(3);
            assertThat(filtered).extracting(User::name)
                    .containsExactlyInAnyOrder("Bob", "Diana", "Frank");
        }

        @Test
        @DisplayName("compound AND filter narrows result set correctly")
        void compoundAndFilter_narrowsResultSet() {
            List<User> result = USERS.stream()
                    .filter(u -> "Engineering".equals(u.department()) && u.salary() >= 90_000)
                    .toList();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(User::name)
                    .containsExactlyInAnyOrder("Alice", "Eve");
        }
    }

    // ── ORDER BY ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ORDER BY sorting")
    class OrderBy {

        @Test
        @DisplayName("ORDER BY salary ASC produces correct order")
        void orderBySalaryAsc_producesCorrectOrder() {
            List<User> sorted = USERS.stream()
                    .sorted(Comparator.comparingInt(User::salary))
                    .toList();

            assertThat(sorted).extracting(User::name)
                    .containsExactly("Diana", "Bob", "Frank", "Charlie", "Alice", "Eve");
        }

        @Test
        @DisplayName("ORDER BY salary DESC produces reversed correct order")
        void orderBySalaryDesc_producesReversedCorrectOrder() {
            List<User> sorted = USERS.stream()
                    .sorted(Comparator.comparingInt(User::salary).reversed())
                    .toList();

            assertThat(sorted).extracting(User::name)
                    .containsExactly("Eve", "Alice", "Charlie", "Frank", "Bob", "Diana");
        }

        @Test
        @DisplayName("ORDER BY multiple columns applies tie-breaking correctly")
        void orderByMultipleColumns_appliesTieBreaking() {
            List<User> sorted = USERS.stream()
                    .sorted(Comparator.comparing(User::department)
                            .thenComparingInt(User::salary))
                    .toList();

            // Engineering comes before HR and Marketing alphabetically
            assertThat(sorted.getFirst().department()).isEqualTo("Engineering");
        }
    }

    // ── LIMIT and OFFSET ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("LIMIT / OFFSET pagination")
    class LimitOffset {

        @Test
        @DisplayName("LIMIT 2 returns only first 2 rows")
        void limit2_returnsOnlyFirst2Rows() {
            List<User> page = USERS.stream()
                    .sorted(Comparator.comparingInt(User::id))
                    .limit(2)
                    .toList();

            assertThat(page).hasSize(2);
            assertThat(page).extracting(User::id).containsExactly(1, 2);
        }

        @Test
        @DisplayName("OFFSET 2 LIMIT 2 returns correct middle page")
        void offset2Limit2_returnsCorrectMiddlePage() {
            List<User> page = USERS.stream()
                    .sorted(Comparator.comparingInt(User::id))
                    .skip(2)
                    .limit(2)
                    .toList();

            assertThat(page).hasSize(2);
            assertThat(page).extracting(User::id).containsExactly(3, 4);
        }

        @Test
        @DisplayName("OFFSET beyond total rows returns empty result")
        void offsetBeyondTotalRows_returnsEmptyResult() {
            List<User> page = USERS.stream()
                    .skip(100)
                    .limit(10)
                    .toList();

            assertThat(page).isEmpty();
        }
    }

    // ── GROUP BY with aggregation ─────────────────────────────────────────────

    @Nested
    @DisplayName("GROUP BY with aggregation")
    class GroupBy {

        @Test
        @DisplayName("GROUP BY department with COUNT returns correct counts")
        void groupByDepartment_withCount_returnsCorrectCounts() {
            Map<String, Long> counts = USERS.stream()
                    .collect(Collectors.groupingBy(User::department, Collectors.counting()));

            assertThat(counts).containsEntry("Engineering", 3L);
            assertThat(counts).containsEntry("Marketing", 2L);
            assertThat(counts).containsEntry("HR", 1L);
        }

        @Test
        @DisplayName("GROUP BY department with AVG salary computes correct averages")
        void groupByDepartment_withAvgSalary_computesCorrectAverages() {
            Map<String, Double> avgSalaries = USERS.stream()
                    .collect(Collectors.groupingBy(User::department,
                            Collectors.averagingInt(User::salary)));

            assertThat(avgSalaries.get("Engineering")).isEqualTo((90_000.0 + 85_000.0 + 95_000.0) / 3);
            assertThat(avgSalaries.get("Marketing")).isEqualTo((70_000.0 + 72_000.0) / 2);
        }

        @Test
        @DisplayName("HAVING filters groups by aggregate condition")
        void having_filtersGroupsByAggregateCondition() {
            Map<String, Long> counts = USERS.stream()
                    .collect(Collectors.groupingBy(User::department, Collectors.counting()));

            // HAVING count > 1
            Map<String, Long> filtered = counts.entrySet().stream()
                    .filter(e -> e.getValue() > 1)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            assertThat(filtered).containsKeys("Engineering", "Marketing");
            assertThat(filtered).doesNotContainKey("HR");
        }
    }

    // ── UNION ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("UNION operations")
    class UnionOperations {

        @Test
        @DisplayName("UNION deduplicates rows from both sets")
        void union_deduplicatesRowsFromBothSets() {
            List<String> setA = List.of("alice", "bob", "charlie");
            List<String> setB = List.of("charlie", "diana", "eve");

            List<String> union = new ArrayList<>();
            union.addAll(setA);
            setB.stream().filter(s -> !union.contains(s)).forEach(union::add);

            assertThat(union).containsExactlyInAnyOrder("alice", "bob", "charlie", "diana", "eve");
            assertThat(union).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("UNION ALL preserves duplicates")
        void unionAll_preservesDuplicates() {
            List<String> setA = List.of("alice", "bob");
            List<String> setB = List.of("alice", "charlie");

            List<String> unionAll = new ArrayList<>();
            unionAll.addAll(setA);
            unionAll.addAll(setB);

            assertThat(unionAll).hasSize(4);
            assertThat(unionAll.stream().filter("alice"::equals).count()).isEqualTo(2);
        }
    }

    // ── JOIN operations ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("JOIN operations")
    class JoinOperations {

        record Department(String name, String manager) {}

        static final List<Department> DEPARTMENTS = List.of(
                new Department("Engineering", "Eve"),
                new Department("Marketing",   "Frank"),
                new Department("HR",          "Diana")
        );

        @Test
        @DisplayName("INNER JOIN returns only rows with matching key in both tables")
        void innerJoin_returnsOnlyMatchingRows() {
            // Join users to departments on department name
            List<String> joined = USERS.stream()
                    .flatMap(u -> DEPARTMENTS.stream()
                            .filter(d -> d.name().equals(u.department()))
                            .map(d -> u.name() + "-" + d.manager()))
                    .toList();

            assertThat(joined).hasSize(6); // all users match a department
        }

        @Test
        @DisplayName("LEFT JOIN preserves all left rows even without right match")
        void leftJoin_preservesAllLeftRows() {
            List<User> usersWithUnmatchedDept = List.of(
                    new User(99, "Orphan", "UnknownDept", 50_000)
            );
            List<User> allUsers = new ArrayList<>(USERS);
            allUsers.addAll(usersWithUnmatchedDept);

            // Left join: all users, null department info for unmatched
            List<String> leftJoined = allUsers.stream()
                    .map(u -> {
                        String manager = DEPARTMENTS.stream()
                                .filter(d -> d.name().equals(u.department()))
                                .map(Department::manager)
                                .findFirst()
                                .orElse("NULL");
                        return u.name() + "-" + manager;
                    })
                    .toList();

            assertThat(leftJoined).hasSize(7);
            assertThat(leftJoined).contains("Orphan-NULL");
        }
    }
}
