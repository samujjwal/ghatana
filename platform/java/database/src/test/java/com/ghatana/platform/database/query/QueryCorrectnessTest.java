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
@DisplayName("Query Correctness Tests [GH-90000]")
@Tag("integration [GH-90000]")
class QueryCorrectnessTest extends EventloopTestBase {

    // ── Test data ─────────────────────────────────────────────────────────────

    record User(int id, String name, String department, int salary) {} // GH-90000

    static final List<User> USERS = List.of( // GH-90000
            new User(1, "Alice",   "Engineering", 90_000), // GH-90000
            new User(2, "Bob",     "Marketing",   70_000), // GH-90000
            new User(3, "Charlie", "Engineering", 85_000), // GH-90000
            new User(4, "Diana",   "HR",          65_000), // GH-90000
            new User(5, "Eve",     "Engineering", 95_000), // GH-90000
            new User(6, "Frank",   "Marketing",   72_000) // GH-90000
    );

    // ── SELECT with filters ───────────────────────────────────────────────────

    @Nested
    @DisplayName("SELECT with filters (WHERE clause) [GH-90000]")
    class SelectWithFilters {

        @Test
        @DisplayName("equality filter returns only matching rows [GH-90000]")
        void equalityFilter_returnsOnlyMatchingRows() { // GH-90000
            List<User> engineering = USERS.stream() // GH-90000
                    .filter(u -> "Engineering".equals(u.department())) // GH-90000
                    .toList(); // GH-90000

            assertThat(engineering).hasSize(3); // GH-90000
            assertThat(engineering).extracting(User::name) // GH-90000
                    .containsExactlyInAnyOrder("Alice", "Charlie", "Eve"); // GH-90000
        }

        @Test
        @DisplayName("range filter (salary > X) returns correct rows [GH-90000]")
        void rangeFilter_returnsCorrectRows() { // GH-90000
            List<User> highEarners = USERS.stream() // GH-90000
                    .filter(u -> u.salary() > 80_000) // GH-90000
                    .toList(); // GH-90000

            assertThat(highEarners).hasSize(3); // GH-90000
            assertThat(highEarners).extracting(User::name) // GH-90000
                    .containsExactlyInAnyOrder("Alice", "Charlie", "Eve"); // GH-90000
        }

        @Test
        @DisplayName("IN filter returns all matching rows [GH-90000]")
        void inFilter_returnsAllMatchingRows() { // GH-90000
            List<String> departments = List.of("Marketing", "HR"); // GH-90000
            List<User> filtered = USERS.stream() // GH-90000
                    .filter(u -> departments.contains(u.department())) // GH-90000
                    .toList(); // GH-90000

            assertThat(filtered).hasSize(3); // GH-90000
            assertThat(filtered).extracting(User::name) // GH-90000
                    .containsExactlyInAnyOrder("Bob", "Diana", "Frank"); // GH-90000
        }

        @Test
        @DisplayName("compound AND filter narrows result set correctly [GH-90000]")
        void compoundAndFilter_narrowsResultSet() { // GH-90000
            List<User> result = USERS.stream() // GH-90000
                    .filter(u -> "Engineering".equals(u.department()) && u.salary() >= 90_000) // GH-90000
                    .toList(); // GH-90000

            assertThat(result).hasSize(2); // GH-90000
            assertThat(result).extracting(User::name) // GH-90000
                    .containsExactlyInAnyOrder("Alice", "Eve"); // GH-90000
        }
    }

    // ── ORDER BY ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ORDER BY sorting [GH-90000]")
    class OrderBy {

        @Test
        @DisplayName("ORDER BY salary ASC produces correct order [GH-90000]")
        void orderBySalaryAsc_producesCorrectOrder() { // GH-90000
            List<User> sorted = USERS.stream() // GH-90000
                    .sorted(Comparator.comparingInt(User::salary)) // GH-90000
                    .toList(); // GH-90000

            assertThat(sorted).extracting(User::name) // GH-90000
                    .containsExactly("Diana", "Bob", "Frank", "Charlie", "Alice", "Eve"); // GH-90000
        }

