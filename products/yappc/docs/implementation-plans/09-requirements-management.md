# AI-Native Requirements Management — Detailed Implementation Plan

**Priority:** P0 HIGH  
**Current State:** CRUD only — `RequirementAIService` and `RequirementEmbeddingService` exist but AI-assisted writing, semantic duplicate detection, and traceability are not wired  
**Target State:** AI-assisted requirements writing with semantic analysis, duplicate detection, traceability, and impact assessment  
**Estimated Effort:** 3 sprints (~24 engineer-days)

---

## 1. Current State Analysis

### What Exists

```
core/ai/src/main/java/com/ghatana/yappc/ai/requirements/
├── ai/
│   ├── RequirementAIService.java        ✅ AI service interface
│   ├── RequirementEmbeddingService.java ✅ Embedding service
│   └── (implementations unclear)
├── api/
│   ├── [REST controllers]               ✅ Controllers exist
│   └── [DTOs]                           ✅ DTOs exist
├── application/
│   ├── ProjectService.java              ✅ Project management
│   └── WorkspaceService.java            ✅ Workspace management
└── domain/
    ├── Project.java                     ✅ Domain model
    ├── Requirement.java                 ✅ Requirement model
    ├── Workspace.java                   ✅ Workspace model
    └── [status enums]                   ✅ Status models
```

### Critical Gaps

1. **AI-assisted writing not active** — `RequirementAIService` not called when creating/editing requirements  
2. **Duplicate detection not wired** — `RequirementEmbeddingService` exists but similarity search not triggered  
3. **No quality validation** — vague requirements pass without suggestion to improve
4. **No traceability** — requirements are not linked to code modules, tests, or decisions in the KG
5. **No impact analysis** — changing a requirement doesn't show what code/tests are affected
6. **No conflict detection** — contradictory requirements can coexist

---

## 2. Target Architecture

```
User Types Requirement
  │
  ├── [Proactive] AI drafts acceptance criteria as user types (debounced)
  ├── [Reactive] AI validates requirement on save
  └── [Background] ConsistencyChecker runs against all project requirements
       │
       ▼
RequirementService
  ├── RequirementAIEnricher
  │     ├── AI-draft acceptance criteria
  │     ├── Complexity estimator (S/M/L/XL)
  │     ├── Dependency identifier (links to other requirements)
  │     └── Risk assessor
  │
  ├── DuplicateDetector
  │     └── RequirementEmbeddingService.findSimilar() → warn if > 0.9 similarity
  │
  ├── QualityValidator
  │     ├── Completeness check (has acceptance criteria?)
  │     ├── Testability check (is it testable?)
  │     ├── Ambiguity check (vague terms: "fast", "user-friendly")
  │     └── Returns QualityScore with improvement suggestions
  │
  ├── TraceabilityLinker
  │     └── KnowledgeGraph.createEdge(requirement → implementations)
  │
  └── ImpactAnalyzer
        └── KG.impactAnalysis(requirementNodeId) → affected code, tests
```

---

## 3. Domain Model Enhancements

### Enhanced `Requirement` Model [MOD]

```java
public record Requirement(
    String requirementId,
    String projectId,
    String tenantId,
    String title,
    String description,
    String acceptanceCriteria,         // NEW: AI-drafted, human-edited
    RequirementType type,              // FUNCTIONAL | NON_FUNCTIONAL | CONSTRAINT
    RequirementPriority priority,      // MUST | SHOULD | COULD | WONT
    RequirementStatus status,          // DRAFT | UNDER_REVIEW | APPROVED | IMPLEMENTED | ARCHIVED
    Complexity complexity,             // S | M | L | XL — AI estimated
    QualityScore qualityScore,         // NEW: AI-computed quality signal
    List<String> linkedRequirementIds, // NEW: AI-detected dependencies
    List<String> implementedByModules, // NEW: KG traceability links
    List<String> testedByModules,      // NEW: KG traceability links
    float[] embedding,                 // NEW: vector for similarity search
    Instant createdAt,
    Instant updatedAt,
    String createdBy
) {}
```

### Database Additions

```sql
-- V008__requirements_ai_enhancements.sql

ALTER TABLE requirements ADD COLUMN acceptance_criteria TEXT;
ALTER TABLE requirements ADD COLUMN complexity VARCHAR(10);
ALTER TABLE requirements ADD COLUMN quality_score DECIMAL(4,2);
ALTER TABLE requirements ADD COLUMN quality_issues JSONB;
ALTER TABLE requirements ADD COLUMN embedding vector(1536);
ALTER TABLE requirements ADD COLUMN linked_requirement_ids TEXT[];

-- Traceability links (normalized)
CREATE TABLE requirement_implementations (
    requirement_id VARCHAR(36) NOT NULL REFERENCES requirements(requirement_id),
    module_path    TEXT NOT NULL,
    link_type      VARCHAR(50) NOT NULL,  -- IMPLEMENTS | TESTS | DECIDED_BY
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (requirement_id, module_path, link_type)
);

CREATE INDEX idx_req_embedding ON requirements USING ivfflat (embedding vector_cosine_ops);
```

