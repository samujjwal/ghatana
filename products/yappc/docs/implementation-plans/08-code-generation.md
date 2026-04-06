# AI-Native Code Generation — Detailed Implementation Plan

**Priority:** P0 HIGH  
**Current State:** Template-based only; `LLMPoweredGenerator` and `LLMGeneratorFactory` exist in agent runtime but LLM integration path is unclear; no context-aware generation  
**Target State:** Fully LLM-powered, context-aware code generation with iterative refinement and quality validation  
**Estimated Effort:** 4 sprints (~32 engineer-days)

---

## 1. Current State Analysis

### What Exists

| Component | Location | Status |
|-----------|----------|--------|
| `LLMGeneratorFactory.java` | `core/agents/runtime/` | ✅ Exists |
| `LLMPoweredGenerator.java` | `core/agents/runtime/` | ✅ Exists — LLM path unclear |
| `core/scaffold/api/` | Scaffold module | ✅ API layer |
| `core/scaffold/core/` | Same | ✅ Templates + engine + generators |
| `core/scaffold/templates/` | Same | ✅ Framework templates (React, Express, Spring Boot) |
| `core/scaffold/engine/` | Same | ✅ Template engine |
| `core/scaffold/generators/` | Same | ✅ Generator impls |
| `YAPPCPromptTemplates.java` | `core/agents/runtime/prompts/` | ✅ Prompt templates |
| `AgentPromptTemplate.java` | Same | ✅ Agent prompt templates |
| Context-aware generation | — | **MISSING** |
| Iterative refinement | — | **MISSING** |
| Quality validation of generated code | — | **MISSING** |
| Style consistency enforcement | — | **MISSING** |
| Frontend code gen UI | `frontend/apps/web/src/` | ⚠️ Exists but LLM path unverified |

---

## 2. Target Architecture

```
User Request / Agent Trigger
  │
  ▼
CodeGenerationService
  ├── 1. ContextCollector
  │     ├── ProjectStructureContext (existing files, modules)
  │     ├── RequirementContext (linked requirements from KG)
  │     ├── StyleContext (existing code patterns via AST analysis)
  │     └── DependencyContext (available libraries in project)
  │
  ├── 2. PromptBuilder
  │     ├── Selects template (new module | implement method | fix bug | add test)
  │     ├── Injects context chunks (RAG-style)
  │     └── Adds style constraints from StyleContext
  │
  ├── 3. LLMPoweredGenerator (existing, now properly wired)
  │     ├── Calls YAPPCAIService.complete()
  │     ├── Streaming response for large generations
  │     └── Returns raw generated code
  │
  ├── 4. CodeQualityValidator
  │     ├── Syntax check (AST parse)
  │     ├── Checkstyle / ESLint (non-blocking warning)
  │     ├── AI self-review (second LLM call with lower cost model)
  │     └── Returns QualityReport{valid, issues, suggestions}
  │
  ├── 5. IterativeRefiner (optional, high-confidence needed)
  │     ├── If quality < threshold: re-prompt with quality feedback
  │     ├── Max 3 refinement iterations
  │     └── Returns best-quality version
  │
  └── 6. GenerationResult
        ├── Generated code
        ├── Confidence score
        ├── Quality report
        ├── Applied style rules
        └── Linked requirement IDs
```

---

## 3. Generation Targets

| Target | Prompt Strategy | Context Needed | Estimated Tokens |
|--------|----------------|----------------|-----------------|
| New REST endpoint | Template + OpenAPI spec | Module structure, existing routes | 800 |
| Implement interface method | Signature + KG neighbors | Interface, implementing classes | 600 |
| Unit test for class | Class source + coverage gaps | Class under test, test framework | 1000 |
| Database migration | Schema change spec | Current schema, past migrations | 500 |
| React component | Design spec + existing components | Component library, design system | 1200 |
| Fix bug (diff mode) | Bug description + stacktrace | Failing code, error context | 700 |

---

## 4. Implementation Tasks

### Sprint 1 — Context Collection & Prompt Building (8 days)

#### T1.1 — Create `GenerationContextCollector` [NEW] [L]
**File:** `core/scaffold/core/src/main/java/com/ghatana/yappc/scaffold/context/GenerationContextCollector.java`

