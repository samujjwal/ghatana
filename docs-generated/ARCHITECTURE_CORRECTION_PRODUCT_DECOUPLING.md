# Corrected Architecture: Product Decoupling via Kernel Platform

## Architectural Principle

**Core Rule**: PHR and Finance are strictly decoupled. They share NO direct dependencies.

**Integration Pattern**: All shared functionality is abstracted through the Kernel platform:
```
PHR ──► Kernel Platform ◄── Finance
         │
         ├── Billing Plugin
         ├── Audit Plugin
         ├── Security Plugin
         └── Event Bus
```

## Implementation Corrections

### 1. Shared Billing Contracts (Platform Level)

**Location**: `platform/java/kernel/src/main/java/com/ghatana/kernel/plugin/billing/`

```java
/**
 * @doc.type interface
 * @doc.purpose Platform-level billing contract for cross-product ledger posting
 * @doc.layer platform
 * @doc.pattern Plugin Contract
 */
public interface BillingLedgerPlugin {
    
    /**
     * Posts a billing entry to the ledger
     * @param request The billing request
     * @return Promise of the ledger entry result
     */
    Promise<LedgerEntryResult> postToLedger(BillingLedgerRequest request);
    
    /**
     * Queries ledger entries by correlation ID
     * @param correlationId The correlation ID
     * @return Promise of ledger entries
     */
    Promise<List<LedgerEntry>> queryByCorrelation(String correlationId);
}

/**
 * @doc.type class
 * @doc.purpose Billing ledger request data structure
 * @doc.layer platform
 */
public record BillingLedgerRequest(
    String correlationId,           // Cross-product correlation
    String sourceProduct,         // "PHR", "FINANCE", etc.
    String sourceEntityId,        // Encounter ID, Trade ID, etc.
    String sourceEntityType,      // "ENCOUNTER", "TRADE", etc.
    BigDecimal amount,
    String currency,
    String debitAccount,          // GL account code
    String creditAccount,         // GL account code
    String description,
    Map<String, Object> metadata,
    Instant timestamp
) {
    public BillingLedgerRequest {
        Objects.requireNonNull(correlationId, "correlationId required");
        Objects.requireNonNull(sourceProduct, "sourceProduct required");
        Objects.requireNonNull(amount, "amount required");
    }
}
```

### 2. Kernel Billing Plugin Implementation

**Location**: `platform/java/kernel/src/main/java/com/ghatana/kernel/plugin/billing/internal/`

```java
/**
 * @doc.type class
 * @doc.purpose Kernel billing plugin implementation
 * @doc.layer platform
 * @doc.pattern Plugin
 */
@KernelPlugin(id = "billing-ledger", version = "1.0.0")
public class BillingLedgerPluginImpl implements BillingLedgerPlugin, KernelModule {
    
    private final EventBus eventBus;
    private final AuditTrailService auditService;
    private final LedgerAdapter ledgerAdapter;
    
    @Inject
    public BillingLedgerPluginImpl(EventBus eventBus, 
                                   AuditTrailService auditService,
                                   @Nullable LedgerAdapter ledgerAdapter) {
        this.eventBus = eventBus;
        this.auditService = auditService;
        this.ledgerAdapter = ledgerAdapter;
    }
    
    @Override
    public Promise<LedgerEntryResult> postToLedger(BillingLedgerRequest request) {
        return Promise.ofCallable(() -> {
            // Validate request
            validateRequest(request);
            
            // Create ledger entry
            LedgerEntry entry = LedgerEntry.builder()
                .id(UUID.randomUUID().toString())
                .correlationId(request.correlationId())
                .sourceProduct(request.sourceProduct())
                .sourceEntityId(request.sourceEntityId())
                .amount(request.amount())
                .currency(request.currency())
                .debitAccount(request.debitAccount())
                .creditAccount(request.creditAccount())
                .description(request.description())
                .metadata(request.metadata())
                .status(LedgerEntryStatus.PENDING)
                .createdAt(Instant.now())
                .build();
            
            // Post to ledger (if adapter available)
            if (ledgerAdapter != null) {
                LedgerPostResult postResult = ledgerAdapter.post(entry);
                entry = entry.withStatus(postResult.success() ? 
                    LedgerEntryStatus.POSTED : LedgerEntryStatus.FAILED);
            }
            
            // Audit the posting
            auditService.record(AuditEvent.builder()
                .eventType("ledger.posted")
                .correlationId(request.correlationId())
                .data(Map.of(
                    "entryId", entry.id(),
                    "sourceProduct", request.sourceProduct(),
                    "amount", request.amount()
                ))
                .build());
            
            // Publish event for interested products
            eventBus.publish(KernelEvent.builder()
                .type("billing.ledger.posted")
                .correlationId(request.correlationId())
                .payload(Map.of(
                    "entryId", entry.id(),
                    "sourceProduct", request.sourceProduct(),
                    "amount", request.amount(),
                    "status", entry.status()
                ))
                .build());
            
            return new LedgerEntryResult(
                entry.id(),
                entry.status(),
                entry.status() == LedgerEntryStatus.POSTED
            );
        });
    }
    
    @Override
    public Promise<List<LedgerEntry>> queryByCorrelation(String correlationId) {
        return ledgerAdapter.queryByCorrelation(correlationId);
    }
    
    private void validateRequest(BillingLedgerRequest request) {
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BillingValidationException("Amount must be positive");
        }
    }
}
```

