/**
 * Simulation Analytics Events
 *
 * @doc.type module
 * @doc.purpose Analytics event tracking for simulation parameter changes and interactions
 * @doc.layer product
 * @doc.pattern Service
 */

// =============================================================================
// Types
// =============================================================================

export type SimulationDomain =
    | "CS_DISCRETE"
    | "PHYSICS"
    | "CHEMISTRY"
    | "BIOLOGY"
    | "MEDICINE"
    | "ECONOMICS";

export interface AnalyticsContext {
    tenantId: string;
    userId: string;
    sessionId: string;
    simulationId?: string;
    manifestId?: string;
    domain?: SimulationDomain;
}

export interface BaseAnalyticsEvent {
    eventType: string;
    timestamp: number;
    context: AnalyticsContext;
}

// =============================================================================
// Parameter Change Events
// =============================================================================

export interface ParameterChangeEvent extends BaseAnalyticsEvent {
    eventType: "parameter_change";
    payload: {
        parameterId: string;
        parameterName: string;
        parameterType: "numeric" | "categorical" | "boolean" | "range";
        previousValue: unknown;
        newValue: unknown;
        unit?: string;
        source: "slider" | "input" | "preset" | "reset" | "ai_suggestion";
        domain: SimulationDomain;
        stepIndex?: number;
        entityId?: string;
    };
}

export interface ParameterRangeExceededEvent extends BaseAnalyticsEvent {
    eventType: "parameter_range_exceeded";
    payload: {
        parameterId: string;
        parameterName: string;
        attemptedValue: number;
        allowedMin: number;
        allowedMax: number;
        domain: SimulationDomain;
    };
}

export interface ParameterPresetAppliedEvent extends BaseAnalyticsEvent {
    eventType: "parameter_preset_applied";
    payload: {
        presetId: string;
        presetName: string;
        parameterCount: number;
        domain: SimulationDomain;
    };
}

// =============================================================================
// Economics-Specific Events
// =============================================================================

export interface EconParameterChangeEvent extends BaseAnalyticsEvent {
    eventType: "econ_parameter_change";
    payload: {
        parameterCategory: EconParameterCategory;
        parameterId: string;
        parameterName: string;
        previousValue: number;
        newValue: number;
        unit: string;
        percentChange: number;
        source: "slider" | "input" | "scenario" | "reset";
        scenarioName?: string;
        stepIndex?: number;
    };
}

export type EconParameterCategory =
    | "stock_level"
    | "flow_rate"
    | "growth_rate"
    | "interest_rate"
    | "delay"
    | "capacity"
    | "price"
    | "quantity"
    | "elasticity"
    | "multiplier"
    | "utility"
    | "cost"
    | "revenue"
    | "probability";

export interface EconScenarioComparisonEvent extends BaseAnalyticsEvent {
    eventType: "econ_scenario_comparison";
    payload: {
        scenarioA: string;
        scenarioB: string;
        comparisonMetrics: {
            metricName: string;
            scenarioAValue: number;
            scenarioBValue: number;
            difference: number;
            percentDifference: number;
        }[];
        duration: number;
    };
}

export interface EconEquilibriumReachedEvent extends BaseAnalyticsEvent {
    eventType: "econ_equilibrium_reached";
    payload: {
        modelType: "supply_demand" | "system_dynamics" | "game_theory";
        equilibriumTime: number;
        equilibriumValues: Record<string, number>;
        convergenceIterations?: number;
    };
}

export interface EconSensitivityAnalysisEvent extends BaseAnalyticsEvent {
    eventType: "econ_sensitivity_analysis";
    payload: {
        targetVariable: string;
        variedParameter: string;
        parameterRange: [number, number];
        steps: number;
        results: {
            parameterValue: number;
            targetValue: number;
        }[];
        elasticity: number;
    };
}

// =============================================================================
// Playback Events
// =============================================================================

export interface PlaybackEvent extends BaseAnalyticsEvent {
    eventType: "playback_action";
    payload: {
        action: "play" | "pause" | "seek" | "speed_change" | "reset" | "step_forward" | "step_backward";
        currentStep: number;
        totalSteps: number;
        playbackSpeed?: number;
        seekTarget?: number;
        domain: SimulationDomain;
    };
}

export interface PlaybackCompletedEvent extends BaseAnalyticsEvent {
    eventType: "playback_completed";
    payload: {
        totalDuration: number;
        stepsPlayed: number;
        totalSteps: number;
        pauseCount: number;
        seekCount: number;
        averageStepDuration: number;
        domain: SimulationDomain;
    };
}