```java
/**
 * @doc.type class
 * @doc.purpose Collects multi-source context for LLM code generation to maximize relevance and style consistency.
 * @doc.layer product
 * @doc.pattern Context Builder
 */
public final class GenerationContextCollector {
    private final KGQueryService knowledgeGraph;
    private final ProjectStructureReader structureReader;
    private final CodeStyleExtractor styleExtractor;
    
    public Promise<GenerationContext> collect(GenerationRequest request) {
        return Promises.all(
            structureReader.readProjectStructure(request.projectId()),
            knowledgeGraph.semanticSearch(request.description(), request.tenantId(), 5),
            styleExtractor.extractStyleFromSimilarFiles(request.projectId(), request.targetPath())
        ).map((structure, relatedNodes, styleRules) ->
            GenerationContext.builder()
                .projectStructure(structure)
                .relatedRequirements(relatedNodes)
                .styleRules(styleRules)
                .targetPath(request.targetPath())
                .targetFramework(structure.detectFramework())
                .availableDependencies(structure.dependencies())
                .build()
        );
    }
}
```

#### T1.2 — Create `CodeStyleExtractor` [NEW] [M]
**File:** `core/scaffold/core/src/main/java/com/ghatana/yappc/scaffold/context/CodeStyleExtractor.java`

Uses AST analysis to extract style patterns:
- Naming conventions (camelCase, snake_case, etc.)
- Import ordering
- Class/method ordering patterns
- Comment style (Javadoc vs inline)
- Error handling patterns (try/catch vs Result type)

```java
public Promise<StyleRules> extractStyleFromSimilarFiles(String projectId, String targetPath) {
    String fileExtension = getExtension(targetPath);
    String targetDir = getDirectory(targetPath);
    
    // Find similar files adjacent to target
    return structureReader.findFilesInDir(projectId, targetDir, fileExtension, 3)
        .then(similarFiles -> {
            String combinedSource = similarFiles.stream()
                .map(f -> f.source())
                .collect(Collectors.joining("\n\n---\n\n"));
            
            String prompt = STYLE_EXTRACTION_PROMPT.formatted(combinedSource);
            return aiService.complete(AIRequest.of(prompt).withWorkflow("style_extraction"))
                .map(response -> parseStyleRules(response));
        });
}
```

#### T1.3 — Create `GenerationPromptBuilder` [NEW] [M]
**File:** `core/scaffold/core/src/main/java/com/ghatana/yappc/scaffold/prompt/GenerationPromptBuilder.java`

RAG-style prompt construction with context injection:

```java
public String build(GenerationRequest request, GenerationContext context) {
    return """
        You are an expert %s developer. Generate %s following the project's established patterns.
        
        ## Project Style Rules
        %s
        
        ## Related Requirements (for context)
        %s
        
        ## Similar Existing Files (follow these patterns)
        %s
        
        ## Your Task
        %s
        
        Target file path: %s
        Available dependencies: %s
        
        Generate complete, production-ready code. Include proper error handling,
        documentation, and follow the style rules above exactly.
        Output ONLY the code, no markdown fences.
        """.formatted(
            context.targetFramework(),
            request.generationType(),
            context.styleRules().format(),
            context.relatedRequirements().stream().map(KGNode::label).collect(joining("\n- ", "- ", "")),
            context.exampleFiles(),
            request.description(),
            context.targetPath(),
            context.availableDependencies()
        );
}
```

---

### Sprint 2 — LLM Generator Integration (8 days)

#### T2.1 — Wire `LLMPoweredGenerator` to `YAPPCAIService` [MOD] [L]
**File:** `core/agents/runtime/LLMPoweredGenerator.java`

Verify and fix the actual LLM call path. Add streaming support for large generations:

```java
/**
 * @doc.type class
 * @doc.purpose Generates code using LLM with streaming support for long outputs.
 * @doc.layer product  
 * @doc.pattern Generator
 */
public final class LLMPoweredGenerator {
    private final YAPPCAIService aiService;
    
    public Promise<GeneratedCode> generate(String prompt, GenerationRequest request) {
        AIRequest aiRequest = AIRequest.builder()
            .prompt(prompt)
            .workflow("code_generation")
            .preferredModel("codellama:34b")  // code-specialized model
            .maxTokens(4096)
            .temperature(0.2)   // low temperature for determinism
            .build();
        
        return aiService.complete(aiRequest)
            .map(response -> GeneratedCode.builder()
                .code(response.content())
                .confidence(response.confidence())
                .provider(response.provider())
                .model(response.model())
                .tokenCount(response.promptTokens() + response.completionTokens())
                .build());
    }
    
    /** Streaming version for real-time preview in frontend */
    public Flux<String> generateStream(String prompt) {
        return aiService.stream(AIRequest.of(prompt).withWorkflow("code_generation_stream"));
    }
}
```

