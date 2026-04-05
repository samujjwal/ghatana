package com.ghatana.products.finance.domains.phr.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Portfolio Tax Reporting Tests")
class PortfolioTaxReportingTest {
    private TaxReportingService service;

    @BeforeEach
    void setUp() {
        service = new TaxReportingService();
    }

    @Test
    @DisplayName("Should calculate capital gains")
    void shouldCalculateCapitalGains() {
        service.recordSale("AAPL", 100L, BigDecimal.valueOf(150.00), BigDecimal.valueOf(155.00), LocalDate.now());
        BigDecimal capitalGain = service.calculateCapitalGains();
        assertThat(capitalGain).isEqualByComparingTo(BigDecimal.valueOf(500.00));
    }

    @Test
    @DisplayName("Should distinguish short-term and long-term gains")
    void shouldDistinguishShortTermAndLongTermGains() {
        service.recordSale("AAPL", 100L, BigDecimal.valueOf(150.00), BigDecimal.valueOf(155.00), 
            LocalDate.now(), LocalDate.now().minusDays(400));
        TaxClassification classification = service.classifyGains();
        assertThat(classification.longTermGains()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should track wash sales")
    void shouldTrackWashSales() {
        service.recordSale("AAPL", 100L, BigDecimal.valueOf(150.00), BigDecimal.valueOf(145.00), LocalDate.now());
        service.recordPurchase("AAPL", 100L, BigDecimal.valueOf(146.00), LocalDate.now().plusDays(15));
        boolean hasWashSale = service.detectWashSales();
        assertThat(hasWashSale).isTrue();
    }

    @Test
    @DisplayName("Should calculate dividend income")
    void shouldCalculateDividendIncome() {
        service.recordDividend("AAPL", BigDecimal.valueOf(0.50), 1000L, LocalDate.now());
        BigDecimal dividendIncome = service.calculateDividendIncome();
        assertThat(dividendIncome).isEqualByComparingTo(BigDecimal.valueOf(500.00));
    }

    @Test
    @DisplayName("Should classify qualified vs non-qualified dividends")
    void shouldClassifyQualifiedVsNonQualifiedDividends() {
        service.recordDividend("AAPL", BigDecimal.valueOf(0.50), 1000L, LocalDate.now(), true);
        service.recordDividend("REIT-1", BigDecimal.valueOf(0.30), 500L, LocalDate.now(), false);
        DividendClassification classification = service.classifyDividends();
        assertThat(classification.qualifiedDividends()).isGreaterThan(BigDecimal.ZERO);
        assertThat(classification.nonQualifiedDividends()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should generate Form 1099-B data")
    void shouldGenerateForm1099BData() {
        service.recordSale("AAPL", 100L, BigDecimal.valueOf(150.00), BigDecimal.valueOf(155.00), LocalDate.now());
        Form1099B form = service.generateForm1099B();
        assertThat(form.proceeds()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should generate Form 1099-DIV data")
    void shouldGenerateForm1099DIVData() {
        service.recordDividend("AAPL", BigDecimal.valueOf(0.50), 1000L, LocalDate.now(), true);
        Form1099DIV form = service.generateForm1099DIV();
        assertThat(form.totalDividends()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should track cost basis adjustments")
    void shouldTrackCostBasisAdjustments() {
        service.recordCostBasisAdjustment("AAPL", BigDecimal.valueOf(5.00), "RETURN_OF_CAPITAL");
        List<CostBasisAdjustment> adjustments = service.getCostBasisAdjustments();
        assertThat(adjustments).hasSize(1);
    }

    @Test
    @DisplayName("Should support FIFO lot selection")
    void shouldSupportFIFOLotSelection() {
        service.setLotSelectionMethod("FIFO");
        service.addTaxLot("AAPL", 100L, BigDecimal.valueOf(150.00), LocalDate.now().minusDays(100));
        service.addTaxLot("AAPL", 100L, BigDecimal.valueOf(155.00), LocalDate.now().minusDays(50));
        TaxLot selectedLot = service.selectLotForSale("AAPL", 50L);
        assertThat(selectedLot.costBasis()).isEqualByComparingTo(BigDecimal.valueOf(150.00));
    }

    @Test
    @DisplayName("Should generate annual tax summary")
    void shouldGenerateAnnualTaxSummary() {
        service.recordSale("AAPL", 100L, BigDecimal.valueOf(150.00), BigDecimal.valueOf(155.00), LocalDate.now());
        service.recordDividend("AAPL", BigDecimal.valueOf(0.50), 1000L, LocalDate.now(), true);
        TaxSummary summary = service.generateAnnualSummary(2024);
        assertThat(summary.totalCapitalGains()).isGreaterThan(BigDecimal.ZERO);
        assertThat(summary.totalDividends()).isGreaterThan(BigDecimal.ZERO);
    }

    record TaxClassification(BigDecimal shortTermGains, BigDecimal longTermGains) {}
    record DividendClassification(BigDecimal qualifiedDividends, BigDecimal nonQualifiedDividends) {}
    record Form1099B(BigDecimal proceeds, BigDecimal costBasis, BigDecimal gainLoss) {}
    record Form1099DIV(BigDecimal totalDividends, BigDecimal qualifiedDividends) {}
    record CostBasisAdjustment(String symbol, BigDecimal amount, String reason) {}
    record TaxLot(String symbol, long quantity, BigDecimal costBasis, LocalDate purchaseDate) {}
    record TaxSummary(BigDecimal totalCapitalGains, BigDecimal totalDividends, int year) {}

    static class TaxReportingService {
        private final List<Sale> sales = new java.util.ArrayList<>();
        private final List<Dividend> dividends = new java.util.ArrayList<>();
        private final List<Purchase> purchases = new java.util.ArrayList<>();
        private final List<CostBasisAdjustment> adjustments = new java.util.ArrayList<>();
        private final List<TaxLot> taxLots = new java.util.ArrayList<>();
        private String lotSelectionMethod = "FIFO";

        void recordSale(String symbol, long quantity, BigDecimal costBasis, BigDecimal salePrice, LocalDate saleDate) {
            sales.add(new Sale(symbol, quantity, costBasis, salePrice, saleDate, saleDate.minusDays(365)));
        }

        void recordSale(String symbol, long quantity, BigDecimal costBasis, BigDecimal salePrice, 
                       LocalDate saleDate, LocalDate purchaseDate) {
            sales.add(new Sale(symbol, quantity, costBasis, salePrice, saleDate, purchaseDate));
        }

        void recordPurchase(String symbol, long quantity, BigDecimal price, LocalDate date) {
            purchases.add(new Purchase(symbol, quantity, price, date));
        }

        void recordDividend(String symbol, BigDecimal perShare, long shares, LocalDate date) {
            recordDividend(symbol, perShare, shares, date, true);
        }

        void recordDividend(String symbol, BigDecimal perShare, long shares, LocalDate date, boolean qualified) {
            dividends.add(new Dividend(symbol, perShare, shares, date, qualified));
        }

        void recordCostBasisAdjustment(String symbol, BigDecimal amount, String reason) {
            adjustments.add(new CostBasisAdjustment(symbol, amount, reason));
        }

        void addTaxLot(String symbol, long quantity, BigDecimal costBasis, LocalDate purchaseDate) {
            taxLots.add(new TaxLot(symbol, quantity, costBasis, purchaseDate));
        }

        void setLotSelectionMethod(String method) {
            this.lotSelectionMethod = method;
        }

        BigDecimal calculateCapitalGains() {
            return sales.stream()
                .map(s -> s.salePrice().subtract(s.costBasis()).multiply(BigDecimal.valueOf(s.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        TaxClassification classifyGains() {
            BigDecimal shortTerm = BigDecimal.ZERO;
            BigDecimal longTerm = BigDecimal.ZERO;

            for (Sale sale : sales) {
                long holdingDays = java.time.temporal.ChronoUnit.DAYS.between(sale.purchaseDate(), sale.saleDate());
                BigDecimal gain = sale.salePrice().subtract(sale.costBasis()).multiply(BigDecimal.valueOf(sale.quantity()));
                
                if (holdingDays > 365) {
                    longTerm = longTerm.add(gain);
                } else {
                    shortTerm = shortTerm.add(gain);
                }
            }

            return new TaxClassification(shortTerm, longTerm);
        }

        boolean detectWashSales() {
            for (Sale sale : sales) {
                BigDecimal loss = sale.salePrice().subtract(sale.costBasis());
                if (loss.compareTo(BigDecimal.ZERO) < 0) {
                    for (Purchase purchase : purchases) {
                        if (purchase.symbol().equals(sale.symbol())) {
                            long daysBetween = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(sale.saleDate(), purchase.date()));
                            if (daysBetween <= 30) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }

        BigDecimal calculateDividendIncome() {
            return dividends.stream()
                .map(d -> d.perShare().multiply(BigDecimal.valueOf(d.shares())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        DividendClassification classifyDividends() {
            BigDecimal qualified = dividends.stream()
                .filter(Dividend::qualified)
                .map(d -> d.perShare().multiply(BigDecimal.valueOf(d.shares())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal nonQualified = dividends.stream()
                .filter(d -> !d.qualified())
                .map(d -> d.perShare().multiply(BigDecimal.valueOf(d.shares())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            return new DividendClassification(qualified, nonQualified);
        }

        Form1099B generateForm1099B() {
            BigDecimal proceeds = sales.stream()
                .map(s -> s.salePrice().multiply(BigDecimal.valueOf(s.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal costBasis = sales.stream()
                .map(s -> s.costBasis().multiply(BigDecimal.valueOf(s.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            return new Form1099B(proceeds, costBasis, proceeds.subtract(costBasis));
        }

        Form1099DIV generateForm1099DIV() {
            DividendClassification classification = classifyDividends();
            BigDecimal total = classification.qualifiedDividends().add(classification.nonQualifiedDividends());
            return new Form1099DIV(total, classification.qualifiedDividends());
        }

        List<CostBasisAdjustment> getCostBasisAdjustments() {
            return adjustments;
        }

        TaxLot selectLotForSale(String symbol, long quantity) {
            return taxLots.stream()
                .filter(lot -> lot.symbol().equals(symbol))
                .sorted((a, b) -> a.purchaseDate().compareTo(b.purchaseDate()))
                .findFirst()
                .orElse(null);
        }

        TaxSummary generateAnnualSummary(int year) {
            return new TaxSummary(calculateCapitalGains(), calculateDividendIncome(), year);
        }

        record Sale(String symbol, long quantity, BigDecimal costBasis, BigDecimal salePrice, 
                   LocalDate saleDate, LocalDate purchaseDate) {}
        record Purchase(String symbol, long quantity, BigDecimal price, LocalDate date) {}
        record Dividend(String symbol, BigDecimal perShare, long shares, LocalDate date, boolean qualified) {}
    }
}
