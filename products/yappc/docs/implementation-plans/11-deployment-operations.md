# AI-Native Deployment & Operations — Detailed Implementation Plan

**Priority:** P2 MEDIUM  
**Current State:** Manual pipeline; `config/pipelines/lifecycle-management-v1.yaml` exists; Prometheus config in `monitoring/prometheus.yappc.yml`; no AI-driven deployment strategy, predictive scaling, or automated incident response  
**Target State:** AI-recommended deployment strategy, predictive failure detection, automated incident routing, and capacity planning  
**Estimated Effort:** 3 sprints (~24 engineer-days)

---

## 1. Current State Analysis

### What Exists

```
config/
  pipelines/
    lifecycle-management-v1.yaml      ✅ Basic pipeline config

monitoring/
  prometheus.yappc.yml                ✅ Prometheus scrape config
  grafana/
    dashboards/                       ✅ Grafana dashboards

products/yappc/
  core/services-lifecycle/            ✅ Phase lifecycle management
  core/services-platform/            ✅ Platform-wide services
```

### Critical Gaps

1. **No AI deployment strategy selection** — deployments are triggered manually without risk assessment
2. **No predictive failure detection** — reactive alerting only
3. **No automated rollback triggers** — rollback is 100% manual
4. **No capacity planning** — infrastructure scaled reactively
5. **No incident routing** — alerts are aggregated but not routed or correlated
6. **No cost optimization** — infrastructure sizing based on intuition, not data

---

## 2. Architecture Overview

```
CI/CD Pipeline Trigger (code merged to main)
  │
  ▼
DeploymentOrchestrator
  ├── 1. DeploymentRiskAssessor
  │     ├── Reads: change size (lines diff), change scope (modules affected)
  │     ├── Reads: recent failure rate, test coverage, KG impact analysis
  │     ├── AI scores: RISK_SCORE (0-10) → deployment strategy recommendation
  │     └── Returns: DeploymentStrategy{ROLLING | CANARY | BLUE_GREEN | IMMEDIATE}
  │
  ├── 2. CanaryAnalyzer (if CANARY strategy)
  │     ├── Routes 5% of traffic to new version
  │     ├── Monitors error rate, latency P99, business KPIs
  │     ├── AI evaluates: "promote" vs "rollback"
  │     └── Decision after configurable window (default: 30 min)
  │
  ├── 3. AutoRollbackTrigger
  │     ├── Monitors post-deploy metrics for 1h
  │     ├── Triggers rollback if: error rate +50%, P99 latency +200%, any 5xx spike
  │     └── Sends rollback event to lifecycle engine (PhaseTransition: Deploy → Observe)
  │
  ├── 4. IncidentCorrelator
  │     ├── Groups related alerts into incidents
  │     ├── AI-inferred root cause from correlated signals
  │     ├── Routes incident to owning team based on affected module KG
  │     └── Generates runbook suggestion
  │
  └── 5. CapacityAdvisor
        ├── Analyzes 30-day usage trends
        ├── Projects capacity needs for next 7 days (ARIMA + AI)
        └── Recommends: scale-up/down, instance type change, cost savings
```

---

## 3. Domain Models

### Deployment Models [NEW]

```java
public record DeploymentPlan(
    String deploymentId,
    String projectId,
    String tenantId,
    String version,
    DeploymentStrategy strategy,     // ROLLING | CANARY | BLUE_GREEN | IMMEDIATE
    double riskScore,                // 0-10
    String riskRationale,            // AI-generated explanation
    List<String> riskFactors,        // e.g., ["high change scope", "low test coverage"]
    CanaryConfig canaryConfig,       // null if not CANARY
    boolean requiresApproval,        // true if riskScore > 7
    Instant createdAt
) {}

public record CanaryConfig(
    int trafficPercent,             // typically 5-10%
    Duration analysisWindow,        // how long to monitor
    double errorRateThreshold,      // rollback if exceeded
    long latencyP99ThresholdMs      // rollback if exceeded
) {}

public record DeploymentEvent(
    String eventId,
    String deploymentId,
    DeploymentEventType type,       // STARTED | PROMOTED | ROLLED_BACK | COMPLETED | FAILED
    String trigger,                 // MANUAL | AUTO_PROMOTE | AUTO_ROLLBACK | AI_DECISION
    String aiRationale,             // present when trigger = AI_DECISION
    Map<String, Double> metrics,    // snapshot of key metrics at time of event
    Instant occurredAt
) {}
```

