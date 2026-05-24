# EventOperator

**Status:** Target specification  
**Owner:** AEP maintainers
**Current code contract:** `products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/aep/operator/contract/EventOperator.java`
**Compatibility runtime contract:** `products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/core/operator/UnifiedOperator.java`

`EventOperator` is the formal validation, compilation, and execution contract for AEP operators. `UnifiedOperator` remains the current compatibility runtime abstraction while modules migrate.

```java
interface EventOperator<I, O> {
    OperatorId id();
    OperatorKind kind();
    OperatorVersion version();
    ValidationResult validate(OperatorSpec spec, ValidationContext ctx);
    RuntimePlan compile(OperatorSpec spec, CompileContext ctx);
    Promise<EventOperatorResult<O>> process(EventContext<I> input, OperatorRuntimeContext ctx);
}
```

Every operator reports metrics and traces through shared abstractions. Standard operators and agent-backed `EventOperatorCapability` implementations use the same runtime contract.
