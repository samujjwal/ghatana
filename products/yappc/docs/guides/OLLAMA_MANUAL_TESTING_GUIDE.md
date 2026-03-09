# Ollama Manual E2E Testing Guide

> **Purpose**: Manual testing procedures for validating YAPPC agents with real Ollama inference
> **Status**: Ready for manual execution
> **Last Updated**: January 21, 2026

## Prerequisites

### 1. Install Ollama

```bash
# macOS
brew install ollama

# Start Ollama service (in a separate terminal)
ollama serve
```

### 2. Pull Models

```bash
# Recommended: Llama 3.2 (3.2B parameters, fast)
ollama pull llama3.2

# Alternative: Full Llama 3 (8B parameters, better quality)
ollama pull llama3

# Alternative: Code-specialized
ollama pull codellama
```

### 3. Verify Installation

```bash
# Check Ollama is running
curl http://localhost:11434/api/tags

# Should return JSON with available models
# Example output:
# {"models":[{"name":"llama3.2:latest",...}]}
```

## Quick Start: Run Integration Tests

The easiest way to validate Ollama integration:

```bash
# Run all integration tests (uses mocks, always passes)
./gradlew :products:yappc:core:sdlc-agents:test --tests LLMGeneratorIntegrationTest

# Output will show:
# ✓ Ollama detected at http://localhost:11434
# ✓ All 12 LLM-powered generators created successfully
```

**Note**: Integration tests use mocked LLM responses for reliability. For real LLM generation, follow the test scenarios below.

## Test Scenarios

### Scenario 1: Intake Analysis (Requirements Extraction)

**Objective**: Validate that IntakeGenerator extracts structured requirements

```bash
# 1. Start your YAPPC agent service
./gradlew :products:yappc:core:sdlc-agents:run

# 2. Send intake request via API
curl -X POST http://localhost:8080/api/agents/intake \
  -H "Content-Type: application/json" \
  -d '{
    "projectId": "manual-test-001",
    "requirements": "Build a REST API for todo management with CRUD operations, user authentication, and task prioritization",
    "source": "text"
  }'

# 3. Expected output: JSON with functionalRequirements and nonFunctionalRequirements arrays
```

**Success Criteria**:
- ✅ Response contains `functionalRequirements` array
- ✅ Response contains `nonFunctionalRequirements` array
- ✅ At least 3 functional requirements identified
- ✅ Requirements are specific and actionable
- ✅ Response time < 10 seconds

### Scenario 2: Design Generation (Architecture Design)

**Objective**: Validate that DesignGenerator creates architecture designs

```bash
# 1. Send design request (using requirements from Scenario 1)
curl -X POST http://localhost:8080/api/agents/design \
  -H "Content-Type: application/json" \
  -d '{
    "projectId": "manual-test-001",
    "requirementsId": "req-001",
    "constraints": []
  }'

# 2. Expected output: JSON with designId and architecture details
```

**Success Criteria**:
- ✅ Response contains `designId`
- ✅ Design includes component breakdown
- ✅ Technology stack recommendations present
- ✅ Response time < 15 seconds

### Scenario 3: Code Implementation (Scaffold + Implement)

**Objective**: Validate that generators produce actual code artifacts

```bash
# 1. Send scaffold request
curl -X POST http://localhost:8080/api/agents/scaffold \
  -H "Content-Type: application/json" \
  -d '{
    "projectId": "manual-test-001",
    "designId": "design-001"
  }'

# 2. Send implement request
curl -X POST http://localhost:8080/api/agents/implement \
  -H "Content-Type: application/json" \
  -d '{
    "projectId": "manual-test-001",
    "unitId": "unit-001"
  }'
```

**Success Criteria**:
- ✅ Scaffold generates directory structure
- ✅ Implement produces compilable code
- ✅ Code follows YAPPC conventions
- ✅ Response time < 20 seconds per unit

### Scenario 4: Performance Benchmarking

**Objective**: Measure Ollama latency and quality

