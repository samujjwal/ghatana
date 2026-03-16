package com.ghatana.appplatform.risk.service;

import com.ghatana.appplatform.risk.domain.*;
import com.ghatana.appplatform.risk.domain.RiskCheckResult.RiskStatus;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Enforces portfolio concentration limits (D06-003).
 *              Default rule: no single instrument may represent more than 10% of total portfolio value
 *              after the order is filled. Configurable per-client via K-02.
 * @doc.layer   Application Service
 * @doc.pattern Hexagonal Architecture — application service
 */
public class ConcentrationLimitService {

    private static final Logger log = LoggerFactory.getLogger(ConcentrationLimitService.class);

    /** Default max concentration: 10% of portfolio in any single instrument. */
    private static final BigDecimal DEFAULT_MAX_CONCENTRATION = new BigDecimal("0.10");

    private final PortfolioValuePort portfolioValuePort;
    private final Executor executor;

    public ConcentrationLimitService(PortfolioValuePort portfolioValuePort, Executor executor) {
        this.portfolioValuePort = portfolioValuePort;
        this.executor = executor;
    }

    /**
     * Check portfolio concentration after hypothetically adding the order (D06-003).
     *
     * @param request     The pending order to evaluate.
     * @param currentPrice Current market price of the instrument.
     */
    public Promise<RiskCheckResult> check(RiskCheckRequest request, BigDecimal currentPrice) {
        return Promise.ofBlocking(executor, () -> {
            BigDecimal totalPortfolioValue = portfolioValuePort.getTotalPortfolioValue(
                    request.clientId(), request.accountId());

            if (totalPortfolioValue.compareTo(BigDecimal.ZERO) == 0) {
                // Can't compute concentration — approve (first trade)
                return new RiskCheckResult(RiskStatus.APPROVE, null,
                        BigDecimal.ZERO, BigDecimal.ZERO, 0.0);
            }

            BigDecimal currentInstrumentValue = portfolioValuePort.getInstrumentValue(
                    request.clientId(), request.accountId(), request.instrumentId());

            BigDecimal addedValue = currentPrice
                    .multiply(BigDecimal.valueOf(request.quantity()))
                    .setScale(2, RoundingMode.HALF_EVEN);

            BigDecimal projectedInstrumentValue = currentInstrumentValue.add(addedValue);
            BigDecimal projectedTotalValue = totalPortfolioValue.add(addedValue);

            BigDecimal maxAllowed = portfolioValuePort.getMaxConcentration(
                    request.clientId(), request.instrumentId())
                    .orElse(DEFAULT_MAX_CONCENTRATION);

            BigDecimal concentration = projectedInstrumentValue
                    .divide(projectedTotalValue, 6, RoundingMode.HALF_EVEN);

            if (concentration.compareTo(maxAllowed) > 0) {
                log.warn("Concentration limit breach: clientId={} instrument={} projected={}% max={}%",
                        request.clientId(), request.instrumentId(),
                        concentration.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_EVEN),
                        maxAllowed.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_EVEN));
                return new RiskCheckResult(RiskStatus.DENY,
                        "Portfolio concentration limit breach: projected="
                                + concentration.multiply(BigDecimal.valueOf(100))
                                        .setScale(2, RoundingMode.HALF_EVEN) + "% max="
                                + maxAllowed.multiply(BigDecimal.valueOf(100))
                                        .setScale(2, RoundingMode.HALF_EVEN) + "%",
                        BigDecimal.ZERO, BigDecimal.ZERO, concentration.doubleValue());
            }

            return new RiskCheckResult(RiskStatus.APPROVE, null,
                    BigDecimal.ZERO, BigDecimal.ZERO, concentration.doubleValue());
        });
    }

    // ─── Port ────────────────────────────────────────────────────────────────

    /** Port to retrieve portfolio value data for concentration calculations. */
    public interface PortfolioValuePort {
        BigDecimal getTotalPortfolioValue(String clientId, String accountId);
        BigDecimal getInstrumentValue(String clientId, String accountId, String instrumentId);
        java.util.Optional<BigDecimal> getMaxConcentration(String clientId, String instrumentId);
    }
}
