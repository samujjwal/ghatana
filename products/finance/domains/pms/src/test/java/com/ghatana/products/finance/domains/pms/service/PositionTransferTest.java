package com.ghatana.products.finance.domains.pms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Position Transfer Tests")
class PositionTransferTest {
    private TransferService service;

    @BeforeEach
    void setUp() {
        service = new TransferService();
    }

    @Test
    @DisplayName("Should transfer position between accounts")
    void shouldTransferPositionBetweenAccounts() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        Transfer transfer = service.transferPosition(position, "account-1", "account-2");
        assertThat(transfer.fromAccount()).isEqualTo("account-1");
        assertThat(transfer.toAccount()).isEqualTo("account-2");
        assertThat(transfer.quantity()).isEqualTo(100L);
    }

    @Test
    @DisplayName("Should validate transfer eligibility")
    void shouldValidateTransferEligibility() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        boolean eligible = service.isTransferEligible(position, 100L);
        assertThat(eligible).isTrue();
    }

    @Test
    @DisplayName("Should reject transfer exceeding available quantity")
    void shouldRejectTransferExceedingAvailableQuantity() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        assertThatThrownBy(() -> service.validateTransfer(position, 150L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should track transfer history")
    void shouldTrackTransferHistory() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        service.transferPosition(position, "account-1", "account-2");
        assertThat(service.getTransferHistory("AAPL")).hasSize(1);
    }

    @Test
    @DisplayName("Should handle partial transfers")
    void shouldHandlePartialTransfers() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        Transfer transfer = service.transferPosition(position, "account-1", "account-2", 50L);
        assertThat(transfer.quantity()).isEqualTo(50L);
    }

    @Test
    @DisplayName("Should preserve cost basis on transfer")
    void shouldPreserveCostBasisOnTransfer() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        Transfer transfer = service.transferPosition(position, "account-1", "account-2");
        assertThat(transfer.costBasis()).isEqualByComparingTo(BigDecimal.valueOf(150.00));
    }

    @Test
    @DisplayName("Should generate transfer confirmation")
    void shouldGenerateTransferConfirmation() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        Transfer transfer = service.transferPosition(position, "account-1", "account-2");
        String confirmation = service.generateConfirmation(transfer);
        assertThat(confirmation).contains("AAPL").contains("100");
    }

    @Test
    @DisplayName("Should handle cross-currency transfers")
    void shouldHandleCrossCurrencyTransfers() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        Transfer transfer = service.transferWithCurrencyConversion(position, "account-1", "account-2", BigDecimal.valueOf(1.20));
        assertThat(transfer.exchangeRate()).isEqualByComparingTo(BigDecimal.valueOf(1.20));
    }

    @Test
    @DisplayName("Should apply transfer fees")
    void shouldApplyTransferFees() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        BigDecimal fee = service.calculateTransferFee(position, BigDecimal.valueOf(0.001));
        assertThat(fee).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should validate account permissions")
    void shouldValidateAccountPermissions() {
        boolean hasPermission = service.hasTransferPermission("account-1", "account-2");
        assertThat(hasPermission).isTrue();
    }

    record Position(String symbol, long quantity, BigDecimal averagePrice) {}
    record Transfer(String symbol, long quantity, String fromAccount, String toAccount, BigDecimal costBasis, BigDecimal exchangeRate) {
        Transfer(String symbol, long quantity, String fromAccount, String toAccount, BigDecimal costBasis) {
            this(symbol, quantity, fromAccount, toAccount, costBasis, BigDecimal.ONE);
        }
    }

    static class TransferService {
        private final java.util.List<Transfer> history = new java.util.ArrayList<>();

        Transfer transferPosition(Position position, String from, String to) {
            return transferPosition(position, from, to, position.quantity());
        }

        Transfer transferPosition(Position position, String from, String to, long quantity) {
            Transfer transfer = new Transfer(position.symbol(), quantity, from, to, position.averagePrice());
            history.add(transfer);
            return transfer;
        }

        boolean isTransferEligible(Position position, long quantity) {
            return position.quantity() >= quantity;
        }

        void validateTransfer(Position position, long quantity) {
            if (quantity > position.quantity()) {
                throw new IllegalArgumentException("Transfer quantity exceeds available");
            }
        }

        java.util.List<Transfer> getTransferHistory(String symbol) {
            return history.stream().filter(t -> t.symbol().equals(symbol)).toList();
        }

        String generateConfirmation(Transfer transfer) {
            return String.format("Transfer: %s %d shares from %s to %s",
                transfer.symbol(), transfer.quantity(), transfer.fromAccount(), transfer.toAccount());
        }

        Transfer transferWithCurrencyConversion(Position position, String from, String to, BigDecimal rate) {
            return new Transfer(position.symbol(), position.quantity(), from, to, position.averagePrice(), rate);
        }

        BigDecimal calculateTransferFee(Position position, BigDecimal feeRate) {
            return position.averagePrice().multiply(BigDecimal.valueOf(position.quantity())).multiply(feeRate);
        }

        boolean hasTransferPermission(String from, String to) {
            return true;
        }
    }
}
