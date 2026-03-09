/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.di;

import com.ghatana.aep.analytics.AdvancedTimeSeriesForecaster;
import com.ghatana.aep.analytics.AnalyticsEngine;
import com.ghatana.aep.analytics.BusinessIntelligenceService;
import com.ghatana.aep.analytics.DefaultAdvancedTimeSeriesForecaster;
import com.ghatana.aep.analytics.DefaultBusinessIntelligenceService;
import com.ghatana.aep.analytics.DefaultIntelligentPredictiveAlerting;
import com.ghatana.aep.analytics.DefaultKPIAggregator;
import com.ghatana.aep.analytics.DefaultPatternPerformanceAnalyzer;
import com.ghatana.aep.analytics.DefaultPredictiveAnalyticsEngine;
import com.ghatana.aep.analytics.DefaultRealTimeAnomalyDetectionEngine;
import com.ghatana.aep.analytics.IntelligentPredictiveAlerting;
import com.ghatana.aep.analytics.KPIAggregator;
import com.ghatana.aep.analytics.PatternPerformanceAnalyzer;
import com.ghatana.aep.analytics.PredictiveAnalyticsEngine;
import com.ghatana.aep.analytics.RealTimeAnomalyDetectionEngine;
import com.ghatana.aep.event.EventCloud;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.ObservabilityModule;
import io.activej.eventloop.Eventloop;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * ActiveJ DI module for AEP observability and analytics.
 *
 * <p>Extends the platform {@link ObservabilityModule} with AEP-specific
 * analytics capabilities. Provides all analytics sub-services and the
 * composite {@link AnalyticsEngine} facade:
 * <ul>
 *   <li>{@link AnalyticsEngine} — unified analytics API (anomaly detection, predictions, KPIs)</li>
 *   <li>{@link BusinessIntelligenceService} — BI dashboards and reports</li>
 *   <li>{@link PredictiveAnalyticsEngine} — ML-based predictions</li>
 *   <li>{@link PatternPerformanceAnalyzer} — pattern match performance</li>
 *   <li>{@link KPIAggregator} — KPI computation and aggregation</li>
 *   <li>{@link RealTimeAnomalyDetectionEngine} — streaming anomaly detection</li>
 *   <li>{@link IntelligentPredictiveAlerting} — ML-driven alerting</li>
 *   <li>{@link AdvancedTimeSeriesForecaster} — time series forecasting</li>
 * </ul>
 *
 * <p>The platform {@link ObservabilityModule} must be included in the injector
 * to provide {@link MeterRegistry}, {@link MetricsCollector}, and tracing bindings.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * Injector injector = Injector.of(
 *     new ObservabilityModule(),  // platform layer
 *     new AepCoreModule(),
 *     new AepObservabilityModule()
 * );
 * AnalyticsEngine analytics = injector.getInstance(AnalyticsEngine.class);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose ActiveJ DI module for AEP analytics and observability
 * @doc.layer product
 * @doc.pattern Module, Facade
 * @see ObservabilityModule
 * @see AnalyticsEngine
 */
public class AepObservabilityModule extends AbstractModule {

    // ═══════════════════════════════════════════════════════════════
    //  Analytics Sub-Services (Default Implementations)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Provides the business intelligence service.
     *
     * @return default BI service
     */
    @Provides
    BusinessIntelligenceService businessIntelligenceService() {
        return new DefaultBusinessIntelligenceService();
    }

    /**
     * Provides the predictive analytics engine.
     *
     * @return default predictive engine
     */
    @Provides
    PredictiveAnalyticsEngine predictiveAnalyticsEngine() {
        return new DefaultPredictiveAnalyticsEngine();
    }

    /**
     * Provides the pattern performance analyzer.
     *
     * @return default pattern analyzer
     */
    @Provides
    PatternPerformanceAnalyzer patternPerformanceAnalyzer() {
        return new DefaultPatternPerformanceAnalyzer();
    }

    /**
     * Provides the KPI aggregator.
     *
     * @return default KPI aggregator
     */
    @Provides
    KPIAggregator kpiAggregator() {
        return new DefaultKPIAggregator();
    }

    /**
     * Provides the real-time anomaly detection engine.
     *
     * @return default anomaly detection engine
     */
    @Provides
    RealTimeAnomalyDetectionEngine realTimeAnomalyDetectionEngine() {
        return new DefaultRealTimeAnomalyDetectionEngine();
    }

    /**
     * Provides the intelligent predictive alerting service.
     *
     * @return default alerting service
     */
    @Provides
    IntelligentPredictiveAlerting intelligentPredictiveAlerting() {
        return new DefaultIntelligentPredictiveAlerting();
    }

    /**
     * Provides the advanced time series forecaster.
     *
     * @return default forecaster
     */
    @Provides
    AdvancedTimeSeriesForecaster advancedTimeSeriesForecaster() {
        return new DefaultAdvancedTimeSeriesForecaster();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Composite Analytics Engine
    // ═══════════════════════════════════════════════════════════════

    /**
     * Provides the composite analytics engine.
     *
     * <p>Assembles the {@link AnalyticsEngine} facade using its builder pattern,
     * injecting all analytics sub-services. The engine provides a unified API
     * for anomaly detection, predictions, KPIs, BI, and time series forecasting.
     *
     * @param eventCloud       platform event cloud for event access
     * @param eventloop        ActiveJ event loop
     * @param metricsCollector platform metrics collector
     * @param biService        business intelligence service
     * @param predictiveEngine predictive analytics engine
     * @param patternAnalyzer  pattern performance analyzer
     * @param kpiAggregator    KPI aggregation service
     * @param anomalyEngine    real-time anomaly detection
     * @param alerting         intelligent predictive alerting
     * @param forecaster       time series forecaster
     * @return fully wired analytics engine
     */
    @Provides
    AnalyticsEngine analyticsEngine(
            EventCloud eventCloud,
            Eventloop eventloop,
            MetricsCollector metricsCollector,
            BusinessIntelligenceService biService,
            PredictiveAnalyticsEngine predictiveEngine,
            PatternPerformanceAnalyzer patternAnalyzer,
            KPIAggregator kpiAggregator,
            RealTimeAnomalyDetectionEngine anomalyEngine,
            IntelligentPredictiveAlerting alerting,
            AdvancedTimeSeriesForecaster forecaster) {
        return AnalyticsEngine.builder()
                .withEventCloud(eventCloud)
                .withEventloop(eventloop)
                .withMetricsCollector(metricsCollector)
                .withBusinessIntelligenceService(biService)
                .withPredictiveAnalyticsEngine(predictiveEngine)
                .withPatternPerformanceAnalyzer(patternAnalyzer)
                .withKPIAggregator(kpiAggregator)
                .withAnomalyDetectionEngine(anomalyEngine)
                .withPredictiveAlerting(alerting)
                .withTimeSeriesForecaster(forecaster)
                .build();
    }
}
