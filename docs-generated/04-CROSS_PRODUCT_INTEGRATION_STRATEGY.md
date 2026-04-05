# Cross-Product Integration & End-to-End Test Strategy

## Overview

**Scope**: Integration testing across Kernel, Finance, and PHR products  
**Purpose**: Validate cross-product workflows, data consistency, and unified observability  
**Timeline**: Week 6 of comprehensive implementation plan  
**Test Environment**: Staging with production-like data volumes

---

## Integration Architecture

### Correct Decoupled Architecture

**Key Principle**: PHR and Finance NEVER communicate directly. All interactions flow through Kernel plugins.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           PRODUCTS                                       │
│  ┌──────────────┐                        ┌──────────────┐              │
│  │     PHR      │                        │   Finance    │              │
│  │  (Healthcare)│                        │  (Trading)   │              │
│  │              │                        │              │              │
│  │ ┌──────────┐ │                        │ ┌──────────┐ │              │
│  │ │Billing   │ │   NO DIRECT LINK       │ │Ledger    │ │              │
│  │ │Service   │ │◄──────────────────────►│ │Service   │ │              │
│  │ └────┬─────┘ │                        │ └────┬─────┘ │              │
│  └──────┼───────┘                        └──────┼───────┘              │
│         │                                         │                      │
│         │ Uses                                    │ Implements           │
│         ▼                                         ▼                      │
└─────────────────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────────────────┐
│                      KERNEL PLATFORM (The Hub)                         │
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │              Billing Ledger Plugin (platform)                    │   │
│  │  ┌──────────────┐      ┌──────────────┐      ┌──────────────┐  │   │
│  │  │  Event Bus   │      │   Registry   │      │Audit Plugin  │  │   │
│  │  └──────────────┘      └──────────────┘      └──────────────┘  │   │
│  │                                                                  │   │
│  │  Interface: BillingLedgerPlugin                                  │   │
│  │  - postToLedger(request)                                         │   │
│  │  - queryByCorrelation(id)                                        │   │
│  │                                                                  │   │
│  │  Events: billing.ledger.posted, billing.ledger.retry_scheduled   │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │           LedgerAdapter Interface (implemented by Finance)      │   │
│  │                                                                  │   │
│  │  Finance implements this to receive billing from ANY product    │   │
│  └─────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
```

### Data Flow Example: PHR Encounter Billing

```
PHR Billing Service
       │
       │ 1. POST billing to Kernel plugin
       ▼
┌──────────────────────────┐
│ BillingLedgerPlugin      │
│ - Validates request      │
│ - Generates correlation ID│
│ - Publishes event        │
└──────────┬───────────────┘
           │
           │ 2. Event: "billing.ledger.posted"
           ▼
┌──────────────────────────┐
│    Finance LedgerService │
│  (implements LedgerAdapter)│
│ - Receives event         │
│ - Posts to GL            │
│ - Acknowledges           │
└──────────────────────────┘
```

### What the Test Names Mean

The test `testPHRBillingPostedToFinanceViaKernel` is a **descriptive test name** showing the scenario:

- **PHR** = the source product (could be any product)
- **Finance** = the consumer (implements LedgerAdapter)
- **ViaKernel** = the critical part - they don't touch directly

The Kernel plugin doesn't know about "Finance" - it just calls `LedgerAdapter.post()`. Finance registered itself as the `LedgerAdapter` implementation.

### Integration Points Explained

#### 1. Kernel Plugin Architecture

**What is a Kernel Plugin?**

- A platform-level module that provides shared functionality
- Lives in `platform/java/kernel/src/main/java/com/ghatana/kernel/plugin/`
- Products consume the plugin interface, NOT each other

**Billing Ledger Plugin Contract:**

```java
// Location: platform/java/kernel/plugin/billing/BillingLedgerPlugin.java

public interface BillingLedgerPlugin {
    // Any product calls this to post billing
    Promise<LedgerEntryResult> postToLedger(BillingLedgerRequest request);

    // Query ledger entries across all products
    Promise<List<LedgerEntry>> queryByCorrelation(String correlationId);
}

// Data that flows through (product-agnostic)
public record BillingLedgerRequest(
    String correlationId,     // For tracing
    String sourceProduct,     // "PHR", "INVENTORY", etc.
    String sourceEntityId,    // "ENC-001"
    String sourceEntityType,  // "ENCOUNTER", "SALE", etc.
    BigDecimal amount,
    String debitAccount,    // GL code
    String creditAccount    // GL code
) {}
```

#### 2. How PHR Uses the Plugin (Producer)

**PHR Code** (`products/phr/src/main/java/com/ghatana/phr/billing/BillingService.java`):

```java
@Service
public class BillingService {
    // PHR only knows about Kernel plugin - NO Finance import!
    private final BillingLedgerPlugin billingPlugin;

