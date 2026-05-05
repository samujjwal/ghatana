package com.ghatana.digitalmarketing.api.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * P2: API-level metrics for DMOS observability.
 *
 * <p>Tracks key metrics for API health and performance:
 * <ul>
 *   <li>Request counts by endpoint, method, status</li>
 *   <li>Request latency (p50, p95, p99)</li>
 *   <li>Error rates by error type</li>
 *   <li>Active connections</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose API metrics collection for DMOS observability (P2-OBS-002)
 * @doc.layer product
 * @doc.pattern Metrics, Observability
 */
public final class DmosApiMetrics {

    private static final Logger LOG = LoggerFactory.getLogger(DmosApiMetrics.class);

    private final MeterRegistry meterRegistry;

    // Request counters
    private final Counter campaignCreateCounter;
    private final Counter campaignListCounter;
    private final Counter campaignGetCounter;
    private final Counter campaignLaunchCounter;
    private final Counter campaignPauseCounter;

    // Error counters
    private final Counter authErrorCounter;
    private final Counter validationErrorCounter;
    private final Counter internalErrorCounter;

    // Latency timers
    private final Timer campaignCreateTimer;
    private final Timer campaignListTimer;
    private final Timer campaignGetTimer;
    private final Timer campaignLaunchTimer;
    private final Timer campaignPauseTimer;

    public DmosApiMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");

        // Initialize counters
        this.campaignCreateCounter = Counter.builder("dmos.campaign.create")
            .description("Number of campaign creation requests")
            .register(meterRegistry);

        this.campaignListCounter = Counter.builder("dmos.campaign.list")
            .description("Number of campaign list requests")
            .register(meterRegistry);

        this.campaignGetCounter = Counter.builder("dmos.campaign.get")
            .description("Number of campaign get requests")
            .register(meterRegistry);

        this.campaignLaunchCounter = Counter.builder("dmos.campaign.launch")
            .description("Number of campaign launch requests")
            .register(meterRegistry);

        this.campaignPauseCounter = Counter.builder("dmos.campaign.pause")
            .description("Number of campaign pause requests")
            .register(meterRegistry);

        // Initialize error counters
        this.authErrorCounter = Counter.builder("dmos.errors")
            .tag("type", "authentication")
            .description("Number of authentication errors")
            .register(meterRegistry);

        this.validationErrorCounter = Counter.builder("dmos.errors")
            .tag("type", "validation")
            .description("Number of validation errors")
            .register(meterRegistry);

        this.internalErrorCounter = Counter.builder("dmos.errors")
            .tag("type", "internal")
            .description("Number of internal errors")
            .register(meterRegistry);

        // Initialize timers
        this.campaignCreateTimer = Timer.builder("dmos.campaign.create.latency")
            .description("Campaign creation latency")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);

        this.campaignListTimer = Timer.builder("dmos.campaign.list.latency")
            .description("Campaign list latency")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);

        this.campaignGetTimer = Timer.builder("dmos.campaign.get.latency")
            .description("Campaign get latency")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);

        this.campaignLaunchTimer = Timer.builder("dmos.campaign.launch.latency")
            .description("Campaign launch latency")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);

        this.campaignPauseTimer = Timer.builder("dmos.campaign.pause.latency")
            .description("Campaign pause latency")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
    }

    /**
     * Records a campaign creation request.
     */
    public void recordCampaignCreate() {
        campaignCreateCounter.increment();
    }

    /**
     * Records a campaign list request.
     */
    public void recordCampaignList() {
        campaignListCounter.increment();
    }

    /**
     * Records a campaign get request.
     */
    public void recordCampaignGet() {
        campaignGetCounter.increment();
    }

    /**
     * Records a campaign launch request.
     */
    public void recordCampaignLaunch() {
        campaignLaunchCounter.increment();
    }

    /**
     * Records a campaign pause request.
     */
    public void recordCampaignPause() {
        campaignPauseCounter.increment();
    }

    /**
     * Records request latency.
     */
    public void recordCreateLatency(long durationMs) {
        campaignCreateTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordListLatency(long durationMs) {
        campaignListTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordGetLatency(long durationMs) {
        campaignGetTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordLaunchLatency(long durationMs) {
        campaignLaunchTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordPauseLatency(long durationMs) {
        campaignPauseTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Records an authentication/authorization error.
     */
    public void recordAuthError() {
        authErrorCounter.increment();
    }

    /**
     * Records a validation error (400 Bad Request).
     */
    public void recordValidationError() {
        validationErrorCounter.increment();
    }

    /**
     * Records an internal server error (500).
     */
    public void recordInternalError() {
        internalErrorCounter.increment();
    }

    /**
     * Wraps a runnable with timer recording.
     */
    public void timeCreate(Runnable operation) {
        campaignCreateTimer.record(operation);
    }

    public void timeList(Runnable operation) {
        campaignListTimer.record(operation);
    }
}
