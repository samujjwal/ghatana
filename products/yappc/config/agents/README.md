# YAPPC Agent Configuration README

## Overview

This directory contains the complete agent specification for the YAPPC platform, built on top of the Ghatana Agent Framework (`libs:agent-framework`).

## Directory Structure

```
agents/
├── definitions/          # Agent definitions (stable blueprints)
│   ├── products-officer.yaml
│   ├── systems-architect.yaml
│   ├── java-expert.yaml
│   ├── java-class-writer.yaml
│   ├── unit-test-writer.yaml
│   ├── vuln-scanner.yaml
│   └── sentinel.yaml
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
  capabilities: [...]
  inputs: [...]
  outputs: [...]
  generator:
    type: llm|template|pipeline|rule-based|service-call
    config: {...}
  memory:
    types: [episodic, semantic, procedural, preference]
  delegation:
    can_delegate_to: [...]
  tools: [...]
```

## Generator Types

YAPPC leverages the framework's `OutputGenerator` flexibility:

1. **LLMGenerator**: Probabilistic, for reasoning/creative tasks
2. **TemplateGenerator**: Deterministic, for scaffolding
3. **PipelineGenerator**: Hybrid, chains multiple generators
4. **RuleBasedGenerator**: Deterministic, for compliance/validation
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