---

## 4. Implementation Tasks

### Sprint 1 — AI Enrichment on Write (8 days)

#### T1.1 — Create `RequirementAIEnricher` [NEW] [L]
**File:** `core/ai/src/main/java/com/ghatana/yappc/ai/requirements/ai/RequirementAIEnricher.java`

```java
/**
 * @doc.type class
 * @doc.purpose AI-powers requirement creation by drafting acceptance criteria, estimating complexity, and identifying risks.
 * @doc.layer product
 * @doc.pattern Enricher
 */
public final class RequirementAIEnricher {
    private final YAPPCAIService aiService;
    
    public Promise<RequirementEnrichment> enrich(String title, String description, String projectContext) {
        String prompt = """
            You are a product requirements expert. Enrich this requirement:
            
            Title: %s
            Description: %s
            Project context: %s
            
            Return JSON:
            {
              "acceptanceCriteria": "Given/When/Then format...",
              "complexity": "S|M|L|XL",
              "complexity_rationale": "...",
              "risks": ["risk1", "risk2"],
              "dependencies": ["may depend on: ..."],
              "testabilityScore": 0.0-1.0,
              "ambiguousTerms": ["fast", "easy"],
              "suggestedTitle": "improved title if needed"
            }
            """.formatted(title, description, projectContext);
        
        return aiService.complete(AIRequest.of(prompt).withWorkflow("requirement_enrichment"))
            .map(response -> parseEnrichment(response));
    }
}
```

#### T1.2 — Create `RequirementQualityValidator` [NEW] [M]
**File:** `core/ai/src/main/java/com/ghatana/yappc/ai/requirements/ai/RequirementQualityValidator.java`

```java
public final class RequirementQualityValidator {
    
    private static final List<String> AMBIGUOUS_TERMS = List.of(
        "fast", "quickly", "easily", "user-friendly", "simple", "intuitive",
        "performant", "scalable", "modern", "efficient"
    );
    
    public Promise<QualityScore> validate(Requirement requirement) {
        List<QualityIssue> issues = new ArrayList<>();
        
        // Deterministic checks (fast, no LLM needed)
        if (requirement.acceptanceCriteria() == null || requirement.acceptanceCriteria().isBlank()) {
            issues.add(QualityIssue.error("Missing acceptance criteria", "Add Given/When/Then criteria"));
        }
        
        AMBIGUOUS_TERMS.stream()
            .filter(term -> requirement.description().toLowerCase().contains(term))
            .forEach(term -> issues.add(QualityIssue.warning(
                "Ambiguous term: \"" + term + "\"",
                "Define what \"" + term + "\" means in measurable terms (e.g., latency < 200ms)")));
        
        if (requirement.title().length() < 10) {
            issues.add(QualityIssue.warning("Title too short", "Provide a descriptive title"));
        }
        
        // AI-powered testability check
        return aiService.complete(buildTestabilityPrompt(requirement))
            .map(response -> {
                double testabilityScore = parseTestabilityScore(response);
                if (testabilityScore < 0.6) {
                    issues.add(QualityIssue.warning("Low testability",
                        "Rewrite as specific, verifiable behavior: " + response.content()));
                }
                return QualityScore.of(issues, testabilityScore);
            });
    }
}
```

#### T1.3 — Create `DuplicateDetector` [NEW] [M]
**File:** `core/ai/src/main/java/com/ghatana/yappc/ai/requirements/ai/DuplicateDetector.java`

```java
public final class DuplicateDetector {
    private final RequirementEmbeddingService embeddingService;
    private final RequirementRepository repository;
    private static final double DUPLICATE_THRESHOLD = 0.92;
    private static final double SIMILAR_THRESHOLD = 0.75;
    
    public Promise<DuplicateAnalysis> analyze(String requirementText, String projectId, String tenantId) {
        return embeddingService.embed(requirementText)
            .then(embedding -> repository.findSimilar(embedding, projectId, tenantId, 10))
            .map(similar -> {
                List<SimilarRequirement> duplicates = similar.stream()
                    .filter(r -> r.similarity() >= DUPLICATE_THRESHOLD)
                    .toList();
                List<SimilarRequirement> related = similar.stream()
                    .filter(r -> r.similarity() >= SIMILAR_THRESHOLD && r.similarity() < DUPLICATE_THRESHOLD)
                    .toList();
                return new DuplicateAnalysis(duplicates, related);
            });
    }
}
```

#### T1.4 — Wire Enrichment into `RequirementService` [MOD] [M]
**File:** `core/ai/src/main/java/com/ghatana/yappc/ai/requirements/application/RequirementService.java` (or create if missing)

