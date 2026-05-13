# YAPPC Agent Configuration README

## Overview

This directory contains the complete agent specification for the YAPPC platform, built on top of the Ghatana Agent Framework (`libs:agent-framework`).

## Directory Structure

```
agents/
├── definitions/          # Agent definitions (stable blueprints)
│   ├── code-generation/  # Code generation agents
│   │   └── java-code-generation-agent.yaml
│   ├── drift-detection/  # Drift and schema analysis agents
│   │   └── drift-detection-agent.yaml
│   ├── mastery/          # Seed mastery registry entries (MasteryEntry kind)
│   │   ├── java-class-writing.yaml
│   │   ├── react-ui-generation.yaml
│   │   ├── requirements-drafting.yaml
│   │   ├── acceptance-criteria-formatting.yaml
│   │   ├── drift-detection.yaml
│   │   └── memory-capability.yaml
│   ├── memory/           # Memory management agents
│   │   └── memory-capability-agent.yaml
│   ├── requirements/     # Requirements and acceptance criteria agents
│   │   └── requirements-drafting-agent.yaml
│   └── ui-generation/    # UI generation agents
│       └── react-ui-generation-agent.yaml
│
├── instances/           # Agent instances (tenant-specific runtime config)
│   ├── products-officer-default.yaml
│   └── systems-architect-default.yaml
│
├── registry.yaml        # Master catalog and routing rules
├── capabilities.yaml    # Capability definitions (existing)
└── mappings.yaml        # Task-to-agent mappings (existing)
```

## Agent Hierarchy

### Level 1: Strategic Agents
- **ProductsOfficer**: Product vision, roadmap, requirements prioritization
- **SystemsArchitect**: System design, tech stack, ADRs
- **UXDirector**: User experience strategy
- **HeadOfDevOps**: Infrastructure strategy

### Level 2: Domain Experts
- **JavaExpert**: Backend development (Java/ActiveJ)
- **ReactExpert**: Frontend development (React/Tailwind)
- **DBGuardian**: Database schema and optimization
- **QualityGuard**: Testing strategy and quality assurance
- **CloudPilot**: DevOps and deployment
- **Sentinel**: Security analysis and governance

### Level 3: Task Workers
- **JavaClassWriter**: Generate Java class files
- **UnitTestWriter**: Generate JUnit tests
- **VulnScanner**: Security vulnerability scanning
- **MigrationWriter**: Database migration scripts
- And more...

## Agent Definition Format

Each agent definition follows the `AgentDefinition` schema from `libs:agent-framework`:

```yaml
kind: AgentDefinition
apiVersion: ghatana.agent/v2
metadata:
  id: agent.yappc.{name}
  version: 1
  displayName: "Human Readable Name"
  description: "What this agent does"
  tags: [...]

spec:
  level: 1|2|3  # Strategic, Expert, Worker
  agentType: PROBABILISTIC|DETERMINISTIC|STREAM_PROCESSOR|PLANNING|HYBRID|ADAPTIVE|COMPOSITE|REACTIVE|CUSTOM
  agentSubtype: LLM|RULE_ENGINE|POLICY_ENGINE|PATTERN_MATCHER|ML_MODEL|...
  capabilities: [...]
  inputs: [...]
  outputs: [...]
  generator:
    type: PROBABILISTIC|DETERMINISTIC|HYBRID|SERVICE_CALL
    config: {...}
  memory:
    types: [episodic, semantic, procedural, preference]
  delegation:
    can_delegate_to: [...]
  tools: [...]
```

## Generator Types

YAPPC leverages the framework's `OutputGenerator` flexibility:

1. **LLMGenerator**: `PROBABILISTIC` with subtype `LLM`, for reasoning/creative tasks
2. **TemplateGenerator**: `DETERMINISTIC`, for scaffolding
3. **PipelineGenerator**: `HYBRID`, chains multiple generators
4. **RuleEngineGenerator**: `DETERMINISTIC` with subtype `RULE_ENGINE`, for compliance/validation
5. **ServiceCallGenerator**: Calls external tools

## Instance Configuration

Agent instances allow tenant-specific overrides:

```yaml
kind: AgentInstance
metadata:
  id: instance.yappc.{agent}.{tenant}
  definition_ref: agent.yappc.{agent}
  tenant_id: {tenant}

spec:
  enabled: true
  generator_overrides: {...}
  memory_overrides: {...}
  cost_limits: {...}
  custom_config: {...}
```

## Integration with AEP

YAPPC agents run on the AEP platform (`libs:agent-runtime`):

1. **Registry Discovery**: AEP loads agents from `registry.yaml`
2. **Factory Pattern**: `AgentFactory` instantiates `BaseAgent` with appropriate `OutputGenerator`
3. **Event-Driven**: Agents subscribe to events via ActiveJ `Eventloop`
4. **Memory**: All agents use `EventLogMemoryStore` for event sourcing

