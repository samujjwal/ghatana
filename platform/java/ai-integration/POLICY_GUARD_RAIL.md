# Policy Guard Rail for LLM Calls

## Overview

The policy guard rail integrates OPA (Open Policy Agent) policy evaluation into the LLM call flow. All LLM requests are evaluated against policies before being executed, ensuring compliance with governance rules.

## Architecture

```
LLM Request → PolicyGuardRail → OPA Policy Evaluation
                                    ↓
                              Allow → LLMGateway → LLM Provider
                                    ↓
                              Deny → PolicyDeniedException
```

## Usage

### Basic Setup

```java
import com.ghatana.ai.llm.PolicyGuardRail;
import com.ghatana.platform.pac.OpaClient;

// Create OPA client
Executor executor = Executors.newFixedThreadPool(4);
OpaClient opaClient = new OpaClient("http://opa:8181", executor);

// Create LLM gateway
LLMGateway gateway = DefaultLLMGateway.builder()
    .addProvider("openai", openAIService)
    .metrics(metricsCollector)
    .build();

// Wrap with policy guard rail
LLMGateway guardedGateway = new PolicyGuardRail(
    gateway,
    opaClient,
    "llm.guard"  // default policy path
);
```

### Policy Evaluation

The guard rail evaluates policies with the following input:

```json
{
  "input": {
    "prompt": "...",
    "model": "...",
    "temperature": 0.7,
    "maxTokens": 1000,
    "metadata": {
      "tenantId": "tenant-123",
      "policyPath": "llm.guard"
    }
  }
}
```

### OPA Policy Example

```rego
package llm.guard

default allow = false

allow {
    not contains(input.prompt, "blocked")
    input.temperature <= 1.0
    input.maxTokens <= 4000
}
```

### Custom Policy Path

Specify a custom policy path in request metadata:

```java
CompletionRequest request = CompletionRequest.builder()
    .prompt("test")
    .metadata(Map.of(
        "tenantId", "tenant-123",
        "policyPath", "custom.policy.path"
    ))
    .build();
```

## Error Handling

When policy denies a request, a `PolicyDeniedException` is thrown:

```java
try {
    CompletionResult result = guardedGateway.complete(request);
} catch (PolicyGuardRail.PolicyDeniedException e) {
    logger.warn("Request denied: {}", e.getMessage());
    PolicyEvalResult result = e.getPolicyResult();
    // Handle denial (return error to user, log, etc.)
}
```

## Policy Evaluation Pattern

1. **Intercept**: Guard rail intercepts all LLM calls except embeddings
2. **Evaluate**: Calls OPA with request context
3. **Allow/Deny**: Based on OPA response
4. **Execute**: If allowed, proceeds to LLM gateway
5. **Block**: If denied, returns PolicyDeniedException

## Current Status

As of the platform coverage audit (P3-29), the policy guard rail is implemented with:
- PolicyGuardRail class wrapping LLMGateway
- OPA integration via PolicyAsCodeEngine
- Policy evaluation before all LLM operations
- Custom policy path support via metadata
- Comprehensive test coverage

## Testing

See `PolicyGuardRailTest` for test coverage including:
- Allow when policy permits
- Deny when policy denies
- Custom policy path handling
- Embedding bypass (no policy check)

## Resources

- OPA Documentation: https://www.openpolicyagent.org/docs/latest/
- Rego Policy Language: https://www.openpolicyagent.org/docs/latest/policy-language/