#### T2.2 — Create `CodeGenerationService` Orchestrator [NEW] [L]
**File:** `core/scaffold/core/src/main/java/com/ghatana/yappc/scaffold/CodeGenerationService.java`

```java
/**
 * @doc.type class
 * @doc.purpose Orchestrates full AI code generation pipeline: context → prompt → generate → validate → refine.
 * @doc.layer product
 * @doc.pattern Service
 */
public final class CodeGenerationService {
    private final GenerationContextCollector contextCollector;
    private final GenerationPromptBuilder promptBuilder;
    private final LLMPoweredGenerator generator;
    private final CodeQualityValidator validator;
    private final IterativeRefiner refiner;
    private final CostTrackingService costTracker;
    
    public Promise<GenerationResult> generate(GenerationRequest request, UserPrincipal caller) {
        rbacEvaluator.requirePermission(caller, YappcPermission.CODE_GENERATE);
        
        return contextCollector.collect(request)
            .then(context -> {
                String prompt = promptBuilder.build(request, context);
                return generator.generate(prompt, request);
            })
            .then(generated -> validator.validate(generated, request.targetPath()))
            .then(validated -> {
                if (validated.quality().passesThreshold()) {
                    return Promise.of(GenerationResult.success(validated));
                }
                return refiner.refine(validated, request, 3);
            })
            .then(result -> {
                costTracker.record(result.tokenCount(), request.workflow(), caller.tenantId());
                knowledgeGraph.updateWithGeneratedCode(result, request);
                return Promise.of(result);
            });
    }
}
```

#### T2.3 — Create Streaming API Endpoint [NEW] [M]
**File:** `core/scaffold/api/...`

```java
// GET /api/v1/code-generation/stream?requestId={id}
// Server-Sent Events (SSE) for streaming code preview
public Promise<HttpResponse> streamGeneration(HttpRequest request) {
    String requestId = request.getQueryParameter("requestId");
    
    return HttpResponse.ofCode(200)
        .withHeader(CONTENT_TYPE, "text/event-stream")
        .withHeader("Cache-Control", "no-cache")
        .withBody(stream -> {
            generationStream.subscribe(requestId, chunk -> {
                stream.writeAsync("data: " + chunk + "\n\n");
            });
        });
}
```

---

### Sprint 3 — Code Validation & Iterative Refinement (8 days)

#### T3.1 — Create `CodeQualityValidator` [NEW] [M]
**File:** `core/scaffold/core/src/main/java/com/ghatana/yappc/scaffold/validation/CodeQualityValidator.java`

```java
/**
 * @doc.type class
 * @doc.purpose Validates generated code for syntactic correctness, style compliance, and AI self-review.
 * @doc.layer product
 * @doc.pattern Validator
 */
public final class CodeQualityValidator {
    
    public Promise<ValidatedCode> validate(GeneratedCode code, String targetPath) {
        List<ValidationIssue> issues = new ArrayList<>();
        
        // Step 1: Parse (syntax check)
        ParseResult parseResult = parseCode(code.code(), targetPath);
        if (!parseResult.isValid()) {
            return Promise.of(ValidatedCode.syntaxError(code, parseResult.errors()));
        }
        
        // Step 2: Static analysis (non-blocking warnings)
        List<ValidationIssue> staticIssues = runStaticAnalysis(code, targetPath);
        issues.addAll(staticIssues);
        
        // Step 3: AI self-review (second LLM call with review prompt)
        return aiService.complete(buildSelfReviewPrompt(code))
            .map(reviewResponse -> {
                issues.addAll(parseReviewIssues(reviewResponse));
                double qualityScore = computeQualityScore(issues);
                return ValidatedCode.of(code, issues, qualityScore);
            });
    }
    
    private String buildSelfReviewPrompt(GeneratedCode code) {
        return """
            Review this generated code for issues before it is shown to the user.
            Check: correctness, completeness, error handling, security, performance.
            
            Code:
            %s
            
            Return JSON: {"issues": [{"severity": "ERROR|WARNING", "description": "...", "line": N}], "score": 0-10}
            """.formatted(code.code());
    }
}
```

