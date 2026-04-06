# AI-Native Testing & QA — Detailed Implementation Plan

**Priority:** P1 HIGH  
**Current State:** Static template stubs; `TestGenerationAgent` exists in `core/agents/testing-specialists/` but generates from templates only; no AI-powered test case generation, no mutation analysis, no flaky test detection  
**Target State:** AI-generated test suites from requirements, intelligent coverage optimization, flaky test detection, and test prioritization  
**Estimated Effort:** 4 sprints (~30 engineer-days)

---

## 1. Current State Analysis

### What Exists

| Component | Location | Status |
|-----------|----------|--------|
| `TestGenerationAgent.java` | `core/agents/testing-specialists/` | ✅ Exists — template-only |
| Tools in `core/agents/runtime/tools/` | Jacoco, Checkstyle, ArchUnit, PMD | ✅ Wired |
| `YAPPCAgentBase.java` | `core/agents/runtime/` | ✅ Agent base |
| AI test generation | — | **MISSING** |
| Edge case generation | — | **MISSING** |
| Test data generation | — | **MISSING** |
| Flaky test detection | — | **MISSING** |
| Test prioritization | — | **MISSING** |
| Coverage optimization | — | **MISSING** |

---

## 2. Architecture Overview

```
Trigger: Code change / Requirement approved / Phase gate
  │
  ▼
TestOrchestrationService
  ├── TestGapAnalyzer
  │     ├── Reads Jacoco report (SQL or JSON output)
  │     ├── Identifies untested methods/paths
  │     └── Prioritizes by risk (critical path + KG centrality)
  │
  ├── TestGenerationService (AI-powered)
  │     ├── Context collection (code + requirements + existing tests)
  │     ├── Test case specification generation (AI → list of test scenarios)
  │     ├── Test code generation (AI → actual test code)
  │     ├── Test data generation (AI → varied, edge-case-rich input sets)
  │     └── Quality validation (syntax + coverage increase estimation)
  │
  ├── FlakyCandidateDetector
  │     ├── Analyzes test history (timing variance, environmental dependencies)
  │     ├── Detects time-based, non-deterministic, and order-dependent tests
  │     └── Reports with AI-generated fix suggestions
  │
  ├── TestPrioritizer
  │     ├── Sorts test suite by change impact (code change coverage)
  │     ├── Failure probability (historical data)
  │     └── Returns ordered test run plan for CI/CD
  │
  └── MutationTestingAdvisor
        ├── Integrates with PIT mutation testing reports
        ├── Identifies surviving mutants (under-tested logic)
        └── Generates targeted tests to kill surviving mutants
```

---

## 3. Domain Models

### Test Specification Model [NEW]

```java
public record TestSpec(
    String testSpecId,
    String projectId,
    String tenantId,
    String targetClass,         // class under test
    String targetMethod,        // method under test (null = full class)
    String requirementId,       // linked requirement (nullable)
    List<TestScenario> scenarios,
    TestSpecStatus status,      // GENERATED | REVIEWED | APPROVED | IMPLEMENTED
    double aiConfidence,
    Instant generatedAt
) {}

public record TestScenario(
    String description,         // human-readable "Given/When/Then" scenario
    TestCategory category,      // HAPPY_PATH | EDGE_CASE | BOUNDARY | ERROR | SECURITY
    String generatedCode,       // actual test code (Java/TypeScript)
    TestDataSet testData,       // generated test inputs
    boolean requiresManualReview // high-risk scenarios
) {}

public enum TestCategory {
    HAPPY_PATH, EDGE_CASE, BOUNDARY_VALUE, NULL_INPUT, ERROR_HANDLING,
    SECURITY, CONCURRENCY, PERFORMANCE
}
```

### Database Schema

