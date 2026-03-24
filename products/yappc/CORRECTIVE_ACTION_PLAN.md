# YAPPC Corrective Action Plan - AEP Integration & Proper Implementation

## 🎯 Objective
Execute proper implementation of YAPPC simplification by:
1. Leveraging AEP agent registry and event processing (no duplication)
2. Completing actual source code migration (not just planning)
3. Cleaning up legacy code and folders
4. Ensuring maximum reuse of platform/shared libraries

---

## 🔧 CORRECTIVE ACTION 1: Replace Custom Registry with AEP Integration

### Current Problem
- Created custom `YappcAgentRegistry` duplicating AEP functionality
- Not using AEP's `AgentRegistryService` which provides:
  - Agent manifest management
  - Agent execution with events
  - Multi-tenant support
  - Metrics and monitoring
  - Batch processing

### Solution: Integrate with AEP AgentRegistryService

**Step 1.1: Create YAML to AgentManifest Converter**
```java
package com.ghatana.yappc.agents.config;

import com.ghatana.contracts.agent.v1.AgentManifestProto;
import com.ghatana.contracts.agent.v1.AgentSpecProto;
import com.ghatana.contracts.agent.v1.RuntimeProto;

/**
 * Converts YAPPC YAML agent configs to AEP AgentManifestProto format.
 * Bridges YAPPC's simplified YAML with AEP's standard manifest format.
 */
public class YamlToManifestConverter {
    
    public AgentManifestProto convert(YamlAgentConfig yamlConfig) {
        return AgentManifestProto.newBuilder()
            .setMetadata(buildMetadata(yamlConfig))
            .setSpec(buildSpec(yamlConfig))
            .build();
    }
    
    private AgentSpecProto buildSpec(YamlAgentConfig config) {
        AgentSpecProto.Builder spec = AgentSpecProto.newBuilder();
        
        // Set runtime configuration
        spec.setRuntime(RuntimeProto.newBuilder()
            .setName(config.getId())
            .setVersion(config.getVersion())
            .setDescription(config.getDescription())
            .build());
        
        // Add capabilities
        config.getCapabilities().forEach(spec::addCapabilities);
        
        // Add tags as event types for filtering
        config.getTags().forEach(tag -> spec.addInputEventTypes("tag:" + tag));
        
        return spec.build();
    }
}
```

**Step 1.2: Use AEP AgentRegistryService**
```java
package com.ghatana.yappc.agents.config;

import com.ghatana.agent.registry.service.AgentRegistryService;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.contracts.agent.v1.AgentManifestProto;
import io.activej.inject.annotation.Inject;
import io.activej.promise.Promise;

/**
 * YAPPC Agent Loader - Loads YAML agents into AEP registry.
 * Uses AEP's AgentRegistryService instead of custom implementation.
 */
public class YappcAgentLoader {
    
    private final AgentRegistryService aepRegistry;
    private final YamlAgentConfigLoader yamlLoader;
    private final YamlToManifestConverter converter;
    
    @Inject
    public YappcAgentLoader(
        AgentRegistryService aepRegistry,
        YamlAgentConfigLoader yamlLoader,
        YamlToManifestConverter converter
    ) {
        this.aepRegistry = aepRegistry;
        this.yamlLoader = yamlLoader;
        this.converter = converter;
    }
    
    /**
     * Load all YAML agents and register with AEP.
     */
    public Promise<List<AgentManifestProto>> loadAndRegisterAgents(TenantId tenantId) {
        List<YamlAgentConfig> yamlConfigs = yamlLoader.loadFromClasspath("agents/");
        
        List<Promise<AgentManifestProto>> registrations = yamlConfigs.stream()
            .map(converter::convert)
            .map(manifest -> aepRegistry.register(tenantId, manifest))
            .toList();
        
        return Promise.all(registrations);
    }
}
```

**Step 1.3: Delete Custom Registry**
- ❌ DELETE: `YappcAgentRegistry.java` (custom implementation)
- ✅ KEEP: `YamlAgentConfig.java` (configuration model)
- ✅ KEEP: `YamlAgentConfigLoader.java` (YAML parsing)
- ✅ ADD: `YamlToManifestConverter.java` (bridge to AEP)

---