### Database Schema

```sql
-- V010__deployments.sql

CREATE TABLE deployment_plans (
    deployment_id     VARCHAR(36)    PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id        VARCHAR(36)    NOT NULL,
    tenant_id         VARCHAR(128)   NOT NULL,
    version           VARCHAR(100)   NOT NULL,
    strategy          VARCHAR(50)    NOT NULL,
    risk_score        DECIMAL(4,2),
    risk_rationale    TEXT,
    risk_factors      JSONB,
    canary_config     JSONB,
    requires_approval BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE TABLE deployment_events (
    event_id          VARCHAR(36)    PRIMARY KEY DEFAULT gen_random_uuid(),
    deployment_id     VARCHAR(36)    NOT NULL REFERENCES deployment_plans(deployment_id),
    event_type        VARCHAR(50)    NOT NULL,
    trigger           VARCHAR(50)    NOT NULL,
    ai_rationale      TEXT,
    metrics           JSONB,
    occurred_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE TABLE incidents (
    incident_id       VARCHAR(36)    PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id        VARCHAR(36)    NOT NULL,
    tenant_id         VARCHAR(128)   NOT NULL,
    title             TEXT           NOT NULL,
    severity          VARCHAR(20)    NOT NULL,  -- P0 | P1 | P2 | P3
    status            VARCHAR(20)    NOT NULL DEFAULT 'OPEN',
    root_cause        TEXT,
    ai_root_cause     TEXT,
    runbook_suggestion TEXT,
    owning_team       VARCHAR(100),
    correlated_alerts JSONB,
    deployment_id     VARCHAR(36),              -- linked deployment if relevant
    opened_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    resolved_at       TIMESTAMPTZ
);

CREATE INDEX idx_deployment_project ON deployment_plans(project_id, tenant_id, created_at);
CREATE INDEX idx_incidents_project ON incidents(project_id, tenant_id, status, opened_at);
```

---

## 4. Implementation Tasks

### Sprint 1 — AI Deployment Strategy (8 days)

#### T1.1 — Create `DeploymentRiskAssessor` [NEW] [L]
**File:** `core/services-platform/src/main/java/com/ghatana/yappc/platform/deployment/DeploymentRiskAssessor.java`

```java
/**
 * @doc.type class
 * @doc.purpose AI-scores deployment risk from code change signals and recommends deployment strategy.
 * @doc.layer product
 * @doc.pattern Assessor
 */
public final class DeploymentRiskAssessor {
    private final YAPPCAIService aiService;
    private final KGQueryService knowledgeGraph;
    private final MetricsQueryService metricsQuery;
    
    public Promise<DeploymentRisk> assess(DeploymentRequest request) {
        return Promises.all(
            knowledgeGraph.impactAnalysis(request.changedModules(), request.tenantId()),
            metricsQuery.getRecentFailureRate(request.projectId(), Duration.ofDays(7)),
            metricsQuery.getTestCoverage(request.projectId())
        ).then((impact, failureRate, coverage) -> {
            
            DeploymentSignals signals = DeploymentSignals.builder()
                .changeLinesAdded(request.linesAdded())
                .changeLinesRemoved(request.linesRemoved())
                .modulesAffected(request.changedModules().size())
                .downstreamImpact(impact.affectedNodeCount())
                .recentFailureRate(failureRate)
                .testCoverage(coverage)
                .hasBreakingApiChanges(request.hasBreakingApiChanges())
                .build();
            
            String prompt = buildRiskAssessmentPrompt(signals);
            return aiService.complete(AIRequest.of(prompt).withWorkflow("deployment_risk"))
                .map(response -> parseRisk(response, signals));
        });
    }
    
    private String buildRiskAssessmentPrompt(DeploymentSignals signals) {
        return """
            Assess the deployment risk for this software release.
            
            Change signals:
            - Lines added: %d, Lines removed: %d
            - Modules changed: %d
            - Downstream modules affected (KG): %d
            - Recent 7-day failure rate: %.1f%%
            - Test coverage: %.1f%%
            - Has breaking API changes: %s
            
            Return JSON:
            {
              "riskScore": 0.0-10.0,
              "strategy": "IMMEDIATE|ROLLING|CANARY|BLUE_GREEN",
              "rationale": "...",
              "riskFactors": ["factor1", "factor2"],
              "requiresApproval": true|false,
              "canaryPercent": 5
            }
            """.formatted(
                signals.changeLinesAdded(), signals.changeLinesRemoved(),
                signals.modulesAffected(), signals.downstreamImpact(),
                signals.recentFailureRate() * 100, signals.testCoverage() * 100,
                signals.hasBreakingApiChanges()
            );
    }
}
```

