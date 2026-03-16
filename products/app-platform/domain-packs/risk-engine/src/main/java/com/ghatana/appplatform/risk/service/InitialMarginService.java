package com.ghatana.appplatform.risk.service;

import com.ghatana.appplatform.risk.domain.*;
import com.ghatana.appplatform.risk.domain.RiskCheckResult.RiskStatus;
import com.ghatana.appplatform.risk.port.MarginStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Aggregates required initial margin across all open positions (D06-009).
 *              A margin call is triggered when utilization exceeds {@value #MARGIN_CALL_THRESHOLD}.
 *              Called periodically (e.g., daily EOD) and on large market moves.
 * @doc.layer   Application Service
 * @doc.pattern Hexagonal Architecture — application service
 */
public class InitialMarginService {

    private static final Logger log = LoggerFactory.getLogger(InitialMarginService.class);
    /** Trigger a margin call when used / deposited ≥ 80%. */
    private static final double MARGIN_CALL_THRESHOLD = 0.80;

    private final MarginStore marginStore;
    private final OpenPositionsPort openPositionsPort;
    private final Executor executor;
    private final Consumer<Object> eventPublisher;

    public InitialMarginService(MarginStore marginStore,
                                 OpenPositionsPort openPositionsPort,
                                 Executor executor,
                                 Consumer<Object> eventPublisher) {
        this.marginStore = marginStore;
        this.openPositionsPort = openPositionsPort;
        this.executor = executor;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Recalculate total initial margin across all open positions for a client (D06-009).
     * Compares against deposited collateral and emits a {@link MarginCallEvent} if needed.
     */
    public Promise<MarginUtilizationSummary> calculateAndCheck(String clientId, String accountId) {
        return Promise.ofBlocking(executor, () -> {
            var positions = openPositionsPort.findOpenPositions(clientId, accountId);

            BigDecimal totalRequired = positions.stream()
                    .map(p -> p.marketValue()
                            .multiply(p.marginRate())
                            .setScale(2, RoundingMode.HALF_EVEN))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            var marginOpt = marginStore.find(clientId, accountId);
            if (marginOpt.isEmpty()) {
                log.warn("No margin account during initial margin check: clientId={}", clientId);
                return new MarginUtilizationSummary(clientId, accountId,
                        BigDecimal.ZERO, totalRequired, 1.0, true);
            }

            var margin = marginOpt.get();
            double utilization = totalRequired.compareTo(BigDecimal.ZERO) == 0
                    ? 0.0
                    : totalRequired.divide(margin.deposited(), 6, RoundingMode.HALF_EVEN)
                            .doubleValue();

            boolean marginCallTriggered = utilization >= MARGIN_CALL_THRESHOLD;
            if (marginCallTriggered) {
                log.warn("Margin call triggered: clientId={} utilization={}", clientId, utilization);
                eventPublisher.accept(new MarginCallEvent(clientId, accountId,
                        margin.deposited(), totalRequired, utilization));
            }

            return new MarginUtilizationSummary(clientId, accountId,
                    margin.deposited(), totalRequired, utilization, marginCallTriggered);
        });
    }

    // ─── Port ────────────────────────────────────────────────────────────────

    /** Port to load current open positions with market values for initial margin calculation. */
    public interface OpenPositionsPort {
        List<PositionSnapshot> findOpenPositions(String clientId, String accountId);

        record PositionSnapshot(String instrumentId, long quantity,
                                BigDecimal marketValue, BigDecimal marginRate) {}
    }

    // ─── Supporting Types ─────────────────────────────────────────────────────

    public record MarginUtilizationSummary(String clientId, String accountId,
                                            BigDecimal deposited, BigDecimal required,
                                            double utilization, boolean marginCallTriggered) {}

    public record MarginCallEvent(String clientId, String accountId,
                                   BigDecimal deposited, BigDecimal required, double utilization) {}
}