```sql
-- V009__test_ai_tables.sql

CREATE TABLE test_specs (
    test_spec_id     VARCHAR(36)    PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id       VARCHAR(36)    NOT NULL,
    tenant_id        VARCHAR(128)   NOT NULL,
    target_class     TEXT           NOT NULL,
    target_method    TEXT,
    requirement_id   VARCHAR(36),
    scenarios        JSONB          NOT NULL,
    status           VARCHAR(50)    NOT NULL DEFAULT 'GENERATED',
    ai_confidence    DECIMAL(4,2),
    generated_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE TABLE flaky_test_reports (
    report_id        VARCHAR(36)    PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id       VARCHAR(36)    NOT NULL,
    tenant_id        VARCHAR(128)   NOT NULL,
    test_class       TEXT           NOT NULL,
    test_method      TEXT           NOT NULL,
    flakiness_score  DECIMAL(5,4),  -- 0 = stable, 1 = always flaky
    flakiness_pattern VARCHAR(100), -- TIME_DEPENDENT | ORDER_DEPENDENT | RESOURCE_RACE | EXTERNAL_DEPENDENCY
    ai_fix_suggestion TEXT,
    first_detected_at TIMESTAMPTZ,
    last_seen_at     TIMESTAMPTZ
);

CREATE TABLE test_run_history (
    run_id           BIGSERIAL      PRIMARY KEY,
    project_id       VARCHAR(36)    NOT NULL,
    tenant_id        VARCHAR(128)   NOT NULL,
    test_class       TEXT           NOT NULL,
    test_method      TEXT           NOT NULL,
    duration_ms      INTEGER        NOT NULL,
    result           VARCHAR(20)    NOT NULL,  -- PASS | FAIL | SKIPPED
    environment      VARCHAR(100),
    ran_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_test_history_method ON test_run_history(project_id, test_class, test_method);
CREATE INDEX idx_test_history_result ON test_run_history(project_id, result, ran_at);
```

---

## 4. Implementation Tasks

### Sprint 1 — Test Generation (8 days)

#### T1.1 — Create `TestSpecificationGenerator` [NEW] [L]
**File:** `core/agents/testing-specialists/TestSpecificationGenerator.java`

LLM generates test *scenarios* first (before code), to separate the "what to test" from "how to code it":

```java
/**
 * @doc.type class
 * @doc.purpose AI-generates structured test scenarios from code and requirements before generating test code.
 * @doc.layer product
 * @doc.pattern Generator
 */
public final class TestSpecificationGenerator {
    private final YAPPCAIService aiService;
    private final RequirementRepository requirementRepository;
    
    public Promise<List<TestScenario>> generateScenarios(String classSource, String requirementId, String tenantId) {
        return requirementRepository.findById(requirementId, tenantId)
            .then(requirement -> {
                String prompt = """
                    You are a QA expert. Given this Java class and its requirement, generate comprehensive test scenarios.
                    
                    Requirement: %s
                    Acceptance Criteria: %s
                    
                    Class under test:
                    %s
                    
                    Generate test scenarios as JSON array:
                    [
                      {
                        "description": "Given valid input, when method called, then returns expected result",
                        "category": "HAPPY_PATH|EDGE_CASE|BOUNDARY_VALUE|NULL_INPUT|ERROR_HANDLING|SECURITY|CONCURRENCY",
                        "inputs": {"param1": "value1"},
                        "expectedOutcome": "...",
                        "requiresManualReview": false
                      }
                    ]
                    
                    Include: happy paths (2-3), boundary values (2-4), null/empty inputs, error conditions,
                    at least 1 security test if method touches auth/data.
                    """.formatted(
                        requirement.title(), requirement.acceptanceCriteria(), classSource
                    );
                
                return aiService.complete(AIRequest.of(prompt).withWorkflow("test_scenario_gen"))
                    .map(response -> parseScenarios(response));
            });
    }
}
```

#### T1.2 — Create `TestCodeGenerator` [NEW] [L]
**File:** `core/agents/testing-specialists/TestCodeGenerator.java`

Translates scenarios to actual JUnit/Vitest code:

