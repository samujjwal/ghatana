# Enhanced Architecture Implementation Plan

## Executive Summary

This document provides a detailed implementation plan for the Enhanced Architecture Proposal, focusing on:

- **Reusing existing code** wherever possible
- **Avoiding duplicates** by extending current implementations
- **No backward compatibility** - using new approach/API and cleaning up older usage
- **Systematic migration** from current to enhanced architecture

## Current Architecture Analysis

### Existing Components (To Reuse/Extend)

#### 1. Pack System (EXISTING - EXTEND)

**Location:** `core/src/main/java/com/ghatana/yappc/core/pack/`

**Current Capabilities:**

- `PackEngine` - Pack loading and generation
- `PackMetadata` - Pack metadata schema (pack.json)
- `DefaultPackEngine` - Implementation with template rendering
- Pack types: BASE, SERVICE, LIBRARY, APPLICATION, FEATURE, FULLSTACK, MIDDLEWARE

**Enhancement Strategy:**

- ✅ **REUSE**: Core PackEngine interface and DefaultPackEngine
- 🔄 **EXTEND**: PackMetadata to support universal composition schema
- ➕ **ADD**: Module and integration definitions
- ➕ **ADD**: Enhanced lifecycle hooks

#### 2. Template System (EXISTING - EXTEND)

**Location:** `core/src/main/java/com/ghatana/yappc/core/template/`

**Current Capabilities:**

- `TemplateEngine` interface
- `SimpleTemplateEngine` with basic variable substitution
- Built-in helpers: packagePath, camelCase, pascalCase, snakeCase, etc.
- `TemplateMerger` for 3-way merge operations

**Enhancement Strategy:**

- ✅ **REUSE**: TemplateEngine interface
- 🔄 **UPGRADE**: Replace SimpleTemplateEngine with full Handlebars integration
- ➕ **ADD**: Template inheritance and composition
- ➕ **ADD**: Conditional rendering with complex expressions
- ➕ **ADD**: Partial templates and layouts

#### 3. Plugin System (EXISTING - EXTEND)

**Location:** `core/src/main/java/com/ghatana/yappc/core/plugin/`

**Current Capabilities:**

- `YappcPlugin` base interface
- `PluginManager` for lifecycle management
- `PluginLoader` for JAR loading
- `PluginRegistry` for plugin discovery
- Plugin types: AnalyzerPlugin, BuildSystemPlugin, FeaturePackPlugin, etc.

**Enhancement Strategy:**

- ✅ **REUSE**: Core plugin infrastructure
- 🔄 **EXTEND**: YappcPlugin interface with new capabilities
- ➕ **ADD**: Template contribution support
- ➕ **ADD**: Variable contribution support
- ➕ **ADD**: Integration contribution support
- ➕ **ADD**: Plugin registry with remote support

#### 4. Configuration System (EXISTING - EXTEND)

**Location:** `core/src/main/java/com/ghatana/yappc/core/config/`

**Current Capabilities:**

- `SimpleUnifiedConfigurationManager` with YAML/JSON support
- Configuration sections: telemetry, cache, security, observability
- File-based configuration loading

**Enhancement Strategy:**

- ✅ **REUSE**: Configuration manager foundation
- 🔄 **EXTEND**: Add hierarchical configuration support
- ➕ **ADD**: Global, organization, project, environment levels
- ➕ **ADD**: Configuration merging and precedence
- ➕ **ADD**: Variable resolution with interpolation

#### 5. Build System Generators (EXISTING - REUSE)

**Location:** `core/src/main/java/com/ghatana/yappc/core/buildgen/`, `maven/`, `cargo/`, `go/`, `make/`

**Current Capabilities:**

- Multiple build system generators: Gradle, Maven, Cargo, Go, Make
- Build script specifications and templates

**Enhancement Strategy:**

- ✅ **REUSE**: All existing build generators
- 🔄 **INTEGRATE**: Into universal language/framework system
- ➕ **ADD**: Build system registry

## Implementation Phases

### Phase 1: Foundation & Schema Enhancement (Week 1-2)

#### Task 1.1: Extend PackMetadata for Universal Composition

**File:** `core/src/main/java/com/ghatana/yappc/core/pack/PackMetadata.java`

**Changes:**