    public Promise<BillingResult> closeEncounter(PatientEncounter encounter) {
        // Create billing request
        BillingLedgerRequest request = BillingLedgerRequest.builder()
            .sourceProduct("PHR")  // PHR identifies itself
            .sourceEntityId(encounter.id())
            .sourceEntityType("ENCOUNTER")
            .amount(encounter.totalCharge())
            .debitAccount("AR-PATIENT-" + encounter.patientId())
            .creditAccount("REVENUE-CLINICAL")
            .build();

        // Post to Kernel plugin - PHR doesn't know WHO receives it
        return billingPlugin.postToLedger(request);
    }
}
```

**PHR Test** (uses mocked Kernel plugin):

```java
@Test
void testEncounterBillingPosted() {
    // Given: Mock the Kernel plugin
    MockBillingLedgerPlugin mockPlugin = new MockBillingLedgerPlugin();
    BillingService service = new BillingService(mockPlugin);

    // When: Close encounter
    service.closeEncounter(encounter);

    // Then: Verify posted to Kernel plugin (NOT Finance!)
    assertThat(mockPlugin.getPostedRequests()).hasSize(1);
    assertThat(mockPlugin.getPostedRequests().get(0).sourceProduct()).isEqualTo("PHR");
}
```

#### 3. How Finance Uses the Plugin (Consumer)

**Finance Code** (`products/finance/accounting/LedgerService.java`):

```java
@Service
public class LedgerService implements LedgerAdapter {
    // Finance implements Kernel interface - NO PHR import!

    @Override
    public Promise<LedgerEntryResult> postToLedger(BillingLedgerRequest request) {
        // Finance receives from ANY product, not just PHR
        FinanceLedgerRecord record = FinanceLedgerRecord.builder()
            .externalReference(request.sourceProduct() + ":" + request.sourceEntityId())
            .amount(request.amount())
            .debitAccount(request.debitAccount())
            .creditAccount(request.creditAccount())
            .build();

        return saveToFinanceLedger(record);
    }
}
```

**Finance Test** (uses Kernel event, not PHR):

```java
@Test
void testReceivesBillingFromAnyProduct() {
    // Given: Kernel publishes billing event
    KernelEvent billingEvent = KernelEvent.builder()
        .type("billing.ledger.posted")
        .payload(Map.of(
            "sourceProduct", "PHR",  // Could be ANY product
            "amount", 220.00
        ))
        .build();

    // When: Event received
    eventBus.publish(billingEvent);

    // Then: Finance processes (no PHR dependency!)
    verify(financeLedger).save(argThat(entry ->
        entry.amount().equals(new BigDecimal("220.00"))
    ));
}
```

#### 4. Unified Observability via Kernel

**Audit Flow** (all products → Kernel audit plugin):

```
PHR/FINANCE/ANY
     │
     │ auditEvent("patient.accessed", data)
     ▼
┌─────────────────────┐
│  Kernel AuditPlugin │
│                     │
│  - Records event    │
│  - Adds correlation │
│  - Immutable storage│
└─────────────────────┘
     │
     ▼
Unified Audit Trail (can query across all products)
```

#### 5. Inter-Product Communication Patterns

When products need to communicate, they do so **only** through Kernel mechanisms:

**Pattern A: Event Bus (Fire-and-Forget)**

```java
// PHR publishes event via Kernel
KernelEvent event = KernelEvent.builder()
    .type("phr.encounter.closed")
    .source("phr")
    .correlationId(correlationId)
    .payload(Map.of("encounterId", "ENC-001"))
    .build();

kernelEventBus.publish(event);

// Finance subscribes via Kernel (no direct PHR dependency)
kernelEventBus.subscribe("phr.encounter.closed", handler);
```

**Pattern B: Capability Request (Request-Response)**

```java
// Finance requests PHR's consent capability via Kernel
ConsentService consentService = kernelContext
    .findCapability(Capability.CONSENT_MANAGEMENT)
    .orElseThrow();

// Use the capability through Kernel abstraction
consentService.checkConsent(patientId, operation);
```

**Pattern C: Plugin Interface (Shared Platform)**

```java
// Both products use Kernel's BillingLedgerPlugin
BillingLedgerPlugin plugin = kernelContext.getPlugin("billing-ledger");

// PHR posts
plugin.postToLedger(phrRequest);

// Finance implements LedgerAdapter to receive
```

**Anti-Pattern (NEVER do this):**

```java
// WRONG - Direct product dependency
import com.ghatana.finance.LedgerService;  // ❌ PHR should NOT import Finance