```bash
# Run automated performance test
./gradlew :products:yappc:core:sdlc-agents:test \
  --tests LLMGeneratorIntegrationTest.ollamaAvailabilityStatus

# Then manually test with different prompts and measure:
# - Time to first token
# - Total generation time
# - Token throughput
# - Output quality (subjective assessment)
```

**Expected Metrics** (llama3.2 on M1/M2 Mac):
- Simple prompts (< 100 tokens): **< 2 seconds**
- Medium prompts (< 500 tokens): **< 5 seconds**
- Complex prompts (< 2000 tokens): **< 15 seconds**

### Scenario 5: Multi-Step Workflow

**Objective**: Validate end-to-end SDLC pipeline

```bash
# Run full YAPPC workflow
curl -X POST http://localhost:8080/api/projects \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Todo API",
    "description": "REST API for todo management",
    "mode": "full-auto"
  }'

# Monitor workflow progress
curl http://localhost:8080/api/projects/{projectId}/status
```

**Success Criteria**:
- ✅ All 12 agents execute successfully
- ✅ No LLM timeouts or errors
- ✅ Generated code compiles
- ✅ Tests pass
- ✅ Total pipeline time < 5 minutes

## Troubleshooting

### Issue: "Ollama not available"

**Solution**:
```bash
# Check if Ollama is running
ps aux | grep ollama

# Restart Ollama
killall ollama
ollama serve &

# Verify
curl http://localhost:11434/api/tags
```

### Issue: "Model not found"

**Solution**:
```bash
# List available models
ollama list

# Pull required model
ollama pull llama3.2
```

### Issue: Slow generation

**Possible Causes**:
1. **Large model on limited RAM**: Switch to smaller model (llama3.2 vs llama3)
2. **Cold start**: First request is slower due to model loading
3. **High maxTokens**: Reduce maxTokens in LLMConfig

**Solution**:
```bash
# Use smaller, faster model
ollama pull llama3.2

# Or use quantized version (Q4 vs Q8)
ollama pull llama3.2:q4_k_m
```

### Issue: Low quality output

**Possible Causes**:
1. Temperature too high/low
2. Insufficient context in prompts
3. Model not suitable for task

**Solution**:
- Adjust temperature (0.3-0.9 range)
- Enhance prompt templates with more context
- Try specialized model (codellama for code generation)

## Quality Assessment Checklist

After manual testing, verify:

- [ ] **Correctness**: Outputs match expected format
- [ ] **Completeness**: All required fields populated
- [ ] **Consistency**: Multiple runs produce similar quality
- [ ] **Performance**: Response times within acceptable range
- [ ] **Error Handling**: Graceful degradation on failures
- [ ] **Integration**: Works with YAPPC workflow orchestration

## Reporting Results

Document your findings in this format:

```markdown
## Test Run: [Date]

**Model**: llama3.2
**Hardware**: M2 MacBook Pro, 16GB RAM
**Ollama Version**: 0.1.20

### Results

| Scenario | Success | Latency | Notes |
|----------|---------|---------|-------|
| Intake   | ✅      | 3.2s    | Good quality, 5 functional reqs extracted |
| Design   | ✅      | 7.1s    | Detailed architecture, SOLID principles |
| Scaffold | ✅      | 4.5s    | Complete directory structure |
| Implement| ⚠️      | 12.3s   | Code compiles but missing error handling |

### Issues Found

1. [Issue description]
2. [Issue description]

### Recommendations

1. [Recommendation]
2. [Recommendation]
```

## Next Steps

After successful manual testing:

1. **Production Deployment**: Deploy with Ollama as default LLM
2. **Monitoring**: Set up metrics for LLM performance tracking
3. **Cost Analysis**: Compare Ollama (free) vs cloud LLMs
4. **User Feedback**: Collect quality assessments from real usage
5. **Model Optimization**: Fine-tune models for YAPPC-specific tasks

---

**Questions?** See [GAA_IMPLEMENTATION_TRACKER.md](../../GAA_IMPLEMENTATION_TRACKER.md) for full implementation status.
