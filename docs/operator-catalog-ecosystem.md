# AEP Operator Catalog Ecosystem

## Overview

The AEP (Agentic Event Processor) Operator Catalog is a central registry for discovering and managing unified operators. It provides a standardized interface for registering, unregistering, and looking up operators by their unique identifiers.

## Architecture

### Core Components

1. **OperatorCatalog Interface** (`com.ghatana.core.operator.catalog.OperatorCatalog`)
   - Central registry interface for discovering and managing unified operators
   - Core operations: register, unregister, lookup
   - Promise-based async API for event loop compatibility

2. **UnifiedOperatorCatalog** (`com.ghatana.core.operator.catalog.UnifiedOperatorCatalog`)
   - Default implementation of the OperatorCatalog interface
   - In-memory storage with concurrent access support
   - Tenant-scoped operator isolation

3. **AepCentralCatalogService** (`com.ghatana.aep.catalog.AepCentralCatalogService`)
   - AEP-specific catalog service implementation
   - Integration with AEP engine and agent registry
   - Validation and reporting capabilities

4. **CatalogValidationReport** (`com.ghatana.aep.catalog.CatalogValidationReport`)
   - Validation and reporting for operator catalog
   - Detects duplicate registrations, missing dependencies
   - Generates health reports for catalog state

## Operator Registration

### Registration Process

1. **Operator Definition**
   ```java
   UnifiedOperator operator = new UnifiedOperator(
       OperatorId.of("transform.normalize"),
       "Normalize data transformation",
       List.of(OperatorCapability.TRANSFORM),
       Map.of("version", "1.0.0")
   );
   ```

2. **Catalog Registration**
   ```java
   OperatorCatalog catalog = new UnifiedOperatorCatalog();
   catalog.register(operator).block();
   ```

3. **Validation**
   - Operator ID uniqueness check
   - Capability validation
   - Dependency verification
   - Metadata completeness

### Operator Lifecycle

| State | Description | Transition |
|-------|-------------|------------|
| UNREGISTERED | Operator not in catalog | → REGISTERED (via register()) |
| REGISTERED | Operator available for lookup | → UNREGISTERED (via unregister()) |
| ACTIVE | Operator actively used | ← REGISTERED (via activation) |
| INACTIVE | Operator registered but not used | ← REGISTERED (via deactivation) |

## Catalog Operations

### Register Operator

```java
Promise<Void> register(UnifiedOperator operator)
```

- **Purpose**: Add a new operator to the catalog
- **Parameters**: 
  - `operator`: The operator to register (never null)
- **Returns**: Promise completing when registration is done
- **Throws**: 
  - `NullPointerException` if operator is null
  - `IllegalArgumentException` if operator ID already registered

### Unregister Operator

```java
Promise<Void> unregister(OperatorId operatorId)
```

- **Purpose**: Remove an operator from the catalog
- **Parameters**:
  - `operatorId`: The ID of the operator to unregister (never null)
- **Returns**: Promise completing when unregistration is done
- **Throws**:
  - `NullPointerException` if operatorId is null
  - `IllegalArgumentException` if operator not found

### Lookup Operator

```java
Promise<UnifiedOperator> lookup(OperatorId operatorId)
```

- **Purpose**: Find an operator by its ID
- **Parameters**:
  - `operatorId`: The ID to look up
- **Returns**: Promise resolving to the operator, or null if not found
- **Throws**: 
  - `NullPointerException` if operatorId is null

### Optional Lookup

```java
Promise<Optional<UnifiedOperator>> get(OperatorId operatorId)
```

- **Purpose**: Lookup operator returning Optional for safer null handling
- **Parameters**:
  - `operatorId`: The operator ID to look up
- **Returns**: Promise resolving to an optional containing the operator if found
- **Default Implementation**: Wraps `lookup()` result in Optional

## Integration Points

### AEP Engine Integration

The AEP Engine integrates with the Operator Catalog through:

1. **Agent Registration**
   - Agents register their operators with the catalog on startup
   - Catalog validates operator compatibility
   - Engine maintains operator-to-agent mapping

2. **Pipeline Execution**
   - Pipeline steps reference operators by ID
   - Engine resolves operators from catalog during execution
   - Catalog provides operator metadata for scheduling

3. **Dynamic Operator Loading**
   - Runtime registration of new operators
   - Hot-reload support for operator updates
   - Catalog synchronization across cluster nodes

### Agent Runtime Integration

The Agent Runtime uses the catalog for:

1. **Operator Discovery**
   - Agents discover available operators for task execution
   - Capability matching between tasks and operators
   - Operator selection based on metadata

2. **Operator Dispatching**
   - `CatalogAgentDispatcher` routes tasks to appropriate operators
   - Load balancing across operator instances
   - Failure handling with fallback operators

### Validation and Reporting

The `CatalogValidationReport` provides:

1. **Health Checks**
   - Duplicate operator detection
   - Missing dependency identification
   - Metadata validation
   - Consistency checks

2. **Reporting**
   - Summary statistics (operator count, by type)
   - Error reports with details
   - Recommendations for catalog maintenance

## Usage Patterns

### Basic Usage