// WRONG - Direct HTTP/RPC call
HttpClient.post("http://finance-api:8080/ledger", billing);  // ❌ Bypasses Kernel
```

---

## Test Suite Implementation

### 1. Kernel Billing Plugin Integration Tests

**File**: `platform/java/kernel/src/test/java/com/ghatana/kernel/plugin/billing/BillingLedgerPluginIntegrationTest.java`

````java
/**
 * @doc.type test
 * @doc.purpose Validates Kernel billing plugin routes billing between products
 * @doc.layer integration
 * @doc.pattern Plugin
 */
@DisplayName("Kernel Billing Plugin Integration Tests")
class BillingLedgerPluginIntegrationTest extends EventloopTestBase {

    private KernelContext kernelContext;
    private BillingLedgerPlugin billingPlugin;
    private MockLedgerAdapter mockLedgerAdapter;
    private MockEventBus eventBus;

    @BeforeEach
    void setup() {
        KernelRegistry registry = KernelRegistry.create();

        eventBus = new MockEventBus();
        mockLedgerAdapter = new MockLedgerAdapter();

        // Register billing plugin at Kernel level
        BillingLedgerPluginImpl plugin = new BillingLedgerPluginImpl(
            eventBus, mockAuditService, mockLedgerAdapter
        );

        runPromise(() -> registry.register(
            KernelModuleDescriptor.builder()
                .id("billing-ledger-plugin")
                .name("Billing Ledger Plugin")
                .capabilities(Set.of("billing", "ledger-posting"))
                .build(),
            plugin
        )).assertComplete();

        kernelContext = DefaultKernelContext.create(registry);
        runPromise(() -> kernelContext.initialize()).assertComplete();

        billingPlugin = plugin;
    }

    /**
     * @doc.test_type integration
     * @doc.coverage end-to-end
     * @doc.scenario happy path - PHR posts via Kernel, Finance receives
     */
    @Test
    void testPHRBillingPostedToFinanceViaKernel() {
        // Setup: Simulate PHR posting billing
        String correlationId = "corr-encounter-001";

        BillingLedgerRequest phrRequest = BillingLedgerRequest.builder()
            .correlationId(correlationId)
            .sourceProduct("PHR")           // PHR identifies itself
            .sourceEntityId("ENC-001")      // PHR encounter ID
            .sourceEntityType("ENCOUNTER")
            .amount(new BigDecimal("220.00"))
            .currency("NPR")
            .debitAccount("AR-PATIENT-P001")
            .creditAccount("REVENUE-CLINICAL")
            .description("PHR Encounter ENC-001")
            .metadata(Map.of(
                "patientId", "P001",
                "providerId", "PROV-001"
            ))
            .timestamp(Instant.now())
            .build();

        // Execute: PHR posts via Kernel plugin
        LedgerEntryResult result = runPromise(() ->
            billingPlugin.postToLedger(phrRequest)
        );

        // Verify: Kernel plugin accepted the posting
        assertThat(result.success()).isTrue();
        assertThat(result.entryId()).isNotNull();

        // Verify: Ledger adapter (Finance) received the entry
        assertThat(mockLedgerAdapter.getPostedEntries()).hasSize(1);

        LedgerEntry entry = mockLedgerAdapter.getPostedEntries().get(0);
        assertThat(entry.correlationId()).isEqualTo(correlationId);
        assertThat(entry.sourceProduct()).isEqualTo("PHR");  // Preserved but opaque
        assertThat(entry.amount()).isEqualTo(new BigDecimal("220.00"));

        // Verify: Event published for subscribers
        List<KernelEvent> publishedEvents = eventBus.getPublishedEvents();
        assertThat(publishedEvents).anyMatch(e ->
            e.type().equals("billing.ledger.posted") &&
            e.correlationId().equals(correlationId)
        );
    }

    /**
     * @doc.test_type integration
     * @doc.coverage end-to-end
     * @doc.scenario Finance can query by correlation
     */
    @Test
    void testFinanceQueriesLedgerByCorrelation() {
        // Setup: Multiple postings with same correlation
        String correlationId = "corr-multi-001";

        List<BillingLedgerRequest> requests = List.of(
            createRequest(correlationId, "PHR", "ENC-001", 150.00),
            createRequest(correlationId, "PHR", "ENC-002", 200.00),
            createRequest(correlationId, "PHR", "MED-001", 75.00)
        );

        requests.forEach(r ->
            runPromise(() -> billingPlugin.postToLedger(r)).assertComplete()
        );

        // Execute: Finance queries by correlation
        List<LedgerEntry> entries = runPromise(() ->
            billingPlugin.queryByCorrelation(correlationId)
        );

        // Verify: All entries retrieved
        assertThat(entries).hasSize(3);
        assertThat(entries)
            .extracting(LedgerEntry::sourceEntityId)
            .containsExactlyInAnyOrder("ENC-001", "ENC-002", "MED-001");

        BigDecimal total = entries.stream()
            .map(LedgerEntry::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(total).isEqualTo(new BigDecimal("425.00"));
    }

    /**
     * @doc.test_type integration
     * @doc.coverage error handling
     * @doc.scenario Ledger failure handling
     */
    @Test
    void testLedgerPostingFailureHandling() {
        // Setup: Simulate ledger failure
        mockLedgerAdapter.simulateFailure(true);

        BillingLedgerRequest request = createRequest("corr-fail-001", "PHR", "ENC-003", 100.00);

        // Execute: Posting should still succeed at plugin level (async retry)
        LedgerEntryResult result = runPromise(() ->
            billingPlugin.postToLedger(request)
        );

        // Verify: Result indicates pending status (retry scheduled)
        assertThat(result.status()).isEqualTo(LedgerEntryStatus.PENDING_RETRY);

        // Verify: Failure event published
        await().atMost(Duration.ofSeconds(2)).until(() ->
            eventBus.getPublishedEvents().stream()
                .anyMatch(e -> e.type().equals("billing.ledger.retry_scheduled"))
        );

        // Simulate: Ledger recovers
        mockLedgerAdapter.simulateFailure(false);

        // Trigger: Retry
        runPromise(() -> billingPlugin.retryPendingEntries()).assertComplete();

        // Verify: Now posted
        await().atMost(Duration.ofSeconds(5)).until(() ->
            mockLedgerAdapter.getPostedEntries().stream()
                .anyMatch(e -> e.correlationId().equals("corr-fail-001"))
        );
    }

    /**
     * @doc.test_type integration
     * @doc.coverage audit compliance
     * @doc.scenario All postings audited
     */
    @Test
    void testBillingPluginAuditTrail() {
        String correlationId = "corr-audit-001";

        BillingLedgerRequest request = BillingLedgerRequest.builder()
            .correlationId(correlationId)
            .sourceProduct("PHR")
            .sourceEntityId("ENC-004")
            .amount(new BigDecimal("300.00"))
            .debitAccount("AR-PATIENT-P004")
            .creditAccount("REVENUE-CLINICAL")
            .build();

        runPromise(() -> billingPlugin.postToLedger(request)).assertComplete();

        // Verify: Audit entry created
        List<AuditEvent> auditEvents = mockAuditService.getEvents();
        assertThat(auditEvents).anyMatch(e ->
            e.eventType().equals("ledger.posted") &&
            e.correlationId().equals(correlationId) &&
            e.data().get("amount").equals(new BigDecimal("300.00"))
        );
    }

    private BillingLedgerRequest createRequest(String correlationId, String product,
            String entityId, double amount) {
        return BillingLedgerRequest.builder()
            .correlationId(correlationId)
            .sourceProduct(product)
            .sourceEntityId(entityId)
            .amount(new BigDecimal(amount))
            .currency("NPR")
            .debitAccount("AR-TEST")
            .creditAccount("REV-TEST")
            .timestamp(Instant.now())
            .build();
    }
}

---

### 2. Kernel-Product Integration Tests

**File**: `platform/java/kernel/src/test/java/com/ghatana/kernel/integration/CrossProductIntegrationTest.java`

```java
/**
 * @doc.type test
 * @doc.purpose Validates kernel integration with all products
 * @doc.layer integration
 * @doc.products Kernel, PHR, Finance
 */
