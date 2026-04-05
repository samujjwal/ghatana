package com.ghatana.products.finance.domains.pms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for corporate actions processing per D03-006
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Corporate Actions Tests")
class CorporateActionsTest {

    private CorporateActionsService service;

    @BeforeEach
    void setUp() {
        service = new CorporateActionsService();
    }

    @Test
    @DisplayName("Should process stock split")
    void shouldProcessStockSplit() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        StockSplit split = new StockSplit("AAPL", 2, 1, LocalDate.now());

        Position adjusted = service.applyStockSplit(position, split);

        assertThat(adjusted.quantity()).isEqualTo(200L);
        assertThat(adjusted.averagePrice()).isEqualByComparingTo(BigDecimal.valueOf(75.00));
    }

    @Test
    @DisplayName("Should process dividend payment")
    void shouldProcessDividendPayment() {
        Position position = new Position("AAPL", 1000L, BigDecimal.valueOf(150.00));
        Dividend dividend = new Dividend("AAPL", BigDecimal.valueOf(0.50), LocalDate.now());

        BigDecimal payment = service.calculateDividendPayment(position, dividend);

        assertThat(payment).isEqualByComparingTo(BigDecimal.valueOf(500.00));
    }

    @Test
    @DisplayName("Should process merger")
    void shouldProcessMerger() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        Merger merger = new Merger("AAPL", "NEWCO", BigDecimal.valueOf(1.5), LocalDate.now());

        Position newPosition = service.applyMerger(position, merger);

        assertThat(newPosition.symbol()).isEqualTo("NEWCO");
        assertThat(newPosition.quantity()).isEqualTo(150L);
    }

    @Test
    @DisplayName("Should process spinoff")
    void shouldProcessSpinoff() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        Spinoff spinoff = new Spinoff("AAPL", "NEWCO", BigDecimal.valueOf(0.25), LocalDate.now());

        List<Position> positions = service.applySpinoff(position, spinoff);

        assertThat(positions).hasSize(2);
        assertThat(positions.get(0).symbol()).isEqualTo("AAPL");
        assertThat(positions.get(1).symbol()).isEqualTo("NEWCO");
    }

    @Test
    @DisplayName("Should process rights issue")
    void shouldProcessRightsIssue() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        RightsIssue rights = new RightsIssue("AAPL", 1, 10, BigDecimal.valueOf(140.00), LocalDate.now());

        int rightsReceived = service.calculateRightsReceived(position, rights);

        assertThat(rightsReceived).isEqualTo(10);
    }

    @Test
    @DisplayName("Should track ex-dividend dates")
    void shouldTrackExDividendDates() {
        Dividend dividend = new Dividend("AAPL", BigDecimal.valueOf(0.50), LocalDate.now().plusDays(5));

        boolean isExDividend = service.isExDividendDate(dividend, LocalDate.now().plusDays(6));

        assertThat(isExDividend).isTrue();
    }

    @Test
    @DisplayName("Should adjust cost basis for return of capital")
    void shouldAdjustCostBasisForReturnOfCapital() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        ReturnOfCapital roc = new ReturnOfCapital("AAPL", BigDecimal.valueOf(5.00), LocalDate.now());

        Position adjusted = service.applyReturnOfCapital(position, roc);

        assertThat(adjusted.averagePrice()).isEqualByComparingTo(BigDecimal.valueOf(145.00));
    }

    @Test
    @DisplayName("Should handle reverse stock split")
    void shouldHandleReverseStockSplit() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(10.00));
        StockSplit reverseSplit = new StockSplit("AAPL", 1, 10, LocalDate.now());

        Position adjusted = service.applyStockSplit(position, reverseSplit);

        assertThat(adjusted.quantity()).isEqualTo(10L);
        assertThat(adjusted.averagePrice()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
    }

    @Test
    @DisplayName("Should process stock dividend")
    void shouldProcessStockDividend() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        StockDividend stockDiv = new StockDividend("AAPL", BigDecimal.valueOf(0.05), LocalDate.now());

        Position adjusted = service.applyStockDividend(position, stockDiv);

        assertThat(adjusted.quantity()).isEqualTo(105L);
    }

    @Test
    @DisplayName("Should generate corporate actions report")
    void shouldGenerateCorporateActionsReport() {
        List<CorporateAction> actions = List.of(
            new CorporateAction("AAPL", "SPLIT", LocalDate.now()),
            new CorporateAction("GOOGL", "DIVIDEND", LocalDate.now())
        );

        CorporateActionsReport report = service.generateReport(actions);

        assertThat(report.totalActions()).isEqualTo(2);
    }

    record Position(String symbol, long quantity, BigDecimal averagePrice) {}
    record StockSplit(String symbol, int newShares, int oldShares, LocalDate effectiveDate) {}
    record Dividend(String symbol, BigDecimal amount, LocalDate exDate) {}
    record Merger(String fromSymbol, String toSymbol, BigDecimal exchangeRatio, LocalDate effectiveDate) {}
    record Spinoff(String parentSymbol, String newSymbol, BigDecimal ratio, LocalDate effectiveDate) {}
    record RightsIssue(String symbol, int newShares, int oldShares, BigDecimal subscriptionPrice, LocalDate exDate) {}
    record ReturnOfCapital(String symbol, BigDecimal amount, LocalDate paymentDate) {}
    record StockDividend(String symbol, BigDecimal percentage, LocalDate exDate) {}
    record CorporateAction(String symbol, String type, LocalDate date) {}
    record CorporateActionsReport(int totalActions) {}

    static class CorporateActionsService {
        Position applyStockSplit(Position position, StockSplit split) {
            long newQty = position.quantity() * split.newShares() / split.oldShares();
            BigDecimal newPrice = position.averagePrice()
                .multiply(BigDecimal.valueOf(split.oldShares()))
                .divide(BigDecimal.valueOf(split.newShares()), 2, java.math.RoundingMode.HALF_UP);
            return new Position(position.symbol(), newQty, newPrice);
        }

        BigDecimal calculateDividendPayment(Position position, Dividend dividend) {
            return dividend.amount().multiply(BigDecimal.valueOf(position.quantity()));
        }

        Position applyMerger(Position position, Merger merger) {
            long newQty = BigDecimal.valueOf(position.quantity())
                .multiply(merger.exchangeRatio())
                .longValue();
            return new Position(merger.toSymbol(), newQty, position.averagePrice());
        }

        List<Position> applySpinoff(Position position, Spinoff spinoff) {
            long spinoffQty = BigDecimal.valueOf(position.quantity())
                .multiply(spinoff.ratio())
                .longValue();
            Position spinoffPosition = new Position(spinoff.newSymbol(), spinoffQty, BigDecimal.ZERO);
            return List.of(position, spinoffPosition);
        }

        int calculateRightsReceived(Position position, RightsIssue rights) {
            return (int) (position.quantity() * rights.newShares() / rights.oldShares());
        }

        boolean isExDividendDate(Dividend dividend, LocalDate date) {
            return !date.isBefore(dividend.exDate());
        }

        Position applyReturnOfCapital(Position position, ReturnOfCapital roc) {
            BigDecimal newPrice = position.averagePrice().subtract(roc.amount());
            return new Position(position.symbol(), position.quantity(), newPrice);
        }

        Position applyStockDividend(Position position, StockDividend stockDiv) {
            long additionalShares = stockDiv.percentage()
                .multiply(BigDecimal.valueOf(position.quantity()))
                .longValue();
            long newQty = position.quantity() + additionalShares;
            return new Position(position.symbol(), newQty, position.averagePrice());
        }

        CorporateActionsReport generateReport(List<CorporateAction> actions) {
            return new CorporateActionsReport(actions.size());
        }
    }
}