#### T1.2 — Create `CanaryAnalyzer` [NEW] [L]
**File:** `core/services-platform/src/main/java/com/ghatana/yappc/platform/deployment/CanaryAnalyzer.java`

```java
/**
 * @doc.type class
 * @doc.purpose Evaluates canary deployment health and makes promote/rollback decisions.
 * @doc.layer product
 * @doc.pattern Analyzer
 */
public final class CanaryAnalyzer {
    private final MetricsQueryService metricsQuery;
    private final YAPPCAIService aiService;
    private final EventBusService eventBus;
    
    /** Called on a schedule during canary window. */
    public Promise<CanaryDecision> evaluate(String deploymentId, CanaryConfig config) {
        return metricsQuery.getCanaryMetrics(deploymentId)
            .then(metrics -> {
                // Deterministic fast-checks first
                if (metrics.errorRate() > config.errorRateThreshold()) {
                    return Promise.of(CanaryDecision.rollback("Error rate %.1f%% exceeded threshold %.1f%%"
                        .formatted(metrics.errorRate() * 100, config.errorRateThreshold() * 100)));
                }
                
                if (metrics.latencyP99().toMillis() > config.latencyP99ThresholdMs()) {
                    return Promise.of(CanaryDecision.rollback("P99 latency %dms exceeded threshold %dms"
                        .formatted(metrics.latencyP99().toMillis(), config.latencyP99ThresholdMs())));
                }
                
                // AI evaluates subtle signals
                return aiService.complete(buildCanaryEvalPrompt(metrics, config))
                    .map(response -> parseCanaryDecision(response, metrics));
            })
            .then(decision -> {
                if (decision.type() == CanaryDecisionType.PROMOTE) {
                    eventBus.publish(new DeploymentPromotedEvent(deploymentId, decision.rationale()));
                } else if (decision.type() == CanaryDecisionType.ROLLBACK) {
                    eventBus.publish(new DeploymentRolledBackEvent(deploymentId, "CANARY_FAILED", decision.rationale()));
                }
                return Promise.of(decision);
            });
    }
}
```

#### T1.3 — Create `AutoRollbackTrigger` [NEW] [M]
**File:** `core/services-platform/src/main/java/com/ghatana/yappc/platform/deployment/AutoRollbackTrigger.java`