// =============================================================================
// Interaction Events
// =============================================================================

export interface EntityInteractionEvent extends BaseAnalyticsEvent {
    eventType: "entity_interaction";
    payload: {
        interactionType: "click" | "hover" | "drag" | "select" | "deselect";
        entityId: string;
        entityType: string;
        position?: { x: number; y: number };
        duration?: number;
        domain: SimulationDomain;
    };
}

export interface ChartInteractionEvent extends BaseAnalyticsEvent {
    eventType: "chart_interaction";
    payload: {
        chartType: "time_series" | "bar" | "scatter" | "pie" | "energy_profile" | "pk_curve" | "sir_curve";
        interactionType: "hover" | "click" | "zoom" | "pan" | "tooltip";
        dataPoint?: { x: number; y: number; label?: string };
        domain: SimulationDomain;
    };
}

export interface ViewModeChangeEvent extends BaseAnalyticsEvent {
    eventType: "view_mode_change";
    payload: {
        previousMode: "2d" | "3d" | "split" | "chart";
        newMode: "2d" | "3d" | "split" | "chart";
        domain: SimulationDomain;
    };
}

// =============================================================================
// AI Tutor Events
// =============================================================================

export interface AITutorContextEvent extends BaseAnalyticsEvent {
    eventType: "ai_tutor_context";
    payload: {
        contextType: "step_explanation" | "parameter_hint" | "concept_definition" | "suggestion";
        stepIndex: number;
        entityIds: string[];
        contextLength: number;
        latencyMs: number;
        domain: SimulationDomain;
    };
}

export interface AITutorFeedbackEvent extends BaseAnalyticsEvent {
    eventType: "ai_tutor_feedback";
    payload: {
        feedbackType: "helpful" | "not_helpful" | "incorrect" | "confusing";
        contextId: string;
        stepIndex: number;
        domain: SimulationDomain;
    };
}

// =============================================================================
// Union Type for All Events
// =============================================================================

export type SimulationAnalyticsEvent =
    | ParameterChangeEvent
    | ParameterRangeExceededEvent
    | ParameterPresetAppliedEvent
    | EconParameterChangeEvent
    | EconScenarioComparisonEvent
    | EconEquilibriumReachedEvent
    | EconSensitivityAnalysisEvent
    | PlaybackEvent
    | PlaybackCompletedEvent
    | EntityInteractionEvent
    | ChartInteractionEvent
    | ViewModeChangeEvent
    | AITutorContextEvent
    | AITutorFeedbackEvent;

// =============================================================================
// Analytics Service
// =============================================================================

export interface AnalyticsConfig {
    endpoint: string;
    batchSize: number;
    flushIntervalMs: number;
    enabled: boolean;
    debug: boolean;
}

const DEFAULT_CONFIG: AnalyticsConfig = {
    endpoint: "/api/analytics/events",
    batchSize: 20,
    flushIntervalMs: 5000,
    enabled: true,
    debug: false,
};

export class SimulationAnalyticsService {
    private config: AnalyticsConfig;
    private context: AnalyticsContext;
    private eventQueue: SimulationAnalyticsEvent[] = [];
    private flushTimer?: NodeJS.Timeout;
    private isDisposed = false;

    constructor(context: AnalyticsContext, config?: Partial<AnalyticsConfig>) {
        this.context = context;
        this.config = { ...DEFAULT_CONFIG, ...config };

        if (this.config.enabled) {
            this.startFlushTimer();
        }
    }

    /**
     * Track a parameter change event
     */
    trackParameterChange(payload: ParameterChangeEvent["payload"]): void {
        this.track({
            eventType: "parameter_change",
            timestamp: Date.now(),
            context: this.context,
            payload,
        });
    }

    /**
     * Track an economics parameter change
     */
    trackEconParameterChange(payload: Omit<EconParameterChangeEvent["payload"], "percentChange">): void {
        const percentChange = payload.previousValue !== 0
            ? ((payload.newValue - payload.previousValue) / payload.previousValue) * 100
            : payload.newValue * 100;

        this.track({
            eventType: "econ_parameter_change",
            timestamp: Date.now(),
            context: { ...this.context, domain: "ECONOMICS" },
            payload: { ...payload, percentChange },
        });
    }

    /**
     * Track parameter range exceeded
     */
    trackParameterRangeExceeded(payload: ParameterRangeExceededEvent["payload"]): void {
        this.track({
            eventType: "parameter_range_exceeded",
            timestamp: Date.now(),
            context: this.context,
            payload,
        });
    }

