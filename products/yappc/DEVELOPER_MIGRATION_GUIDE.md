# Developer Migration Guide

**Version:** 1.0  
**Created:** 2026-03-23  
**For:** YAPPC Engineering Team  

---

## Overview

This guide explains how to migrate existing YAPPC agents from the current class-based implementation to the new schema-driven, configuration-based approach.

**Why migrate?**
- 75% less boilerplate code
- Faster development (create agents in minutes, not hours)
- Easier maintenance (single framework instead of 270 custom classes)
- Better testing support
- Improved discoverability

---

## Before vs After

### Before (Current Approach)

To create a new agent, you need **4 files and ~200 lines of code**:

```java
// 1. MyAgent.java - 50 lines
public class MyAgent extends YAPPCAgentBase<MyInput, MyOutput> {
    public MyAgent(MemoryStore store, OutputGenerator<...> gen) {
        super("MyAgent", "my.agent", contract, gen);
    }
    
    @Override
    public ValidationResult validateInput(MyInput input) {
        // Validation logic
    }
    
    @Override
    protected StepRequest<MyInput> perceive(...) {
        // Perception logic
    }
}

// 2. MyInput.java - 30 lines
public record MyInput(@NotNull String field1, @NotNull String field2) {
    public MyInput {
        // Validation in constructor
    }
}

// 3. MyOutput.java - 20 lines
public record MyOutput(String result, List<String> suggestions) {}

// 4. MyGenerator.java - 100 lines
public static class MyGenerator implements OutputGenerator<...> {
    @Override
    public Promise<StepResult<MyOutput>> generate(...) {
        // Complex generation logic
    }
}
```

### After (New Approach)

With the new framework, you need **1 YAML file and ~30 lines**:

```yaml
# my-agent.yaml
agent:
  id: my.agent
  name: "My Agent"
  description: "What this agent does"
  
  input_schema: schemas/my-agent-input.json
  output_schema: schemas/my-agent-output.json
  
  generator:
    type: llm
    prompt_template: prompts/my-agent.txt
    model: gpt-4
    temperature: 0.7
  
  tags: [code-review, java]
  capabilities: [analysis, suggestions]
```

```json
// schemas/my-agent-input.json
{
  "type": "object",
  "required": ["field1", "field2"],
  "properties": {
    "field1": { "type": "string", "minLength": 1 },
    "field2": { "type": "string" }
  }
}
```

That's it! The framework handles everything else.

---

## Step-by-Step Migration

### Step 1: Run the Migration Tool

```bash
# Navigate to yappc directory
cd products/yappc

# Run migration tool (dry-run first)
./gradlew :core:agents:runMigration --args="--agent=YourAgentName --dry-run"

# If everything looks good, run for real
./gradlew :core:agents:runMigration --args="--agent=YourAgentName"
```

**What the tool does:**
1. Analyzes your existing agent class
2. Generates YAML definition
3. Extracts JSON schemas from Input/Output records
4. Creates compatibility adapter if needed
5. Places files in the correct locations

### Step 2: Review Generated Files

The tool generates these files:

```
core/agents/src/main/resources/
├── agents/
│   └── your-agent.yaml          # Agent definition
└── schemas/
    ├── your-agent-input.json    # Input validation schema
    └── your-agent-output.json   # Output validation schema
```

**Review checklist:**
- [ ] Agent ID matches the original
- [ ] Description is accurate and helpful
- [ ] Tags are appropriate
- [ ] Schema validation rules are correct
- [ ] Prompt template reference is correct (for LLM agents)

### Step 3: Custom Generator (If Needed)

**If your agent uses a standard LLM pattern:**
- No custom code needed! The framework handles it.

**If your agent has custom logic:**

```java
// Create a post-processor
@Component
public class YourAgentPostProcessor implements AgentPostProcessor {
    
    @Override
    public AgentResult process(AgentResult result, AgentContext ctx) {
        // Access the raw output
        JsonNode output = result.getOutput();
        
        // Apply custom transformations
        JsonNode enhanced = enhanceOutput(output);
        
        // Return modified result
        return result.withOutput(enhanced);
    }
    
    private JsonNode enhanceOutput(JsonNode output) {
        // Your custom logic here
        return output;
    }
}
```