```java
/**
 * @doc.type class
 * @doc.purpose Monitors post-deploy metrics and triggers automatic rollback on threshold breaches within 1h window.
 * @doc.layer product
 * @doc.pattern Trigger
 */
public final class AutoRollbackTrigger {
    private final MetricsQueryService metricsQuery;
    private final EventBusService eventBus;
    private final Eventloop eventloop;
    
    private static final Duration MONITORING_WINDOW = Duration.ofHours(1);
    private static final double ERROR_RATE_INCREASE_THRESHOLD = 0.5;  // 50% increase
    private static final double LATENCY_INCREASE_THRESHOLD = 2.0;     // 2x baseline
    
    public void startMonitoring(String deploymentId, DeploymentBaseline baseline) {
        // Schedule checkpoints: 5min, 10min, 20min, 30min, 60min
        List<Duration> checkpointDelays = List.of(
            Duration.ofMinutes(5), Duration.ofMinutes(10),
            Duration.ofMinutes(20), Duration.ofMinutes(30), MONITORING_WINDOW
        );
        
        for (Duration delay : checkpointDelays) {
            eventloop.delay(delay.toMillis(), () ->
                checkAndMaybeRollback(deploymentId, baseline));
        }
    }
    
    private void checkAndMaybeRollback(String deploymentId, DeploymentBaseline baseline) {
        metricsQuery.getCurrentMetrics(deploymentId)
            .whenResult(current -> {
                double errorRateRatio = current.errorRate() / Math.max(baseline.errorRate(), 0.001);
                double latencyRatio = current.latencyP99Ms() / Math.max(baseline.latencyP99Ms(), 1.0);
                
                if (errorRateRatio > (1 + ERROR_RATE_INCREASE_THRESHOLD)
                    || latencyRatio > LATENCY_INCREASE_THRESHOLD) {
                    eventBus.publish(new AutoRollbackTriggeredEvent(
                        deploymentId, current, baseline,
                        "Error rate x%.1f or latency x%.1f".formatted(errorRateRatio, latencyRatio)
                    ));
                }
            });
    }
}
```

---

### Sprint 2 — Incident Correlation (8 days)

#### T2.1 — Create `IncidentCorrelator` [NEW] [L]
**File:** `core/services-platform/src/main/java/com/ghatana/yappc/platform/ops/IncidentCorrelator.java`

```java
/**
 * @doc.type class
 * @doc.purpose Groups correlated alerts into incidents, infers root cause using AI, and routes to the owning team.
 * @doc.layer product
 * @doc.pattern Correlator
 */
public final class IncidentCorrelator {
    private final YAPPCAIService aiService;
    private final KGQueryService knowledgeGraph;
    private final IncidentRepository incidentRepository;
    
    /** Called when a new alert arrives from Prometheus Alertmanager webhook. */
    public Promise<String> correlateAlert(PrometheusAlert alert) {
        // Find recent open incidents this might belong to
        return incidentRepository.findRecentOpenByTenant(alert.tenantId(), Duration.ofMinutes(30))
            .then(openIncidents -> {
                Optional<Incident> related = findRelatedIncident(alert, openIncidents);
                
                if (related.isPresent()) {
                    return incidentRepository.addAlert(related.get().incidentId(), alert)
                        .map(_ -> related.get().incidentId());
                }
                
                // Create new incident
                return createNewIncident(alert);
            });
    }
    
    private Promise<String> createNewIncident(PrometheusAlert alert) {
        return Promises.all(
            aiService.complete(buildRootCausePrompt(alert)),
            knowledgeGraph.findOwnerForModule(alert.affectedModule(), alert.tenantId())
        ).then((rootCauseResponse, owner) ->
            incidentRepository.save(Incident.builder()
                .title(alert.alertName())
                .severity(classifySeverity(alert))
                .aiRootCause(rootCauseResponse.content())
                .owningTeam(owner.orElse("platform-team"))
                .correlated_alerts(List.of(alert))
                .runbookSuggestion(generateRunbook(alert, rootCauseResponse.content()))
                .build())
            .map(Incident::incidentId)
        );
    }
    
    private String buildRootCausePrompt(PrometheusAlert alert) {
        return """
            A production alert has fired. Infer the likely root cause.
            
            Alert name: %s
            Alert labels: %s
            Alert annotations: %s
            
            Return a concise root cause hypothesis and recommended first investigation steps.
            """.formatted(alert.alertName(), alert.labels(), alert.annotations());
    }
}
```

#### T2.2 — Alertmanager Webhook Handler [NEW] [M]
**File:** `core/services-platform/src/main/java/com/ghatana/yappc/platform/ops/AlertWebhookHandler.java`

Receives Prometheus Alertmanager POST and triggers correlator:

```java
// POST /api/v1/ops/alerts
public Promise<HttpResponse> handleAlert(HttpRequest request) {
    AlertmanagerPayload payload = mapper.parseBody(request.getBody(), AlertmanagerPayload.class);
    
    return Promises.all(
        payload.alerts().stream()
            .map(alert -> incidentCorrelator.correlateAlert(alert))
            .toList()
    ).map(incidentIds -> HttpResponse.ok200()
        .withBody(mapper.toJson(Map.of("incidentIds", incidentIds))));
}
```

#### T2.3 — Incident API [NEW] [M]

```java
// GET /api/v1/ops/incidents?status=OPEN&tenantId=xxx
// GET /api/v1/ops/incidents/{incidentId}
// POST /api/v1/ops/incidents/{incidentId}/resolve
```

#### T2.4 — Incidents Dashboard [NEW] [M]
**File:** `frontend/apps/web/src/features/ops/IncidentsDashboard.tsx`

```typescript
const IncidentsDashboard: React.FC<{ projectId: string }> = ({ projectId }) => {
  const { data: incidents } = useQuery({
    queryKey: ['incidents', projectId],
    queryFn: () => fetchOpenIncidents(projectId),
    refetchInterval: 30_000,
  });

  return (
    <div className="space-y-4">
      <SeveritySummary incidents={incidents ?? []} />
      <IncidentList
        incidents={incidents ?? []}
        renderItem={incident => (
          <IncidentCard key={incident.incidentId} incident={incident}>
            <AIRootCauseBadge rootCause={incident.aiRootCause} />
            <RunbookSuggestion content={incident.runbookSuggestion} />
          </IncidentCard>
        )}
      />
    </div>
  );
};
```

---

### Sprint 3 — Capacity Planning & Frontend Ops Panel (8 days)

#### T3.1 — Create `CapacityAdvisor` [NEW] [M]
**File:** `core/services-platform/src/main/java/com/ghatana/yappc/platform/ops/CapacityAdvisor.java`

```java
/**
 * @doc.type class
 * @doc.purpose Projects infrastructure capacity needs and recommends scale events to prevent outages.
 * @doc.layer product
 * @doc.pattern Advisor
 */
public final class CapacityAdvisor {
    private final MetricsQueryService metricsQuery;
    private final YAPPCAIService aiService;
    
    /** Runs daily. Analyzes 30-day CPU/memory/RPS trends and projects 7 days forward. */
    public Promise<CapacityReport> generateReport(String projectId, String tenantId) {
        return metricsQuery.getTrends(projectId, Duration.ofDays(30))
            .then(trends -> {
                // Simple ARIMA-style trend: compute slope
                TrendData trend = computeTrend(trends);
                
                String prompt = """
                    Review infrastructure usage trends and give capacity recommendations.
                    
                    Current usage (30-day average):
                    - CPU: %.1f%% (trend: %+.1f%%/week)
                    - Memory: %.1f%% (trend: %+.1f%%/week)
                    - Requests/sec: %.0f (trend: %+.0f/week)
                    - DB connections: %.0f (trend: %+.0f/week)
                    
                    Configured limits:
                    - CPU limit: 80%%
                    - Memory limit: 85%%
                    
                    Return JSON:
                    {
                      "daysUntilCpuLimit": N or null,
                      "daysUntilMemoryLimit": N or null,
                      "recommendations": [
                        {
                          "urgency": "IMMEDIATE|7_DAYS|30_DAYS",
                          "action": "Scale up web replicas from 3 to 5",
                          "estimatedMonthlyCostChange": "+$120"
                        }
                      ],
                      "costOptimizations": ["Consider right-sizing db instance..."]
                    }
                    """.formatted(
                        trends.avgCpu(), trend.cpuWeeklySlope(),
                        trends.avgMemory(), trend.memoryWeeklySlope(),
                        trends.avgRps(), trend.rpsWeeklySlope(),
                        trends.avgDbConnections(), trend.dbConnWeeklySlope()
                    );
                
                return aiService.complete(AIRequest.of(prompt).withWorkflow("capacity_planning"))
                    .map(response -> parseCapacityReport(response, trends));
            });
    }
}
```

#### T3.2 — Deployment Panel [NEW] [M]
**File:** `frontend/apps/web/src/features/ops/DeploymentPanel.tsx`