```java
/**
 * @doc.type class
 * @doc.purpose Generates runnable test code from test scenario specifications.
 * @doc.layer product
 * @doc.pattern Generator
 */
public final class TestCodeGenerator {
    private final YAPPCAIService aiService;
    
    public Promise<String> generateTestCode(
            String classSource, List<TestScenario> scenarios, TestFramework framework) {
        
        String scenarioList = scenarios.stream()
            .map(s -> "- " + s.category() + ": " + s.description())
            .collect(Collectors.joining("\n"));
        
        String existingTestExample = findExistingTestIfAny(classSource);
        
        String prompt = """
            Generate a complete %s test class for this Java class.
            Follow the exact test framework patterns shown in the example below.
            
            Class under test:
            %s
            
            Existing test example to follow (match imports, style, annotations):
            %s
            
            Test scenarios to implement:
            %s
            
            Requirements:
            - Use @DisplayName with descriptive names
            - Use Mockito for dependencies
            - Use AssertJ for assertions
            - Follow AAA (Arrange/Act/Assert) structure
            - Test class: %sTest.java
            Output ONLY the Java code, no markdown.
            """.formatted(
                framework.name(), classSource, existingTestExample,
                scenarioList, extractClassName(classSource)
            );
        
        return aiService.complete(AIRequest.of(prompt)
                .withWorkflow("test_code_gen")
                .withPreferredModel("codellama:34b"))
            .map(AIResponse::content);
    }
}
```

#### T1.3 — Upgrade `TestGenerationAgent` to Use LLM [MOD] [M]
**File:** `core/agents/testing-specialists/TestGenerationAgent.java`

Replace template-based generation with `TestSpecificationGenerator` + `TestCodeGenerator`:

```java
@Override
public Promise<AgentResult<GeneratedTests>> process(AgentContext ctx, TestGenerationRequest request) {
    return specGenerator.generateScenarios(request.classSource(), request.requirementId(), ctx.tenantId())
        .then(scenarios -> codeGenerator.generateTestCode(request.classSource(), scenarios, TestFramework.JUNIT5))
        .then(code -> qualityValidator.validate(code, request.targetPath()))
        .map(validated -> AgentResult.success(new GeneratedTests(
            validated.code(), request.requirementId(), validated.quality())));
}
```

---

### Sprint 2 — Test Gap Analysis (8 days)

#### T2.1 — Create `TestGapAnalyzer` [NEW] [L]
**File:** `core/agents/testing-specialists/TestGapAnalyzer.java`

```java
/**
 * @doc.type class
 * @doc.purpose Identifies untested code paths by integrating Jacoco coverage data with KG centrality scores to prioritize high-risk gaps.
 * @doc.layer product
 * @doc.pattern Analyzer
 */
public final class TestGapAnalyzer {
    private final JacocoReportReader jacocoReader;
    private final KGQueryService knowledgeGraph;
    private final YAPPCAIService aiService;
    
    public Promise<List<TestGap>> analyze(String projectId, String tenantId) {
        return Promises.all(
            jacocoReader.readLatestReport(projectId),
            knowledgeGraph.getCodeModuleCentrality(projectId, tenantId)  // KG: how central is each module?
        ).map((coverageReport, centrality) -> {
            return coverageReport.uncoveredMethods().stream()
                .map(method -> {
                    double riskScore = centrality.getOrDefault(method.className(), 0.0)
                        * (1.0 - method.coverage())   // higher uncoverage = higher risk
                        * method.complexity();         // more complex = higher risk
                    return new TestGap(method, riskScore);
                })
                .sorted(Comparator.comparingDouble(TestGap::riskScore).reversed())
                .limit(20)   // top 20 highest risk gaps
                .toList();
        });
    }
}
```

#### T2.2 — Jacoco Report Reader [NEW] [M]
**File:** `core/agents/testing-specialists/report/JacocoReportReader.java`

Reads Jacoco XML/HTML reports from the file system (or build output):

```java
public Promise<CoverageReport> readLatestReport(String projectId) {
    Path jacocoReportPath = resolvePath(projectId, "build/reports/jacoco/test/jacocoTestReport.xml");
    return Promise.ofBlocking(executor, () -> {
        Document doc = xmlParser.parse(jacocoReportPath.toFile());
        return parseJacocoReport(doc);
    });
}
```

#### T2.3 — Test Gap API + Frontend Panel [NEW] [M]
**File (Java):** REST controller endpoint  
**File (React):** `frontend/apps/web/src/features/testing/TestGapPanel.tsx`

```typescript
const TestGapPanel: React.FC<{ projectId: string }> = ({ projectId }) => {
  const { data: gaps, isLoading } = useQuery({
    queryKey: ['project', projectId, 'test-gaps'],
    queryFn: () => fetchTestGaps(projectId),
    refetchInterval: 5 * 60_000,  // refresh every 5 min
  });

  return (
    <div>
      <h3>Test Coverage Gaps</h3>
      <p>Top untested methods by risk score</p>
      {gaps?.map(gap => (
        <GapCard key={gap.methodId} gap={gap}>
          <button onClick={() => generateTestsForGap(gap)}>
            Generate Tests
          </button>
        </GapCard>
      ))}
    </div>
  );
};
```

