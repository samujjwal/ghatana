# Virtual-Org Phase 2 Implementation Guide

## Current Status
- **Phase 1**: ✅ Complete - Dependencies added, errors reduced from ~60 to ~30
- **Phase 2**: Interface implementation fixes required

## Remaining Errors Breakdown

### 1. AgentProvider Interface (~10 errors)

**Current Interface** (`virtualorg/spi/AgentProvider.java`):
```java
public interface AgentProvider {
    String getProviderId();
    String getProviderName();
    List<String> getSupportedAgentTypes();
    Promise<Agent> createAgent(String agentType, AgentConfig config);
    ProviderCapabilities getCapabilities();
}
```

**Files to Fix**:
- `plugins/healthcare/HealthcareOrgPlugin.java:364-373`
- `plugins/finance/FinanceOrgPlugin.java:453-460`

**Current Implementation** (WRONG):
```java
private AgentProvider createAgentProvider(String id, String name, String department, Map<String, Object> config) {
    return new AgentProvider() {
        @Override
        public String getId() { return "healthcare." + id; }  // WRONG METHOD
        @Override
        public String getName() { return name; }  // WRONG METHOD
        @Override
        public String getDescription() { return name + " agent for " + department; }  // WRONG METHOD
        @Override
        public Map<String, Object> getDefaultConfiguration() { return Map.copyOf(config); }  // WRONG METHOD
    };
}
```

**Correct Implementation**:
```java
private AgentProvider createAgentProvider(String id, String name, String department, Map<String, Object> config) {
    return new AgentProvider() {
        @Override
        public String getProviderId() { 
            return "healthcare." + id; 
        }
        
        @Override
        public String getProviderName() { 
            return name; 
        }
        
        @Override
        public List<String> getSupportedAgentTypes() { 
            return List.of("healthcare." + id); 
        }
        
        @Override
        public io.activej.promise.Promise<Agent> createAgent(String agentType, AgentConfig config) {
            // Stub implementation - can be enhanced later
            return io.activej.promise.Promise.ofException(
                new UnsupportedOperationException("Agent creation not implemented for " + agentType)
            );
        }
        
        @Override
        public ProviderCapabilities getCapabilities() {
            return ProviderCapabilities.basic();
        }
    };
}
```

### 2. ToolProvider Interface (~5 errors)

**Current Interface** (`virtualorg/spi/ToolProvider.java`):
Need to check the actual interface definition to understand required methods.

**Files to Fix**:
- `plugins/healthcare/HealthcareOrgPlugin.java:376-387`
- `plugins/finance/FinanceOrgPlugin.java` (similar location)

**Action Required**:
1. Read `virtualorg/spi/ToolProvider.java` to see required methods
2. Update `createToolProvider()` method to implement all required methods
3. Add missing `validateArguments()` method if required

### 3. WorkflowStepProvider Interface (~3 errors)

**Files to Fix**:
- `plugins/healthcare/HealthcareOrgPlugin.java:389-404`

**Action Required**:
1. Read `virtualorg/spi/WorkflowStepProvider.java` to see required methods
2. Update `createStepProvider()` method to implement all required methods
3. Add missing `createExecutor()` method if required

### 4. OrganizationTemplate Interface (~3 errors)

**Files to Fix**:
- `plugins/healthcare/HealthcareOrgPlugin.java:411` (HealthcareTemplate class)

**Error**: `HealthcareTemplate is not abstract and does not override abstract method instantiate(String,Map<String,Object>)`

**Action Required**:
1. Read `virtualorg/spi/OrganizationTemplate.java` to see required methods
2. Add missing `instantiate()` method to HealthcareTemplate class

### 5. Record Constructor Issues (~6 errors)

**Files to Fix**:
- `plugins/healthcare/HealthcareOrgPlugin.java:432, 439, 446, 453, 460` (DepartmentDefinition)
- `plugins/healthcare/HealthcareOrgPlugin.java:473, 480, 488` (WorkflowDefinition)

**Error**: `constructor DepartmentDefinition in record DepartmentDefinition cannot be applied to given types`

**Current Record Definition**:
```java
public record DepartmentDefinition(
    String id,
    String name,
    String description,
    List<String> defaultRoles,
    Map<String, Object> config
) {}
```

**Issue**: The record is being instantiated with wrong number or types of parameters.

**Action Required**:
1. Find all `new DepartmentDefinition(...)` calls
2. Ensure they match the record constructor signature exactly
3. Same for `WorkflowDefinition`

### 6. Record Accessor Issues (~3 errors)

**Files to Fix**:
- `spi/OrganizationTemplate.java:353` (ValidationResult)
- `spi/ToolProvider.java:245` (ValidationResult)

**Error**: `invalid accessor method in record ValidationResult`

**Action Required**:
1. Find the ValidationResult record definition
2. Check if accessor methods follow Java record naming conventions
3. Fix any custom accessor methods that don't match the canonical form

## Step-by-Step Fix Process

### Step 1: Fix AgentProvider (Highest Priority)
```bash
# Edit these files:
vim products/virtual-org/src/main/java/com/ghatana/virtualorg/plugins/healthcare/HealthcareOrgPlugin.java
vim products/virtual-org/src/main/java/com/ghatana/virtualorg/plugins/finance/FinanceOrgPlugin.java

# Find the createAgentProvider method
# Replace the implementation as shown above
```

### Step 2: Fix ToolProvider
```bash
# First, understand the interface
cat products/virtual-org/src/main/java/com/ghatana/virtualorg/spi/ToolProvider.java

# Then update createToolProvider method in both plugin files
```

### Step 3: Fix WorkflowStepProvider
```bash
# First, understand the interface
cat products/virtual-org/src/main/java/com/ghatana/virtualorg/spi/WorkflowStepProvider.java

# Then update createStepProvider method
```

### Step 4: Fix OrganizationTemplate
```bash
# Add missing instantiate() method to HealthcareTemplate class
```

### Step 5: Fix Record Constructors
```bash
# Search for all DepartmentDefinition and WorkflowDefinition instantiations
# Ensure they match the record constructor signatures
```

### Step 6: Fix Record Accessors
```bash
# Find ValidationResult record
# Fix accessor methods to follow canonical naming
```

## Verification

After each fix:
```bash
./gradlew :products:virtual-org:compileJava 2>&1 | grep "error:" | wc -l
```

Target: 0 errors

## Estimated Time
- Step 1 (AgentProvider): 30 minutes
- Step 2 (ToolProvider): 20 minutes  
- Step 3 (WorkflowStepProvider): 15 minutes
- Step 4 (OrganizationTemplate): 15 minutes
- Step 5 (Record Constructors): 20 minutes
- Step 6 (Record Accessors): 10 minutes

**Total**: ~2 hours

## Success Criteria
1. `./gradlew :products:virtual-org:compileJava` succeeds with 0 errors
2. Remove exclusions from `build-clean.sh`:
   ```bash
   # Remove these lines:
   -x :products:virtual-org:compileJava \
   -x :products:virtual-org:compileTestJava \
   ```
3. Full build succeeds: `./build-clean.sh`