```typescript
interface DeploymentPanelProps {
  projectId: string;
}

const DeploymentPanel: React.FC<DeploymentPanelProps> = ({ projectId }) => {
  const { assess, isAssessing, deploymentPlan } = useDeploymentRiskAssessment(projectId);

  return (
    <div className="p-4 space-y-4">
      <h3 className="text-lg font-semibold">Deploy New Version</h3>
      
      <VersionInput onChange={setVersion} />
      
      <button onClick={() => assess(version)} disabled={isAssessing}>
        {isAssessing ? 'Assessing risk...' : 'Assess Deployment Risk'}
      </button>
      
      {deploymentPlan && (
        <>
          <RiskScoreCard
            score={deploymentPlan.riskScore}
            rationale={deploymentPlan.riskRationale}
            riskFactors={deploymentPlan.riskFactors}
          />
          <StrategyRecommendationCard
            strategy={deploymentPlan.strategy}
            canaryConfig={deploymentPlan.canaryConfig}
          />
          {deploymentPlan.requiresApproval ? (
            <ApprovalRequiredBanner onRequestApproval={handleRequestApproval} />
          ) : (
            <button onClick={handleDeploy} className="bg-blue-600 text-white px-6 py-2 rounded">
              Deploy ({deploymentPlan.strategy})
            </button>
          )}
        </>
      )}
    </div>
  );
};
```

#### T3.3 — Capacity Dashboard [NEW] [M]
**File:** `frontend/apps/web/src/features/ops/CapacityDashboard.tsx`

```typescript
const CapacityDashboard: React.FC<{ projectId: string }> = ({ projectId }) => {
  const { data: report } = useQuery({
    queryKey: ['capacity', projectId],
    queryFn: () => fetchCapacityReport(projectId),
    refetchInterval: 60 * 60_000,  // refresh hourly
  });

  return (
    <div className="space-y-4">
      {report?.daysUntilCpuLimit !== null && (
        <CapacityWarning
          metric="CPU"
          daysRemaining={report.daysUntilCpuLimit}
          urgency={report.daysUntilCpuLimit < 7 ? 'IMMEDIATE' : '7_DAYS'}
        />
      )}
      
      <RecommendationList recommendations={report?.recommendations ?? []} />
      <CostOptimizationList items={report?.costOptimizations ?? []} />
      
      <UsageTrendsChart projectId={projectId} />
    </div>
  );
};
```

---

## 5. Testing Requirements

| Test | Key Scenarios |
|------|--------------|
| `DeploymentRiskAssessorTest` | High risk → CANARY recommended; low risk → ROLLING; breaking change → BLUE_GREEN |
| `CanaryAnalyzerTest` | Clean metrics → PROMOTE; error rate spike → ROLLBACK |
| `AutoRollbackTriggerTest` | Error rate +60% → rollback event fired; clean metrics → no action |
| `IncidentCorrelatorTest` | Related alerts grouped; unrelated opens new incident; owner routed |
| `CapacityAdvisorTest` | CPU trend → correct days-until-limit estimated |

---

## 6. Observability

```
yappc_deployments_total{strategy, outcome}                   counter
yappc_deployment_risk_score                                  histogram
yappc_canary_promotions_total                                counter
yappc_canary_rollbacks_total                                 counter
yappc_auto_rollbacks_triggered_total                         counter
yappc_incidents_open_count{severity}                        gauge
yappc_incident_resolution_time_seconds{severity}             histogram
yappc_capacity_days_until_limit{resource}                   gauge
yappc_capacity_recommendations_acted_on_total               counter
```

---

## 7. Dependencies & Prerequisites

| Dependency | Status |
|-----------|--------|
| Plan 01 — Auth (real JWT) | Required for ops API security |
| Plan 02 — Approval Workflow | Required for high-risk deployment approval |
| Plan 04 — Knowledge Graph | Required for module ownership routing |
| Plan 06 — AI/LLM Integration | Required for all AI risk/root-cause calls |
| Prometheus Alertmanager | Must configure webhook URL → YAPPC |
| Grafana datasource | Must point to YAPPC metrics endpoint |