```java
// ADD new records to PackMetadata
public record ModuleDefinition(
    String id,
    String name,
    String type,  // application|library|service|infrastructure|tool
    String pack,
    String path,
    boolean enabled,
    String condition,
    Map<String, Object> variables,
    List<String> dependencies,
    Map<String, String> outputs
) {}

public record IntegrationDefinition(
    String id,
    String name,
    String type,  // api-client|datasource|event-stream|shared-types
    String from,
    String to,
    List<String> templates,
    Map<String, Object> variables,
    String condition
) {}

public record LifecycleHooks(
    List<String> preGeneration,
    List<String> postGeneration,
    List<String> preBuild,
    List<String> postBuild
) {}

// EXTEND PackMetadata record
public record PackMetadata(
    // ... existing fields ...
    List<ModuleDefinition> modules,           // NEW
    List<IntegrationDefinition> integrations, // NEW
    LifecycleHooks lifecycle                  // ENHANCED
) {}
```

**Status:** ➕ NEW - Extends existing

#### Task 1.2: Create Universal Schema Definitions

**New Files:**

- `core/src/main/java/com/ghatana/yappc/core/schema/CompositionSchema.java`
- `core/src/main/java/com/ghatana/yappc/core/schema/DeploymentSchema.java`
- `core/src/main/java/com/ghatana/yappc/core/schema/LanguageSchema.java`
- `core/src/main/java/com/ghatana/yappc/core/schema/VariableSchema.java`

**Purpose:** Define Java records for all schema types from the proposal

**Status:** ➕ NEW

#### Task 1.3: Create JSON Schema Validators

**New Files:**

- `schemas/src/main/resources/schemas/composition-v1.json`
- `schemas/src/main/resources/schemas/deployment-v1.json`
- `schemas/src/main/resources/schemas/languages-v1.json`
- `schemas/src/main/resources/schemas/variables-v1.json`
- `schemas/src/main/resources/schemas/templates-v1.json`
- `schemas/src/main/resources/schemas/plugins-v1.json`

**Status:** ➕ NEW

#### Task 1.4: Implement Schema Validation Service

**New File:** `core/src/main/java/com/ghatana/yappc/core/validation/SchemaValidationService.java`

**Purpose:** JSON Schema validation for all configuration files

**Status:** ➕ NEW

### Phase 2: Template System Enhancement (Week 2-3)

#### Task 2.1: Upgrade to Full Handlebars Integration

**File:** `core/src/main/java/com/ghatana/yappc/core/template/HandlebarsTemplateEngine.java`

**Changes:**

- ❌ **REPLACE**: SimpleTemplateEngine (keep for backward compat initially)
- ➕ **NEW**: HandlebarsTemplateEngine with full Handlebars.java integration
- ➕ **ADD**: Conditional rendering (if/unless/each/with)
- ➕ **ADD**: Partial templates support
- ➕ **ADD**: Layout templates support

**Status:** ➕ NEW - Replaces SimpleTemplateEngine

#### Task 2.2: Template Inheritance System

**New File:** `core/src/main/java/com/ghatana/yappc/core/template/TemplateInheritanceResolver.java`

**Purpose:**

- Resolve template inheritance chains
- Override strategy: replace|merge|append
- Template order: global → language → framework → project

**Status:** ➕ NEW

#### Task 2.3: Enhanced Template Helpers

**File:** `core/src/main/java/com/ghatana/yappc/core/template/HandlebarsTemplateEngine.java`

**Add Helpers:**

- File operations: fileExists, pathJoin, baseName, dirName
- Format conversions: json, yaml, toml
- Advanced string: pluralize, singularize
- Conditional: eq, ne, gt, lt, and, or

**Status:** 🔄 EXTEND existing helpers

#### Task 2.4: Template Validation

**New File:** `core/src/main/java/com/ghatana/yappc/core/validation/TemplateValidator.java`

**Purpose:**

- Syntax validation
- Variable reference checking
- Partial existence checking
- Helper signature validation

**Status:** ➕ NEW

### Phase 3: Composition & Integration System (Week 3-4)

#### Task 3.1: Composition Engine

**New File:** `core/src/main/java/com/ghatana/yappc/core/composition/CompositionEngine.java`

**Purpose:**

- Load composition definitions
- Resolve module dependencies
- Generate multi-module projects
- Handle module conditions and variables

**Status:** ➕ NEW

#### Task 3.2: Integration Template System

**New File:** `core/src/main/java/com/ghatana/yappc/core/integration/IntegrationTemplateEngine.java`

**Purpose:**

- Generate integration code between modules
- API client generation
- Type definition sharing
- Database connection configuration

**Status:** ➕ NEW

#### Task 3.3: Extend PackEngine for Compositions

