# EventContext

**Status:** Target specification  
**Owner:** AEP maintainers
**Current code contract:** `products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/aep/model/EventContext.java`

`EventContext` is the standard input to every AEP operator.

```java
record EventContext<T>(
    TenantContext tenant,
    List<CanonicalEvent> events,
    Optional<PatternPartialMatch> partialMatch,
    Optional<PatternMatch> match,
    Map<String, Object> bindings,
    EventTimeContext time,
    UncertaintyContext uncertainty,
    ReplayContext replay
) {}
```

Pattern operators, learning operators, and agent operators consume `EventContext`. Operators must not receive ad hoc raw payloads without context.
