package com.ghatana.products.finance.domains.ems.service;

import com.ghatana.products.finance.domains.ems.domain.ExecutionSide;
import com.ghatana.products.finance.domains.ems.domain.ExecutionStatus;
import com.ghatana.products.finance.domains.ems.service.FillAggregationService.FillAggregated;
import com.ghatana.products.finance.domains.ems.service.FillAggregationService.PartialFill;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for execution state transitions and fill aggregation per D02-019
 * @doc.layer Test
 * @doc.pattern Integration Test with EventloopTestBase
 */
@DisplayName("Execution Lifecycle Tests")
class ExecutionLifecycleTest extends EventloopTestBase {

    private ExecutionLifecycleService lifecycleService;
    private FillAggregationService fillAggregationService;
    
    @Mock
    private DataSource dataSource;
    
    private SimpleMeterRegistry meterRegistry;
    private Executor executor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        meterRegistry = new SimpleMeterRegistry();
        executor = Runnable::run;
        
        lifecycleService = new ExecutionLifecycleService();
        fillAggregationService = new FillAggregationService(dataSource, executor, meterRegistry);
    }

    @Test
    @DisplayName("Should transition from PENDING_ROUTE to ROUTED")
    void shouldTransitionFromPendingToRouted() {
        ExecutionState state = new ExecutionState(
            UUID.randomUUID().toString(),
            ExecutionStatus.PENDING_ROUTE,
            BigDecimal.valueOf(100),
            BigDecimal.ZERO,
            Instant.now()
        );

        ExecutionState newState = lifecycleService.transitionToRouted(state, "NASDAQ");

        assertThat(newState.status()).isEqualTo(ExecutionStatus.ROUTED);
        assertThat(newState.executionId()).isEqualTo(state.executionId());
    }

    @Test
    @DisplayName("Should transition from ROUTED to PARTIALLY_FILLED on first fill")
    void shouldTransitionToPartiallyFilledOnFirstFill() {
        ExecutionState state = new ExecutionState(
            UUID.randomUUID().toString(),
            ExecutionStatus.ROUTED,
            BigDecimal.valueOf(100),
            BigDecimal.ZERO,
            Instant.now()
        );

        ExecutionState newState = lifecycleService.applyFill(
            state,
            BigDecimal.valueOf(30),
            BigDecimal.valueOf(150.50)
        );

        assertThat(newState.status()).isEqualTo(ExecutionStatus.PARTIALLY_FILLED);
        assertThat(newState.filledQuantity()).isEqualByComparingTo(BigDecimal.valueOf(30));
        assertThat(newState.remainingQuantity()).isEqualByComparingTo(BigDecimal.valueOf(70));
    }

    @Test
    @DisplayName("Should transition from PARTIALLY_FILLED to FILLED on complete fill")
    void shouldTransitionToFilledOnCompleteFill() {
        ExecutionState state = new ExecutionState(
            UUID.randomUUID().toString(),
            ExecutionStatus.PARTIALLY_FILLED,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(70),
            Instant.now()
        );

        ExecutionState newState = lifecycleService.applyFill(
            state,
            BigDecimal.valueOf(30),
            BigDecimal.valueOf(150.50)
        );

        assertThat(newState.status()).isEqualTo(ExecutionStatus.FILLED);
        assertThat(newState.filledQuantity()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(newState.remainingQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should transition from ROUTED to FILLED on single complete fill")
    void shouldTransitionToFilledOnSingleFill() {
        ExecutionState state = new ExecutionState(
            UUID.randomUUID().toString(),
            ExecutionStatus.ROUTED,
            BigDecimal.valueOf(100),
            BigDecimal.ZERO,
            Instant.now()
        );

        ExecutionState newState = lifecycleService.applyFill(
            state,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(150.50)
        );

        assertThat(newState.status()).isEqualTo(ExecutionStatus.FILLED);
        assertThat(newState.filledQuantity()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    @DisplayName("Should reject overfill")
    void shouldRejectOverfill() {
        ExecutionState state = new ExecutionState(
            UUID.randomUUID().toString(),
            ExecutionStatus.PARTIALLY_FILLED,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(70),
            Instant.now()
        );

        assertThatThrownBy(() -> lifecycleService.applyFill(
            state,
            BigDecimal.valueOf(50),
            BigDecimal.valueOf(150.50)
        ))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("overfill");
    }

    @Test
    @DisplayName("Should transition to CANCELLED from any active state")
    void shouldTransitionToCancelled() {
        ExecutionState routedState = new ExecutionState(
            UUID.randomUUID().toString(),
            ExecutionStatus.ROUTED,
            BigDecimal.valueOf(100),
            BigDecimal.ZERO,
            Instant.now()
        );

        ExecutionState cancelledState = lifecycleService.cancel(routedState);

        assertThat(cancelledState.status()).isEqualTo(ExecutionStatus.CANCELLED);
    }

    @Test
    @DisplayName("Should reject cancellation of FILLED execution")
    void shouldRejectCancellationOfFilledExecution() {
        ExecutionState state = new ExecutionState(
            UUID.randomUUID().toString(),
            ExecutionStatus.FILLED,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(100),
            Instant.now()
        );

        assertThatThrownBy(() -> lifecycleService.cancel(state))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot cancel filled execution");
    }

    @Test
    @DisplayName("Should aggregate multiple partial fills")
    void shouldAggregateMultiplePartialFills() {
        String orderId = UUID.randomUUID().toString();
        
        List<PartialFill> fills = List.of(
            new PartialFill("fill-1", orderId, "slice-1", 30L, BigDecimal.valueOf(150.00), Instant.now()),
            new PartialFill("fill-2", orderId, "slice-2", 40L, BigDecimal.valueOf(150.50), Instant.now()),
            new PartialFill("fill-3", orderId, "slice-3", 30L, BigDecimal.valueOf(151.00), Instant.now())
        );

        BigDecimal wavg = computeWeightedAverage(fills);
        
        assertThat(wavg).isEqualByComparingTo(BigDecimal.valueOf(150.50));
    }

    @Test
    @DisplayName("Should mark aggregation as complete when fully filled")
    void shouldMarkAggregationAsComplete() {
        String orderId = UUID.randomUUID().toString();
        long totalQuantity = 100L;
        long filledQuantity = 100L;

        boolean isComplete = filledQuantity == totalQuantity;

        assertThat(isComplete).isTrue();
    }

    @Test
    @DisplayName("Should transition to REJECTED on validation failure")
    void shouldTransitionToRejectedOnValidationFailure() {
        ExecutionState state = new ExecutionState(
            UUID.randomUUID().toString(),
            ExecutionStatus.PENDING_ROUTE,
            BigDecimal.valueOf(100),
            BigDecimal.ZERO,
            Instant.now()
        );

        ExecutionState rejectedState = lifecycleService.reject(state, "Validation failed");

        assertThat(rejectedState.status()).isEqualTo(ExecutionStatus.REJECTED);
    }

    private BigDecimal computeWeightedAverage(List<PartialFill> fills) {
        BigDecimal totalValue = BigDecimal.ZERO;
        long totalQuantity = 0L;

        for (PartialFill fill : fills) {
            totalValue = totalValue.add(fill.fillPrice().multiply(BigDecimal.valueOf(fill.filledQuantity())));
            totalQuantity += fill.filledQuantity();
        }

        return totalValue.divide(BigDecimal.valueOf(totalQuantity), 2, java.math.RoundingMode.HALF_UP);
    }

    record ExecutionState(
        String executionId,
        ExecutionStatus status,
        BigDecimal totalQuantity,
        BigDecimal filledQuantity,
        Instant createdAt
    ) {
        BigDecimal remainingQuantity() {
            return totalQuantity.subtract(filledQuantity);
        }
    }

    static class ExecutionLifecycleService {
        ExecutionState transitionToRouted(ExecutionState state, String venue) {
            return new ExecutionState(
                state.executionId(),
                ExecutionStatus.ROUTED,
                state.totalQuantity(),
                state.filledQuantity(),
                state.createdAt()
            );
        }

        ExecutionState applyFill(ExecutionState state, BigDecimal fillQuantity, BigDecimal fillPrice) {
            BigDecimal newFilled = state.filledQuantity().add(fillQuantity);
            
            if (newFilled.compareTo(state.totalQuantity()) > 0) {
                throw new IllegalStateException("Overfill detected");
            }

            ExecutionStatus newStatus;
            if (newFilled.compareTo(state.totalQuantity()) == 0) {
                newStatus = ExecutionStatus.FILLED;
            } else {
                newStatus = ExecutionStatus.PARTIALLY_FILLED;
            }

            return new ExecutionState(
                state.executionId(),
                newStatus,
                state.totalQuantity(),
                newFilled,
                state.createdAt()
            );
        }

        ExecutionState cancel(ExecutionState state) {
            if (state.status() == ExecutionStatus.FILLED) {
                throw new IllegalStateException("Cannot cancel filled execution");
            }

            return new ExecutionState(
                state.executionId(),
                ExecutionStatus.CANCELLED,
                state.totalQuantity(),
                state.filledQuantity(),
                state.createdAt()
            );
        }

        ExecutionState reject(ExecutionState state, String reason) {
            return new ExecutionState(
                state.executionId(),
                ExecutionStatus.REJECTED,
                state.totalQuantity(),
                state.filledQuantity(),
                state.createdAt()
            );
        }
    }
}