### Step 4: Test the Migration

```bash
# Run parity tests
./gradlew :core:agents:runValidation --args="--agent=your.agent.id --check-parity"

# Run specific agent tests
./gradlew :core:agents:test --tests "*YourAgentTest*"

# Run all agent tests
./gradlew :core:agents:test
```

**Expected output:**
```
Parity check for agent 'your.agent.id':
  ✓ Input validation matches
  ✓ Output structure matches
  ✓ 10/10 test cases passed
  ✓ Performance within acceptable range
```

### Step 5: Commit and Deploy

```bash
# Add new files
git add core/agents/src/main/resources/agents/your-agent.yaml
git add core/agents/src/main/resources/schemas/your-agent*.json
git add core/agents/src/main/java/.../YourAgentPostProcessor.java  # if created

# Remove old files
git rm core/agents/code-specialists/src/main/java/.../YourAgent.java
git rm core/agents/code-specialists/src/main/java/.../YourAgentInput.java
git rm core/agents/code-specialists/src/main/java/.../YourAgentOutput.java
git rm core/agents/code-specialists/src/main/java/.../YourAgentGenerator.java

# Commit
git commit -m "refactor(agents): migrate YourAgent to schema-driven framework

- Replaced 4 Java classes (~200 lines) with 1 YAML + 2 JSON schemas (~50 lines)
- Maintains full backward compatibility
- All tests passing

Relates-to: YAPPC-SIMPLIFICATION-2026"
```

---

## Common Migration Patterns

### Pattern 1: Simple LLM Agent

**Most common pattern** - Agents that use LLM with a prompt template.

**Before:**
```java
public class JavaExpertAgent extends YAPPCAgentBase<JavaExpertInput, JavaExpertOutput> {
    // ~115 lines of boilerplate
}
```

**After:**
```yaml
agent:
  id: expert.java
  generator:
    type: llm
    prompt_template: prompts/java-expert.txt
    model: gpt-4
```

**Migration effort:** 5 minutes  
**No custom code needed!**

### Pattern 2: Rule-Based Agent

Agents with deterministic logic.

**Before:**
```java
public class ComplianceCheckAgent extends YAPPCAgentBase<...> {
    @Override
    public Promise<StepResult<...>> generate(...) {
        // Complex rule evaluation
        if (input.hasViolationA()) {
            return resultA;
        } else if (input.hasViolationB()) {
            return resultB;
        }
        // ... many more rules
    }
}
```

**After:**
```yaml
agent:
  id: compliance.check
  generator:
    type: rule_based
    rules_file: rules/compliance-rules.yaml
```

```yaml
# rules/compliance-rules.yaml
rules:
  - name: "violation-a"
    condition: "input.violationA == true"
    output:
      violation: "A"
      severity: "high"
      
  - name: "violation-b"
    condition: "input.violationB == true"
    output:
      violation: "B"
      severity: "medium"
```

**Migration effort:** 30 minutes  
**Need to convert Java logic to YAML rules**

### Pattern 3: Template-Based Agent

Agents that generate code/text from templates.

**Before:**
```java
public class ScaffoldGeneratorAgent extends YAPPCAgentBase<...> {
    @Override
    public Promise<StepResult<...>> generate(...) {
        Template template = loadTemplate(input.getTemplateName());
        String result = template.render(input.getData());
        return Promise.of(new StepResult<>(...));
    }
}
```

**After:**
```yaml
agent:
  id: scaffold.generator
  generator:
    type: template
    template_engine: freemarker
    templates_dir: templates/scaffold/
```

**Migration effort:** 10 minutes  
**May need to adjust template syntax**

### Pattern 4: Hybrid Agent

Agents that combine multiple approaches.

**Before:**
```java
public class ComplexAgent extends YAPPCAgentBase<...> {
    @Override
    public Promise<StepResult<...>> generate(...) {
        // Step 1: Rule-based pre-processing
        // Step 2: LLM generation
        // Step 3: Post-processing
    }
}
```

