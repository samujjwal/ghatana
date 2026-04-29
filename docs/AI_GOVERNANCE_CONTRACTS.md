# Unified AI Governance Contracts

**Established:** 2026-04-23  
**Status:** ACTIVE  
**Scope:** YAPPC, AEP, Platform AI integration modules  
**Owner:** Platform AI Integration + YAPPC AI governance  

---

## Overview

All AI-driven operations across Ghatana products must comply with unified governance contracts. These contracts establish:
- **Model lifecycle management** (versioning, approval, deployment)
- **Prompt engineering standards** (validation, versioning, storage)
- **Tool execution safety** (permissions, sandboxing, access control)
- **Evaluation thresholds** (quality gates, accuracy targets)
- **Data redaction** (prompt input, tool output, trace payload, audit data)
- **Approval telemetry** (audit trail, metrics, decision logs)

These are **runtime-enforced contracts**, not advisory guidelines.

---

## Contract 1: Unified Model Versioning

### Purpose
Establish single source of truth for AI model identity, version, and deployment status across all products.

### Contract Definition

```java
/**
 * @doc.type interface
 * @doc.purpose Unified model identity and versioning across all AI operations
 * @doc.layer platform
 */
public interface UnifiedModelContract {
    
    record ModelIdentity(
        String modelId,              // e.g., "gpt-4-turbo-2024-04-09"
        String provider,             // e.g., "openai", "anthropic", "llama"
        String version,              // e.g., "2024-04-09" (immutable, tied to provider version)
        Instant approvedAt,          // Deployment approval timestamp
        String approvedBy,           // User/service that approved
        ModelTier tier,              // PRODUCTION, STAGING, EXPERIMENTAL
        Map<String, String> metadata // Custom fields (context_window, etc.)
    ) {}
    
    enum ModelTier {
        PRODUCTION,      // Approved for critical workflows
        STAGING,         // Under validation, non-user-facing only
        EXPERIMENTAL,    // Research/dev, never user-facing
        DEPRECATED       // Sunset path, no new deployments
    }
    
    /**
     * Resolve model identity at runtime (validates against approved catalog).
     * MUST fail fast if model not approved for operation's tier requirement.
     */
    ModelIdentity resolveModelIdentity(String modelId, OperationContext ctx)
        throws UnapprovedModelException;
    
    /**
     * Audit log: Every model invocation MUST record this.
     */
    void recordModelInvocation(
        ModelIdentity model,
        String operationId,
        OperationTier tier,        // CRITICAL, HIGH, MEDIUM, LOW
        String requestHash,        // SHA256 of sanitized request
        String responseHash,       // SHA256 of sanitized response
        Duration latency,
        String tenantId
    );
}
```

### Runtime Enforcement

**Location**: `platform:java:ai-integration`  
**Service**: `UnifiedModelRegistry`  

Every AI operation MUST:
1. Call `resolveModelIdentity(modelId)` at start
2. Validate returned tier matches operation requirement (PRODUCTION model for user workflow)
3. Call `recordModelInvocation()` after completion
4. Fail hard if model lookup fails or tier mismatch

**Example (Java)**:
```java
public class CodeGenerationAgent extends AbstractTypedAgent<CodeGenRequest, GeneratedCode> {
    @Override
    public Promise<AgentResult<GeneratedCode>> process(AgentContext ctx, CodeGenRequest input) {
        var modelId = "gpt-4-turbo-2024-04-09";
        var model = modelRegistry.resolveModelIdentity(modelId, ctx);
        
        if (model.tier != ModelTier.PRODUCTION) {
            throw new UnapprovedModelException($"Model {modelId} not approved for production workflow");
        }
        
        return llmGateway.invoke(model, prompt)
            .then(response -> {
                modelRegistry.recordModelInvocation(model, ctx.operationId(), ...);
                return response;
            });
    }
}
```

---

## Contract 2: Unified Prompt Versioning

### Purpose
Establish immutable, auditable prompt storage with version history, approval gates, and rollback safety.

### Contract Definition