#### T3.2 — Create `IterativeRefiner` [NEW] [M]
**File:** `core/scaffold/core/src/main/java/com/ghatana/yappc/scaffold/refinement/IterativeRefiner.java`

```java
/**
 * @doc.type class
 * @doc.purpose Re-prompts the LLM with quality feedback to iteratively improve generated code.
 * @doc.layer product
 * @doc.pattern Refiner
 */
public final class IterativeRefiner {
    
    public Promise<GenerationResult> refine(ValidatedCode code, GenerationRequest request, int maxAttempts) {
        if (maxAttempts == 0 || code.quality().passesThreshold()) {
            return Promise.of(GenerationResult.success(code));
        }
        
        String refinementPrompt = buildRefinementPrompt(code);
        return generator.generate(refinementPrompt, request)
            .then(refined -> validator.validate(refined, request.targetPath()))
            .then(revalidated -> refine(revalidated, request, maxAttempts - 1));
    }
    
    private String buildRefinementPrompt(ValidatedCode code) {
        String issueList = code.issues().stream()
            .map(i -> "- Line %d: %s".formatted(i.line(), i.description()))
            .collect(joining("\n"));
        
        return """
            This code has quality issues. Fix all issues and return the corrected code only.
            
            Issues found:
            %s
            
            Original code:
            %s
            """.formatted(issueList, code.code().code());
    }
}
```

---

### Sprint 4 — Frontend Code Generation UI (8 days)

#### T4.1 — Create Code Generation Panel [NEW] [L]
**File:** `frontend/apps/web/src/features/code-gen/CodeGenerationPanel.tsx`

```typescript
interface CodeGenerationPanelProps {
  projectId: string;
  defaultTargetPath?: string;
  onCodeGenerated?: (result: GenerationResult) => void;
}

const CodeGenerationPanel: React.FC<CodeGenerationPanelProps> = ({
  projectId, defaultTargetPath, onCodeGenerated
}) => {
  const [description, setDescription] = useState('');
  const [targetPath, setTargetPath] = useState(defaultTargetPath ?? '');
  const [generationType, setGenerationType] = useState<GenerationType>('NEW_MODULE');
  const { generate, isGenerating, result, streamedCode } = useCodeGeneration();

  const handleGenerate = useCallback(async () => {
    await generate({ projectId, description, targetPath, generationType });
  }, [projectId, description, targetPath, generationType]);

  return (
    <div className="flex flex-col gap-4 p-4">
      <GenerationTypeSelector value={generationType} onChange={setGenerationType} />
      <textarea
        value={description}
        onChange={e => setDescription(e.target.value)}
        placeholder="Describe what you want to generate..."
        className="w-full h-32 p-3 border rounded-lg font-mono text-sm"
        aria-label="Code generation description"
      />
      <PathInput value={targetPath} onChange={setTargetPath} projectId={projectId} />
      
      <button onClick={handleGenerate} disabled={isGenerating || !description}>
        {isGenerating ? 'Generating...' : 'Generate Code'}
      </button>
      
      {(streamedCode || result) && (
        <CodePreviewPanel
          code={streamedCode ?? result?.code ?? ''}
          isStreaming={isGenerating}
          qualityReport={result?.qualityReport}
          confidence={result?.confidence}
          onApply={() => applyToEditor(result!.code, targetPath)}
          onRefine={() => refineWithFeedback(result!)}
        />
      )}
    </div>
  );
};
```

#### T4.2 — Create `useCodeGeneration` Hook [NEW] [M]
**File:** `frontend/libs/yappc-ai/src/hooks/useCodeGeneration.ts`