    /**
     * Track preset applied
     */
    trackPresetApplied(payload: ParameterPresetAppliedEvent["payload"]): void {
        this.track({
            eventType: "parameter_preset_applied",
            timestamp: Date.now(),
            context: this.context,
            payload,
        });
    }

    /**
     * Track scenario comparison
     */
    trackScenarioComparison(payload: EconScenarioComparisonEvent["payload"]): void {
        this.track({
            eventType: "econ_scenario_comparison",
            timestamp: Date.now(),
            context: { ...this.context, domain: "ECONOMICS" },
            payload,
        });
    }

    /**
     * Track equilibrium reached
     */
    trackEquilibriumReached(payload: EconEquilibriumReachedEvent["payload"]): void {
        this.track({
            eventType: "econ_equilibrium_reached",
            timestamp: Date.now(),
            context: { ...this.context, domain: "ECONOMICS" },
            payload,
        });
    }

    /**
     * Track sensitivity analysis
     */
    trackSensitivityAnalysis(payload: EconSensitivityAnalysisEvent["payload"]): void {
        this.track({
            eventType: "econ_sensitivity_analysis",
            timestamp: Date.now(),
            context: { ...this.context, domain: "ECONOMICS" },
            payload,
        });
    }

    /**
     * Track playback action
     */
    trackPlaybackAction(payload: PlaybackEvent["payload"]): void {
        this.track({
            eventType: "playback_action",
            timestamp: Date.now(),
            context: this.context,
            payload,
        });
    }

    /**
     * Track playback completed
     */
    trackPlaybackCompleted(payload: PlaybackCompletedEvent["payload"]): void {
        this.track({
            eventType: "playback_completed",
            timestamp: Date.now(),
            context: this.context,
            payload,
        });
    }

    /**
     * Track entity interaction
     */
    trackEntityInteraction(payload: EntityInteractionEvent["payload"]): void {
        this.track({
            eventType: "entity_interaction",
            timestamp: Date.now(),
            context: this.context,
            payload,
        });
    }

    /**
     * Track chart interaction
     */
    trackChartInteraction(payload: ChartInteractionEvent["payload"]): void {
        this.track({
            eventType: "chart_interaction",
            timestamp: Date.now(),
            context: this.context,
            payload,
        });
    }

    /**
     * Track view mode change
     */
    trackViewModeChange(payload: ViewModeChangeEvent["payload"]): void {
        this.track({
            eventType: "view_mode_change",
            timestamp: Date.now(),
            context: this.context,
            payload,
        });
    }

    /**
     * Track AI tutor context generation
     */
    trackAITutorContext(payload: AITutorContextEvent["payload"]): void {
        this.track({
            eventType: "ai_tutor_context",
            timestamp: Date.now(),
            context: this.context,
            payload,
        });
    }

    /**
     * Track AI tutor feedback
     */
    trackAITutorFeedback(payload: AITutorFeedbackEvent["payload"]): void {
        this.track({
            eventType: "ai_tutor_feedback",
            timestamp: Date.now(),
            context: this.context,
            payload,
        });
    }

    /**
     * Update analytics context
     */
    updateContext(updates: Partial<AnalyticsContext>): void {
        this.context = { ...this.context, ...updates };
    }

    /**
     * Core tracking method
     */
    private track(event: SimulationAnalyticsEvent): void {
        if (!this.config.enabled || this.isDisposed) return;

        if (this.config.debug) {
            console.log("[Analytics]", event.eventType, event.payload);
        }

        this.eventQueue.push(event);

        if (this.eventQueue.length >= this.config.batchSize) {
            this.flush();
        }
    }

    /**
     * Flush events to the backend
     */
    async flush(): Promise<void> {
        if (this.eventQueue.length === 0 || this.isDisposed) return;

        const events = [...this.eventQueue];
        this.eventQueue = [];

        try {
            const response = await fetch(this.config.endpoint, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ events }),
            });

            if (!response.ok) {
                // Re-queue failed events
                this.eventQueue = [...events, ...this.eventQueue];
                console.error("[Analytics] Failed to send events:", response.status);
            }
        } catch (error) {
            // Re-queue failed events
            this.eventQueue = [...events, ...this.eventQueue];
            console.error("[Analytics] Failed to send events:", error);
        }
    }

    /**
     * Start the flush timer
     */
    private startFlushTimer(): void {
        this.flushTimer = setInterval(() => {
            this.flush();
        }, this.config.flushIntervalMs);
    }

    /**
     * Dispose the service
     */
    dispose(): void {
        this.isDisposed = true;
        if (this.flushTimer) {
            clearInterval(this.flushTimer);
        }
        // Final flush
        this.flush();
    }
}