```java
/**
 * @doc.type interface
 * @doc.purpose Unified prompt versioning and approval gates
 * @doc.layer platform
 */
public interface UnifiedPromptContract {
    
    record PromptVersion(
        String promptId,             // e.g., "code-gen-v2024-q2"
        int version,                 // Monotonic: 1, 2, 3, ...
        String template,             // Template with {variable} placeholders
        PromptStatus status,         // DRAFT, STAGING, APPROVED, DEPRECATED
        Instant approvedAt,          // Null if not yet approved
        String approvedBy,
        String changeNotes,          // Audit trail entry
        Map<String, Object> schema   // JSON Schema for variable validation
    ) {}
    
    enum PromptStatus {
        DRAFT,                       // Author editing only
        STAGING,                     // Ready for review/testing
        APPROVED,                    // Allowed in user-facing workflows
        DEPRECATED                   // Sunset path, no new deployments
    }
    
    /**
     * Retrieve immutable prompt version (fails if not APPROVED).
     */
    PromptVersion getApprovedPrompt(String promptId)
        throws UnapprovedPromptException;
    
    /**
     * Create new prompt version (returns as DRAFT).
     */
    PromptVersion createDraft(String promptId, String template, Map<String, Object> schema);
    
    /**
     * Move to STAGING for review (triggers approval workflow).
     */
    void moveToStaging(String promptId, int version, String changeNotes);
    
    /**
     * Approve prompt (admin action, records auditor).
     */
    void approve(String promptId, int version, String approverId);
    
    /**
     * Validate prompt variables against schema before rendering.
     */
    void validateVariables(PromptVersion prompt, Map<String, String> variables)
        throws PromptValidationException;
    
    /**
     * Render prompt with validated variables.
     */
    String renderPrompt(PromptVersion prompt, Map<String, String> variables);
}
```

### Runtime Enforcement

**Location**: `platform:java:ai-integration`  
**Service**: `UnifiedPromptRegistry`  

Every prompt-driven operation MUST:
1. Call `getApprovedPrompt(promptId)` to retrieve — fails if not APPROVED
2. Call `validateVariables()` with user input
3. Call `renderPrompt()` to generate the actual prompt
4. Never construct prompts via string concatenation

**Example (TypeScript/YAPPC)**:
```typescript
async function generateCode(request: CodeGenRequest) {
  const prompt = await promptRegistry.getApprovedPrompt("code-gen-v2024-q2");
  
  promptRegistry.validateVariables(prompt, {
    language: request.language,
    context: request.context,
  });
  
  const renderedPrompt = promptRegistry.renderPrompt(prompt, {
    language: request.language,
    context: request.context,
  });
  
  const response = await llmGateway.invoke(modelRegistry.getModel("gpt-4"), renderedPrompt);
  return response;
}
```

---

## Contract 3: Unified Tool Permissions

### Purpose
Establish declarative tool access control with capability-based security and audit logging.

### Contract Definition

```java
/**
 * @doc.type interface
 * @doc.purpose Unified tool access control and capability management
 * @doc.layer platform
 */
public interface UnifiedToolPermissionContract {
    
    record ToolCapability(
        String toolId,               // e.g., "file-system", "database-query", "email-send"
        String capability,           // e.g., "file:read", "file:write", "db:read", "db:write"
        ToolTier tier,               // RESTRICTED, GUARDED, UNRESTRICTED
        Set<String> allowedOperations,  // e.g., ["list", "read"] for file:read
        Set<String> deniedTenants,   // Explicit blocklist per tenant
        Set<String> allowedTenants   // Explicit allowlist per tenant (if not empty, only these allowed)
    ) {}
    
    enum ToolTier {
        RESTRICTED,       // Admin approval required per-invocation
        GUARDED,          // Runtime validation + audit logging required
        UNRESTRICTED      // Normal validation, no special gates
    }
    
    /**
     * Check if agent has permission to invoke tool capability (in specific tenant context).
     * MUST fail hard if permission denied.
     */
    boolean canInvokeTool(
        String agentId,
        String toolId,
        String capability,
        TenantContext tenant,
        OperationContext operation
    ) throws ToolAccessDeniedException;
    
    /**
     * Get tool capabilities available to an agent (honors tenant + agent permissions).
     */
    Set<ToolCapability> getAvailableCapabilities(String agentId, TenantContext tenant);
    
    /**
     * Audit log: Every tool invocation MUST record this.
     */
    void recordToolInvocation(
        String agentId,
        String toolId,
        String capability,
        String operationId,
        String requestHash,        // SHA256 of sanitized request
        String responseHash,       // SHA256 of sanitized response
        boolean succeeded,
        String tenantId
    );
    
    /**
     * Sandbox tool execution with permission boundaries.
     * Prevents tool from accessing capabilities outside its permissions.
     */
    <T> T executeWithinSandbox(
        String toolId,
        ToolCapability capability,
        Supplier<T> toolLogic
    ) throws ToolAccessDeniedException;
}
```

### Runtime Enforcement

**Location**: `platform:java:agent-core`  
**Service**: `UnifiedToolPermissionManager`  