### 3. PHR Uses Kernel Billing Plugin (Not Finance Directly)

**Location**: `products/phr/src/main/java/com/ghatana/phr/kernel/service/BillingService.java`

```java
/**
 * @doc.type class
 * @doc.purpose PHR billing service - uses Kernel billing plugin
 * @doc.layer product
 * @doc.domain Healthcare
 */
public class BillingService {
    
    // NO direct reference to Finance!
    private final BillingLedgerPlugin billingPlugin; // From Kernel
    private final EventBus eventBus;                  // From Kernel
    private final AuditTrailService auditService;     // From Kernel
    
    @Inject
    public BillingService(BillingLedgerPlugin billingPlugin,
                         EventBus eventBus,
                         AuditTrailService auditService) {
        this.billingPlugin = billingPlugin;
        this.eventBus = eventBus;
        this.auditService = auditService;
        
        // Listen for ledger confirmations (from any product)
        eventBus.subscribe("billing.ledger.posted", this::onLedgerPosted);
    }
    
    public Promise<BillingResult> closeEncounter(PatientEncounter encounter) {
        return Promise.ofCallable(() -> {
            String correlationId = generateCorrelationId();
            String billId = generateBillId();
            
            // Create billing record in PHR
            BillingRecord record = BillingRecord.builder()
                .id(billId)
                .encounterId(encounter.id())
                .patientId(encounter.patientId())
                .totalAmount(encounter.totalCharge())
                .status(BillingStatus.PENDING)
                .correlationId(correlationId)
                .build();
            
            saveBillingRecord(record);
            
            // Post to ledger via Kernel plugin (not directly to Finance!)
            BillingLedgerRequest ledgerRequest = BillingLedgerRequest.builder()
                .correlationId(correlationId)
                .sourceProduct("PHR")
                .sourceEntityId(encounter.id())
                .sourceEntityType("ENCOUNTER")
                .amount(encounter.totalCharge())
                .currency("NPR")
                .debitAccount("AR-PATIENT-" + encounter.patientId())
                .creditAccount("REVENUE-CLINICAL")
                .description("PHR Encounter " + encounter.id())
                .metadata(Map.of(
                    "billId", billId,
                    "patientId", encounter.patientId(),
                    "providerId", encounter.providerId()
                ))
                .timestamp(Instant.now())
                .build();
            
            billingPlugin.postToLedger(ledgerRequest)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        handleLedgerError(billId, error);
                    } else {
                        updateBillStatus(billId, BillingStatus.POSTED, result.entryId());
                    }
                });
            
            return new BillingResult(billId, correlationId, BillingStatus.PENDING);
        });
    }
    
    private void onLedgerPosted(KernelEvent event) {
        // Handle ledger posting confirmation from Kernel
        Map<String, Object> payload = event.payload();
        String correlationId = event.correlationId();
        
        // Find PHR billing record by correlation
        BillingRecord record = findByCorrelation(correlationId);
        if (record != null) {
            String entryId = (String) payload.get("entryId");
            LedgerEntryStatus status = LedgerEntryStatus.valueOf((String) payload.get("status"));
            
            if (status == LedgerEntryStatus.POSTED) {
                updateBillStatus(record.id(), BillingStatus.POSTED, entryId);
            }
        }
    }
}
```

