package com.ghatana.appplatform.ems.service;

import io.activej.promise.Promise;

import java.math.BigDecimal;
import java.util.concurrent.Executor;
import javax.sql.DataSource;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Algorithm selection factory (D02-009).
 *              Selects the appropriate execution algorithm (VWAP, TWAP, IS, MARKET, LIMIT)
 *              based on order characteristics, K-02 clearing constraints, and SOR config.
 *              No business logic lives here — delegates to individual algorithm services.
 * @doc.layer   Domain — EMS routing
 * @doc.pattern Factory; Strategy selector; bridges order params to algorithm services
 */
public class AlgorithmFactoryService {

    public enum AlgoType { VWAP, TWAP, IMPLEMENTATION_SHORTFALL, MARKET, LIMIT }

    public record AlgoSelection(
        AlgoType algoType,
        String rationale,
        Object scheduleResult  // typed at call site
    ) {}

    private final VwapAlgorithmService vwapService;
    private final TwapAlgorithmService twapService;
    private final ImplementationShortfallService isService;
    private final SorConfigurationService sorConfigService;

    public AlgorithmFactoryService(VwapAlgorithmService vwapService,
                                   TwapAlgorithmService twapService,
                                   ImplementationShortfallService isService,
                                   SorConfigurationService sorConfigService) {
        this.vwapService    = vwapService;
        this.twapService    = twapService;
        this.isService      = isService;
        this.sorConfigService = sorConfigService;
    }

    /**
     * Select algorithm and build schedule for an order.
     * Decision tree:
     * - quantity > 10x avg daily volume  → IS (urgent=HIGH)
     * - quantity > 3x avg daily volume   → VWAP (participation 15%)
     * - quantity > 0.5x avg daily volume → TWAP (equal time slices)
     * - otherwise                        → MARKET (direct SOR routing)
     */
    public Promise<AlgoType> selectAlgorithm(String orderId, long quantity, long avgDailyVolume,
                                              ImplementationShortfallService.Urgency urgency,
                                              boolean limitOrder) {
        return Promise.ofBlocking(() -> {
            if (limitOrder) return AlgoType.LIMIT;
            if (avgDailyVolume > 0) {
                double participation = (double) quantity / avgDailyVolume;
                if (participation > 10.0) return AlgoType.IMPLEMENTATION_SHORTFALL;
                if (participation > 3.0)  return AlgoType.VWAP;
                if (participation > 0.5)  return AlgoType.TWAP;
            }
            return AlgoType.MARKET;
        });
    }

    /**
     * Route to VWAP algorithm and build its execution schedule.
     */
    public Promise<VwapAlgorithmService.VwapSchedule> routeToVwap(String parentOrderId,
                                                                    long totalQuantity,
                                                                    String instrumentId,
                                                                    Double participationRate) {
        return vwapService.buildSchedule(parentOrderId, totalQuantity, instrumentId, participationRate);
    }

    /**
     * Route to TWAP algorithm and build its execution schedule.
     */
    public Promise<TwapAlgorithmService.TwapSchedule> routeToTwap(String parentOrderId,
                                                                    long totalQuantity,
                                                                    java.time.Instant startAt,
                                                                    java.time.Instant endAt,
                                                                    int sliceCount) {
        return twapService.buildSchedule(parentOrderId, totalQuantity, startAt, endAt, sliceCount);
    }

    /**
     * Route to IS algorithm and build its execution schedule.
     */
    public Promise<ImplementationShortfallService.IsSchedule> routeToIs(
            String parentOrderId, long totalQuantity,
            ImplementationShortfallService.Urgency urgency,
            long dailyVolume, BigDecimal arrivalMidPrice) {
        return isService.buildSchedule(parentOrderId, totalQuantity, urgency, dailyVolume, arrivalMidPrice);
    }
}