Every agent MUST:
1. Declare all tool dependencies in manifest (schema: agent-manifest.proto)
2. At runtime, call `canInvokeTool()` before each tool invocation
3. Execute tool logic within `executeWithinSandbox()` to prevent breakout
4. Call `recordToolInvocation()` for audit trail

**Example (Java agent)**:
```java
@ToolProvider
public class FileSystemTool {
    @Inject private UnifiedToolPermissionManager permissions;
    
    public String readFile(String path, AgentContext ctx) throws ToolAccessDeniedException {
        // Check permission
        permissions.canInvokeTool(ctx.agentId(), "file-system", "file:read", 
                                   ctx.tenant(), ctx.operation());
        
        // Execute within sandbox
        return permissions.executeWithinSandbox("file-system", 
            new ToolCapability("file-system", "file:read", ToolTier.GUARDED, ...),
            () -> Files.readString(Paths.get(path))
        );
    }
}
```

---

## Contract 4: Unified Evaluation Thresholds

### Purpose
Establish quality gates for AI outputs with configurable accuracy, latency, and failure thresholds.

### Contract Definition

```java
/**
 * @doc.type interface
 * @doc.purpose Unified evaluation thresholds and quality gates
 * @doc.layer platform
 */
public interface UnifiedEvaluationContract {
    
    record EvaluationThreshold(
        String operationId,          // e.g., "code-gen", "response-classification"
        double minAccuracy,          // e.g., 0.85 for 85%
        Duration maxLatency,         // e.g., PT30S
        int maxRetries,              // Retry budget if below threshold
        FailureStrategy failureMode, // FAIL_HARD, RETRY, FALLBACK, LOG_WARN
        Map<String, Object> config   // Operation-specific thresholds
    ) {}
    
    enum FailureStrategy {
        FAIL_HARD,         // Throw exception to caller
        RETRY,             // Retry with different model/params
        FALLBACK,          // Use fallback implementation
        LOG_WARN           // Log warning, return partial/cached result
    }
    
    /**
     * Evaluate output against thresholds (accuracy, latency, etc).
     * Returns evaluation result with pass/fail + metrics.
     */
    EvaluationResult evaluate(
        String operationId,
        AiOutput output,
        EvaluationThreshold threshold
    );
    
    record EvaluationResult(
        boolean passed,
        double accuracy,
        Duration latency,
        List<String> failures,  // Reason(s) for failing
        FailureStrategy recommended
    ) {}
    
    /**
     * Enforce threshold (evaluate + apply failure strategy).
     */
    void enforceThreshold(
        String operationId,
        AiOutput output,
        EvaluationThreshold threshold
    ) throws OutputThresholdException;
    
    /**
     * Define thresholds by operation (typically loaded from config).
     */
    Map<String, EvaluationThreshold> getThresholds();
}
```

### Runtime Enforcement

**Location**: `platform:java:ai-integration`  
**Service**: `UnifiedEvaluationGate`  

Every AI operation MUST:
1. Define evaluation thresholds in config (by operationId)
2. Call `enforceThreshold()` after receiving output
3. Handle exceptions per failure strategy (RETRY, FALLBACK, etc.)

**Example (YAPPC code generation)**:
```java
var thresholds = new EvaluationThreshold(
    "code-gen",
    0.85,                  // 85% accuracy min
    Duration.ofSeconds(30),
    3,                     // Retry 3 times
    FailureStrategy.FALLBACK
);

var output = codeGenAgent.process(ctx, request).getResult();
evaluationGate.enforceThreshold("code-gen", output, thresholds);
// If accuracy < 85%, apply FALLBACK strategy
```

---

## Contract 5: Unified Data Redaction

### Purpose
Automatically redact PII and sensitive data from prompts, outputs, traces, and audit logs.

### Contract Definition