@DisplayName("Cross-Product Kernel Integration Tests")
class CrossProductIntegrationTest extends EventloopTestBase {

    private KernelRegistry registry;
    private KernelContext context;
    private PHRKernelModule phrModule;
    private FinanceKernelModule financeModule;

    @BeforeEach
    void setup() {
        registry = KernelRegistry.create();
        phrModule = new PHRKernelModule();
        financeModule = new FinanceKernelModule();
    }

    /**
     * @doc.test_type integration
     * @doc.coverage lifecycle
     */
    @Test
    void testMultiProductModuleLifecycle() {
        // Register both products
        runPromise(() -> registry.register(
            KernelModuleDescriptor.builder()
                .id("phr")
                .name("PHR Nepal")
                .capabilities(Set.of("healthcare-records", "fhir"))
                .build(),
            phrModule
        )).assertComplete();

        runPromise(() -> registry.register(
            KernelModuleDescriptor.builder()
                .id("finance")
                .name("Finance")
                .capabilities(Set.of("trading", "risk", "ledger"))
                .dependencies(Set.of("phr")) // Finance depends on PHR for billing
                .build(),
            financeModule
        )).assertComplete();

        context = DefaultKernelContext.create(registry);

        // Initialize - should respect dependency order
        runPromise(() -> context.initialize()).assertComplete();

        // Verify: PHR initialized before Finance (dependency order)
        assertThat(phrModule.getInitTimestamp()).isBefore(financeModule.getInitTimestamp());

        // Verify: Both running
        KernelHealth health = runPromise(() -> context.checkHealth());
        assertThat(health.status()).isEqualTo(HealthStatus.HEALTHY);
        assertThat(health.moduleHealth()).hasSize(2);
        assertThat(health.moduleHealth()).allMatch(m -> m.status() == ModuleHealthStatus.HEALTHY);

        // Stop
        runPromise(() -> context.stop()).assertComplete();

        // Verify: Clean shutdown
        assertThat(phrModule.isStopped()).isTrue();
        assertThat(financeModule.isStopped()).isTrue();
    }