```typescript
interface UseCodeGenerationReturn {
  generate: (request: GenerationRequest) => Promise<void>;
  refine: (feedback: string) => Promise<void>;
  isGenerating: boolean;
  streamedCode: string | null;
  result: GenerationResult | null;
  error: Error | null;
}

export function useCodeGeneration(): UseCodeGenerationReturn {
  const [streamedCode, setStreamedCode] = useState<string | null>(null);
  const [result, setResult] = useState<GenerationResult | null>(null);
  const [isGenerating, setIsGenerating] = useState(false);

  const generate = useCallback(async (request: GenerationRequest) => {
    setIsGenerating(true);
    setStreamedCode('');
    setResult(null);
    
    try {
      // Stream code preview
      const stream = await codeGenApi.generateStream(request);
      for await (const chunk of stream) {
        setStreamedCode(prev => (prev ?? '') + chunk);
      }
      
      // Get final validated result
      const finalResult = await codeGenApi.getResult(request.requestId);
      setResult(finalResult);
    } finally {
      setIsGenerating(false);
    }
  }, []);
  
  return { generate, refine, isGenerating, streamedCode, result, error };
}
```

#### T4.3 — Code Preview Panel with Diff View [NEW] [M]
**File:** `frontend/apps/web/src/features/code-gen/CodePreviewPanel.tsx`

```typescript
interface CodePreviewPanelProps {
  code: string;
  isStreaming: boolean;
  qualityReport?: QualityReport;
  confidence?: number;
  onApply: () => void;
  onRefine: () => void;
}

const CodePreviewPanel: React.FC<CodePreviewPanelProps> = ({
  code, isStreaming, qualityReport, confidence, onApply, onRefine
}) => (
  <div className="border rounded-lg overflow-hidden">
    <div className="flex items-center justify-between p-2 bg-gray-50 border-b">
      <div className="flex items-center gap-2">
        {confidence !== undefined && (
          <AIConfidenceIndicator
            confidence={confidence}
            confidenceLevel={scoreToLevel(confidence)}
            needsHumanReview={confidence < 0.6}
          />
        )}
        {isStreaming && <span className="text-xs text-gray-500 animate-pulse">Generating...</span>}
      </div>
      <div className="flex gap-2">
        <button onClick={onRefine} className="text-sm px-3 py-1 border rounded">Refine</button>
        <button onClick={onApply} disabled={isStreaming} className="text-sm px-3 py-1 bg-blue-600 text-white rounded">
          Apply to Editor
        </button>
      </div>
    </div>
    
    {qualityReport && qualityReport.issues.length > 0 && (
      <QualityIssuesList issues={qualityReport.issues} />
    )}
    
    <MonacoEditor value={code} language={detectLanguage(code)} readOnly options={{ minimap: { enabled: false } }} />
  </div>
);
```

---

## 5. Testing Requirements

| Test | Scenarios |
|------|-----------|
| `GenerationContextCollectorTest` | Context assembled from KG + project structure |
| `CodeStyleExtractorTest` | Style rules extracted from sample files |
| `GenerationPromptBuilderTest` | Prompt contains context, style, requirements |
| `LLMPoweredGeneratorTest` | Code generated from prompt; streaming response |
| `CodeQualityValidatorTest` | Valid code passes; syntax error detected; AI review finds issues |
| `IterativeRefinerTest` | 1st attempt fails; 2nd refinement passes; max 3 attempts |
| `CodeGenerationServiceTest` | Full pipeline; RBAC check; cost recorded |

### Integration Test

```java
@Test
void codeGenerationPipelineShouldProduceValidJavaFile() {
    GenerationRequest request = GenerationRequest.builder()
        .projectId("test-project")
        .description("Create a REST controller for user profile management")
        .targetPath("src/main/java/com/example/ProfileController.java")
        .generationType(GenerationType.NEW_MODULE)
        .build();
    
    GenerationResult result = runPromise(() -> 
        codeGenerationService.generate(request, testPrincipal()));
    
    assertThat(result.code()).contains("class ProfileController");
    assertThat(result.qualityReport().score()).isGreaterThan(7.0);
    assertThat(result.confidence()).isGreaterThan(0.6);
}
```

---

## 6. Observability

```
yappc_code_gen_requests_total{type, status}                  counter
yappc_code_gen_duration_seconds{type}                        histogram
yappc_code_gen_quality_score{type}                           histogram
yappc_code_gen_confidence{type}                              histogram
yappc_code_gen_refinement_iterations{type}                   histogram (0-3)
yappc_code_gen_tokens_total{type, provider}                  counter
yappc_code_gen_validation_failures{reason}                   counter
yappc_code_gen_self_review_issues_found                      histogram
```