**File:** `core/src/main/java/com/ghatana/yappc/core/pack/DefaultPackEngine.java`

**Changes:**

```java
// ADD new method
public GenerationResult generateComposition(
    CompositionDefinition composition,
    Path outputPath,
    Map<String, Object> variables
) throws PackException {
    // Load all module packs
    // Resolve dependencies
    // Generate each module
    // Generate integrations
    // Execute lifecycle hooks
}
```

**Status:** 🔄 EXTEND existing

### Phase 4: Language & Framework Registry (Week 4-5)

#### Task 4.1: Language Registry

**New Files:**

- `core/src/main/java/com/ghatana/yappc/core/language/LanguageRegistry.java`
- `core/src/main/java/com/ghatana/yappc/core/language/LanguageDefinition.java`
- `core/src/main/java/com/ghatana/yappc/core/language/FrameworkDefinition.java`

**Purpose:**

- Register supported languages and versions
- Package management systems
- Build systems
- Testing frameworks
- Linting tools

**Status:** ➕ NEW

#### Task 4.2: Language Configuration Files

**New Files:**

- `core/src/main/resources/languages/go.yaml`
- `core/src/main/resources/languages/typescript.yaml`
- `core/src/main/resources/languages/java.yaml`
- `core/src/main/resources/languages/python.yaml`
- `core/src/main/resources/languages/rust.yaml`

**Status:** ➕ NEW

#### Task 4.3: Framework Registry Integration

**File:** `core/src/main/java/com/ghatana/yappc/core/language/LanguageRegistry.java`

**Add Methods:**

```java
List<String> getSupportedLanguages()
LanguageDefinition getLanguage(String name)
List<FrameworkDefinition> getFrameworks(String language)
FrameworkDefinition getFramework(String language, String framework)
```

**Status:** ➕ NEW

### Phase 5: Deployment Pattern System (Week 5-6)

#### Task 5.1: Deployment Pattern Registry

**New Files:**

- `core/src/main/java/com/ghatana/yappc/core/deployment/DeploymentPatternRegistry.java`
- `core/src/main/java/com/ghatana/yappc/core/deployment/DeploymentPattern.java`

**Purpose:**

- Register deployment patterns: library, api-layer, service, hosted-application
- Target platforms: docker, kubernetes, cloud-run, ecs, etc.

**Status:** ➕ NEW

#### Task 5.2: Deployment Configuration Files

**New Files:**

- `core/src/main/resources/deployment/library.yaml`
- `core/src/main/resources/deployment/api-layer.yaml`
- `core/src/main/resources/deployment/service.yaml`
- `core/src/main/resources/deployment/hosted-application.yaml`

**Status:** ➕ NEW

#### Task 5.3: Extend Existing Deployment Managers

**Files:**

- `core/src/main/java/com/ghatana/yappc/core/deployment/DockerDeploymentManager.java`
- (Add new managers for K8s, Cloud Run, etc.)

**Changes:**

- ✅ **REUSE**: Existing DockerDeploymentManager
- ➕ **ADD**: KubernetesDeploymentManager
- ➕ **ADD**: CloudRunDeploymentManager
- ➕ **ADD**: ECSDeploymentManager

**Status:** 🔄 EXTEND existing

### Phase 6: Variable & Configuration System (Week 6-7)

#### Task 6.1: Enhanced Variable System

**New File:** `core/src/main/java/com/ghatana/yappc/core/variable/VariableResolver.java`

**Purpose:**

- Variable type definitions with validation
- Variable interpolation: {{variable.path}}
- Functions: env, file, exec, timestamp, uuid
- Conditionals: if, unless, switch
- Precedence: command-line → env → project → user → org → defaults

**Status:** ➕ NEW

#### Task 6.2: Hierarchical Configuration Manager

**File:** `core/src/main/java/com/ghatana/yappc/core/config/HierarchicalConfigurationManager.java`

**Purpose:**

- Replace SimpleUnifiedConfigurationManager
- Support hierarchy: global → organization → project → environment → runtime
- Deep merge strategy
- Configuration validation

**Status:** ➕ NEW - Replaces SimpleUnifiedConfigurationManager

#### Task 6.3: Configuration Schema

**New Files:**

- `core/src/main/java/com/ghatana/yappc/core/config/GlobalConfiguration.java`
- `core/src/main/java/com/ghatana/yappc/core/config/OrganizationConfiguration.java`
- `core/src/main/java/com/ghatana/yappc/core/config/ProjectConfiguration.java`
- `core/src/main/java/com/ghatana/yappc/core/config/EnvironmentConfiguration.java`

