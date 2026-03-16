package com.ghatana.appplatform.compliance.service;

import com.ghatana.appplatform.compliance.domain.*;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Orchestrates the full pre-trade compliance rule pipeline (D07-001).
 *              Iterates through enabled rules in order; first FAIL stops pipeline.
 * @doc.layer   Application Service
 * @doc.pattern Hexagonal Architecture — Application Service, Chain of Responsibility
 */
public class ComplianceOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceOrchestrationService.class);

    private final LockInPeriodService lockInService;
    private final KycValidationService kycService;
    private final RestrictedListService restrictedListService;
    private final JurisdictionRuleRouterService jurisdictionRouter;
    private final Executor executor;
    private final Consumer<Object> eventPublisher;
    private final Counter checksPassedCounter;
    private final Counter checksFailedCounter;
    private final Timer pipelineTimer;

    public ComplianceOrchestrationService(LockInPeriodService lockInService,
                                           KycValidationService kycService,
                                           RestrictedListService restrictedListService,
                                           JurisdictionRuleRouterService jurisdictionRouter,
                                           Executor executor,
                                           Consumer<Object> eventPublisher,
                                           MeterRegistry meterRegistry) {
        this.lockInService = lockInService;
        this.kycService = kycService;
        this.restrictedListService = restrictedListService;
        this.jurisdictionRouter = jurisdictionRouter;
        this.executor = executor;
        this.eventPublisher = eventPublisher;
        this.checksPassedCounter = meterRegistry.counter("compliance.checks.passed");
        this.checksFailedCounter = meterRegistry.counter("compliance.checks.failed");
        this.pipelineTimer = meterRegistry.timer("compliance.pipeline.duration");
    }

    /**
     * Run all compliance checks for an order (D07-001 + D07-002).
     * Pipeline stops at first FAIL and returns immediately.
     */
    public Promise<ComplianceCheckResult> check(ComplianceCheckRequest request) {
        return Promise.ofBlocking(executor, () -> {
            var start = System.currentTimeMillis();
            var details = new ArrayList<ComplianceCheckResult.RuleEvaluationDetail>();
            var reasons = new ArrayList<String>();
            ComplianceStatus overallStatus = ComplianceStatus.PASS;

            // 1. KYC status check (D07-006)
            var kycDetail = kycService.evaluate(request);
            details.add(kycDetail);
            if (kycDetail.result() == ComplianceStatus.FAIL) {
                reasons.add(kycDetail.reason());
                overallStatus = ComplianceStatus.FAIL;
            }

            // 2. Lock-in period check (D07-004)
            if (overallStatus == ComplianceStatus.PASS && "SELL".equals(request.orderSide())) {
                var lockInDetail = lockInService.evaluate(request);
                details.add(lockInDetail);
                if (lockInDetail.result() == ComplianceStatus.FAIL) {
                    reasons.add(lockInDetail.reason());
                    overallStatus = ComplianceStatus.FAIL;
                }
            }

            // 3. Restricted list check (D07-011)
            if (overallStatus == ComplianceStatus.PASS) {
                var restrictedDetail = restrictedListService.evaluate(request);
                details.add(restrictedDetail);
                if (restrictedDetail.result() == ComplianceStatus.FAIL) {
                    reasons.add(restrictedDetail.reason());
                    overallStatus = ComplianceStatus.FAIL;
                } else if (restrictedDetail.result() == ComplianceStatus.REVIEW) {
                    reasons.add(restrictedDetail.reason());
                    overallStatus = ComplianceStatus.REVIEW;
                }
            }

            // 4. Jurisdiction-specific rules via K-03 OPA (D07-002)
            if (overallStatus == ComplianceStatus.PASS) {
                var jurisdictionDetail = jurisdictionRouter.evaluate(request);
                details.add(jurisdictionDetail);
                if (jurisdictionDetail.result() != ComplianceStatus.PASS) {
                    reasons.add(jurisdictionDetail.reason());
                    if (jurisdictionDetail.result() == ComplianceStatus.FAIL) {
                        overallStatus = ComplianceStatus.FAIL;
                    } else {
                        overallStatus = ComplianceStatus.REVIEW;
                    }
                }
            }

            long durationMs = System.currentTimeMillis() - start;
            pipelineTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);

            var result = new ComplianceCheckResult(
                    UUID.randomUUID().toString(),
                    request.orderId(),
                    overallStatus,
                    List.copyOf(details),
                    List.copyOf(reasons),
                    durationMs,
                    request.jurisdiction(),
                    Instant.now()
            );

            if (overallStatus == ComplianceStatus.PASS) {
                checksPassedCounter.increment();
            } else {
                checksFailedCounter.increment();
            }

            eventPublisher.accept(new ComplianceCheckCompletedEvent(result));
            log.info("Compliance check: orderId={} status={} duration={}ms rules={}",
                    request.orderId(), overallStatus, durationMs, details.size());
            return result;
        });
    }

    public record ComplianceCheckCompletedEvent(ComplianceCheckResult result) {}
}
