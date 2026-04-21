# Performance Benchmarks

## Current State

As of the platform coverage audit (P3-23), the platform does not have comprehensive JMH benchmarks. The agent-core module previously declared JMH dependencies but had no benchmark sources, which has been removed.

## Benchmark Strategy

Performance benchmarks should be added when specific performance requirements are identified for critical paths. The following modules are candidates for benchmarking when needed:

### Priority Modules for Benchmarking

1. **Database Operations** (`platform/java/database`)
   - Connection pool performance
   - Query execution time
   - Transaction throughput

2. **HTTP Layer** (`platform/java/http`)
   - Request handling throughput
   - Response serialization performance
   - Filter chain overhead

3. **Messaging** (`platform/java/messaging`)
   - Message throughput
   - Consumer lag
   - Producer latency

4. **Observability** (`platform/java/observability`)
   - Trace ingestion rate
   - Span serialization performance
   - Query response time

## Adding Benchmarks

When adding JMH benchmarks to a module:

1. Enable JMH source set in build.gradle.kts:
```kotlin
ext.enableJmh = true

dependencies {
    jmhImplementation(libs.jmh.core)
    jmhAnnotationProcessor(libs.jmh.generator.annprocess)
}
```

2. Create benchmark class in `src/jmh/java/`:
```java
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ExampleBenchmark {
    @Benchmark
    public void benchmarkOperation() {
        // benchmark code
    }
}
```

3. Run benchmarks:
```bash
./gradlew :module:jmh
```

## CI Integration

To add benchmark execution to CI:
1. Add nightly workflow in `.github/workflows/benchmarks.yml`
2. Configure to run JMH benchmarks
3. Store results for trend analysis
4. Add performance regression detection

## Resources

- JMH Documentation: http://openjdk.java.net/projects/code-tools/jmh/
- Gradle JMH Plugin: https://github.com/melix/jmh-gradle-plugin