```java
/**
 * @doc.type interface
 * @doc.purpose Unified PII/sensitive data redaction across AI operations
 * @doc.layer platform
 */
public interface UnifiedRedactionContract {
    
    enum RedactionTarget {
        PROMPT_INPUT,      // User-provided prompt input
        MODEL_OUTPUT,      // Model response/completion
        TRACE_PAYLOAD,     // OpenTelemetry traces
        AUDIT_DATA,        // Audit log entries
        METRICS_LABELS     // Prometheus metric labels
    }
    
    /**
     * Define redaction patterns per tenant + operation.
     */
    record RedactionRule(
        String operationId,
        Set<DataClassification> classifyAs,  // PII, PHI, PCI, CONFIDENTIAL
        String pattern,                      // Regex to match sensitive data
        String replacement,                  // Redaction string (e.g., "[REDACTED-SSN]")
        boolean includeInAuditLog            // Log the fact of redaction (without value)?
    ) {}
    
    enum DataClassification {
        PII,               // Personally identifiable info (name, email, phone, SSN)
        PHI,               // Protected health info (HIPAA/FHIR)
        PCI,               // Payment card industry data
        CONFIDENTIAL,      // Business confidential
        CREDENTIAL         // Passwords, API keys, tokens
    }
    
    /**
     * Redact prompt input before sending to model.
     */
    String redactPromptInput(String prompt, String operationId, TenantContext tenant)
        throws RedactionException;
    
    /**
     * Redact model output before returning to user/logging.
     */
    String redactModelOutput(String output, String operationId, TenantContext tenant)
        throws RedactionException;
    
    /**
     * Redact trace span attributes to prevent PII leakage.
     */
    Map<String, String> redactTraceAttributes(Map<String, String> attrs, 
                                              String operationId, TenantContext tenant);
    
    /**
     * Redact audit log entries.
     */
    AuditLogEntry redactAuditEntry(AuditLogEntry entry, TenantContext tenant);
    
    /**
     * Batch redact (for performance in high-volume scenarios).
     */
    List<String> redactBatch(List<String> texts, String operationId, TenantContext tenant);
}
```

### Runtime Enforcement

**Location**: `platform:java:ai-integration`  
**Service**: `UnifiedRedactionManager`  

Every AI operation MUST:
1. Call `redactPromptInput()` before LLM invocation
2. Call `redactModelOutput()` before response to user
3. Ensure traces use `redactTraceAttributes()` on all spans
4. Ensure audit logs use `redactAuditEntry()` before persistence

**Example (YAPPC)**:
```typescript
async function codeGen(request: CodeGenRequest, tenant: TenantContext) {
  // Redact user input
  const sanitizedPrompt = await redactionManager.redactPromptInput(
    request.prompt,
    "code-gen",
    tenant
  );
  
  // Invoke model
  const output = await llmGateway.invoke(model, sanitizedPrompt);
  
  // Redact output before returning
  const sanitizedOutput = await redactionManager.redactModelOutput(
    output,
    "code-gen",
    tenant
  );
  
  return sanitizedOutput;
}
```

---

## Contract 6: Unified Approval Telemetry

### Purpose
Create immutable audit trail for all AI decisions, approvals, and changes.

### Contract Definition

```java
/**
 * @doc.type interface
 * @doc.purpose Unified audit trail for AI governance decisions
 * @doc.layer platform
 */
public interface UnifiedApprovalTelemetryContract {
    
    record ApprovalEvent(
        String eventId,              // UUID
        Instant timestamp,
        String actor,                // User/service approving/rejecting
        ApprovalAction action,       // APPROVED, REJECTED, MODIFIED
        String resourceType,         // "model", "prompt", "tool"
        String resourceId,           // Model ID, prompt ID, tool ID
        String resourceVersion,
        String rationale,            // Why approved/rejected
        String tenantId,
        Map<String, String> metadata // Custom audit fields
    ) {}
    
    enum ApprovalAction {
        APPROVED,          // Resource moved to APPROVED tier
        REJECTED,          // Resource rejected, returned to DRAFT
        MODIFIED,          // Resource config changed
        DEPRECATED,        // Resource moved to DEPRECATED
        INVOKED            // Resource used in workflow
    }
    
    /**
     * Record approval/rejection event in immutable audit log.
     */
    void recordApprovalEvent(ApprovalEvent event) throws AuditException;
    
    /**
     * Retrieve approval history for resource.
     */
    List<ApprovalEvent> getApprovalHistory(String resourceType, String resourceId);
    
    /**
     * Verify resource has approval chain (trace back to approval).
     */
    ApprovalChain getApprovalChain(String resourceType, String resourceId)
        throws UnapprovedResourceException;
    
    record ApprovalChain(
        List<ApprovalEvent> chain,   // Chronological approval events
        Instant approvalDate,
        String approvedBy
    ) {}
    
    /**
     * Emit metrics: approval latency, rejection rate, resource usage.
     */
    void emitApprovalMetrics(ApprovalEvent event);
}
```

### Runtime Enforcement

**Location**: `platform:java:ai-integration`  
**Service**: `UnifiedApprovalAuditLog`  

Every AI governance action MUST:
1. Call `recordApprovalEvent()` when approving/rejecting/modifying resource
2. Call `getApprovalChain()` to verify approval before deployment
3. Ensure metrics are emitted for dashboard/alerting

---

## Enforcement Points

All contracts are enforced at **runtime** via interceptor pattern:

### Java (ActiveJ)
```java
@ProvidesDependency(AiOperation.class)
public class UnifiedAiEnforcer {
    // Intercepts all @AiOperation annotated methods
    // Validates model, prompt, tool permissions, redaction
    // Enforces evaluation thresholds
}
```