    /**
     * @doc.test_type integration
     * @doc.coverage event flow
     */
    @Test
    void testCrossProductEventFlow() {
        // Setup event handlers
        List<KernelEvent> phrReceivedEvents = new ArrayList<>();
        List<KernelEvent> financeReceivedEvents = new ArrayList<>();

        phrModule.getEventBus().subscribe("finance.transaction.completed", phrReceivedEvents::add);
        financeModule.getEventBus().subscribe("phr.encounter.closed", financeReceivedEvents::add);

        runPromise(() -> registry.register(
            createDescriptor("phr"), phrModule
        )).assertComplete();

        runPromise(() -> registry.register(
            createDescriptor("finance"), financeModule
        )).assertComplete();

        context = DefaultKernelContext.create(registry);
        runPromise(() -> context.initialize()).assertComplete();

        // PHR publishes event
        KernelEvent phrEvent = KernelEvent.builder()
            .type("phr.encounter.closed")
            .source("phr")
            .payload(Map.of("encounterId", "ENC-001", "total", 150.00))
            .correlationId("corr-001")
            .build();

        runPromise(() -> phrModule.getEventBus().publish(phrEvent)).assertComplete();

        // Verify: Finance received event
        await().atMost(Duration.ofSeconds(2)).until(() -> !financeReceivedEvents.isEmpty());

        assertThat(financeReceivedEvents).hasSize(1);
        assertThat(financeReceivedEvents.get(0).type()).isEqualTo("phr.encounter.closed");
        assertThat(financeReceivedEvents.get(0).correlationId()).isEqualTo("corr-001");

        // Finance responds with event
        KernelEvent financeEvent = KernelEvent.builder()
            .type("finance.transaction.completed")
            .source("finance")
            .payload(Map.of("reference", "ENC-001", "posted", true))
            .correlationId("corr-001")
            .build();

        runPromise(() -> financeModule.getEventBus().publish(financeEvent)).assertComplete();

        // Verify: PHR received response
        await().atMost(Duration.ofSeconds(2)).until(() -> !phrReceivedEvents.isEmpty());

        assertThat(phrReceivedEvents).hasSize(1);
        assertThat(phrReceivedEvents.get(0).type()).isEqualTo("finance.transaction.completed");
    }

    /**
     * @doc.test_type integration
     * @doc.coverage capability discovery
     */
    @Test
    void testCrossProductCapabilityDiscovery() {
        // Register modules with capabilities
        runPromise(() -> registry.register(
            KernelModuleDescriptor.builder()
                .id("phr")
                .capabilities(Set.of(
                    Capability.HEALTHCARE_RECORDS,
                    Capability.FHIR_R4,
                    Capability.CONSENT_MANAGEMENT
                ))
                .build(),
            phrModule
        )).assertComplete();

        runPromise(() -> registry.register(
            KernelModuleDescriptor.builder()
                .id("finance")
                .capabilities(Set.of(
                    Capability.LEDGER,
                    Capability.TRADING,
                    Capability.RISK_MANAGEMENT
                ))
                .build(),
            financeModule
        )).assertComplete();

        context = DefaultKernelContext.create(registry);
        runPromise(() -> context.initialize()).assertComplete();

        // Query capabilities
        Set<KernelCapability> healthcareCaps = runPromise(() ->
            context.findCapabilities(Capability.HEALTHCARE_RECORDS)
        );
        assertThat(healthcareCaps).anyMatch(c -> c.moduleId().equals("phr"));

        Set<KernelCapability> ledgerCaps = runPromise(() ->
            context.findCapabilities(Capability.LEDGER)
        );
        assertThat(ledgerCaps).anyMatch(c -> c.moduleId().equals("finance"));

        // Cross-product capability request
        // Finance needs healthcare consent capability for billing
        Set<KernelCapability> consentCaps = runPromise(() ->
            context.findCapabilities(Capability.CONSENT_MANAGEMENT)
        );
        assertThat(consentCaps).anyMatch(c -> c.moduleId().equals("phr"));

        // Use cross-product capability
        ConsentService consentService = runPromise(() ->
            context.getCapability("phr", Capability.CONSENT_MANAGEMENT)
        );
        assertThat(consentService).isNotNull();
    }

    /**
     * @doc.test_type integration
     * @doc.coverage error handling
     */
    @Test
    void testCrossProductFailurePropagation() {
        // Setup: Finance depends on PHR
        FailingPHRModule failingPhr = new FailingPHRModule();

        runPromise(() -> registry.register(
            KernelModuleDescriptor.builder()
                .id("phr")
                .build(),
            failingPhr
        )).assertComplete();

        runPromise(() -> registry.register(
            KernelModuleDescriptor.builder()
                .id("finance")
                .dependencies(Set.of("phr"))
                .build(),
            financeModule
        )).assertComplete();

        context = DefaultKernelContext.create(registry);

        // PHR fails during start
        failingPhr.failOnStart(true);

        KernelInitializationResult result = runPromise(() -> context.initialize());

        // Verify: Initialization partial failure
        assertThat(result.status()).isEqualTo(InitializationStatus.PARTIAL_FAILURE);
        assertThat(result.failedModules()).contains("phr");

        // Finance should not start due to dependency failure
        assertThat(result.skippedModules()).contains("finance");
        assertThat(financeModule.isInitialized()).isFalse();
    }

    private KernelModuleDescriptor createDescriptor(String id) {
        return KernelModuleDescriptor.builder()
            .id(id)
            .name("Test " + id)
            .build();
    }
}
````

---

### 3. End-to-End Workflow Tests

**File**: `integration-tests/e2e/src/test/java/EndToEndWorkflowTest.java`

