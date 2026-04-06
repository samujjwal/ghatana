# Implicit / Pervasive AI Layer — Detailed Implementation Plan

**Priority:** P1 STRATEGIC  
**Current State:** 0/10 — AI is entirely explicit (button-triggered); no background analysis; no proactive suggestions  
**Target State:** AI is invisible operating system of the platform — continuous background analysis, proactive surfacing, seamless suggestions  
**Estimated Effort:** 6 sprints (~50 engineer-days, Month 3-6)

---

## 1. Vision

The shift from **Explicit AI** (user must ask) to **Implicit AI** (AI continuously watches and proactively acts):

| Explicit (Current) | Implicit (Target) |
|-------------------|------------------|
| User clicks "Generate Requirements" | AI drafts requirements as user types intent |
| User clicks "AI Suggestions" panel | AI surfaces suggestions inline as user edits code |
| User requests code review | AI continuously watches commits and flags issues |
| User asks for test generation | AI generates tests as code is written |
| User runs security scan | AI flags security patterns in real time |

**Constraint:** Implicit AI must never feel intrusive. It surfaces insights only when actionable and high-confidence.

---

## 2. Architecture: Background Intelligence Pipeline

```
Event Stream (AEP)
  ├── DocumentChanged (canvas, requirements, code)
  ├── CodeCommitted
  ├── RequirementModified
  ├── PhaseTransitioned
  └── UserSessionStarted / UserFocusChanged
       │
       ▼
BackgroundAnalysisPipeline (ActiveJ event loop)
  ├── ChangeDebouncer (wait 2s idle after last change)
  ├── ContextAggregator (collects relevant context)
  ├── AnalysisDispatcher
  │     ├── CodeQualityAnalyzer → suggestions
  │     ├── SecurityPatternDetector → alerts
  │     ├── RequirementsConsistencyChecker → warnings
  │     ├── ArchitectureAdvisor → advisories
  │     └── TestGapDetector → suggestions
  ├── ConfidenceFilter (suppress low-confidence < 0.6)
  ├── DeduplicationFilter (suppress repeated identical suggestions)
  └── InsightPublisher → AEP → Frontend WebSocket Push
       │
       ▼
Frontend
  ├── InsightStream (WebSocket subscription)
  ├── InsightPanel (non-intrusive side panel)
  ├── InlineHints (in editor, canvas)
  └── NotificationBadge (high-priority items only)
```

---

## 3. Implementation Tasks

### Sprint 1 — Background Analysis Infrastructure (9 days)

#### T3.1 — Create `BackgroundAnalysisPipeline` [NEW] [L]
**File:** `core/services-platform/src/main/java/com/ghatana/yappc/platform/ai/BackgroundAnalysisPipeline.java`

```java
/**
 * @doc.type class
 * @doc.purpose Event-driven pipeline that continuously analyzes project state and dispatches AI analysis tasks.
 * @doc.layer platform
 * @doc.pattern Pipeline
 */
public final class BackgroundAnalysisPipeline {
    private final ChangeDebouncer debouncer;
    private final AnalysisDispatcher dispatcher;
    private final InsightPublisher publisher;
    
    public void onDomainEvent(DomainEvent event) {
        // Debounce: only trigger analysis if quiet for 2 seconds
        debouncer.debounce(event.documentId(), Duration.ofSeconds(2), () -> {
            analyzeDocument(event);
        });
    }
    
    private Promise<Void> analyzeDocument(DomainEvent event) {
        return dispatcher.dispatch(event)
            .then(insights -> filterAndPublish(insights, event.tenantId()));
    }
}
```

#### T3.2 — Create `ChangeDebouncer` [NEW] [S]
**File:** `core/services-platform/src/main/java/com/ghatana/yappc/platform/ai/ChangeDebouncer.java`

```java
/**
 * @doc.type class
 * @doc.purpose Debounces rapid-fire changes to prevent excessive AI analysis calls.
 * @doc.layer platform
 * @doc.pattern Debouncer
 */
public final class ChangeDebouncer {
    private final Eventloop eventloop;
    private final Map<String, ScheduledRunnable> pending = new ConcurrentHashMap<>();
    
    public void debounce(String key, Duration delay, Runnable action) {
        ScheduledRunnable existing = pending.remove(key);
        if (existing != null) existing.cancel();
        
        ScheduledRunnable scheduled = eventloop.delay(delay.toMillis(), () -> {
            pending.remove(key);
            action.run();
        });
        pending.put(key, scheduled);
    }
}
```