## 🔧 CORRECTIVE ACTION 2: Integrate AEP Event Processing

### Current Problem
- No integration with AEP's event-driven agent execution
- Missing event routing and processing capabilities

### Solution: Use AEP Event Processing

**Step 2.1: Configure YAPPC Agents for Event Processing**
```yaml
# agents/java-expert.yaml
agent:
  id: expert.java
  name: "Java Expert"
  
  # Event processing configuration
  event_processing:
    input_event_types:
      - "code.review.requested"
      - "code.analysis.requested"
    output_event_types:
      - "code.review.completed"
      - "code.analysis.completed"
    
  # Generator uses AEP's execution model
  generator:
    type: llm
    prompt_template: prompts/java-expert.txt
```

**Step 2.2: Use AEP Agent Execution**
```java
// Use AEP's executeAgent instead of custom execution
Promise<List<Event>> results = aepRegistry.executeAgent(
    "expert.java",
    inputEvent,
    executionContext
);
```

---

## 🔧 CORRECTIVE ACTION 3: Complete Module Consolidation Migration

### Current Problem
- New module structure created but NO source code migrated
- Old modules still contain all the code
- Build references non-existent projects

### Solution: Execute Actual Migration

**Step 3.1: Migrate yappc-agents Module**

```bash
# Create source directories
mkdir -p products/yappc/core/yappc-agents/src/main/java/com/ghatana/yappc/agents
mkdir -p products/yappc/core/yappc-agents/src/main/resources
mkdir -p products/yappc/core/yappc-agents/src/test/java

# Migrate source code
mv products/yappc/core/agents/src/main/java/com/ghatana/yappc/agents/* \
   products/yappc/core/yappc-agents/src/main/java/com/ghatana/yappc/agents/

# Migrate resources
mv products/yappc/core/agents/src/main/resources/* \
   products/yappc/core/yappc-agents/src/main/resources/

# Migrate tests
mv products/yappc/core/agents/src/test/java/* \
   products/yappc/core/yappc-agents/src/test/java/
```

**Step 3.2: Update settings.gradle.kts**
```kotlin
// Add new consolidated modules
include(":products:yappc:core:yappc-agents")
include(":products:yappc:core:yappc-domain")
include(":products:yappc:core:yappc-infrastructure")
include(":products:yappc:core:yappc-services")
include(":products:yappc:core:yappc-api")
include(":products:yappc:core:yappc-shared")

// Remove old modules after migration
// include(":products:yappc:core:agents")
// include(":products:yappc:core:domain")
```

**Step 3.3: Update Build Dependencies**
```kotlin
// yappc-agents/build.gradle.kts - Use AEP libraries
dependencies {
    // AEP agent services (PRIMARY)
    implementation("com.ghatana.products.aep:aep-registry")
    implementation("com.ghatana.products.aep:aep-agent")
    
    // Platform agent framework (SECONDARY)
    implementation("com.ghatana.platform:agent-core")
    implementation("com.ghatana.platform:agent-registry")
    
    // Platform shared utilities
    implementation("com.ghatana.platform:common-utils")
    implementation("com.ghatana.platform:json-utils")
    implementation("com.ghatana.platform:domain-models")
    
    // YAPPC modules (after migration)
    implementation(project(":products:yappc:core:yappc-domain"))
    implementation(project(":products:yappc:core:yappc-shared"))
}
```

**Step 3.4: Delete Old Modules**
```bash
# After successful migration and testing
rm -rf products/yappc/core/agents/
rm -rf products/yappc/core/domain/
rm -rf products/yappc/core/framework/
rm -rf products/yappc/core/infrastructure/
```

---

## 🔧 CORRECTIVE ACTION 4: Complete Frontend Library Migration

### Current Problem
- New packages created but NO source code migrated
- Old libraries still contain all 759+ files
- Invalid workspace dependencies

### Solution: Execute Actual Migration

**Step 4.1: Migrate yappc-core Package**
```bash
# Create source directory
mkdir -p products/yappc/frontend/libs/yappc-core/src

# Migrate from old libraries
cp -r products/yappc/frontend/libs/core/src/* \
      products/yappc/frontend/libs/yappc-core/src/
cp -r products/yappc/frontend/libs/types/src/* \
      products/yappc/frontend/libs/yappc-core/src/types/
cp -r products/yappc/frontend/libs/utils/src/* \
      products/yappc/frontend/libs/yappc-core/src/utils/
```