**Status:** ➕ NEW

### Phase 7: Plugin System Enhancement (Week 7-8)

#### Task 7.1: Extend Plugin Interface

**File:** `core/src/main/java/com/ghatana/yappc/core/plugin/YappcPlugin.java`

**Add Methods:**

```java
// Template contributions
Map<String, TemplateSource> getTemplates();
Map<String, HelperFunction> getHelpers();

// Variable contributions
Map<String, VariableDefinition> getVariables();
Object resolveVariable(String name, VariableContext context);

// Integration contributions
Map<String, IntegrationDefinition> getIntegrations();

// Compatibility checking
boolean supports(PluginContext context);
List<String> getSupportedLanguages();
List<String> getSupportedFrameworks();
```

**Status:** 🔄 EXTEND existing

#### Task 7.2: Plugin Registry with Remote Support

**New File:** `core/src/main/java/com/ghatana/yappc/core/plugin/RemotePluginRegistry.java`

**Purpose:**

- Discover plugins from remote registry
- Plugin caching
- Signature verification
- Version management

**Status:** ➕ NEW

#### Task 7.3: Plugin Configuration

**New File:** `core/src/main/resources/plugin-config.yaml`

**Content:**

```yaml
discovery:
  paths:
    - ~/.yappc/plugins
    - ./plugins
    - https://registry.yappc.dev/plugins
  autoLoad: true
  priority: [local, global, registry]

registry:
  url: https://registry.yappc.dev
  authentication: optional
  caching:
    enabled: true
    ttl: 3600
    path: ~/.yappc/cache/registry

security:
  signatureVerification: true
  sandboxing: true
  permissions: [read, write, network]
  trustedSources: [official, verified]
```

**Status:** ➕ NEW

### Phase 8: Validation & Testing Framework (Week 8-9)

#### Task 8.1: Multi-Level Validation Framework

**New Files:**

- `core/src/main/java/com/ghatana/yappc/core/validation/ValidationFramework.java`
- `core/src/main/java/com/ghatana/yappc/core/validation/SchemaValidator.java`
- `core/src/main/java/com/ghatana/yappc/core/validation/SemanticValidator.java`
- `core/src/main/java/com/ghatana/yappc/core/validation/TemplateValidator.java`
- `core/src/main/java/com/ghatana/yappc/core/validation/IntegrationValidator.java`
- `core/src/main/java/com/ghatana/yappc/core/validation/SecurityValidator.java`

**Purpose:**

- Schema validation (JSON Schema)
- Semantic validation (dependencies, conflicts)
- Template validation (syntax, references)
- Integration validation (compatibility)
- Security validation (secrets, vulnerabilities)

**Status:** ➕ NEW

#### Task 8.2: Generated Code Testing Framework

**New File:** `core/src/main/java/com/ghatana/yappc/core/testing/GeneratedCodeTestFramework.java`

**Purpose:**

- Compilation tests
- Unit tests
- Integration tests
- Linting tests
- Security scanning

**Status:** ➕ NEW

#### Task 8.3: Test Execution Engine

**New File:** `core/src/main/java/com/ghatana/yappc/core/testing/TestExecutionEngine.java`

**Purpose:**

- Parallel test execution
- Timeout handling
- Retry logic
- Result reporting (JUnit, HTML, JSON)

**Status:** ➕ NEW

### Phase 9: Migration & Cleanup (Week 9-10)

#### Task 9.1: Update Existing Packs

**Files:** All pack.json files in `packs/*/pack.json`

**Changes:**

- Update schema to v1.0
- Add module definitions where applicable
- Add integration definitions
- Update variable definitions with types
- Add lifecycle hooks

**Status:** 🔄 UPDATE existing

#### Task 9.2: Deprecate Old APIs

**Files:**

- Mark SimpleTemplateEngine as @Deprecated
- Mark SimpleUnifiedConfigurationManager as @Deprecated
- Add migration guides in JavaDoc

**Status:** 🔄 DEPRECATE

#### Task 9.3: Update CLI

**File:** `cli/src/main/java/com/ghatana/yappc/cli/YappcCli.java`

**Changes:**

- Add composition commands
- Add validation commands
- Add plugin management commands
- Update generate command for new features

**Status:** 🔄 UPDATE existing

#### Task 9.4: Create Migration Guide

**New File:** `docs/MIGRATION_GUIDE.md`