### 4. Finance Uses Kernel Billing Plugin (Not PHR Directly)

**Location**: `products/finance/domains/accounting/src/main/java/com/ghatana/finance/accounting/LedgerService.java`

```java
/**
 * @doc.type class
 * @doc.purpose Finance ledger service - implements Kernel ledger adapter
 * @doc.layer product
 * @doc.domain Accounting
 */
public class LedgerService implements LedgerAdapter {
    
    // NO direct reference to PHR!
    private final EventBus eventBus;
    private final LedgerRepository ledgerRepository;
    
    @Inject
    public LedgerService(EventBus eventBus, LedgerRepository ledgerRepository) {
        this.eventBus = eventBus;
        this.ledgerRepository = ledgerRepository;
        
        // Subscribe to billing events from Kernel
        eventBus.subscribe("billing.ledger.posted", this::handleLedgerPosted);
    }
    
    @Override
    public Promise<LedgerPostResult> post(LedgerEntry entry) {
        return Promise.ofCallable(() -> {
            // Validate accounting rules
            validateAccountingRules(entry);
            
            // Save to Finance ledger
            FinanceLedgerRecord record = FinanceLedgerRecord.builder()
                .id(entry.id())
                .correlationId(entry.correlationId())
                .externalReference(entry.sourceProduct() + ":" + entry.sourceEntityId())
                .debitAccount(mapToFinanceAccount(entry.debitAccount()))
                .creditAccount(mapToFinanceAccount(entry.creditAccount()))
                .amount(entry.amount())
                .currency(entry.currency())
                .description(entry.description())
                .postedAt(Instant.now())
                .build();
            
            ledgerRepository.save(record);
            
            return new LedgerPostResult(true, entry.id());
        });
    }
    
    @Override
    public Promise<List<LedgerEntry>> queryByCorrelation(String correlationId) {
        return Promise.ofCallable(() -> {
            List<FinanceLedgerRecord> records = ledgerRepository.findByCorrelation(correlationId);
            return records.stream()
                .map(this::toLedgerEntry)
                .collect(Collectors.toList());
        });
    }
    
    private void handleLedgerPosted(KernelEvent event) {
        // Finance can react to ledger postings from any product
        Map<String, Object> payload = event.payload();
        String sourceProduct = (String) payload.get("sourceProduct");
        
        if ("PHR".equals(sourceProduct)) {
            // Handle PHR billing (no direct PHR dependency!)
            processHealthcareBilling(event);
        }
        // Handle other products...
    }
    
    private void processHealthcareBilling(KernelEvent event) {
        // React to healthcare billing without PHR dependency
        Map<String, Object> payload = event.payload();
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());
        
        // Update healthcare revenue metrics
        metricsService.recordHealthcareRevenue(amount);
    }
}
```

### 5. Product-Independent Test Strategy

**PHR Tests** - Test PHR in isolation with mocked Kernel:
```java
@DisplayName("PHR Billing Tests")
class BillingServiceTest extends EventloopTestBase {
    
    private BillingService billingService;
    private MockBillingLedgerPlugin mockBillingPlugin; // Mock Kernel plugin
    private MockEventBus mockEventBus;
    
    @BeforeEach
    void setup() {
        mockBillingPlugin = new MockBillingLedgerPlugin();
        mockEventBus = new MockEventBus();
        
        billingService = new BillingService(mockBillingPlugin, mockEventBus, mockAuditService);
    }
    
    @Test
    void testCloseEncounterPostsToLedger() {
        PatientEncounter encounter = createSampleEncounter();
        
        BillingResult result = runPromise(() -> billingService.closeEncounter(encounter));
        
        // Verify: Posted to Kernel billing plugin (NOT directly to Finance!)
        assertThat(mockBillingPlugin.getPostedRequests()).hasSize(1);
        
        BillingLedgerRequest request = mockBillingPlugin.getPostedRequests().get(0);
        assertThat(request.sourceProduct()).isEqualTo("PHR");
        assertThat(request.sourceEntityType()).isEqualTo("ENCOUNTER");
        assertThat(request.amount()).isEqualTo(encounter.totalCharge());
        
        // PHR has NO knowledge of Finance!
        assertThat(request.sourceProduct()).isNotEqualTo("FINANCE");
    }
}
```