#### T3.3 — Create `AnalysisDispatcher` [NEW] [M]
**File:** `core/services-platform/src/main/java/com/ghatana/yappc/platform/ai/AnalysisDispatcher.java`

Routes each event type to the appropriate analyzers (runs in parallel):

```java
public Promise<List<AIInsight>> dispatch(DomainEvent event) {
    List<Promise<List<AIInsight>>> analysisPromises = new ArrayList<>();
    
    if (event instanceof CodeChangedEvent) {
        analysisPromises.add(codeQualityAnalyzer.analyze((CodeChangedEvent) event));
        analysisPromises.add(securityDetector.analyze((CodeChangedEvent) event));
        analysisPromises.add(testGapDetector.analyze((CodeChangedEvent) event));
    }
    if (event instanceof RequirementModifiedEvent) {
        analysisPromises.add(requirementsConsistencyChecker.analyze((RequirementModifiedEvent) event));
    }
    
    return Promises.all(analysisPromises)
        .map(results -> results.stream().flatMap(List::stream).toList());
}
```

#### T3.4 — Create `AIInsight` Domain Model [NEW] [S]
**File:** `core/services-platform/src/main/java/com/ghatana/yappc/platform/ai/model/AIInsight.java`

```java
public record AIInsight(
    String insightId,
    String tenantId,
    String projectId,
    InsightType type,      // CODE_QUALITY | SECURITY | ARCHITECTURE | REQUIREMENT | TEST_GAP | PERFORMANCE
    InsightSeverity severity, // INFO | WARNING | ERROR | CRITICAL
    String title,
    String description,
    String suggestion,     // What to do about it
    double confidence,
    String sourceRef,      // Which file/requirement/node triggered this
    int lineNumber,        // For code insights
    List<String> tags,
    Instant generatedAt,
    boolean dismissed      // User dismissed this insight
) {}
```

#### T3.5 — Create `InsightPublisher` [NEW] [M]
**File:** `core/services-platform/src/main/java/com/ghatana/yappc/platform/ai/InsightPublisher.java`

Pushes insights to connected frontend clients via WebSocket (through existing `RealTimeService`):

```java
public Promise<Void> publish(List<AIInsight> insights, String tenantId) {
    return insightRepository.saveAll(insights)
        .then(saved -> {
            realTimeService.broadcastToTenant(tenantId, InsightEvent.of(saved));
            metrics.recordInsightsPublished(saved.size());
            return Promise.ofComplete();
        });
}
```

---

### Sprint 2 — Code Quality Analyzer (8 days)

#### T2.1 — Implement `CodeQualityAnalyzer` [NEW] [L]
**File:** `core/services-platform/src/main/java/com/ghatana/yappc/platform/ai/analyzers/CodeQualityAnalyzer.java`

```java
/**
 * @doc.type class
 * @doc.purpose Analyzes code changes using AI to detect quality issues, anti-patterns, and improvement opportunities.
 * @doc.layer platform
 * @doc.pattern Analyzer
 */
public final class CodeQualityAnalyzer {
    private final YAPPCAIService aiService;
    
    public Promise<List<AIInsight>> analyze(CodeChangedEvent event) {
        String prompt = """
            Review this code change for quality issues, anti-patterns, and improvements.
            Focus on: readability, maintainability, performance, naming, complexity.
            
            Changed file: %s
            Diff:
            %s
            
            Return JSON array of issues found:
            [{"severity": "WARNING|ERROR", "type": "CODE_QUALITY",
              "title": "...", "description": "...", "suggestion": "...",
              "lineNumber": N, "confidence": 0.0-1.0}]
              
            If no issues found, return empty array [].
            """.formatted(event.filePath(), event.diff());
        
        return aiService.complete(AIRequest.of(prompt).withWorkflow("code_quality_analysis"))
            .map(response -> parseInsights(response, event));
    }
}
```

#### T2.2 — Implement `SecurityPatternDetector` [NEW] [L]
**File:** `core/services-platform/src/main/java/com/ghatana/yappc/platform/ai/analyzers/SecurityPatternDetector.java`

Detect OWASP Top 10 patterns:
- SQL injection patterns (string concatenation in queries)
- Hardcoded secrets (API keys, passwords in code)
- Unsafe deserialization
- Missing input validation at boundaries