```java
/**
 * @doc.type test
 * @doc.purpose End-to-end workflow tests simulating real user scenarios
 * @doc.layer e2e
 * @doc.products PHR, Finance, Kernel
 */
@DisplayName("End-to-End Workflow Tests")
class EndToEndWorkflowTest extends EventloopTestBase {

    private E2ETestEnvironment env;

    @BeforeEach
    void setup() {
        env = E2ETestEnvironment.create();
        env.startAll();
    }

    /**
     * @doc.test_type e2e
     * @doc.coverage complete workflow
     * @doc.scenario patient journey
     */
    @Test
    void testCompletePatientJourney() {
        // Actor: Patient Ram Sharma
        String patientId = "PATIENT-RAM-SHARMA-001";

        // Step 1: Patient registration
        PatientRegistrationRequest registration = PatientRegistrationRequest.builder()
            .name("Ram Sharma")
            .dateOfBirth(LocalDate.of(1980, 5, 15))
            .gender(Gender.MALE)
            .phone("+977-9812345678")
            .address("Kathmandu, Nepal")
            .build();

        PatientRegistrationResult regResult = runPromise(() ->
            env.phrApi().registerPatient(registration)
        );

        assertThat(regResult.patientId()).isEqualTo(patientId);
        assertThat(regResult.success()).isTrue();

        // Step 2: Schedule appointment
        AppointmentRequest appointment = AppointmentRequest.builder()
            .patientId(patientId)
            .providerId("PROV-DR-SHARMA-001")
            .scheduledTime(LocalDateTime.now().plusDays(1))
            .reason("Annual checkup")
            .build();

        AppointmentResult apptResult = runPromise(() ->
            env.phrApi().scheduleAppointment(appointment)
        );

        String appointmentId = apptResult.appointmentId();
        assertThat(appointmentId).isNotNull();

        // Step 3: Check-in with copay
        CopayRequest copay = CopayRequest.builder()
            .patientId(patientId)
            .appointmentId(appointmentId)
            .amount(new BigDecimal("25.00"))
            .paymentMethod(PaymentMethod.CASH)
            .build();

        CopayResult copayResult = runPromise(() -> env.phrApi().collectCopay(copay));
        assertThat(copayResult.receiptId()).isNotNull();

        // Step 4: Provider documents visit
        String encounterId = "ENC-" + appointmentId;

        // Add observations
        ObservationRequest vitals = ObservationRequest.builder()
            .patientId(patientId)
            .encounterId(encounterId)
            .type(ObservationType.VITAL_SIGNS)
            .readings(Map.of(
                "bloodPressure", "120/80",
                "heartRate", 72,
                "temperature", 98.6,
                "weight", 75.0
            ))
            .build();

        runPromise(() -> env.phrApi().recordObservation(vitals)).assertComplete();

        // Add clinical note
        ClinicalNoteRequest note = ClinicalNoteRequest.builder()
            .patientId(patientId)
            .encounterId(encounterId)
            .providerId("PROV-DR-SHARMA-001")
            .content("Patient presents for annual checkup. All vitals within normal limits.")
            .build();

        runPromise(() -> env.phrApi().createClinicalNote(note)).assertComplete();

        // Step 5: Order lab tests
        LabOrderRequest labs = LabOrderRequest.builder()
            .patientId(patientId)
            .encounterId(encounterId)
            .tests(List.of("CBC", "LIPID_PANEL", "GLUCOSE"))
            .build();

        LabOrderResult labResult = runPromise(() -> env.phrApi().orderLabs(labs));
        String labOrderId = labResult.orderId();

        // Step 6: Lab results received (simulate external lab)
        LabResultRequest results = LabResultRequest.builder()
            .orderId(labOrderId)
            .results(List.of(
                LabResultItem.builder()
                    .testCode("GLUCOSE")
                    .value(95)
                    .unit("mg/dL")
                    .referenceRange("70-100")
                    .status(LabResultStatus.NORMAL)
                    .build(),
                LabResultItem.builder()
                    .testCode("CHOLESTEROL")
                    .value(220)
                    .unit("mg/dL")
                    .referenceRange("<200")
                    .status(LabResultStatus.HIGH)
                    .build()
            ))
            .build();

        runPromise(() -> env.phrApi().receiveLabResults(results)).assertComplete();

        // Step 7: AI flags high cholesterol
        await().atMost(Duration.ofSeconds(5)).until(() -> {
            List<ClinicalAlert> alerts = env.phrApi().getAlertsForPatient(patientId);
            return alerts.stream().anyMatch(a ->
                a.type().equals("LAB_ANOMALY") &&
                a.details().contains("CHOLESTEROL")
            );
        });

        // Step 8: Provider prescribes medication
        PrescriptionRequest rx = PrescriptionRequest.builder()
            .patientId(patientId)
            .encounterId(encounterId)
            .medication("Atorvastatin 20mg")
            .dosage("Once daily")
            .quantity(30)
            .refills(2)
            .build();

        PrescriptionResult rxResult = runPromise(() -> env.phrApi().createPrescription(rx));

        // Step 9: Close encounter and bill
        EncounterCloseRequest close = EncounterCloseRequest.builder()
            .encounterId(encounterId)
            .services(List.of(
                ServiceCode.builder().code("99213").description("Office visit").charge(150.00).build(),
                ServiceCode.builder().code("85025").description("CBC").charge(45.00).build(),
                ServiceCode.builder().code("80061").description("Lipid panel").charge(75.00).build()
            ))
            .build();

        EncounterCloseResult closeResult = runPromise(() -> env.phrApi().closeEncounter(close));

        // Step 10: Verify billing posted to Finance
        await().atMost(Duration.ofSeconds(10)).until(() -> {
            BigDecimal expected = new BigDecimal("295.00"); // 150 + 45 + 75 + copay already paid
            return env.financeApi().hasLedgerEntryFor(closeResult.billId());
        });

        LedgerEntry ledgerEntry = env.financeApi().getLedgerEntry(closeResult.billId());
        assertThat(ledgerEntry.amount()).isEqualTo(new BigDecimal("270.00")); // 295 - 25 copay

        // Step 11: Verify patient can view record via FHIR API
        HttpRequest fhirRequest = HttpRequest.newBuilder()
            .uri(URI.create(env.phrFhirUrl() + "/Patient/" + patientId))
            .header("Authorization", "Bearer " + env.getPatientToken(patientId))
            .GET()
            .build();

        HttpResponse<String> fhirResponse = env.httpClient().send(fhirRequest,
            HttpResponse.BodyHandlers.ofString());

        assertThat(fhirResponse.statusCode()).isEqualTo(200);

        Patient fhirPatient = FhirParser.parsePatient(fhirResponse.body());
        assertThat(fhirPatient.getName().get(0).getFamily()).isEqualTo("Sharma");

        // Step 12: Verify complete audit trail
        List<UnifiedAuditEvent> auditTrail = runPromise(() ->
            env.auditService().getAuditTrailForPatient(patientId)
        );

        assertThat(auditTrail).anyMatch(e -> e.eventType().equals("patient.registered"));
        assertThat(auditTrail).anyMatch(e -> e.eventType().equals("appointment.scheduled"));
        assertThat(auditTrail).anyMatch(e -> e.eventType().equals("encounter.closed"));
        assertThat(auditTrail).anyMatch(e -> e.eventType().equals("billing.posted"));
        assertThat(auditTrail).anyMatch(e -> e.eventType().equals("fhir.access"));
    }

    /**
     * @doc.test_type e2e
     * @doc.coverage fraud detection
     * @doc.scenario security
     */
    @Test
    void testFraudDetectionInTradingFlow() {
        // Actor: Trading desk user
        String traderId = "TRADER-001";
        String accountId = "ACCT-INSTITUTION-001";

        // Step 1: Normal trading activity
        OrderRequest normalOrder = OrderRequest.builder()
            .accountId(accountId)
            .symbol("AAPL")
            .side(Side.BUY)
            .quantity(100)
            .orderType(OrderType.MARKET)
            .traderId(traderId)
            .build();

        OrderResult normalResult = runPromise(() ->
            env.financeApi().submitOrder(normalOrder)
        );

        assertThat(normalResult.status()).isEqualTo(OrderStatus.FILLED);

        // Step 2: Suspicious activity - unusual size
        OrderRequest suspiciousOrder = OrderRequest.builder()
            .accountId(accountId)
            .symbol("AAPL")
            .side(Side.SELL)
            .quantity(1000000) // 1M shares - unusual
            .orderType(OrderType.MARKET)
            .traderId(traderId)
            .build();

        // Step 3: Fraud detection should flag
        await().atMost(Duration.ofSeconds(5)).until(() -> {
            List<FraudAlert> alerts = env.financeApi().getFraudAlerts();
            return alerts.stream().anyMatch(a ->
                a.type() == FraudAlertType.UNUSUAL_SIZE &&
                a.traderId().equals(traderId)
            );
        });

        // Step 4: Order should be held for review
        OrderResult suspiciousResult = runPromise(() ->
            env.financeApi().submitOrder(suspiciousOrder)
        );

        assertThat(suspiciousResult.status()).isEqualTo(OrderStatus.PENDING_REVIEW);

        // Step 5: Compliance officer review
        ReviewRequest review = ReviewRequest.builder()
            .orderId(suspiciousResult.orderId())
            .reviewerId("COMPLIANCE-001")
            .decision(ReviewDecision.APPROVE_WITH_LIMIT)
            .limitQuantity(500000)
            .build();

        ReviewResult reviewResult = runPromise(() ->
            env.financeApi().submitReview(review)
        );

        assertThat(reviewResult.approved()).isTrue();

        // Step 6: Order proceeds with limit
        Order modifiedOrder = env.financeApi().getOrder(suspiciousResult.orderId());
        assertThat(modifiedOrder.quantity()).isEqualTo(500000);

        // Step 7: Verify audit trail includes fraud review
        List<ComplianceAuditEvent> auditTrail = runPromise(() ->
            env.financeApi().getComplianceAuditForOrder(suspiciousResult.orderId())
        );

        assertThat(auditTrail).anyMatch(e -> e.eventType().equals("fraud.alert.generated"));
        assertThat(auditTrail).anyMatch(e -> e.eventType().equals("order.review.requested"));
        assertThat(auditTrail).anyMatch(e -> e.eventType().equals("compliance.review.completed"));
    }

    @AfterEach
    void cleanup() {
        env.stopAll();
    }
}
```