**After:**
```yaml
agent:
  id: complex.agent
  generator:
    type: composed
    steps:
      - type: rule_based
        rules_file: rules/pre-process.yaml
      - type: llm
        prompt_template: prompts/main.txt
      - type: post_processor
        class: com.ghatana.yappc.agents.processors.ComplexPostProcessor
```

**Migration effort:** 1-2 hours  
**May need custom post-processor**

---

## Troubleshooting

### Issue 1: Schema Validation Errors

**Symptom:**
```
Validation failed: field 'codeContext' is required but was null
```

**Solution:**
```json
// In your schema, ensure proper defaults
{
  "properties": {
    "codeContext": {
      "type": "string",
      "default": "",  // Add default
      "minLength": 0  // Allow empty if needed
    }
  }
}
```

### Issue 2: Generator Not Found

**Symptom:**
```
No generator found for type: custom_generator
```

**Solution:**
Either:
1. Use a built-in generator type: `llm`, `rule_based`, `template`
2. Register your custom generator:
```java
@Component
public class CustomGenerator implements AgentGenerator {
    @Override
    public String getType() { return "custom"; }
    
    @Override
    public Promise<AgentResult> generate(AgentInput input, AgentContext ctx) {
        // Your logic
    }
}
```

### Issue 3: Migration Tool Fails

**Symptom:**
```
Migration failed: Unable to parse agent class
```

**Solutions:**
1. Check agent class compiles independently
2. Verify no circular dependencies
3. Run with `--verbose` flag for details:
```bash
./gradlew :core:agents:runMigration --args="--agent=YourAgent --verbose"
```

### Issue 4: Test Parity Failures

**Symptom:**
```
Parity check failed: Output mismatch
Expected: { "result": "A" }
Actual:   { "result": "a" }
```

**Solutions:**
1. Check case sensitivity in output schema
2. Verify prompt template produces consistent output
3. Add post-processor to normalize output:
```java
public class NormalizePostProcessor implements AgentPostProcessor {
    @Override
    public AgentResult process(AgentResult result, AgentContext ctx) {
        JsonNode output = result.getOutput();
        // Normalize case
        return result.withOutput(normalize(output));
    }
}
```

---

## Best Practices

### 1. Agent Naming

**Good:**
```yaml
agent:
  id: code.java.expert       # Hierarchical, clear
  name: "Java Expert"        # Human readable
  tags: [java, code-review]  # Searchable
```

**Bad:**
```yaml
agent:
  id: agent123               # Meaningless
  name: "Agent"              # Too generic
  tags: []                   # Missing tags
```

### 2. Schema Design

**Good:**
```json
{
  "description": "Java code to analyze",
  "examples": ["public class Foo { ... }"],
  "properties": {
    "codeContext": {
      "type": "string",
      "minLength": 10,
      "maxLength": 50000,
      "description": "The Java code to analyze"
    }
  }
}
```

**Bad:**
```json
{
  "properties": {
    "codeContext": {
      "type": "string"
      // Missing: description, constraints, examples
    }
  }
}
```

### 3. Prompt Templates

**Good:**
```
You are an expert Java engineer. Review the following code:

${codeContext}

Focus on:
- Performance issues
- Security vulnerabilities
- Best practices

Provide:
1. Overall assessment
2. Specific recommendations
3. Code examples where applicable
```

**Bad:**
```
Review this code: ${codeContext}
```

### 4. Testing

**Always test migrated agents:**
```bash
# Unit tests
./gradlew test --tests "*YourAgent*"

# Parity tests
./gradlew runValidation --args="--agent=your.agent"

# Integration tests
./gradlew integrationTest --tests "*YourAgent*"
```

---

## FAQ

### Q: Do I need to migrate all agents at once?

**A:** No. Migration is incremental:
1. New agents use the new framework
2. Existing agents can be migrated one at a time
3. Both old and new agents coexist during transition
4. Gradual migration reduces risk

### Q: Can I customize the framework for my specific needs?

**A:** Yes! The framework is extensible:
- Custom generators
- Custom middleware
- Custom validators
- Custom post-processors