```java
public Promise<List<AIInsight>> analyze(CodeChangedEvent event) {
    // First: fast deterministic check (regex) for obvious issues
    List<AIInsight> fastFindings = runRegexChecks(event);
    if (!fastFindings.isEmpty()) return Promise.of(fastFindings);
    
    // Then: AI-powered deep analysis
    String prompt = SECURITY_ANALYSIS_PROMPT.formatted(event.filePath(), event.diff());
    return aiService.complete(AIRequest.of(prompt).withWorkflow("security_analysis"))
        .map(response -> parseSecurityInsights(response, event));
}
```

#### T2.3 — Implement `TestGapDetector` [NEW] [M]
**File:** `core/services-platform/src/main/java/com/ghatana/yappc/platform/ai/analyzers/TestGapDetector.java`

Detects when new code paths are added without corresponding tests:

```java
public Promise<List<AIInsight>> analyze(CodeChangedEvent event) {
    if (isTestFile(event.filePath())) return Promise.of(List.of());  // skip test files
    
    String prompt = """
        This code was added or modified:
        %s
        
        These test files exist for this module:
        %s
        
        Identify: which new code paths are NOT covered by existing tests.
        Return JSON: [{"uncoveredPath": "...", "suggestedTest": "...", "severity": "WARNING"}]
        """.formatted(event.diff(), event.relatedTestFiles());
    
    return aiService.complete(AIRequest.of(prompt).withWorkflow("test_gap_detection"))
        .map(response -> parseTestGaps(response, event));
}
```

---

### Sprint 3 — Requirements Consistency Checker (8 days)

#### T3.1 — Implement `RequirementsConsistencyChecker` [NEW] [L]
**File:** `core/services-platform/src/main/java/com/ghatana/yappc/platform/ai/analyzers/RequirementsConsistencyChecker.java`

Checks requirements for:
- **Conflicts**: two requirements that can't both be true
- **Duplicates**: semantically identical requirements (different wording)
- **Incompleteness**: vague requirements missing acceptance criteria
- **Traceability gaps**: requirements with no associated code/tests

```java
public Promise<List<AIInsight>> analyze(RequirementModifiedEvent event) {
    return requirementRepository.findAllByProject(event.projectId(), event.tenantId())
        .then(allRequirements -> {
            String prompt = buildConsistencyPrompt(event.requirement(), allRequirements);
            return aiService.complete(AIRequest.of(prompt).withWorkflow("requirements_consistency"));
        })
        .map(response -> parseConsistencyInsights(response, event));
}

private String buildConsistencyPrompt(Requirement changed, List<Requirement> all) {
    return """
        A requirement was changed:
        NEW: %s
        
        All existing requirements (check for conflicts and duplicates):
        %s
        
        Return JSON:
        [{"type": "CONFLICT|DUPLICATE|INCOMPLETE|TRACEABILITY_GAP",
          "severity": "WARNING|ERROR",
          "affectedRequirementId": "...",
          "description": "...",
          "suggestion": "...",
          "confidence": 0.0-1.0}]
        """.formatted(changed.text(), formatRequirements(all));
}
```

#### T3.2 — Implement `ArchitectureAdvisor` [NEW] [L]
**File:** `core/services-platform/src/main/java/com/ghatana/yappc/platform/ai/analyzers/ArchitectureAdvisor.java`

Activates on **phase transitions** and **significant code changes** (>50 lines):

```java
public Promise<List<AIInsight>> analyze(PhaseTransitionedEvent event) {
    return projectContextBuilder.build(event.projectId(), event.newPhase())
        .then(ctx -> {
            String prompt = """
                Analyze this project's architecture readiness for the %s phase.
                
                Project structure:
                %s
                
                Dependencies:
                %s
                
                Identify: architectural risks, missing patterns, scaling concerns, security gaps.
                Return JSON: [{"type": "ARCHITECTURE", "severity": "...", ...}]
                """.formatted(event.newPhase(), ctx.structureSummary(), ctx.dependencySummary());
            
            return aiService.complete(AIRequest.of(prompt).withWorkflow("architecture_advisory"));
        })
        .map(response -> parseArchInsights(response, event));
}
```

---

### Sprint 4 — Frontend Insight Layer (9 days)

#### T4.1 — Create `InsightStream` WebSocket Hook [NEW] [M]
**File:** `frontend/libs/yappc-ai/src/hooks/useInsightStream.ts`