### TypeScript (YAPPC)
```typescript
export function withAiGovernance(operation: AiOperationName) {
  return async function (...args: unknown[]) {
    // Validate model
    await modelRegistry.resolve(operation);
    
    // Validate prompt
    const prompt = await promptRegistry.getApprovedPrompt(operation);
    
    // Validate tools
    for (const tool of operation.tools) {
      await permissions.canInvokeTool(tool);
    }
    
    // Execute operation + redact
    const result = await operation(...args);
    
    // Enforce evaluation
    await evaluationGate.enforce(operation, result);
    
    return result;
  };
}
```

---

## Implementation Roadmap

| Week | Deliverable | Modules |
|------|-------------|---------|
| 1-2 | Model versioning contract + registry | platform:java:ai-integration |
| 2-3 | Prompt versioning contract + approval workflow | platform:java:ai-integration |
| 3-4 | Tool permissions contract + sandbox | platform:java:agent-core |
| 4-5 | Evaluation thresholds + gates | platform:java:ai-integration |
| 5-6 | Data redaction contract + rules engine | platform:java:ai-integration |
| 6-7 | Approval telemetry + audit trail | platform:java:ai-integration |
| 7-8 | Java interceptor enforcement | AEP + YAPPC agents |
| 8-9 | TypeScript enforcement decorators | YAPPC frontend + backend |
| 9-10 | Integration tests + cross-product validation | All AI modules |

---

**Owner**: Platform AI Integration Lead  
**Status**: Draft (awaiting stakeholder review and approval)  
**Review Date**: 2026-05-07

---

## Appendix A: AI Capability Release Status Registry

This registry records the formal release-readiness decision for every AI-backed capability in Ghatana.
Entries here supersede marketing language in product READMEs for governance purposes.
All terms follow the definitions in [docs/process/PRODUCT_TRUTHFULNESS_POLICY.md](process/PRODUCT_TRUTHFULNESS_POLICY.md).

### Audio-Video: AI Voice Module

**Product area**: `products/audio-video/modules/intelligence/ai-voice/`  
**Formal decision date**: 2026-04-28  
**Decision authority**: Platform AI Integration + Audio-Video Product Owner  

| Capability | Phase | Status | Evidence requirement before promotion |
|---|---|---|---|
| Stem separation (Demucs) | D3 | `verified locally` | Reproducible pytest suite in `apps/desktop/tests/`; Demucs dependency must be installed |
| Voice training (RVC/VITS) | D4 | `experimental` | Pipeline scaffolding and UI exist; end-to-end ML quality claims are not release evidence; real model training not validated in CI |
| Voice conversion | D5 | `experimental` | Conversion flow implemented; quality and latency claims are environment-dependent; no reproducible benchmark in CI |
| Multi-track editing | D6 | `verified locally` | Local editor functional; not deployment-validated |
| Export/publish workflows | — | `experimental` | Preview workflow only; no persistent backend evidence |

**Formal governance constraints for D4 and D5 (`experimental` tier)**:

1. **D4 and D5 MUST NOT be presented as `production-ready` or `deployment-validated`** in any product surface (UI, docs, marketing) until the evidence requirements above are met.
2. Any UI surface exposing D4/D5 capabilities MUST display an explicit `Experimental` badge or disclaimer consistent with `PRODUCT_TRUTHFULNESS_POLICY.md` §UI Rules.
3. D4/D5 model paths that integrate with LLM/ML providers MUST register models as `ModelTier.EXPERIMENTAL` in the `UnifiedModelRegistry` (Contract 1 above) before any user-facing invocation.
4. Promotion from `experimental` to `verified in integration` requires: (a) reproducible integration test in CI against a real model, (b) latency and quality benchmarks with a documented baseline, (c) sign-off from Platform AI Integration Lead.
5. Promotion from `verified in integration` to `production-ready` additionally requires: (a) deployment runbook, (b) rollback plan, (c) observable failure modes (metrics + alerts).

**Rationale**: D4 (Voice Training) and D5 (Voice Conversion) contain ML pipeline scaffolding and UI, but the repository does not contain reproducible CI evidence of model-quality claims meeting a defined threshold. Marking them `experimental` is the correct truthful status per `PRODUCT_TRUTHFULNESS_POLICY.md`. This decision is not a defect — it is an honest boundary that protects users from relying on unvalidated capabilities.

**Review cycle**: Re-evaluate this entry when a reproducible CI integration test exists for D4 or D5 that exercises a real model (not a mock). Next scheduled review: 2026-07-01.