### Q: What if the migration tool makes a mistake?

**A:** The tool generates files, you review and modify:
1. Always use `--dry-run` first
2. Review all generated files
3. Manually fix any issues
4. Tests will catch any problems

### Q: Will this break existing API contracts?

**A:** No. The migration maintains full backward compatibility:
- Same input/output structure
- Same agent IDs
- Same behavior
- Parity tests verify this

### Q: How do I add a new agent after migration?

**A:** Much simpler than before:

```yaml
# 1. Create YAML definition (5 minutes)
# 2. Create JSON schema (5 minutes)
# 3. Create prompt template (10 minutes)
# 4. Done!
```

No Java classes needed for standard LLM agents!

### Q: What happens to my existing tests?

**A:** Two options:
1. **Migrate tests** - Convert to new testing framework
2. **Keep as-is** - Original tests continue to work (framework is backward compatible)

---

## Migration Checklist

### Pre-Migration
- [ ] Understand the agent's purpose and behavior
- [ ] Review existing tests
- [ ] Identify custom logic that needs special handling
- [ ] Back up original code (Git history is sufficient)

### During Migration
- [ ] Run migration tool with `--dry-run`
- [ ] Review generated YAML and schemas
- [ ] Create custom post-processor if needed
- [ ] Run parity tests
- [ ] Fix any issues

### Post-Migration
- [ ] All tests passing
- [ ] Parity check successful
- [ ] Code review completed
- [ ] Documentation updated
- [ ] Committed to Git

---

## Support

### Getting Help

1. **Documentation:**
   - This guide
   - `SIMPLIFICATION_PLAN.md`
   - `IMPLEMENTATION_ROADMAP.md`

2. **Slack Channels:**
   - #yappc-development - General questions
   - #yappc-migration - Migration-specific help
   - #yappc-architecture - Design questions

3. **Office Hours:**
   - Tuesdays 2-3pm PT - Migration help desk
   - Thursdays 10-11am PT - Architecture Q&A

### Escalation

**Issues/blockers:**
1. Ask in #yappc-migration
2. If unresolved in 2 hours, escalate to YAPPC Tech Lead
3. If architectural concern, schedule review meeting

---

## Resources

### Internal Links

- [SIMPLIFICATION_PLAN.md](./SIMPLIFICATION_PLAN.md) - Full simplification strategy
- [IMPLEMENTATION_ROADMAP.md](./IMPLEMENTATION_ROADMAP.md) - Detailed timeline
- [CORE_ARCHITECTURE.md](./docs/CORE_ARCHITECTURE.md) - Current architecture

### External Links

- [JSON Schema Specification](https://json-schema.org/)
- [YAML Specification](https://yaml.org/spec/)
- [ActiveJ Documentation](https://activej.io/)

---

## Quick Reference Card

### Migration Command Cheat Sheet

```bash
# Dry run migration
./gradlew :core:agents:runMigration --args="--agent=X --dry-run"

# Actual migration
./gradlew :core:agents:runMigration --args="--agent=X"

# Validate migration
./gradlew :core:agents:runValidation --args="--agent=X --check-parity"

# Test specific agent
./gradlew :core:agents:test --tests "*X*"

# Test all agents
./gradlew :core:agents:test
```

### Agent YAML Template

```yaml
agent:
  id: your.agent.id
  name: "Human Readable Name"
  description: "What this agent does"
  
  input_schema: schemas/your-agent-input.json
  output_schema: schemas/your-agent-output.json
  
  generator:
    type: llm  # llm | rule_based | template | composed
    prompt_template: prompts/your-prompt.txt
    model: gpt-4
    temperature: 0.7
  
  tags: [tag1, tag2]
  capabilities: [capability1, capability2]
  
  validation:
    - field: fieldName
      required: true
      minLength: 1
```

### Schema Template

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["field1"],
  "properties": {
    "field1": {
      "type": "string",
      "minLength": 1,
      "description": "Description of field1",
      "examples": ["example value"]
    }
  }
}
```

---

*End of Developer Migration Guide*