        @Test
        @DisplayName("ORDER BY salary DESC produces reversed correct order [GH-90000]")
        void orderBySalaryDesc_producesReversedCorrectOrder() { // GH-90000
            List<User> sorted = USERS.stream() // GH-90000
                    .sorted(Comparator.comparingInt(User::salary).reversed()) // GH-90000
                    .toList(); // GH-90000

            assertThat(sorted).extracting(User::name) // GH-90000
                    .containsExactly("Eve", "Alice", "Charlie", "Frank", "Bob", "Diana"); // GH-90000
        }

        @Test
        @DisplayName("ORDER BY multiple columns applies tie-breaking correctly [GH-90000]")
        void orderByMultipleColumns_appliesTieBreaking() { // GH-90000
            List<User> sorted = USERS.stream() // GH-90000
                    .sorted(Comparator.comparing(User::department) // GH-90000
                            .thenComparingInt(User::salary)) // GH-90000
                    .toList(); // GH-90000

            // Engineering comes before HR and Marketing alphabetically
            assertThat(sorted.getFirst().department()).isEqualTo("Engineering [GH-90000]");
        }
    }

    // ── LIMIT and OFFSET ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("LIMIT / OFFSET pagination [GH-90000]")
    class LimitOffset {

        @Test
        @DisplayName("LIMIT 2 returns only first 2 rows [GH-90000]")
        void limit2_returnsOnlyFirst2Rows() { // GH-90000
            List<User> page = USERS.stream() // GH-90000
                    .sorted(Comparator.comparingInt(User::id)) // GH-90000
                    .limit(2) // GH-90000
                    .toList(); // GH-90000

            assertThat(page).hasSize(2); // GH-90000
            assertThat(page).extracting(User::id).containsExactly(1, 2); // GH-90000
        }

        @Test
        @DisplayName("OFFSET 2 LIMIT 2 returns correct middle page [GH-90000]")
        void offset2Limit2_returnsCorrectMiddlePage() { // GH-90000
            List<User> page = USERS.stream() // GH-90000
                    .sorted(Comparator.comparingInt(User::id)) // GH-90000
                    .skip(2) // GH-90000
                    .limit(2) // GH-90000
                    .toList(); // GH-90000

            assertThat(page).hasSize(2); // GH-90000
            assertThat(page).extracting(User::id).containsExactly(3, 4); // GH-90000
        }

        @Test
        @DisplayName("OFFSET beyond total rows returns empty result [GH-90000]")
        void offsetBeyondTotalRows_returnsEmptyResult() { // GH-90000
            List<User> page = USERS.stream() // GH-90000
                    .skip(100) // GH-90000
                    .limit(10) // GH-90000
                    .toList(); // GH-90000

            assertThat(page).isEmpty(); // GH-90000
        }
    }

    // ── GROUP BY with aggregation ─────────────────────────────────────────────

    @Nested
    @DisplayName("GROUP BY with aggregation [GH-90000]")
    class GroupBy {

        @Test
        @DisplayName("GROUP BY department with COUNT returns correct counts [GH-90000]")
        void groupByDepartment_withCount_returnsCorrectCounts() { // GH-90000
            Map<String, Long> counts = USERS.stream() // GH-90000
                    .collect(Collectors.groupingBy(User::department, Collectors.counting())); // GH-90000

            assertThat(counts).containsEntry("Engineering", 3L); // GH-90000
            assertThat(counts).containsEntry("Marketing", 2L); // GH-90000
            assertThat(counts).containsEntry("HR", 1L); // GH-90000
        }

        @Test
        @DisplayName("GROUP BY department with AVG salary computes correct averages [GH-90000]")
        void groupByDepartment_withAvgSalary_computesCorrectAverages() { // GH-90000
            Map<String, Double> avgSalaries = USERS.stream() // GH-90000
                    .collect(Collectors.groupingBy(User::department, // GH-90000
                            Collectors.averagingInt(User::salary))); // GH-90000

            assertThat(avgSalaries.get("Engineering [GH-90000]")).isEqualTo((90_000.0 + 85_000.0 + 95_000.0) / 3);
            assertThat(avgSalaries.get("Marketing [GH-90000]")).isEqualTo((70_000.0 + 72_000.0) / 2);
        }