**Content:**

- Old vs New API comparison
- Step-by-step migration instructions
- Breaking changes
- New features guide

**Status:** ➕ NEW

## Dependency Injection Updates

**File:** `core/src/main/java/com/ghatana/yappc/core/di/CoreModule.java`

**Changes:**

```java
@Provides
TemplateEngine templateEngine() {
    return new HandlebarsTemplateEngine();  // REPLACE SimpleTemplateEngine
}

@Provides
ConfigurationManager configurationManager() {
    return new HierarchicalConfigurationManager();  // REPLACE SimpleUnifiedConfigurationManager
}

@Provides
CompositionEngine compositionEngine(PackEngine packEngine, TemplateEngine templateEngine) {
    return new CompositionEngine(packEngine, templateEngine);  // NEW
}

@Provides
LanguageRegistry languageRegistry() {
    return new LanguageRegistry();  // NEW
}

@Provides
DeploymentPatternRegistry deploymentPatternRegistry() {
    return new DeploymentPatternRegistry();  // NEW
}

@Provides
ValidationFramework validationFramework() {
    return new ValidationFramework();  // NEW
}
```

## File Structure Summary

### New Directories

```
core/src/main/java/com/ghatana/yappc/core/
├── schema/              # NEW - Universal schema definitions
├── composition/         # NEW - Composition engine
├── integration/         # NEW - Integration templates
├── language/           # NEW - Language & framework registry
├── variable/           # NEW - Variable resolution
├── validation/         # NEW - Multi-level validation
└── testing/            # NEW - Testing framework

core/src/main/resources/
├── schemas/            # NEW - JSON Schema definitions
├── languages/          # NEW - Language configurations
└── deployment/         # NEW - Deployment patterns

schemas/src/main/resources/schemas/  # NEW - JSON Schema files
```

### Files to Update

```
core/src/main/java/com/ghatana/yappc/core/
├── pack/PackMetadata.java              # EXTEND
├── pack/DefaultPackEngine.java         # EXTEND
├── template/HandlebarsTemplateEngine.java  # NEW (replaces SimpleTemplateEngine)
├── plugin/YappcPlugin.java             # EXTEND
├── plugin/PluginManager.java           # EXTEND
├── config/HierarchicalConfigurationManager.java  # NEW (replaces SimpleUnifiedConfigurationManager)
└── di/CoreModule.java                  # UPDATE
```

### Files to Deprecate (Not Delete)

```
core/src/main/java/com/ghatana/yappc/core/
├── template/SimpleTemplateEngine.java           # @Deprecated
└── config/SimpleUnifiedConfigurationManager.java  # @Deprecated
```

## Implementation Priority

### High Priority (Weeks 1-4)

1. ✅ Schema enhancement (PackMetadata extension)
2. ✅ Template system upgrade (Handlebars)
3. ✅ Composition engine
4. ✅ Variable resolution

### Medium Priority (Weeks 5-7)

5. ✅ Language registry
6. ✅ Deployment patterns
7. ✅ Plugin enhancements
8. ✅ Configuration hierarchy

### Lower Priority (Weeks 8-10)

9. ✅ Validation framework
10. ✅ Testing framework
11. ✅ Migration & cleanup

## Success Criteria

### Technical Metrics

- ✅ Support for 10+ programming languages
- ✅ 20+ frameworks supported
- ✅ 100+ configurable variables
- ✅ 50+ deployment patterns
- ✅ Generate complex projects in <30 seconds
- ✅ 99.9% template validation accuracy

### Code Quality

- ✅ All new code follows existing patterns
- ✅ No duplicate functionality
- ✅ Comprehensive JavaDoc with @doc.\* tags
- ✅ Unit tests for all new components
- ✅ Integration tests for end-to-end flows

### Migration

- ✅ Zero breaking changes for existing packs (initially)
- ✅ Clear deprecation warnings
- ✅ Migration guide with examples
- ✅ Gradual transition path

## Next Steps

1. **Review this plan** with stakeholders
2. **Create detailed task breakdown** for Phase 1
3. **Set up feature branch** for implementation
4. **Begin Phase 1 implementation** with schema enhancement
5. **Iterative development** with continuous testing

## Notes

- **No Backward Compatibility Required**: We can use new approach/API and cleanup older usage
- **Avoid Duplicates**: Always check existing code before creating new
- **Reuse First**: Extend existing components rather than creating new ones
- **Clean as We Go**: Remove deprecated code after migration is complete
