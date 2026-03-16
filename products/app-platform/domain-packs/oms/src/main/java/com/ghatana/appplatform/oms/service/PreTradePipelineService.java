package com.ghatana.appplatform.oms.service;

import com.ghatana.appplatform.oms.service.ComplianceCheckIntegrationService.ComplianceOutcome;
import com.ghatana.appplatform.oms.service.RiskCheckIntegrationService.RiskOutcome;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * @doc.type      Service
 * @doc.purpose   Configurable pre-trade pipeline orchestration. Executes compliance and
 *                risk checks in a configurable step order, with per-jurisdiction step
 *                enable/disable via K-02. All steps must PASS; first FAIL stops the pipeline.
 * @doc.layer     Application
 * @doc.pattern   Chain of Responsibility — configurable ordered steps
 *
 * Pipeline steps (configurable per jurisdiction):
 *   1. ComplianceCheck  (D01-008)
 *   2. RiskCheck        (D01-009)
 *   3. MakerCheckerCheck (threshold-based, see D01-011)
 *   4. CustomPluginCheck (T2 plugin — jurisdiction-specific)
 *
 * Story: D01-010
 */
public class PreTradePipelineService {

    private static final Logger log = LoggerFactory.getLogger(PreTradePipelineService.class);

    private final ComplianceCheckIntegrationService complianceService;
    private final RiskCheckIntegrationService       riskService;
    private final PipelineConfig                    defaultConfig;
    private final JurisdictionConfigPort            jurisdictionConfig;
    private final Consumer<Object>                  eventPublisher;
    private final Counter pipelinePassed;
    private final Counter pipelineFailed;

    public PreTradePipelineService(ComplianceCheckIntegrationService complianceService,
                                   RiskCheckIntegrationService riskService,
                                   PipelineConfig defaultConfig,
                                   JurisdictionConfigPort jurisdictionConfig,
                                   Consumer<Object> eventPublisher,
                                   MeterRegistry meterRegistry) {
        this.complianceService  = complianceService;
        this.riskService        = riskService;
        this.defaultConfig      = defaultConfig;
        this.jurisdictionConfig = jurisdictionConfig;
        this.eventPublisher     = eventPublisher;
        this.pipelinePassed     = meterRegistry.counter("oms.pipeline.passed");
        this.pipelineFailed     = meterRegistry.counter("oms.pipeline.failed");
    }

    /**
     * Executes the pre-trade pipeline for an order request.
     *
     * @return pipeline result indicating PASS, FAIL, or REVIEW with reasons
     */
    public PipelineResult execute(String orderId, String clientId, String accountId,
                                   String instrumentId, String jurisdiction, String marginType,
                                   String side, long quantity, BigDecimal price, BigDecimal orderValue) {

        PipelineConfig config = jurisdictionConfig.getConfig(jurisdiction)
                .orElse(defaultConfig);

        List<String> reasons = new ArrayList<>();

        // Step 1: Compliance check
        if (config.complianceEnabled()) {
            ComplianceOutcome comp = complianceService.check(orderId, clientId, instrumentId, side, quantity);
            if (comp.isFailed()) {
                pipelineFailed.increment();
                return PipelineResult.fail("COMPLIANCE", comp.reasons());
            }
            if (comp.isReview()) {
                return PipelineResult.review("COMPLIANCE", comp.reasons());
            }
        }

        // Step 2: Risk check
        if (config.riskEnabled()) {
            RiskOutcome risk = riskService.check(orderId, clientId, accountId, instrumentId,
                    marginType, side, quantity, price, orderValue);
            if (!risk.approved()) {
                pipelineFailed.increment();
                return PipelineResult.fail("RISK", List.of(risk.reason()));
            }
        }

        // Step 3: Maker-checker threshold check
        if (config.makerCheckerEnabled() && exceedsThreshold(orderValue, quantity, config)) {
            return PipelineResult.review("MAKER_CHECKER",
                    List.of("Order value or quantity exceeds approval threshold"));
        }

        // Step 4: T2 custom plugin check
        if (config.customPluginEnabled() && config.customPluginPort() != null) {
            PluginCheckResult pluginResult = config.customPluginPort().check(
                    orderId, clientId, instrumentId, side, quantity, price);
            if (!pluginResult.pass()) {
                pipelineFailed.increment();
                return PipelineResult.fail("CUSTOM_PLUGIN", List.of(pluginResult.reason()));
            }
        }

        pipelinePassed.increment();
        log.debug("Pre-trade pipeline PASSED orderId={}", orderId);
        return PipelineResult.pass();
    }

    private boolean exceedsThreshold(BigDecimal orderValue, long quantity, PipelineConfig config) {
        if (config.makerCheckerValueThreshold() != null
                && orderValue.compareTo(config.makerCheckerValueThreshold()) > 0) {
            return true;
        }
        if (config.makerCheckerQuantityThreshold() != null
                && quantity > config.makerCheckerQuantityThreshold()) {
            return true;
        }
        return false;
    }

    // ─── Ports & Config ───────────────────────────────────────────────────────

    public interface JurisdictionConfigPort {
        java.util.Optional<PipelineConfig> getConfig(String jurisdiction);
    }

    public interface PluginCheckPort {
        PluginCheckResult check(String orderId, String clientId, String instrumentId,
                                String side, long quantity, BigDecimal price);
    }

    public record PluginCheckResult(boolean pass, String reason) {}

    public record PipelineConfig(
            boolean complianceEnabled,
            boolean riskEnabled,
            boolean makerCheckerEnabled,
            boolean customPluginEnabled,
            BigDecimal makerCheckerValueThreshold,
            Long makerCheckerQuantityThreshold,
            PluginCheckPort customPluginPort
    ) {
        public static PipelineConfig defaultConfig() {
            return new PipelineConfig(true, true, true, false,
                    new BigDecimal("5000000"), 100_000L, null);
        }
    }

    // ─── Result ───────────────────────────────────────────────────────────────

    public enum PipelineDecision { PASS, FAIL, REVIEW }

    public record PipelineResult(PipelineDecision decision, String failedStep, List<String> reasons) {
        public static PipelineResult pass()                              { return new PipelineResult(PipelineDecision.PASS, null, List.of()); }
        public static PipelineResult fail(String step, List<String> r)  { return new PipelineResult(PipelineDecision.FAIL, step, r); }
        public static PipelineResult review(String step, List<String> r){ return new PipelineResult(PipelineDecision.REVIEW, step, r); }
        public boolean isPassed() { return decision == PipelineDecision.PASS; }
        public boolean isFailed() { return decision == PipelineDecision.FAIL; }
    }
}