---

## Performance Test Strategy

### Load Testing Scenarios

```java
/**
 * @doc.type test
 * @doc.purpose Load testing for cross-product scenarios
 * @doc.layer performance
 */
@DisplayName("Cross-Product Load Tests")
class CrossProductLoadTest extends EventloopTestBase {

    private LoadTestEnvironment env;

    @BeforeEach
    void setup() {
        env = LoadTestEnvironment.create();
    }

    /**
     * @doc.test_type performance
     * @doc.coverage load
     */
    @Test
    void testConcurrentPHREncounters() {
        int concurrentEncounters = 100;
        int encountersPerMinute = 500;

        LoadTestResult result = runPromise(() -> env.runLoadTest(
            LoadTestSpec.builder()
                .scenario("phr.close-encounter")
                .concurrency(concurrentEncounters)
                .throughputPerSecond(encountersPerMinute / 60)
                .duration(Duration.ofMinutes(5))
                .build()
        ));

        assertThat(result.successRate()).isGreaterThan(0.995); // 99.5% success
        assertThat(result.p95LatencyMs()).isLessThan(500);
        assertThat(result.p99LatencyMs()).isLessThan(1000);

        // Verify: All billing posted to Finance
        assertThat(env.financeLedger().getUnreconciledCount()).isEqualTo(0);
    }

    /**
     * @doc.test_type performance
     * @doc.coverage stress
     */
    @Test
    void testKernelEventBusThroughput() {
        int eventsPerSecond = 10000;
        int consumers = 50;

        ThroughputTestResult result = runPromise(() -> env.testEventBusThroughput(
            ThroughputTestSpec.builder()
                .eventsPerSecond(eventsPerSecond)
                .consumers(consumers)
                .duration(Duration.ofMinutes(2))
                .build()
        ));

        assertThat(result.actualThroughput()).isGreaterThan(eventsPerSecond * 0.95);
        assertThat(result.eventLossRate()).isLessThan(0.001); // < 0.1% loss
        assertThat(result.averageLatencyMs()).isLessThan(10);
    }
}
```