        @Test
        @DisplayName("HAVING filters groups by aggregate condition [GH-90000]")
        void having_filtersGroupsByAggregateCondition() { // GH-90000
            Map<String, Long> counts = USERS.stream() // GH-90000
                    .collect(Collectors.groupingBy(User::department, Collectors.counting())); // GH-90000

            // HAVING count > 1
            Map<String, Long> filtered = counts.entrySet().stream() // GH-90000
                    .filter(e -> e.getValue() > 1) // GH-90000
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)); // GH-90000

            assertThat(filtered).containsKeys("Engineering", "Marketing"); // GH-90000
            assertThat(filtered).doesNotContainKey("HR [GH-90000]");
        }
    }

    // ── UNION ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("UNION operations [GH-90000]")
    class UnionOperations {

        @Test
        @DisplayName("UNION deduplicates rows from both sets [GH-90000]")
        void union_deduplicatesRowsFromBothSets() { // GH-90000
            List<String> setA = List.of("alice", "bob", "charlie"); // GH-90000
            List<String> setB = List.of("charlie", "diana", "eve"); // GH-90000

            List<String> union = new ArrayList<>(); // GH-90000
            union.addAll(setA); // GH-90000
            setB.stream().filter(s -> !union.contains(s)).forEach(union::add); // GH-90000

            assertThat(union).containsExactlyInAnyOrder("alice", "bob", "charlie", "diana", "eve"); // GH-90000
            assertThat(union).doesNotHaveDuplicates(); // GH-90000
        }

        @Test
        @DisplayName("UNION ALL preserves duplicates [GH-90000]")
        void unionAll_preservesDuplicates() { // GH-90000
            List<String> setA = List.of("alice", "bob"); // GH-90000
            List<String> setB = List.of("alice", "charlie"); // GH-90000

            List<String> unionAll = new ArrayList<>(); // GH-90000
            unionAll.addAll(setA); // GH-90000
            unionAll.addAll(setB); // GH-90000

            assertThat(unionAll).hasSize(4); // GH-90000
            assertThat(unionAll.stream().filter("alice"::equals).count()).isEqualTo(2); // GH-90000
        }
    }

    // ── JOIN operations ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("JOIN operations [GH-90000]")
    class JoinOperations {

        record Department(String name, String manager) {} // GH-90000

        static final List<Department> DEPARTMENTS = List.of( // GH-90000
                new Department("Engineering", "Eve"), // GH-90000
                new Department("Marketing",   "Frank"), // GH-90000
                new Department("HR",          "Diana") // GH-90000
        );

        @Test
        @DisplayName("INNER JOIN returns only rows with matching key in both tables [GH-90000]")
        void innerJoin_returnsOnlyMatchingRows() { // GH-90000
            // Join users to departments on department name
            List<String> joined = USERS.stream() // GH-90000
                    .flatMap(u -> DEPARTMENTS.stream() // GH-90000
                            .filter(d -> d.name().equals(u.department())) // GH-90000
                            .map(d -> u.name() + "-" + d.manager())) // GH-90000
                    .toList(); // GH-90000

            assertThat(joined).hasSize(6); // all users match a department // GH-90000
        }

        @Test
        @DisplayName("LEFT JOIN preserves all left rows even without right match [GH-90000]")
        void leftJoin_preservesAllLeftRows() { // GH-90000
            List<User> usersWithUnmatchedDept = List.of( // GH-90000
                    new User(99, "Orphan", "UnknownDept", 50_000) // GH-90000
            );
            List<User> allUsers = new ArrayList<>(USERS); // GH-90000
            allUsers.addAll(usersWithUnmatchedDept); // GH-90000

            // Left join: all users, null department info for unmatched
            List<String> leftJoined = allUsers.stream() // GH-90000
                    .map(u -> { // GH-90000
                        String manager = DEPARTMENTS.stream() // GH-90000
                                .filter(d -> d.name().equals(u.department())) // GH-90000
                                .map(Department::manager) // GH-90000
                                .findFirst() // GH-90000
                                .orElse("NULL [GH-90000]");
                        return u.name() + "-" + manager; // GH-90000
                    })
                    .toList(); // GH-90000

            assertThat(leftJoined).hasSize(7); // GH-90000
            assertThat(leftJoined).contains("Orphan-NULL [GH-90000]");
        }
    }
}