```typescript
interface InsightStreamOptions {
  projectId: string;
  tenantId: string;
  types?: InsightType[];
  minSeverity?: InsightSeverity;
}

interface UseInsightStreamReturn {
  insights: AIInsight[];
  unreadCount: number;
  dismiss: (insightId: string) => Promise<void>;
  dismissAll: () => Promise<void>;
  markRead: (insightId: string) => Promise<void>;
}

export function useInsightStream({
  projectId, tenantId, types, minSeverity = 'WARNING'
}: InsightStreamOptions): UseInsightStreamReturn {
  const [insights, setInsights] = useState<AIInsight[]>([]);
  
  useEffect(() => {
    const ws = new WebSocketClient(`/realtime/insights?projectId=${projectId}`);
    
    ws.on('insight', (insight: AIInsight) => {
      if (shouldShow(insight, types, minSeverity)) {
        setInsights(prev => deduplicateAndSort([insight, ...prev]));
      }
    });
    
    return () => ws.close();
  }, [projectId, tenantId]);
  
  return { insights, unreadCount: insights.filter(i => !i.read).length, dismiss, dismissAll, markRead };
}
```

#### T4.2 — Create Insight Panel Component [NEW] [M]
**File:** `frontend/apps/web/src/features/ai-insights/InsightPanel.tsx`

```typescript
interface InsightPanelProps {
  projectId: string;
  position?: 'right' | 'bottom';
}

const InsightPanel: React.FC<InsightPanelProps> = ({ projectId, position = 'right' }) => {
  const { insights, unreadCount, dismiss } = useInsightStream({ projectId, tenantId: useTenantId() });
  const [isExpanded, setIsExpanded] = useState(false);

  return (
    <aside
      className={`fixed ${position === 'right' ? 'right-0 top-16 h-full w-80' : 'bottom-0 w-full h-48'} 
                  bg-white border-l shadow-lg transition-transform`}
      aria-label="AI Insights Panel"
    >
      <PanelHeader unreadCount={unreadCount} onToggle={() => setIsExpanded(!isExpanded)} />
      {isExpanded && (
        <InsightList insights={insights} onDismiss={dismiss} />
      )}
    </aside>
  );
};
```

#### T4.3 — Create Inline Code Editor Hints [NEW] [M]
**File:** `frontend/libs/code-editor/src/plugins/AIHintsPlugin.ts`

Monaco editor decoration that shows inline AI hints:

```typescript
export function registerAIHintsPlugin(editor: monaco.editor.IStandaloneCodeEditor, insights: AIInsight[]): void {
  const codeInsights = insights.filter(i => i.lineNumber !== null && i.type !== 'ARCHITECTURE');
  
  const decorations = codeInsights.map(insight => ({
    range: new monaco.Range(insight.lineNumber!, 1, insight.lineNumber!, 1),
    options: {
      isWholeLine: false,
      glyphMarginClassName: severityToGlyphClass(insight.severity),
      glyphMarginHoverMessage: {
        value: `**${insight.title}** (${(insight.confidence * 100).toFixed(0)}% confidence)\n\n${insight.description}\n\n**Suggestion:** ${insight.suggestion}`
      },
      className: severityToLineClass(insight.severity),
    }
  }));
  
  editor.deltaDecorations([], decorations);
}
```

#### T4.4 — Notification Badge in App Header [MOD] [S]
**File:** Modify main app header component to show unread insight count:

```typescript
// In main nav/header
const { unreadCount } = useInsightStream({ projectId: currentProject?.id ?? '', tenantId });

<button aria-label={`${unreadCount} AI insights`} onClick={() => setInsightPanelOpen(true)}>
  <BrainIcon />
  {unreadCount > 0 && (
    <span className="absolute -top-1 -right-1 bg-red-500 text-white text-xs rounded-full w-5 h-5 flex items-center justify-center" 
          aria-hidden>
      {unreadCount > 99 ? '99+' : unreadCount}
    </span>
  )}
</button>
```

---

### Sprint 5 — Insight Quality & Learning (8 days)

#### T5.1 — Insight Feedback Loop [NEW] [M]
**File:** `core/services-platform/src/main/java/com/ghatana/yappc/platform/ai/InsightFeedbackService.java`

Track which insights users act on vs dismiss:

```java
public Promise<Void> recordFeedback(String insightId, InsightFeedback feedback) {
    // feedback: ACTED_ON | DISMISSED | INCORRECT | NOT_RELEVANT
    return insightRepository.updateFeedback(insightId, feedback)
        .then(__ -> {
            metrics.recordInsightFeedback(feedback);
            if (feedback == InsightFeedback.INCORRECT) {
                // Flag for model retraining
                modelFeedbackQueue.enqueue(insightId);
            }
            return Promise.ofComplete();
        });
}
```