## Usage Example

```java
// Load agent definition
AgentDefinition definition = AgentLoader.load("definitions/java-expert.yaml");
AgentInstance instance = AgentLoader.loadInstance("instances/java-expert-default.yaml");

// Create agent
OutputGenerator<CodeGenRequest, JavaCode> generator = 
    GeneratorFactory.create(definition.getSpec().getGenerator());
    
JavaExpert agent = new JavaExpert(definition, instance, generator);

// Execute
AgentContext context = AgentContext.builder()
    .tenantId("tenant-1")
    .userId("user-1")
    .build();
    
Promise<JavaCode> result = agent.executeTurn(request, context);
```

## Next Steps

1. ✅ Agent definitions created
2. ⬜ Implement remaining agent definitions (see registry.yaml)
3. ⬜ Create agent factory and loader
4. ⬜ Integrate with AEP runtime
5. ⬜ Implement tool bindings (Maven, Git, etc.)
6. ⬜ Add Liquid/Jinja templates for TemplateGenerators
7. ⬜ Migrate in-memory repositories to PostgreSQL

## Reference

- Framework: `libs/java/agent-framework`
- Examples: `libs/java/agent-framework/examples/`
- Documentation: `libs/java/agent-framework/README.md`

---

## Learning Governance Contract

Every agent definition under `definitions/` must include a `learning:` block that declares its
`LearningLevel` and associated mastery bindings. The following rules are enforced by
`products/yappc/scripts/validate_agents.py`:

| Level | Label         | Provenance Required | Promotion Required | evaluationRefs Required | Allowed Targets           |
|-------|---------------|---------------------|--------------------|-------------------------|---------------------------|
| L0    | None          | No                  | No                 | No                      | None                      |
| L1    | Minimal       | No                  | No                 | No                      | EPISODIC_MEMORY           |
| L2    | Observed      | **Yes**             | No                 | No                      | L1 + SEMANTIC_FACT, RETRIEVAL_POLICY, CONFIDENCE_THRESHOLD, ROUTING_POLICY |
| L3    | Practiced     | **Yes**             | **Yes**            | **Yes**                 | L2 + PROCEDURAL_SKILL, NEGATIVE_KNOWLEDGE |
| L4    | Competent     | **Yes**             | **Yes**            | **Yes**                 | L3 + PROMPT_TEMPLATE, PLANNER_POLICY |
| L5    | Governance    | **Yes**             | **Yes**            | **Yes**                 | All (including MASTERY_STATE) — offline-only governance tier |

### Required fields in `learning:` block

```yaml
learning:
  learningLevel: L3           # L0..L5
  adaptationTargets:          # subset of LearningTarget enum values
    - PROCEDURAL_SKILL
    - EPISODIC_MEMORY
    - SEMANTIC_FACT
  masteryBindings:            # required when skillRefs is non-empty
    - skillRef: <mastery-entry-id>
      masteryPolicyRef: <policy-id>
  skillRefs:                  # IDs that must resolve to MasteryEntry files in definitions/mastery/
    - java-class-writing
  masteryPolicyRefs:          # promotion policy IDs
    - promotion-policy-v1
  evaluationRefs:             # evaluation pack IDs — required for L3+
    - eval-pack-java-code-quality-v1
  provenanceRequired: true    # must be true for L2+
  promotionRequired: true     # must be true for L3+
```

### Valid `adaptationTargets` values (LearningTarget enum)

```
EPISODIC_MEMORY, SEMANTIC_FACT, RETRIEVAL_POLICY, CONFIDENCE_THRESHOLD,
ROUTING_POLICY, PROCEDURAL_SKILL, NEGATIVE_KNOWLEDGE, PROMPT_TEMPLATE,
PLANNER_POLICY, MODEL_ADAPTER, MASTERY_STATE
```

### Mastery Registry

Seed mastery entries live under `definitions/mastery/`. Each `kind: MasteryEntry` file
defines the initial state and promotion rules for a specific skill:

| Skill ID                      | File                                          |
|-------------------------------|-----------------------------------------------|
| `java-class-writing`          | `mastery/java-class-writing.yaml`             |
| `react-ui-generation`         | `mastery/react-ui-generation.yaml`            |
| `requirements-drafting`       | `mastery/requirements-drafting.yaml`          |
| `acceptance-criteria-formatting` | `mastery/acceptance-criteria-formatting.yaml` |
| `drift-detection`             | `mastery/drift-detection.yaml`                |
| `memory-capability`           | `mastery/memory-capability.yaml`              |

### Validation

Run from the repository root:

```bash
python3 products/yappc/scripts/validate_agents.py
```

Exit code 0 means all checks pass. Non-zero exit code means at least one validation error.