---

### Sprint 3 — Flaky Test Detection (7 days)

#### T3.1 — Create `FlakyTestDetector` [NEW] [L]
**File:** `core/agents/testing-specialists/FlakyTestDetector.java`

```java
/**
 * @doc.type class
 * @doc.purpose Detects flaky tests by analyzing execution history and classifying flakiness patterns with AI assistance.
 * @doc.layer product
 * @doc.pattern Analyzer
 */
public final class FlakyTestDetector {
    private final TestRunHistoryRepository historyRepository;
    private final YAPPCAIService aiService;
    private static final double FLAKINESS_THRESHOLD = 0.1; // >10% failure rate = suspect
    
    public Promise<List<FlakyTestReport>> detectFlaky(String projectId, String tenantId) {
        return historyRepository.findTestsWithVariableResults(projectId, tenantId, 30)  // last 30 days
            .then(suspects -> Promises.all(
                suspects.stream()
                    .filter(s -> s.failureRate() > FLAKINESS_THRESHOLD)
                    .map(s -> analyzeFlakiness(s, tenantId))
                    .toList()
            ));
    }
    
    private Promise<FlakyTestReport> analyzeFlakiness(TestRunSummary summary, String tenantId) {
        // Classify pattern deterministically first
        FlakinessPattern pattern = classifyPattern(summary);
        
        // AI generates fix suggestion
        return aiService.complete(buildFlakinessDiagnosticPrompt(summary, pattern))
            .map(response -> FlakyTestReport.builder()
                .testClass(summary.testClass())
                .testMethod(summary.testMethod())
                .flakinessScore(summary.failureRate())
                .pattern(pattern)
                .aiFixSuggestion(response.content())
                .build());
    }
    
    private FlakinessPattern classifyPattern(TestRunSummary summary) {
        // Check timing correlation
        if (hasTimingCorrelation(summary)) return FlakinessPattern.TIME_DEPENDENT;
        // Check if failures cluster at list position
        if (hasOrderDependency(summary)) return FlakinessPattern.ORDER_DEPENDENT;
        // Check if environment varies
        if (hasEnvironmentCorrelation(summary)) return FlakinessPattern.EXTERNAL_DEPENDENCY;
        return FlakinessPattern.RESOURCE_RACE;
    }
}
```

#### T3.2 — Test Run History Recorder [NEW] [M]
**File:** `core/agents/testing-specialists/history/TestRunHistoryRepository.java`

Populated by CI/CD events posted to the YAPPC API after each test run.

```java
// POST /api/v1/testing/runs — called by CI/CD pipeline
public Promise<HttpResponse> recordTestRun(HttpRequest request) {
    TestRunBatch batch = mapper.parseBody(request.getBody(), TestRunBatch.class);
    return historyRepository.saveBatch(batch)
        .then(saved -> {
            // Trigger async flaky detection if meaningful size
            if (batch.tests().size() > 0) {
                flakyDetector.detectFlaky(batch.projectId(), batch.tenantId());
            }
            return Promise.of(HttpResponse.ok200());
        });
}
```

---

### Sprint 4 — Test Prioritization & Frontend QA Dashboard (7 days)

#### T4.1 — Create `TestPrioritizer` [NEW] [M]
**File:** `core/agents/testing-specialists/TestPrioritizer.java`

```java
/**
 * @doc.type class
 * @doc.purpose Ranks tests by failure probability and code change coverage to minimize feedback time in CI.
 * @doc.layer product
 * @doc.pattern Ranker
 */
public final class TestPrioritizer {
    
    public Promise<List<PrioritizedTest>> prioritize(
            String projectId, List<String> changedFiles, String tenantId) {
        return Promises.all(
            historyRepository.getFailureRates(projectId, tenantId),
            coverageMapper.mapChangedFilesToTests(changedFiles, projectId)
        ).map((failureRates, impactedTests) -> {
            return impactedTests.stream()
                .map(test -> {
                    double failureProb = failureRates.getOrDefault(test.fullName(), 0.5);
                    double impactScore = test.coverageOverlapRatio(); // how much does test cover changed code
                    double priority = (impactScore * 0.7) + (failureProb * 0.3);
                    return new PrioritizedTest(test, priority);
                })
                .sorted(Comparator.comparingDouble(PrioritizedTest::priority).reversed())
                .toList();
        });
    }
}
```