**Step 4.2: Update pnpm-workspace.yaml**
```yaml
packages:
  # New consolidated packages
  - 'libs/yappc-core'
  - 'libs/yappc-ui'
  - 'libs/yappc-ai'
  - 'libs/yappc-canvas'
  - 'libs/yappc-collab'
  # ... other new packages
  
  # Remove old packages after migration
  # - 'libs/core'
  # - 'libs/types'
  # - 'libs/utils'
```

**Step 4.3: Update Import Statements**
```typescript
// OLD
import { debounce } from '@yappc/utils';
import { ApiResponse } from '@yappc/types';

// NEW
import { debounce, ApiResponse } from '@yappc/core';
```

**Step 4.4: Delete Old Libraries**
```bash
# After successful migration and testing
rm -rf products/yappc/frontend/libs/core/
rm -rf products/yappc/frontend/libs/types/
rm -rf products/yappc/frontend/libs/utils/
rm -rf products/yappc/frontend/libs/ui/
rm -rf products/yappc/frontend/libs/ai/
```

---

## 🔧 CORRECTIVE ACTION 5: Clean Up Legacy Code

### Folders to Delete
```bash
# Empty framework folder
rm -rf products/yappc/core/agents/src/main/java/com/ghatana/yappc/agents/framework/

# After module migration
rm -rf products/yappc/core/agents/
rm -rf products/yappc/core/domain/
rm -rf products/yappc/core/framework/

# After frontend migration
rm -rf products/yappc/frontend/libs/core/
rm -rf products/yappc/frontend/libs/types/
rm -rf products/yappc/frontend/libs/utils/
# ... and 32 other old libraries
```

### Files to Delete
- Custom registry: `YappcAgentRegistry.java`
- Any duplicate framework code
- Old build files from migrated modules

---

## 📊 EXECUTION CHECKLIST

### Phase 1: AEP Integration (Priority 1)
- [ ] Create `YamlToManifestConverter.java`
- [ ] Update `YappcAgentLoader` to use AEP `AgentRegistryService`
- [ ] Delete custom `YappcAgentRegistry.java`
- [ ] Add AEP dependencies to build files
- [ ] Test agent registration with AEP
- [ ] Integrate event processing

### Phase 2: Module Migration (Priority 2)
- [ ] Create source directories in new modules
- [ ] Migrate source code from old to new
- [ ] Update package declarations
- [ ] Fix all import statements
- [ ] Update settings.gradle.kts
- [ ] Update build dependencies
- [ ] Run tests to verify migration
- [ ] Delete old module directories

### Phase 3: Frontend Migration (Priority 3)
- [ ] Create src/ directories in new packages
- [ ] Migrate source code from old to new
- [ ] Update import statements across codebase
- [ ] Update pnpm-workspace.yaml
- [ ] Run tests to verify migration
- [ ] Delete old library directories

### Phase 4: Cleanup (Priority 4)
- [ ] Delete empty framework folder
- [ ] Delete all old module directories
- [ ] Delete all old library directories
- [ ] Remove unused dependencies
- [ ] Update documentation

---

## 🎯 SUCCESS CRITERIA

- ✅ Zero custom agent registry code (use AEP)
- ✅ Zero duplicate framework code
- ✅ All source code in new consolidated modules
- ✅ All old modules deleted
- ✅ All old frontend libraries deleted
- ✅ Build succeeds with new structure
- ✅ All tests passing
- ✅ Maximum reuse of AEP/platform libraries

---

## 📈 REALISTIC STATUS TRACKING

| Phase | Planning | Implementation | Migration | Cleanup | Total |
|-------|----------|----------------|-----------|---------|-------|
| Phase 1 | 100% | 40% | 0% | 0% | **35%** |
| Phase 2 | 100% | 20% | 0% | 0% | **30%** |
| Phase 3 | 100% | 15% | 0% | 0% | **29%** |
| **Overall** | **100%** | **25%** | **0%** | **0%** | **31%** |

**Current Status: 31% Complete (Planning + Partial Design)**

**To Reach 100%:** Execute all corrective actions above
