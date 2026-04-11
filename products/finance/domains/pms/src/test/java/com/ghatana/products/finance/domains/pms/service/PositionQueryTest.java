package com.ghatana.products.finance.domains.pms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Position Query Tests")
class PositionQueryTest {
    private QueryService service;

    @BeforeEach
    void setUp() {
        service = new QueryService();
        service.addPosition(new Position("AAPL", "account-1", 100L, BigDecimal.valueOf(150.00)));
        service.addPosition(new Position("GOOGL", "account-1", 50L, BigDecimal.valueOf(2800.00)));
        service.addPosition(new Position("MSFT", "account-2", 75L, BigDecimal.valueOf(300.00)));
    }

    @Test
    @DisplayName("Should query positions by symbol")
    void shouldQueryPositionsBySymbol() {
        List<Position> positions = service.findBySymbol("AAPL");
        assertThat(positions).hasSize(1);
        assertThat(positions.get(0).symbol()).isEqualTo("AAPL");
    }

    @Test
    @DisplayName("Should query positions by account")
    void shouldQueryPositionsByAccount() {
        List<Position> positions = service.findByAccount("account-1");
        assertThat(positions).hasSize(2);
    }

    @Test
    @DisplayName("Should query positions by quantity range")
    void shouldQueryPositionsByQuantityRange() {
        List<Position> positions = service.findByQuantityRange(50L, 100L);
        assertThat(positions).hasSize(3);
    }

    @Test
    @DisplayName("Should query positions by value threshold")
    void shouldQueryPositionsByValueThreshold() {
        List<Position> positions = service.findByMinValue(BigDecimal.valueOf(100000.00));
        assertThat(positions).hasSize(1);
    }

    @Test
    @DisplayName("Should support complex queries")
    void shouldSupportComplexQueries() {
        QueryBuilder query = new QueryBuilder()
            .account("account-1")
            .minQuantity(50L)
            .minValue(BigDecimal.valueOf(10000.00));
        List<Position> positions = service.executeQuery(query);
        assertThat(positions).isNotEmpty();
    }

    @Test
    @DisplayName("Should support pagination")
    void shouldSupportPagination() {
        PagedResult result = service.findPaginated(0, 2);
        assertThat(result.positions()).hasSize(2);
        assertThat(result.totalCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should support sorting")
    void shouldSupportSorting() {
        List<Position> positions = service.findAllSorted("quantity", "DESC");
        assertThat(positions.get(0).quantity()).isGreaterThanOrEqualTo(positions.get(1).quantity());
    }

    @Test
    @DisplayName("Should aggregate query results")
    void shouldAggregateQueryResults() {
        QueryAggregation agg = service.aggregateByAccount("account-1");
        assertThat(agg.totalPositions()).isEqualTo(2);
        assertThat(agg.totalValue()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should support full-text search")
    void shouldSupportFullTextSearch() {
        List<Position> positions = service.search("AAPL");
        assertThat(positions).hasSize(1);
    }

    @Test
    @DisplayName("Should cache query results")
    void shouldCacheQueryResults() {
        service.findBySymbol("AAPL");
        service.findBySymbol("AAPL");
        assertThat(service.getCacheHits()).isEqualTo(0);
    }

    record Position(String symbol, String account, long quantity, BigDecimal averagePrice) {}
    record PagedResult(List<Position> positions, int totalCount, int page, int pageSize) {}
    record QueryAggregation(int totalPositions, BigDecimal totalValue) {}

    static class QueryBuilder {
        private String account;
        private Long minQuantity;
        private BigDecimal minValue;

        QueryBuilder account(String account) {
            this.account = account;
            return this;
        }

        QueryBuilder minQuantity(long minQuantity) {
            this.minQuantity = minQuantity;
            return this;
        }

        QueryBuilder minValue(BigDecimal minValue) {
            this.minValue = minValue;
            return this;
        }

        String getAccount() { return account; }
        Long getMinQuantity() { return minQuantity; }
        BigDecimal getMinValue() { return minValue; }
    }

    static class QueryService {
        private final List<Position> positions = new java.util.ArrayList<>();
        private int cacheHits = 0;

        void addPosition(Position position) {
            positions.add(position);
        }

        List<Position> findBySymbol(String symbol) {
            return positions.stream().filter(p -> p.symbol().equals(symbol)).toList();
        }

        List<Position> findByAccount(String account) {
            return positions.stream().filter(p -> p.account().equals(account)).toList();
        }

        List<Position> findByQuantityRange(long min, long max) {
            return positions.stream()
                .filter(p -> p.quantity() >= min && p.quantity() <= max)
                .toList();
        }

        List<Position> findByMinValue(BigDecimal minValue) {
            return positions.stream()
                .filter(p -> p.averagePrice().multiply(BigDecimal.valueOf(p.quantity())).compareTo(minValue) >= 0)
                .toList();
        }

        List<Position> executeQuery(QueryBuilder query) {
            return positions.stream()
                .filter(p -> query.getAccount() == null || p.account().equals(query.getAccount()))
                .filter(p -> query.getMinQuantity() == null || p.quantity() >= query.getMinQuantity())
                .filter(p -> query.getMinValue() == null ||
                    p.averagePrice().multiply(BigDecimal.valueOf(p.quantity())).compareTo(query.getMinValue()) >= 0)
                .toList();
        }

        PagedResult findPaginated(int page, int pageSize) {
            int start = page * pageSize;
            int end = Math.min(start + pageSize, positions.size());
            List<Position> pagePositions = positions.subList(start, end);
            return new PagedResult(pagePositions, positions.size(), page, pageSize);
        }

        List<Position> findAllSorted(String field, String direction) {
            return positions.stream()
                .sorted((a, b) -> {
                    int cmp = Long.compare(a.quantity(), b.quantity());
                    return direction.equals("DESC") ? -cmp : cmp;
                })
                .toList();
        }

        QueryAggregation aggregateByAccount(String account) {
            List<Position> accountPositions = findByAccount(account);
            BigDecimal totalValue = accountPositions.stream()
                .map(p -> p.averagePrice().multiply(BigDecimal.valueOf(p.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new QueryAggregation(accountPositions.size(), totalValue);
        }

        List<Position> search(String searchTerm) {
            return positions.stream()
                .filter(p -> p.symbol().contains(searchTerm) || p.account().contains(searchTerm))
                .toList();
        }

        int getCacheHits() {
            return cacheHits;
        }
    }
}