#### T4.2 — QA Dashboard [NEW] [M]
**File:** `frontend/apps/web/src/features/testing/QADashboard.tsx`

Comprehensive QA overview:

```typescript
interface QADashboardProps {
  projectId: string;
}

const QADashboard: React.FC<QADashboardProps> = ({ projectId }) => {
  const { data: summary } = useQuery({
    queryKey: ['project', projectId, 'qa-summary'],
    queryFn: () => fetchQASummary(projectId),
    refetchInterval: 30_000,
  });

  return (
    <div className="grid grid-cols-2 gap-4 p-4">
      <CoverageCard
        overall={summary?.overallCoverage ?? 0}
        byModule={summary?.coverageByModule ?? []}
      />
      <FlakyCandidatesCard
        count={summary?.flakyTestCount ?? 0}
        tests={summary?.topFlakyTests ?? []}
      />
      <TestGapCard
        highRiskGaps={summary?.highRiskGapCount ?? 0}
        topGaps={summary?.topGaps ?? []}
        onGenerateTests={handleGenerateTests}
      />
      <GeneratedTestsCard
        totalGenerated={summary?.generatedTestCount ?? 0}
        acceptanceRate={summary?.generatedTestAcceptanceRate ?? 0}
      />
    </div>
  );
};
```

#### T4.3 — Mutation Testing Integration [NEW] [M]

Integrate PIT mutation testing reports (generated outside YAPPC) and parse surviving mutants to drive targeted test generation:

```java
// core/agents/testing-specialists/mutation/MutationTestingAdvisor.java

/**
 * @doc.type class
 * @doc.purpose Parses PIT mutation testing reports and generates targeted tests to kill surviving mutants.
 * @doc.layer product
 * @doc.pattern Advisor
 */
public final class MutationTestingAdvisor {
    
    public Promise<List<MutantKillerTest>> advise(String projectId, String tenantId) {
        return pitReportReader.readSurvivingMutants(projectId)
            .then(mutants -> Promises.all(
                mutants.stream().limit(10).map(m -> generateKillerTest(m, tenantId)).toList()
            ));
    }
    
    private Promise<MutantKillerTest> generateKillerTest(SurvivingMutant mutant, String tenantId) {
        String prompt = """
            This code mutation survived testing (no test caught it):
            
            Original line: %s
            Mutated to: %s
            In method: %s
            
            Write a specific JUnit5 test that would detect and fail for the mutation.
            Output ONLY the test method code.
            """.formatted(mutant.originalCode(), mutant.mutatedCode(), mutant.methodName());
        
        return aiService.complete(AIRequest.of(prompt).withWorkflow("mutation_test_gen"))
            .map(response -> new MutantKillerTest(mutant, response.content()));
    }
}
```

---

## 5. Testing Requirements

| Test | Key Scenarios |
|------|--------------|
| `TestSpecificationGeneratorTest` | Generates happy path, edge cases, error scenarios |
| `TestCodeGeneratorTest` | Valid JUnit5 syntax; uses AssertJ; matches class under test |
| `TestGapAnalyzerTest` | Identifies untested methods; prioritizes by risk + KG centrality |
| `FlakyTestDetectorTest` | High variance → detected; stable tests clean; pattern classification |
| `TestPrioritizerTest` | Changed files → prioritized test order; high failure rate weighted |
| `MutationTestingAdvisorTest` | Surviving mutant → killer test generated |

---

## 6. Observability

```
yappc_test_gen_requests_total{trigger, status}               counter
yappc_test_gen_duration_seconds                              histogram
yappc_test_gen_acceptance_rate                               gauge
yappc_test_gap_count{risk_level}                            gauge
yappc_flaky_tests_detected_total{pattern}                    counter
yappc_overall_test_coverage_pct                              gauge
yappc_mutation_score_pct                                     gauge
yappc_test_prioritization_time_saved_seconds                 histogram
```