#### T5.2 — Confidence Calibration via Feedback [NEW] [M]
Use the feedback data to calibrate confidence thresholds per insight type:

```java
/**
 * If CODE_QUALITY insights with confidence > 0.7 have 80% ACTED_ON rate,
 * we can lower the display threshold to 0.6 (show more).
 * If they have < 30% ACTED_ON rate, raise threshold to 0.8 (show fewer, higher quality).
 */
public void recalibrateThresholds() {
    insightFeedbackRepository.computeAccuracyByTypeAndConfidence()
        .forEach((typeConfidence, accuracy) -> {
            double newThreshold = accuracy > 0.6 ? currentThreshold - 0.05 : currentThreshold + 0.05;
            thresholdConfig.update(typeConfidence.type(), newThreshold);
        });
}
```

#### T5.3 — Deduplication and Rate Limiting [NEW] [M]
**File:** `core/services-platform/src/main/java/com/ghatana/yappc/platform/ai/InsightDeduplicator.java`

Rules:
- Same insight (same type + same location) within 1 hour → suppress
- Same insight dismissed by user → suppress for 24 hours
- More than 5 CRITICAL insights in 10 minutes → batch into digest
- User in "focus mode" → suppress INFO and WARNING; show only ERROR and CRITICAL

---

### Sprint 6 — Performance Advisor (8 days)

#### T6.1 — Implement `PerformanceAdvisor` [NEW] [L]
**File:** `core/services-platform/src/main/java/com/ghatana/yappc/platform/ai/analyzers/PerformanceAdvisor.java`

Activates on code changes in critical paths (detected via KG impact analysis):

```java
public Promise<List<AIInsight>> analyze(CodeChangedEvent event) {
    // Check if changed file is in a known hot path (via KG)
    return knowledgeGraph.impactAnalysis(event.fileNodeId())
        .then(impact -> {
            if (impact.totalAffected() < 5) return Promise.of(List.of());  // low-impact change, skip
            
            String prompt = buildPerformancePrompt(event, impact);
            return aiService.complete(AIRequest.of(prompt).withWorkflow("performance_analysis"))
                .map(response -> parsePerformanceInsights(response, event));
        });
}
```

---

## 4. Testing Requirements

| Test | Scenarios |
|------|-----------|
| `BackgroundAnalysisPipelineTest` | Event processed → insights generated → published |
| `ChangeDebouncerTest` | Rapid events debounced; single analysis triggered |
| `CodeQualityAnalyzerTest` | Issues found; no issues; AI failure → empty |
| `SecurityPatternDetectorTest` | SQL injection detected; hardcoded secret detected; clean code → empty |
| `TestGapDetectorTest` | New method without test → gap detected |
| `RequirementsConsistencyCheckerTest` | Conflict detected; duplicate detected |
| `InsightDeduplicatorTest` | Duplicate suppressed; rate limit enforced |
| `InsightFeedbackServiceTest` | Feedback stored; threshold recalibrated |

### User Experience Tests (Playwright)

```typescript
test('AI insights appear within 5s of code change without user action', async ({ page }) => {
  await page.goto('/project/test-project/code');
  await simulateCodeChange(page, 'src/auth.ts', 'const password = "hardcoded123";');
  await expect(page.locator('[data-testid="insight-panel"]')).toBeVisible({ timeout: 5000 });
  await expect(page.locator('[data-testid="insight-security"]')).toContainText('hardcoded');
});

test('User can dismiss insight and it does not reappear for 24h', async ({ page }) => {
  // ... test dismissal persistence
});
```

---

## 5. Observability

```
yappc_insights_generated_total{type, severity, workflow}     counter
yappc_insights_published_total{type, severity}               counter
yappc_insights_dismissed_total{type, reason}                 counter
yappc_insights_acted_on_total{type}                          counter
yappc_insights_accuracy_rate{type}                           gauge (from feedback)
yappc_background_analysis_duration_seconds{analyzer}        histogram
yappc_debouncer_suppressed_events_total                      counter
yappc_insight_display_threshold{type}                        gauge (calibrated threshold)
```

### Quality Alert

```yaml
- alert: InsightAccuracyDegraded
  expr: yappc_insights_accuracy_rate < 0.5
  for: 24h
  annotations:
    summary: "AI insight accuracy below 50% — review model or prompts"
```