---

## Test Execution Schedule

### Week 6 - Integration & E2E Testing

| Day   | Focus                      | Deliverables             |
| ----- | -------------------------- | ------------------------ |
| Day 1 | PHR-Finance billing tests  | 5 integration test files |
| Day 2 | Kernel-product integration | 3 integration test files |
| Day 3 | End-to-end workflows       | 3 E2E test files         |
| Day 4 | Performance baseline       | JMH benchmarks           |
| Day 5 | Load testing               | k6/Gatling scripts       |
| Day 6 | CI/CD integration          | GitHub Actions workflow  |
| Day 7 | Documentation              | Test runbook             |

---

## CI/CD Integration

### GitHub Actions Workflow

```yaml
# .github/workflows/cross-product-integration.yml
name: Cross-Product Integration Tests

on:
  push:
    branches: [main, staging]
    paths:
      - "platform/java/kernel/**"
      - "products/finance/**"
      - "products/phr/**"
      - "integration-tests/**"
  pull_request:
    branches: [main]

jobs:
  integration-test:
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:15
        env:
          POSTGRES_USER: test
          POSTGRES_PASSWORD: test
          POSTGRES_DB: ghatana_test
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          - 5432:5432

      redis:
        image: redis:7
        ports:
          - 6379:6379

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: "21"
          distribution: "temurin"

      - name: Start test environment
        run: |
          docker-compose -f integration-tests/docker-compose.yml up -d

      - name: Run kernel tests
        run: ./gradlew :platform:java:kernel:test

      - name: Run product unit tests
        run: |
          ./gradlew :products:finance:test
          ./gradlew :products:phr:test

      - name: Run integration tests
        run: ./gradlew :integration-tests:test
        env:
          GHATANA_TEST_DB_URL: jdbc:postgresql://localhost:5432/ghatana_test
          GHATANA_TEST_REDIS_URL: redis://localhost:6379

      - name: Run E2E tests
        run: ./gradlew :integration-tests:e2eTest

      - name: Generate test report
        uses: dorny/test-reporter@v1
        if: success() || failure()
        with:
          name: Integration Tests
          path: "**/build/test-results/**/*.xml"
          reporter: java-junit

      - name: Stop test environment
        if: always()
        run: |
          docker-compose -f integration-tests/docker-compose.yml down
```

---

## Success Criteria

1. **Integration Test Coverage**: All cross-product flows tested
2. **E2E Test Coverage**: 10+ complete user journeys tested
3. **Performance**: All latency targets met under load
4. **Reliability**: 99.9% success rate in load tests
5. **Data Consistency**: Zero reconciliation mismatches
6. **Audit Completeness**: 100% cross-product events logged

---

_Document Version: 1.0_  
_Last Updated: April 4, 2026_