```java
// Create catalog
OperatorCatalog catalog = new UnifiedOperatorCatalog();

// Register operators
UnifiedOperator op1 = new UnifiedOperator(
    OperatorId.of("transform.normalize"),
    "Normalize data transformation",
    List.of(OperatorCapability.TRANSFORM),
    Map.of("version", "1.0.0")
);
catalog.register(op1).block();

// Lookup operator
Optional<UnifiedOperator> found = catalog.get(
    OperatorId.of("transform.normalize")
).block();

// Unregister operator
catalog.unregister(OperatorId.of("transform.normalize")).block();
```

### Tenant-Scoped Catalog

```java
// Create tenant-scoped catalog
OperatorCatalog catalog = new UnifiedOperatorCatalog("tenant-123");

// Register operator for tenant
catalog.register(tenantOperator).block();

// Lookup is tenant-isolated
Optional<UnifiedOperator> found = catalog.get(operatorId).block();
```

### Validation

```java
CatalogValidationReport report = CatalogValidationReport.validate(catalog);

if (report.hasErrors()) {
    report.errors().forEach(error -> {
        System.err.println("Catalog error: " + error);
    });
}

// Get summary
System.out.println("Total operators: " + report.totalOperators());
System.out.println("Errors: " + report.errorCount());
```

## Best Practices

### Registration

1. **Unique Operator IDs**: Ensure operator IDs are globally unique across the catalog
2. **Version Management**: Include version in operator metadata for compatibility tracking
3. **Capability Declaration**: Declare all operator capabilities for proper matching
4. **Dependency Specification**: List operator dependencies for validation

### Lookup

1. **Handle Missing Operators**: Use `get()` instead of `lookup()` for Optional handling
2. **Cache Results**: Cache frequently accessed operators to reduce catalog lookups
3. **Fallback Operators**: Implement fallback logic for missing critical operators

### Maintenance

1. **Regular Validation**: Run validation reports periodically to detect issues
2. **Cleanup**: Unregister unused operators to maintain catalog health
3. **Version Updates**: Update operator registrations when versions change

## Error Handling

### Common Errors

| Error | Cause | Resolution |
|-------|-------|------------|
| NullPointerException | Null operator or operatorId | Validate inputs before registration/lookup |
| IllegalArgumentException | Duplicate operator ID | Check for existing registration before adding |
| IllegalStateException | Catalog closed | Reopen catalog or use new instance |
| PromiseException | Async operation failure | Check promise result and handle exceptions |

### Error Handling Pattern

```java
try {
    catalog.register(operator).block();
} catch (NullPointerException e) {
    // Handle null input
    throw new IllegalArgumentException("Operator cannot be null", e);
} catch (IllegalArgumentException e) {
    // Handle duplicate registration
    log.warn("Operator already registered: {}", operator.id());
    throw e;
} catch (Exception e) {
    // Handle unexpected errors
    log.error("Failed to register operator: {}", operator.id(), e);
    throw new CatalogException("Registration failed", e);
}
```

## Performance Considerations

1. **Concurrent Access**: `UnifiedOperatorCatalog` uses concurrent data structures for thread-safe access
2. **Lookup Performance**: O(1) lookup time for operator ID-based queries
3. **Memory Usage**: In-memory storage; consider persistence for large catalogs
4. **Async Operations**: All catalog operations are Promise-based for event loop compatibility

## Security

1. **Tenant Isolation**: Tenant-scoped catalogs prevent cross-tenant operator access
2. **Access Control**: Implement custom catalog wrappers for RBAC
3. **Validation**: Input validation prevents injection attacks
4. **Audit Logging**: Catalog operations should be logged for audit trails

## Extensibility

### Custom Catalog Implementations

```java
public class PersistentOperatorCatalog implements OperatorCatalog {
    private final OperatorCatalog delegate;
    private final CatalogRepository repository;

    @Override
    public Promise<Void> register(UnifiedOperator operator) {
        return delegate.register(operator)
            .then(() -> repository.save(operator));
    }

    // Implement other methods with persistence
}
```

### Custom Validation

```java
public class CustomCatalogValidator implements CatalogValidator {
    @Override
    public ValidationResult validate(OperatorCatalog catalog) {
        // Custom validation logic
        return ValidationResult.builder()
            .addError("Custom validation error")
            .build();
    }
}
```

## Monitoring and Observability

### Metrics

- **Operator Count**: Total number of registered operators
- **Registration Rate**: Rate of operator registrations
- **Lookup Latency**: Time taken for operator lookups
- **Error Rate**: Rate of catalog operation failures

### Logging

Catalog operations should log:
- Successful registrations with operator ID
- Failed unregistrations with reason
- Lookup misses for monitoring operator usage
- Validation errors for catalog health

## Future Enhancements

1. **Distributed Catalog**: Support for distributed operator catalog across cluster nodes
2. **Operator Versioning**: Support for multiple versions of the same operator
3. **Operator Discovery**: Automatic discovery of operators from agent registrations
4. **Catalog Sync**: Synchronization with external operator repositories
5. **Operator Marketplace**: Integration with external operator marketplace

## References

- Operator Catalog Interface: `com.ghatana.core.operator.catalog.OperatorCatalog`
- Unified Operator: `com.ghatana.core.operator.UnifiedOperator`
- AEP Catalog Service: `com.ghatana.aep.catalog.AepCentralCatalogService`
- Validation Report: `com.ghatana.aep.catalog.CatalogValidationReport`

## Support

For questions or issues related to the Operator Catalog:
- Review the Javadoc for detailed API documentation
- Check test cases in `CatalogValidationReportTest` for usage examples
- Consult the AEP architecture documentation for integration patterns
