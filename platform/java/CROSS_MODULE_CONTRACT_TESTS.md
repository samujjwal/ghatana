# Cross-Module Contract Tests

## Overview

Cross-module contract tests verify that interactions between different platform modules and products conform to their published contracts. This ensures that changes in one module don't break consumers in another module.

## Critical Module Boundaries

The following boundaries have been identified as critical for contract testing:

### Platform Internal Boundaries

1. **HTTP Layer → Core**
   - Contract: ResponseBuilder API
   - Consumers: All platform HTTP services
   - Test: Verify response serialization matches contract

2. **Database → Core**
   - Contract: JPA entity mappings
   - Consumers: Platform persistence modules
   - Test: Verify entity serialization/deserialization

3. **Observability → Platform Modules**
   - Contract: TraceStorage API
   - Consumers: Platform services emitting traces
   - Test: Verify trace ingestion contract

4. **Security → Platform Modules**
   - Contract: RBAC policy evaluation
   - Consumers: Platform services with authorization
   - Test: Verify authorization decision contract

### Platform → Product Boundaries

5. **Agent Core → AEP Runtime**
   - Contract: Agent execution API
   - Consumers: AEP agent runtime
   - Test: Verify agent lifecycle contract

6. **Event Cloud → Products**
   - Contract: Event ingestion API
   - Consumers: AEP, Data Cloud, YAPPC
   - Test: Verify event publishing contract

7. **Audit → Platform Services**
   - Contract: Audit event schema
   - Consumers: All platform services
   - Test: Verify audit event contract

## Contract Testing Strategy

### Approach: Consumer-Driven Contracts with Pact

Use Pact for consumer-driven contract testing:
- Consumers define expected interactions
- Providers verify they meet consumer expectations
- Contracts stored in shared location
- CI verifies contracts on both sides

### Implementation Steps

#### 1. Add Pact Dependencies

For consumer modules (e.g., platform/java/http):
```kotlin
dependencies {
    testImplementation("au.com.dius:pact-junit5:4.6.0")
    testImplementation("au.com.dius:pact-consumer-junit5:4.6.0")
}
```

For provider modules (e.g., platform/java/core):
```kotlin
dependencies {
    testImplementation("au.com.dius:pact-junit5-provider:4.6.0")
    testImplementation("au.com.dius:pact-provider-junit5:4.6.0")
}
```

#### 2. Write Consumer Tests

```java
@PactTestFor(providerName = "http-server", pactVersion = "1.0.0")
class HttpResponseContractTest {
    @Pact(consumer = "http-consumer")
    RequestResponsePact createResponsePact(PactDslWithProvider builder) {
        return builder
            .given("response builder creates JSON response")
            .uponReceiving("request for JSON response")
            .path("/api/resource")
            .method("GET")
            .willRespondWith()
            .status(200)
            .header("Content-Type", "application/json")
            .body(PactDslJsonBody.newBody()
                .stringValue("status", "ok")
                .integerType("code", 200))
            .toPact();
    }

    @Test
    @PactTestFor
    void testResponseContract(MockServer mockServer) {
        // Test consumer against mock server
        HttpResponse response = httpClient.get(mockServer.getUrl());
        assertThat(response.getStatusCode()).isEqualTo(200);
    }
}
```

#### 3. Write Provider Tests

```java
@Provider("http-server")
@PactFolder("pacts")
class HttpResponseProviderTest {
    @TestTarget
    final HttpTestTarget target = new HttpTestTarget(8080);

    @BeforeEach
    void setUp() {
        // Start provider service
        server.start();
    }

    @State("response builder creates JSON response")
    void responseBuilderCreatesJsonResponse() {
        // Set up provider state
    }
}
```

#### 4. Store Pacts in Shared Location

```yaml
# .pactbroker.yml
pacticipants:
  - name: http-server
    url: http://localhost:8080
  - name: http-consumer
    url: http://localhost:8081

pact_broker:
  url: http://localhost:9292
```

## CI Integration

### Consumer CI Workflow

```yaml
# .github/workflows/contract-consumer-tests.yml
name: Contract Consumer Tests

on: [push, pull_request]

jobs:
  consumer-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
      - name: Run consumer contract tests
        run: ./gradlew test -PconsumerContract
      - name: Publish pacts
        run: ./gradlew pactPublish
```

### Provider CI Workflow

```yaml
# .github/workflows/contract-provider-tests.yml
name: Contract Provider Tests

on: [push, pull_request]

jobs:
  provider-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
      - name: Run provider contract tests
        run: ./gradlew test -PproviderContract
      - name: Verify pacts
        run: ./gradlew pactVerify
```

## Current Status

As of the platform coverage audit (P3-25), cross-module contract tests are documented but not yet implemented. The framework and approach are defined above for future implementation when specific contract requirements are identified.

## When to Add Contract Tests

Add contract tests when:
1. A module boundary is identified as critical
2. Multiple consumers depend on a module
3. Breaking changes would have significant impact
4. SLA requirements exist for the integration

## Resources

- Pact Documentation: https://docs.pact.io/
- Consumer-Driven Contracts: https://martinfowler.com/articles/consumerDrivenContracts.html