```java
public Promise<Requirement> createRequirement(CreateRequirementCommand cmd, UserPrincipal caller) {
    return Promises.all(
        aiEnricher.enrich(cmd.title(), cmd.description(), cmd.projectContext()),
        duplicateDetector.analyze(cmd.description(), cmd.projectId(), caller.tenantId()),
        qualityValidator.validate(buildDraftRequirement(cmd))
    ).then((enrichment, duplicateAnalysis, quality) -> {
        if (duplicateAnalysis.hasDuplicates()) {
            // Return warning with duplicate list; don't save yet
            return Promise.of(RequirementCreationResult.duplicateWarning(duplicateAnalysis));
        }
        
        Requirement requirement = buildRequirement(cmd, enrichment, quality);
        return repository.save(requirement)
            .then(saved -> {
                // Update KG asynchronously
                knowledgeGraph.addRequirementNode(saved);
                // Embed and store vector
                embeddingService.embed(saved);
                return Promise.of(RequirementCreationResult.success(saved));
            });
    });
}
```

---

### Sprint 2 — Traceability & Impact (8 days)

#### T2.1 — Implement Requirement → Code Traceability [NEW] [M]
**File:** `core/ai/src/main/java/com/ghatana/yappc/ai/requirements/traceability/TraceabilityLinker.java`

```java
/**
 * @doc.type class
 * @doc.purpose Establishes and maintains traceability links between requirements and their implementations.
 * @doc.layer product
 * @doc.pattern Linker
 */
public final class TraceabilityLinker {
    private final KGQueryService knowledgeGraph;
    private final YAPPCAIService aiService;
    
    /** Find code modules that implement a requirement (AI-matched). */
    public Promise<List<TraceabilityLink>> discoverImplementations(Requirement requirement) {
        // Search KG for code nodes semantically similar to this requirement
        return knowledgeGraph.semanticSearch(requirement.title() + " " + requirement.description(),
                requirement.tenantId(), 10)
            .then(candidates -> aiService.complete(buildTraceabilityPrompt(requirement, candidates)))
            .map(response -> parseTraceabilityLinks(response));
    }
    
    /** Record that a code commit implements a requirement. */
    public Promise<Void> linkToCommit(String requirementId, String commitHash, String tenantId) {
        return knowledgeGraph.createEdge(
            requirementId, commitHash, "IMPLEMENTED_BY",
            Map.of("commitHash", commitHash, "linkedAt", Instant.now().toString()),
            tenantId
        ).toVoid();
    }
}
```

#### T2.2 — Auto-Link on Code Commit [MOD] [M]
**File:** `core/knowledge-graph/src/main/java/com/ghatana/kg/pipeline/KGUpdatePipeline.java` [MOD]

When processing a `CodeCommittedEvent`:
1. Extract requirement IDs mentioned in the commit message (e.g., `#REQ-123`)
2. AI-match the diff content to unlinked requirements
3. Create `IMPLEMENTS` edges in KG for confirmed links

```java
private Promise<KGUpdateResult> processCodeCommit(CodeCommittedEvent event) {
    // 1. Parse explicit requirement references from commit message
    List<String> explicitRefs = parseRequirementRefs(event.commitMessage());
    
    // 2. AI-match implicit references
    return requirementRepository.findUnlinkedByProject(event.projectId())
        .then(unlinked -> traceabilityLinker.matchCommitToRequirements(event, unlinked))
        .then(matched -> {
            List<String> allRefs = Stream.concat(explicitRefs.stream(), matched.stream()).distinct().toList();
            return Promises.all(allRefs.stream()
                .map(reqId -> traceabilityLinker.linkToCommit(reqId, event.commitHash(), event.tenantId()))
                .toList());
        });
}
```

#### T2.3 — Expose Traceability in Requirements API [MOD] [M]
**File:** Requirements REST controller

```java
// GET /api/v1/requirements/{requirementId}/traceability
public Promise<HttpResponse> getTraceability(HttpRequest request) {
    String requirementId = request.getPathParameter("requirementId");
    UserPrincipal caller = TenantContext.getCurrentUser();
    
    return requirementService.getTraceability(requirementId, caller)
        .map(traceability -> HttpResponse.ok200()
            .withBody(mapper.toTraceabilityDto(traceability)));
}

// GET /api/v1/requirements/{requirementId}/impact
public Promise<HttpResponse> getImpact(HttpRequest request) {
    String requirementId = request.getPathParameter("requirementId");
    return kgImpactAnalyzer.analyze(requirementId, TenantContext.getCurrentUser().tenantId())
        .map(impact -> HttpResponse.ok200().withBody(mapper.toImpactDto(impact)));
}
```

---

### Sprint 3 — Frontend Requirements AI UX (8 days)

