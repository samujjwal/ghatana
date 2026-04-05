package com.ghatana.products.finance.domains.pms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Position Locking Tests")
class PositionLockingTest {
    private LockingService service;

    @BeforeEach
    void setUp() {
        service = new LockingService();
    }

    @Test
    @DisplayName("Should lock position for trading")
    void shouldLockPositionForTrading() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        PositionLock lock = service.lockPosition(position, 50L, "TRADE");
        assertThat(lock.lockedQuantity()).isEqualTo(50L);
        assertThat(lock.lockType()).isEqualTo("TRADE");
    }

    @Test
    @DisplayName("Should prevent overlocking")
    void shouldPreventOverlocking() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        service.lockPosition(position, 80L, "TRADE");
        assertThatThrownBy(() -> service.lockPosition(position, 50L, "TRADE"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Should release position lock")
    void shouldReleasePositionLock() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        PositionLock lock = service.lockPosition(position, 50L, "TRADE");
        service.releaseLock(lock.lockId());
        assertThat(service.getAvailableQuantity(position)).isEqualTo(100L);
    }

    @Test
    @DisplayName("Should calculate available quantity")
    void shouldCalculateAvailableQuantity() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        service.lockPosition(position, 30L, "TRADE");
        long available = service.getAvailableQuantity(position);
        assertThat(available).isEqualTo(70L);
    }

    @Test
    @DisplayName("Should support multiple lock types")
    void shouldSupportMultipleLockTypes() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        service.lockPosition(position, 30L, "TRADE");
        service.lockPosition(position, 20L, "SETTLEMENT");
        assertThat(service.getAvailableQuantity(position)).isEqualTo(50L);
    }

    @Test
    @DisplayName("Should auto-expire locks")
    void shouldAutoExpireLocks() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        PositionLock lock = service.lockPosition(position, 50L, "TRADE", Instant.now().minusSeconds(3600));
        boolean expired = service.isLockExpired(lock);
        assertThat(expired).isTrue();
    }

    @Test
    @DisplayName("Should track lock history")
    void shouldTrackLockHistory() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        service.lockPosition(position, 50L, "TRADE");
        assertThat(service.getLockHistory(position.symbol())).hasSize(1);
    }

    @Test
    @DisplayName("Should handle lock upgrades")
    void shouldHandleLockUpgrades() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        PositionLock lock = service.lockPosition(position, 50L, "TRADE");
        PositionLock upgraded = service.upgradeLock(lock.lockId(), 70L);
        assertThat(upgraded.lockedQuantity()).isEqualTo(70L);
    }

    @Test
    @DisplayName("Should detect lock conflicts")
    void shouldDetectLockConflicts() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        service.lockPosition(position, 60L, "TRADE");
        boolean hasConflict = service.hasLockConflict(position, 50L);
        assertThat(hasConflict).isTrue();
    }

    @Test
    @DisplayName("Should generate lock report")
    void shouldGenerateLockReport() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        service.lockPosition(position, 50L, "TRADE");
        LockReport report = service.generateLockReport(position);
        assertThat(report.totalLocked()).isEqualTo(50L);
        assertThat(report.available()).isEqualTo(50L);
    }

    record Position(String symbol, long quantity, BigDecimal averagePrice) {}
    record PositionLock(String lockId, String symbol, long lockedQuantity, String lockType, Instant expiresAt) {}
    record LockReport(long totalLocked, long available, int activeLocks) {}

    static class LockingService {
        private final java.util.Map<String, java.util.List<PositionLock>> locks = new java.util.HashMap<>();
        private final java.util.List<PositionLock> history = new java.util.ArrayList<>();

        PositionLock lockPosition(Position position, long quantity, String lockType) {
            return lockPosition(position, quantity, lockType, Instant.now().plusSeconds(3600));
        }

        PositionLock lockPosition(Position position, long quantity, String lockType, Instant expiresAt) {
            long available = getAvailableQuantity(position);
            if (quantity > available) {
                throw new IllegalStateException("Insufficient available quantity");
            }
            PositionLock lock = new PositionLock(
                java.util.UUID.randomUUID().toString(),
                position.symbol(),
                quantity,
                lockType,
                expiresAt
            );
            locks.computeIfAbsent(position.symbol(), k -> new java.util.ArrayList<>()).add(lock);
            history.add(lock);
            return lock;
        }

        void releaseLock(String lockId) {
            locks.values().forEach(list -> list.removeIf(lock -> lock.lockId().equals(lockId)));
        }

        long getAvailableQuantity(Position position) {
            long totalLocked = locks.getOrDefault(position.symbol(), java.util.List.of())
                .stream()
                .filter(lock -> !isLockExpired(lock))
                .mapToLong(PositionLock::lockedQuantity)
                .sum();
            return position.quantity() - totalLocked;
        }

        boolean isLockExpired(PositionLock lock) {
            return Instant.now().isAfter(lock.expiresAt());
        }

        java.util.List<PositionLock> getLockHistory(String symbol) {
            return history.stream().filter(l -> l.symbol().equals(symbol)).toList();
        }

        PositionLock upgradeLock(String lockId, long newQuantity) {
            for (java.util.List<PositionLock> list : locks.values()) {
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).lockId().equals(lockId)) {
                        PositionLock old = list.get(i);
                        PositionLock upgraded = new PositionLock(
                            old.lockId(), old.symbol(), newQuantity, old.lockType(), old.expiresAt()
                        );
                        list.set(i, upgraded);
                        return upgraded;
                    }
                }
            }
            throw new IllegalArgumentException("Lock not found");
        }

        boolean hasLockConflict(Position position, long requestedQuantity) {
            return getAvailableQuantity(position) < requestedQuantity;
        }

        LockReport generateLockReport(Position position) {
            long totalLocked = locks.getOrDefault(position.symbol(), java.util.List.of())
                .stream()
                .filter(lock -> !isLockExpired(lock))
                .mapToLong(PositionLock::lockedQuantity)
                .sum();
            int activeLocks = (int) locks.getOrDefault(position.symbol(), java.util.List.of())
                .stream()
                .filter(lock -> !isLockExpired(lock))
                .count();
            return new LockReport(totalLocked, position.quantity() - totalLocked, activeLocks);
        }
    }
}