**Finance Tests** - Test Finance in isolation:
```java
@DisplayName("Finance Ledger Tests")
class LedgerServiceTest extends EventloopTestBase {
    
    private LedgerService ledgerService;
    private MockEventBus mockEventBus;
    
    @BeforeEach
    void setup() {
        mockEventBus = new MockEventBus();
        ledgerService = new LedgerService(mockEventBus, ledgerRepository);
    }
    
    @Test
    void testLedgerPostingFromAnyProduct() {
        // Simulate PHR posting via Kernel event
        KernelEvent phrEvent = KernelEvent.builder()
            .type("billing.ledger.posted")
            .correlationId("corr-123")
            .payload(Map.of(
                "sourceProduct", "PHR",  // Finance sees generic product name
                "amount", 150.00
            ))
            .build();
        
        mockEventBus.publish(phrEvent);
        
        // Finance processes without PHR dependency
        verify(metricsService).recordHealthcareRevenue(new BigDecimal("150.00"));
    }
}
```

**Integration Tests** - Test only at Kernel level:
```java
@DisplayName("Kernel Billing Plugin Integration Tests")
class BillingLedgerPluginIntegrationTest extends EventloopTestBase {
    
    private KernelContext kernelContext;
    private BillingLedgerPlugin billingPlugin;
    private MockLedgerAdapter mockLedgerAdapter;
    
    @BeforeEach
    void setup() {
        KernelRegistry registry = KernelRegistry.create();
        
        // Register billing plugin
        BillingLedgerPluginImpl plugin = new BillingLedgerPluginImpl(
            eventBus, auditService, mockLedgerAdapter
        );
        registry.register(createDescriptor("billing-plugin"), plugin);
        
        kernelContext = DefaultKernelContext.create(registry);
        kernelContext.initialize();
        
        billingPlugin = plugin;
    }
    
    @Test
    void testPHRPostsToFinanceViaKernel() {
        // PHR posts via Kernel plugin
        BillingLedgerRequest request = BillingLedgerRequest.builder()
            .correlationId("corr-phr-001")
            .sourceProduct("PHR")  // Generic identifier
            .sourceEntityId("ENC-001")
            .amount(new BigDecimal("150.00"))
            .debitAccount("AR-PATIENT-123")
            .creditAccount("REVENUE-CLINICAL")
            .build();
        
        LedgerEntryResult result = runPromise(() -> billingPlugin.postToLedger(request));
        
        // Verify: Posted to ledger adapter (which could be Finance or any GL)
        assertThat(mockLedgerAdapter.getPostedEntries()).hasSize(1);
        
        LedgerEntry entry = mockLedgerAdapter.getPostedEntries().get(0);
        assertThat(entry.sourceProduct()).isEqualTo("PHR"); // Preserved but opaque
        assertThat(entry.amount()).isEqualTo(new BigDecimal("150.00"));
    }
}
```

## Summary of Architectural Corrections

| Anti-Pattern | Correct Approach |
|--------------|-----------------|
| PHR directly calls Finance | PHR → Kernel Billing Plugin ← Finance |
| PHR imports Finance classes | Both import Kernel platform contracts |
| Shared PHR-Finance integration tests | Kernel-level plugin integration tests |
| Product-aware logic | Product-agnostic plugin with metadata |

## File Structure

```
platform/java/kernel/src/main/java/com/ghatana/kernel/plugin/billing/
├── BillingLedgerPlugin.java          # Plugin contract
├── BillingLedgerRequest.java           # Request DTO
├── LedgerEntryResult.java              # Result DTO
└── internal/
    └── BillingLedgerPluginImpl.java    # Plugin implementation

products/phr/src/main/java/com/ghatana/phr/kernel/service/
└── BillingService.java                  # Uses Kernel billing plugin

products/finance/domains/accounting/src/main/java/com/ghatana/finance/accounting/
└── LedgerService.java                   # Implements LedgerAdapter for Kernel
```

## Benefits

1. **True Decoupling**: PHR and Finance can evolve independently
2. **Testability**: Each product tests in isolation
3. **Extensibility**: New products can use same billing plugin
4. **Reusability**: Billing plugin can be used by other products (Inventory, HR, etc.)
5. **Maintainability**: Changes in one product don't affect the other

---

*Document Version: 1.0*  
*Last Updated: April 4, 2026*