#### T3.1 — Requirements Editor with AI Assist [MOD] [M]
**File:** `frontend/apps/web/src/features/requirements/RequirementEditor.tsx`

```typescript
const RequirementEditor: React.FC<RequirementEditorProps> = ({ projectId, requirement, onSave }) => {
  const [title, setTitle] = useState(requirement?.title ?? '');
  const [description, setDescription] = useState(requirement?.description ?? '');
  const { enrichment, isEnriching } = useRequirementEnrichment(title, description);

  return (
    <form onSubmit={handleSave} className="space-y-4">
      <TitleInput value={title} onChange={setTitle} />
      <DescriptionInput value={description} onChange={setDescription} />
      
      {/* AI-suggested acceptance criteria */}
      {enrichment?.acceptanceCriteria && (
        <AIAssistPanel
          title="AI-suggested acceptance criteria"
          confidence={enrichment.confidence}
        >
          <AcceptanceCriteriaPreview
            value={enrichment.acceptanceCriteria}
            onApply={(text) => setAcceptanceCriteria(text)}
          />
        </AIAssistPanel>
      )}
      
      {/* Quality warnings */}
      {enrichment?.ambiguousTerms?.map(term => (
        <InlineWarning key={term}>
          Ambiguous term: "<strong>{term}</strong>" — consider defining it more precisely
        </InlineWarning>
      ))}
      
      {/* Complexity & risks */}
      {enrichment && (
        <AIInsightRow
          complexity={enrichment.complexity}
          risks={enrichment.risks}
          isLoading={isEnriching}
        />
      )}
      
      {/* Duplicate warning */}
      <DuplicateWarningPanel projectId={projectId} description={description} />
      
      <SaveButton disabled={isEnriching} />
    </form>
  );
};
```

#### T3.2 — `useRequirementEnrichment` Hook [NEW] [M]
**File:** `frontend/libs/yappc-ai/src/hooks/useRequirementEnrichment.ts`

```typescript
export function useRequirementEnrichment(title: string, description: string) {
  return useQuery({
    queryKey: ['requirement', 'enrichment', title, description],
    queryFn: () => enrichRequirement({ title, description }),
    enabled: description.length > 30,  // only enrich if meaningful content
    debounce: 1000,                     // wait 1s after last keystroke
    staleTime: 60_000,
  });
}
```

#### T3.3 — Traceability Graph Panel [NEW] [M]
**File:** `frontend/apps/web/src/features/requirements/TraceabilityPanel.tsx`

Shows for a selected requirement:
- Linked code modules (with file paths)
- Linked tests (with coverage status)
- Related decisions (ADRs, approval decisions)
- Impact score (how many things depend on this)

```typescript
const TraceabilityPanel: React.FC<{ requirementId: string }> = ({ requirementId }) => {
  const { data: traceability } = useQuery({
    queryKey: ['requirement', requirementId, 'traceability'],
    queryFn: () => fetchRequirementTraceability(requirementId),
  });

  return (
    <div className="p-4 space-y-3">
      <TraceabilitySection title="Implemented by" items={traceability?.implementations} icon={Code} />
      <TraceabilitySection title="Tested by" items={traceability?.tests} icon={TestTube} />
      <TraceabilitySection title="Related decisions" items={traceability?.decisions} icon={GitMerge} />
      <ImpactScoreBadge score={traceability?.impactScore} affectedCount={traceability?.affectedCount} />
    </div>
  );
};
```

#### T3.4 — Requirements Dashboard with AI Metrics [NEW] [M]
**File:** `frontend/apps/web/src/features/requirements/RequirementsDashboard.tsx`

Show project-level requirements health:
- Quality score distribution (pie chart)
- Coverage: % of requirements with acceptance criteria
- Traceability: % linked to code
- Duplicate detection: unresolved duplicates count
- AI enrichment adoption: % of requirements AI-enriched

---

## 5. Testing Requirements

| Test | Key Scenarios |
|------|--------------|
| `RequirementAIEnricherTest` | Acceptance criteria generated; complexity estimated |
| `RequirementQualityValidatorTest` | Ambiguous terms flagged; missing AC flagged; high quality passes |
| `DuplicateDetectorTest` | Near-identical text → duplicate found; distinct text → clean |
| `TraceabilityLinkerTest` | Commit with `#REQ-xxx` → link created; AI-matched link created |
| `RequirementServiceTest` | Full create flow; duplicate warning returned before save |

---

## 6. Observability

```
yappc_requirements_created_total{enriched, has_ac}           counter
yappc_requirements_quality_score                             histogram
yappc_requirements_duplicate_warnings_total                  counter
yappc_requirements_traceability_links_total{link_type}       counter
yappc_requirements_ai_enrichment_duration_seconds            histogram
yappc_requirements_coverage_pct{has_ac, has_trace}          gauge
```
