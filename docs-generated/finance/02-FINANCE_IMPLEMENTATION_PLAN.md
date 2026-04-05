# Finance Product - Detailed Implementation Plan

## Overview

**Product**: Finance (`products/finance`)  
**Current Status**: Kernel Integration Complete - ~35% test coverage  
**Target**: 100% test coverage, production-grade, SOX compliant  
**Timeline**: Weeks 3-4 of comprehensive implementation plan  

---

## Current State Analysis

### Source Files Inventory (36 files in main src/)

#### AI Governance Components (24 files)
```
ai/
├── Alert.java
├── AlertService.java
├── ApprovalWorkflowService.java
├── Episode.java
├── FinanceAIEvaluationImpl.java
├── FinanceAIModule.java
├── FinanceAgentLogicProvider.java
├── FinanceAgentOrchestratorImpl.java
├── FinanceAutonomyManagerImpl.java
├── FinanceModelGovernanceImpl.java
├── FinanceModelMetadata.java
├── FraudDetectionAgent.java
├── FraudDetectionResult.java
├── ModelApprovalRecord.java
├── ModelApprovalRepository.java
├── ModelNotApprovedException.java
├── ModelPerformanceRecord.java
├── ModelPerformanceRepository.java
├── ModelRecord.java
├── ModelRepository.java
├── Patterns.java
├── PortfolioUpdate.java
├── RiskAssessmentAgent.java
├── RiskAssessmentResult.java
├── RiskEpisode.java
├── RiskPatterns.java
├── RiskUpdate.java
├── TradeEvent.java
└── agents/
    ├── FraudDetectionAgent.java
    └── FraudDetectionResult.java
```

#### Service Layer (3 files)
```
service/
├── Transaction.java
├── TransactionResult.java
└── TransactionService.java
```

#### Contract Validation (3 files)
```
contracts/
├── ContractValidationRunner.java
├── FinanceContracts.java
└── GenericDomainContract.java
```

#### Extensions (2 files)
```
extension/
├── ComplianceKernelExtension.java
└── RiskManagementKernelExtension.java
```

#### Kernel Module (1 file)
```
kernel/
└── FinanceKernelModule.java
```

### Domain Modules (14 domains)

| Domain | Files | Priority | Complexity |
|--------|-------|----------|------------|
| OMS | ~15 | Critical | High |
| EMS | ~20 | Critical | High |
| PMS | ~8 | Critical | High |
| Risk | ~15 | Critical | High |
| Compliance | ~15 | Critical | High |
| Market Data | ~10 | Medium | Medium |
| Post-Trade | ~10 | Medium | Medium |
| Pricing | ~8 | Medium | Medium |
| Reconciliation | ~10 | Medium | Medium |
| Reference Data | ~15 | Medium | Medium |
| Regulatory Reporting | ~8 | Medium | High |
| Sanctions | ~15 | High | Medium |
| Surveillance | ~10 | Medium | Medium |
| Corporate Actions | ~8 | Low | Low |

### Test Files Inventory (13 files - Gap Analysis)

#### Existing Tests
```
src/test/java/com/ghatana/finance/
├── ai/
│   ├── ApprovalWorkflowServiceTest.java
│   ├── FinanceAIEvaluationImplTest.java
│   ├── FinanceAIGovernanceTest.java
│   ├── FinanceModelGovernanceImplTest.java
│   └── RiskAssessmentAgentTest.java
├── contracts/
│   └── ContractValidationTest.java
├── extension/
│   ├── ComplianceKernelExtensionTest.java
│   ├── DualCalendarKernelExtensionTest.java
│   └── RiskManagementKernelExtensionTest.java
├── kernel/
│   └── FinanceKernelModuleTest.java
├── kernel/service/
│   └── BillingLedgerAdapterResilienceTest.java
├── service/
│   └── TransactionServiceTest.java
└── products/finance/
    └── FinanceProductModuleIntegrationTest.java
```

### Coverage Gaps

1. **AI Governance**: 5/24 files tested (~21%)
2. **Service Layer**: 1/3 files tested (~33%)
3. **Contracts**: 1/3 files tested (~33%)
4. **Domains**: 0/14 domains have comprehensive tests (~0%)

---

## Implementation Tasks by Domain

### Week 3, Days 1-2: OMS (Order Management System)

**Domain**: `products/finance/domains/oms`

#### Test Suite: Order Validation
**File**: `domains/oms/src/test/java/com/ghatana/finance/oms/OrderValidationTest.java`

```java
/**
 * @doc.type test
 * @doc.purpose Validates order validation logic for all order types
 * @doc.layer product
 * @doc.domain OMS
 */
@DisplayName("OMS Order Validation Tests")
class OrderValidationTest extends EventloopTestBase {
    
    private OrderValidator validator;
    
    @BeforeEach
    void setup() {
        validator = new OrderValidator();
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage line, branch
     */
    @Test
    void testValidLimitOrder() {
        LimitOrder order = LimitOrder.builder()
            .symbol("AAPL")
            .side(Side.BUY)
            .quantity(100)
            .price(150.00)
            .timeInForce(TimeInForce.DAY)
            .build();
        
        ValidationResult result = validator.validate(order);
        
        assertThat(result.isValid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }
    
    /**
     * @doc.test_type negative
     * @doc.coverage branch
     */
    @Test
    void testInvalidLimitOrderNegativePrice() {
        LimitOrder order = LimitOrder.builder()
            .symbol("AAPL")
            .side(Side.BUY)
            .quantity(100)
            .price(-150.00) // Invalid
            .timeInForce(TimeInForce.DAY)
            .build();
        
        ValidationResult result = validator.validate(order);
        
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).contains("Price must be positive");
    }
    
    /**
     * @doc.test_type negative
     * @doc.coverage branch
     */
    @Test
    void testInvalidOrderZeroQuantity() {
        MarketOrder order = MarketOrder.builder()
            .symbol("AAPL")
            .side(Side.SELL)
            .quantity(0) // Invalid
            .build();
        
        ValidationResult result = validator.validate(order);
        
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).contains("Quantity must be greater than zero");
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage branch
     */
    @Test
    void testValidStopOrder() {
        StopOrder order = StopOrder.builder()
            .symbol("AAPL")
            .side(Side.SELL)
            .quantity(50)
            .stopPrice(145.00)
            .build();
        
        ValidationResult result = validator.validate(order);
        
        assertThat(result.isValid()).isTrue();
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage branch
     */
    @Test
    void testValidStopLimitOrder() {
        StopLimitOrder order = StopLimitOrder.builder()
            .symbol("AAPL")
            .side(Side.BUY)
            .quantity(75)
            .stopPrice(155.00)
            .limitPrice(156.00)
            .build();
        
        ValidationResult result = validator.validate(order);
        
        assertThat(result.isValid()).isTrue();
    }
    
    /**
     * @doc.test_type negative
     * @doc.coverage branch
     */
    @Test
    void testStopLimitOrderInvalidPrices() {
        StopLimitOrder order = StopLimitOrder.builder()
            .symbol("AAPL")
            .side(Side.BUY)
            .quantity(75)
            .stopPrice(160.00)
            .limitPrice(155.00) // Limit below stop for buy
            .build();
        
        ValidationResult result = validator.validate(order);
        
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).contains("Limit price must be >= stop price for buy orders");
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage branch
     */
    @Test
    void testValidIcebergOrder() {
        IcebergOrder order = IcebergOrder.builder()
            .symbol("AAPL")
            .side(Side.BUY)
            .totalQuantity(10000)
            .displayQuantity(1000)
            .price(150.00)
            .build();
        
        ValidationResult result = validator.validate(order);
        
        assertThat(result.isValid()).isTrue();
    }
    
    /**
     * @doc.test_type negative
     * @doc.coverage branch
     */
    @Test
    void testIcebergOrderDisplayLargerThanTotal() {
        IcebergOrder order = IcebergOrder.builder()
            .symbol("AAPL")
            .side(Side.BUY)
            .totalQuantity(1000)
            .displayQuantity(2000) // Invalid
            .price(150.00)
            .build();
        
        ValidationResult result = validator.validate(order);
        
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).contains("Display quantity cannot exceed total quantity");
    }
    
    /**
     * @doc.test_type negative
     * @doc.coverage branch
     */
    @Test
    void testInvalidSymbol() {
        MarketOrder order = MarketOrder.builder()
            .symbol("INVALID_SYMBOL_123")
            .side(Side.BUY)
            .quantity(100)
            .build();
        
        ValidationResult result = validator.validate(order);
        
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).contains("Invalid symbol format");
    }
}
```

#### Test Suite: Order Routing
**File**: `domains/oms/src/test/java/com/ghatana/finance/oms/OrderRoutingTest.java`

```java
/**
 * @doc.type test
 * @doc.purpose Validates smart order routing logic
 * @doc.layer product
 * @doc.domain OMS
 */
@DisplayName("OMS Smart Order Routing Tests")
class OrderRoutingTest extends EventloopTestBase {
    
    private SmartOrderRouter router;
    private MarketDataService marketData;
    private ExchangeConnectorRegistry connectors;
    
    @BeforeEach
    void setup() {
        marketData = new MockMarketDataService();
        connectors = new MockExchangeConnectorRegistry();
        router = new SmartOrderRouter(marketData, connectors);
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage line, branch
     */
    @Test
    void testRouteToBestPrice() {
        // Setup: Exchange A has better price than Exchange B
        marketData.setQuote("AAPL", "EXCHANGE_A", 150.00, 150.05);
        marketData.setQuote("AAPL", "EXCHANGE_B", 150.10, 150.15);
        
        MarketOrder order = MarketOrder.builder()
            .symbol("AAPL")
            .side(Side.BUY)
            .quantity(100)
            .build();
        
        RoutingDecision decision = runPromise(() -> router.route(order));
        
        assertThat(decision.selectedExchange()).isEqualTo("EXCHANGE_A");
        assertThat(decision.expectedPrice()).isEqualTo(150.05);
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage branch
     */
    @Test
    void testRouteWithLiquidityConstraints() {
        // Exchange A has better price but insufficient liquidity
        marketData.setQuoteWithDepth("AAPL", "EXCHANGE_A", 150.00, 150.05, 50); // Only 50 shares
        marketData.setQuoteWithDepth("AAPL", "EXCHANGE_B", 150.10, 150.15, 500);
        
        MarketOrder order = MarketOrder.builder()
            .symbol("AAPL")
            .side(Side.BUY)
            .quantity(200) // More than EXCHANGE_A liquidity
            .build();
        
        RoutingDecision decision = runPromise(() -> router.route(order));
        
        // Should route to B for full fill, or split
        assertThat(decision.routingStrategy()).isIn(RoutingStrategy.SINGLE_DESTINATION, RoutingStrategy.SPLIT);
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage branch
     */
    @Test
    void testRouteWithLatencyConstraints() {
        // Low latency requirement should prefer closer exchange
        marketData.setLatency("EXCHANGE_A", 50);  // 50ms
        marketData.setLatency("EXCHANGE_B", 10); // 10ms
        
        MarketOrder order = MarketOrder.builder()
            .symbol("AAPL")
            .side(Side.BUY)
            .quantity(100)
            .executionPriority(ExecutionPriority.LOW_LATENCY)
            .build();
        
        RoutingDecision decision = runPromise(() -> router.route(order));
        
        assertThat(decision.selectedExchange()).isEqualTo("EXCHANGE_B");
    }
    
    /**
     * @doc.test_type negative
     * @doc.coverage branch
     */
    @Test
    void testRouteWhenNoLiquidity() {
        marketData.setNoLiquidity("AAPL", "EXCHANGE_A");
        marketData.setNoLiquidity("AAPL", "EXCHANGE_B");
        
        MarketOrder order = MarketOrder.builder()
            .symbol("AAPL")
            .side(Side.BUY)
            .quantity(100)
            .build();
        
        assertThatThrownBy(() -> runPromise(() -> router.route(order)))
            .isInstanceOf(RoutingException.class)
            .hasMessageContaining("No liquidity available");
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage path
     */
    @Test
    void testInternalCrossingBeforeExternal() {
        // Internal matching engine has matching sell order
        setupInternalMatching("AAPL", Side.SELL, 100, 150.00);
        
        MarketOrder order = MarketOrder.builder()
            .symbol("AAPL")
            .side(Side.BUY)
            .quantity(100)
            .allowInternalCrossing(true)
            .build();
        
        RoutingDecision decision = runPromise(() -> router.route(order));
        
        assertThat(decision.routingStrategy()).isEqualTo(RoutingStrategy.INTERNAL_CROSS);
        assertThat(decision.internalMatch()).isPresent();
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage branch
     */
    @Test
    void testRouteToDarkPool() {
        // Large order should consider dark pool
        marketData.setDarkPoolAvailable("AAPL", "DARK_POOL_1");
        
        MarketOrder order = MarketOrder.builder()
            .symbol("AAPL")
            .side(Side.BUY)
            .quantity(10000) // Large order
            .minimizeMarketImpact(true)
            .build();
        
        RoutingDecision decision = runPromise(() -> router.route(order));
        
        assertThat(decision.consideredDarkPools()).isNotEmpty();
    }
}
```

#### Test Suite: Order Lifecycle
**File**: `domains/oms/src/test/java/com/ghatana/finance/oms/OrderLifecycleTest.java`

```java
/**
 * @doc.type test
 * @doc.purpose Validates complete order lifecycle management
 * @doc.layer product
 * @doc.domain OMS
 */
@DisplayName("OMS Order Lifecycle Tests")
class OrderLifecycleTest extends EventloopTestBase {
    
    private OrderManager orderManager;
    private OrderRepository repository;
    private EventBus eventBus;
    
    @BeforeEach
    void setup() {
        repository = new InMemoryOrderRepository();
        eventBus = new EventBus();
        orderManager = new OrderManager(repository, eventBus);
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage line
     */
    @Test
    void testNewOrderSubmission() {
        LimitOrder order = createSampleLimitOrder();
        
        OrderSubmissionResult result = runPromise(() -> orderManager.submit(order));
        
        assertThat(result.status()).isEqualTo(SubmissionStatus.ACCEPTED);
        assertThat(result.orderId()).isNotNull();
        
        // Verify stored
        Order stored = runPromise(() -> repository.findById(result.orderId()));
        assertThat(stored).isNotNull();
        assertThat(stored.status()).isEqualTo(OrderStatus.NEW);
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage line, branch
     */
    @Test
    void testOrderAcknowledgment() {
        // Submit order
        LimitOrder order = createSampleLimitOrder();
        OrderSubmissionResult submission = runPromise(() -> orderManager.submit(order));
        
        // Receive acknowledgment from exchange
        ExchangeAcknowledgment ack = ExchangeAcknowledgment.builder()
            .exchangeOrderId("EXCH-12345")
            .timestamp(Instant.now())
            .status(AcknowledgmentStatus.ACCEPTED)
            .build();
        
        runPromise(() -> orderManager.handleAcknowledgment(submission.orderId(), ack)).assertComplete();
        
        // Verify status updated
        Order updated = runPromise(() -> repository.findById(submission.orderId()));
        assertThat(updated.status()).isEqualTo(OrderStatus.OPEN);
        assertThat(updated.exchangeOrderId()).isEqualTo("EXCH-12345");
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage branch
     */
    @Test
    void testPartialFillHandling() {
        String orderId = submitOrder(createSampleLimitOrder());
        
        // First partial fill
        Fill fill1 = Fill.builder()
            .quantity(30)
            .price(150.00)
            .timestamp(Instant.now())
            .build();
        
        FillResult result1 = runPromise(() -> orderManager.handleFill(orderId, fill1));
        
        assertThat(result1.fillStatus()).isEqualTo(FillStatus.PARTIAL);
        assertThat(result1.remainingQuantity()).isEqualTo(70);
        
        // Second partial fill
        Fill fill2 = Fill.builder()
            .quantity(70)
            .price(150.01)
            .timestamp(Instant.now())
            .build();
        
        FillResult result2 = runPromise(() -> orderManager.handleFill(orderId, fill2));
        
        assertThat(result2.fillStatus()).isEqualTo(FillStatus.COMPLETE);
        
        // Verify final state
        Order finalOrder = runPromise(() -> repository.findById(orderId));
        assertThat(finalOrder.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(finalOrder.filledQuantity()).isEqualTo(100);
        assertThat(finalOrder.averagePrice()).isEqualTo(150.007); // Weighted average
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage line, branch
     */
    @Test
    void testOrderCancellation() {
        String orderId = submitOrder(createSampleLimitOrder());
        
        // Request cancellation
        CancellationResult cancelResult = runPromise(() -> orderManager.cancel(orderId));
        
        assertThat(cancelResult.status()).isEqualTo(CancellationStatus.PENDING);
        
        // Receive cancel acknowledgment
        runPromise(() -> orderManager.handleCancelAcknowledgment(orderId)).assertComplete();
        
        Order cancelled = runPromise(() -> repository.findById(orderId));
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage branch
     */
    @Test
    void testOrderModification() {
        String orderId = submitOrder(createSampleLimitOrder());
        
        // Modify quantity
        ModificationRequest mod = ModificationRequest.builder()
            .newQuantity(150)
            .build();
        
        ModificationResult result = runPromise(() -> orderManager.modify(orderId, mod));
        
        assertThat(result.status()).isEqualTo(ModificationStatus.ACCEPTED);
        
        Order modified = runPromise(() -> repository.findById(orderId));
        assertThat(modified.quantity()).isEqualTo(150);
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage branch
     */
    @Test
    void testOrderExpiration() {
        // Submit day order late in day
        LimitOrder order = LimitOrder.builder()
            .symbol("AAPL")
            .side(Side.BUY)
            .quantity(100)
            .price(150.00)
            .timeInForce(TimeInForce.DAY)
            .build();
        
        String orderId = submitOrder(order);
        
        // Simulate market close
        runPromise(() -> orderManager.handleMarketClose()).assertComplete();
        
        Order expired = runPromise(() -> repository.findById(orderId));
        assertThat(expired.status()).isEqualTo(OrderStatus.EXPIRED);
    }
    
    /**
     * @doc.test_type negative
     * @doc.coverage branch
     */
    @Test
    void testCancelAlreadyFilledOrder() {
        String orderId = submitAndFillOrder(createSampleMarketOrder());
        
        assertThatThrownBy(() -> runPromise(() -> orderManager.cancel(orderId)))
            .isInstanceOf(OrderStateException.class)
            .hasMessageContaining("Cannot cancel filled order");
    }
    
    /**
     * @doc.test_type negative
     * @doc.coverage branch
     */
    @Test
    void testModifyRejectedOrder() {
        String orderId = submitOrder(createSampleLimitOrder());
        
        // Exchange rejects order
        runPromise(() -> orderManager.handleRejection(orderId, "Insufficient credit")).assertComplete();
        
        ModificationRequest mod = ModificationRequest.builder()
            .newQuantity(200)
            .build();
        
        assertThatThrownBy(() -> runPromise(() -> orderManager.modify(orderId, mod)))
            .isInstanceOf(OrderStateException.class)
            .hasMessageContaining("Cannot modify rejected order");
    }
    
    private String submitOrder(Order order) {
        return runPromise(() -> orderManager.submit(order)).orderId();
    }
    
    private String submitAndFillOrder(Order order) {
        String orderId = submitOrder(order);
        Fill fill = Fill.builder()
            .quantity(order.quantity())
            .price(150.00)
            .build();
        runPromise(() -> orderManager.handleFill(orderId, fill)).assertComplete();
        return orderId;
    }
    
    private LimitOrder createSampleLimitOrder() {
        return LimitOrder.builder()
            .symbol("AAPL")
            .side(Side.BUY)
            .quantity(100)
            .price(150.00)
            .timeInForce(TimeInForce.DAY)
            .build();
    }
    
    private MarketOrder createSampleMarketOrder() {
        return MarketOrder.builder()
            .symbol("AAPL")
            .side(Side.BUY)
            .quantity(100)
            .build();
    }
}
```

#### Test Suite: OMS Kernel Integration
**File**: `domains/oms/src/test/java/com/ghatana/finance/oms/OMSKernelIntegrationTest.java`

```java
/**
 * @doc.type test
 * @doc.purpose Validates OMS integration with kernel platform
 * @doc.layer product
 * @doc.domain OMS
 */
@DisplayName("OMS Kernel Integration Tests")
class OMSKernelIntegrationTest extends EventloopTestBase {
    
    private KernelContext kernelContext;
    private OMSModule omsModule;
    
    @BeforeEach
    void setup() {
        omsModule = new OMSModule();
        KernelRegistry registry = KernelRegistry.create();
        KernelModuleDescriptor descriptor = KernelModuleDescriptor.builder()
            .id("finance-oms")
            .name("Finance OMS")
            .version("1.0.0")
            .capabilities(Set.of("order-management", "order-routing"))
            .build();
        
        runPromise(() -> registry.register(descriptor, omsModule));
        kernelContext = DefaultKernelContext.create(registry);
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage line
     */
    @Test
    void testOMSModuleRegistration() {
        runPromise(() -> kernelContext.initialize()).assertComplete();
        
        assertThat(omsModule.isInitialized()).isTrue();
        assertThat(kernelContext.getModule("finance-oms")).isPresent();
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage line, branch
     */
    @Test
    void testOMSWithKernelEventBus() {
        runPromise(() -> kernelContext.initialize()).assertComplete();
        
        // Subscribe to order events
        List<KernelEvent> receivedEvents = new ArrayList<>();
        kernelContext.getEventBus().subscribe("order.submitted", receivedEvents::add);
        
        // Submit order
        OrderSubmissionRequest request = createSampleSubmissionRequest();
        runPromise(() -> omsModule.submitOrder(request)).assertComplete();
        
        // Verify event published
        await().atMost(Duration.ofSeconds(2)).until(() -> !receivedEvents.isEmpty());
        
        assertThat(receivedEvents).hasSize(1);
        assertThat(receivedEvents.get(0).type()).isEqualTo("order.submitted");
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage line
     */
    @Test
    void testOMSCapabilityRegistration() {
        runPromise(() -> kernelContext.initialize()).assertComplete();
        
        Set<KernelCapability> capabilities = kernelContext.findCapabilities("order-management");
        
        assertThat(capabilities).anyMatch(cap -> 
            cap.moduleId().equals("finance-oms")
        );
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage branch
     */
    @Test
    void testOMSHealthCheck() {
        runPromise(() -> kernelContext.initialize()).assertComplete();
        
        KernelHealth health = runPromise(() -> kernelContext.checkHealth());
        
        assertThat(health.status()).isEqualTo(HealthStatus.HEALTHY);
        assertThat(health.moduleHealth()).anyMatch(mh -> 
            mh.moduleId().equals("finance-oms") && mh.status() == ModuleHealthStatus.HEALTHY
        );
    }
}
```

---

### Week 3, Days 3-4: EMS (Execution Management System)

**Domain**: `products/finance/domains/ems`

#### Test Suite: Execution Algorithms
**File**: `domains/ems/src/test/java/com/ghatana/finance/ems/ExecutionAlgorithmTest.java`

```java
/**
 * @doc.type test
 * @doc.purpose Validates execution algorithm implementations
 * @doc.layer product
 * @doc.domain EMS
 */
@DisplayName("EMS Execution Algorithm Tests")
class ExecutionAlgorithmTest extends EventloopTestBase {
    
    private MarketDataService marketData;
    private OrderSimulator simulator;
    
    @BeforeEach
    void setup() {
        marketData = new MockMarketDataService();
        simulator = new OrderSimulator();
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage line, branch
     */
    @Test
    void testTWAPExecution() {
        // Setup: Trade 1000 shares over 1 hour in 5-minute intervals
        setupMarketData("AAPL", 150.00, 0.001); // Low volatility
        
        TWAPAlgorithm twap = new TWAPAlgorithm(marketData, simulator);
        
        AlgorithmRequest request = AlgorithmRequest.builder()
            .symbol("AAPL")
            .side(Side.BUY)
            .totalQuantity(1000)
            .duration(Duration.ofHours(1))
            .interval(Duration.ofMinutes(5))
            .build();
        
        AlgorithmResult result = runPromise(() -> twap.execute(request));
        
        assertThat(result.status()).isEqualTo(AlgorithmStatus.COMPLETE);
        assertThat(result.filledQuantity()).isEqualTo(1000);
        
        // Should have 12 slices (60 min / 5 min)
        assertThat(result.slices()).hasSize(12);
        
        // Each slice should be ~83 shares (1000/12)
        assertThat(result.slices())
            .allMatch(slice -> Math.abs(slice.quantity() - 83) <= 1);
        
        // VWAP price should be close to average market price
        assertThat(result.vwapPrice()).isCloseTo(150.00, within(0.50));
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage line, branch
     */
    @Test
    void testVWAPExecution() {
        // Setup historical volume profile
        setupVolumeProfile("AAPL", Map.of(
            LocalTime.of(9, 30), 0.15,  // 15% at open
            LocalTime.of(10, 0), 0.10,
            LocalTime.of(11, 0), 0.08,
            LocalTime.of(14, 0), 0.12,
            LocalTime.of(15, 0), 0.15,
            LocalTime.of(16, 0), 0.40   // 40% at close
        ));
        
        VWAPAlgorithm vwap = new VWAPAlgorithm(marketData, simulator);
        
        AlgorithmRequest request = AlgorithmRequest.builder()
            .symbol("AAPL")
            .side(Side.SELL)
            .totalQuantity(10000)
            .startTime(LocalTime.of(9, 30))
            .endTime(LocalTime.of(16, 0))
            .build();
        
        AlgorithmResult result = runPromise(() -> vwap.execute(request));
        
        assertThat(result.status()).isEqualTo(AlgorithmStatus.COMPLETE);
        
        // Slices should follow volume profile
        List<Slice> slices = result.slices();
        
        // Find close slice (should be largest)
        Slice closeSlice = slices.stream()
            .max(Comparator.comparing(Slice::quantity))
            .orElseThrow();
        
        assertThat(closeSlice.scheduledTime().getHour()).isEqualTo(15);
        
        // VWAP should track market VWAP closely
        assertThat(result.vwapVsMarketVwapDeviation()).isLessThan(0.001); // 0.1% deviation
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage line, branch
     */
    @Test
    void testImplementationShortfall() {
        ImplementationShortfallAlgorithm is = 
            new ImplementationShortfallAlgorithm(marketData, simulator);
        
        AlgorithmRequest request = AlgorithmRequest.builder()
            .symbol("AAPL")
            .side(Side.BUY)
            .totalQuantity(5000)
            .urgency(Urgency.HIGH)
            .riskAversion(0.5)
            .build();
        
        AlgorithmResult result = runPromise(() -> is.execute(request));
        
        assertThat(result.status()).isEqualTo(AlgorithmStatus.COMPLETE);
        
        // Implementation shortfall should be calculated
        assertThat(result.implementationShortfall()).isNotNull();
        assertThat(result.implementationShortfall().bps()).isLessThan(50); // < 50 bps
        
        // Should have optimal trade schedule
        assertThat(result.optimalTradeSchedule()).isNotNull();
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage line, branch
     */
    @Test
    void testArrivalPriceAlgorithm() {
        ArrivalPriceAlgorithm arrivalPrice = 
            new ArrivalPriceAlgorithm(marketData, simulator);
        
        double arrivalPriceValue = marketData.getMidPrice("AAPL");
        
        AlgorithmRequest request = AlgorithmRequest.builder()
            .symbol("AAPL")
            .side(Side.BUY)
            .totalQuantity(3000)
            .arrivalPrice(arrivalPriceValue)
            .targetPercent(0.05) // Within 5% of arrival
            .build();
        
        AlgorithmResult result = runPromise(() -> arrivalPrice.execute(request));
        
        assertThat(result.status()).isEqualTo(AlgorithmStatus.COMPLETE);
        
        // Average price should be within target of arrival price
        double priceDeviation = Math.abs(result.avgPrice() - arrivalPriceValue) / arrivalPriceValue;
        assertThat(priceDeviation).isLessThan(0.05);
    }
    
    /**
     * @doc.test_type negative
     * @doc.coverage branch
     */
    @Test
    void testAlgorithmWithInsufficientLiquidity() {
        setupLowLiquidity("AAPL", 100); // Only 100 shares available
        
        TWAPAlgorithm twap = new TWAPAlgorithm(marketData, simulator);
        
        AlgorithmRequest request = AlgorithmRequest.builder()
            .symbol("AAPL")
            .side(Side.BUY)
            .totalQuantity(10000) // Way more than available
            .build();
        
        AlgorithmResult result = runPromise(() -> twap.execute(request));
        
        assertThat(result.status()).isEqualTo(AlgorithmStatus.INCOMPLETE);
        assertThat(result.completionPercentage()).isLessThan(100.0);
        assertThat(result.unfilledReason()).contains("insufficient liquidity");
    }
}
```

#### Test Suite: Execution Performance
**File**: `domains/ems/src/test/java/com/ghatana/finance/ems/ExecutionPerformanceTest.java`

```java
/**
 * @doc.type test
 * @doc.purpose Validates execution performance metrics and calculations
 * @doc.layer product
 * @doc.domain EMS
 */
@DisplayName("EMS Execution Performance Tests")
class ExecutionPerformanceTest extends EventloopTestBase {
    
    private PerformanceAnalyzer analyzer;
    
    @BeforeEach
    void setup() {
        analyzer = new PerformanceAnalyzer();
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage line, branch
     */
    @Test
    void testLatencyMetricsCalculation() {
        List<ExecutionEvent> events = List.of(
            ExecutionEvent.builder()
                .type(EventType.ORDER_SUBMITTED)
                .timestamp(Instant.parse("2026-04-01T10:00:00.000Z"))
                .build(),
            ExecutionEvent.builder()
                .type(EventType.ORDER_ACKNOWLEDGED)
                .timestamp(Instant.parse("2026-04-01T10:00:00.050Z")) // 50ms
                .build(),
            ExecutionEvent.builder()
                .type(EventType.FILL_RECEIVED)
                .timestamp(Instant.parse("2026-04-01T10:00:00.150Z")) // 100ms after ack
                .build()
        );
        
        LatencyMetrics metrics = analyzer.calculateLatency(events);
        
        assertThat(metrics.orderSubmissionLatencyMs()).isEqualTo(50);
        assertThat(metrics.fillLatencyMs()).isEqualTo(100);
        assertThat(metrics.totalRoundTripMs()).isEqualTo(150);
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage line, branch
     */
    @Test
    void testSlippageCalculation() {
        // Arrival price: $150.00
        // Expected fill price: $150.05 (mid at submission)
        // Actual fill price: $150.12
        
        SlippageResult slippage = analyzer.calculateSlippage(
            150.00,   // arrival price
            150.05,   // expected price
            150.12,   // actual fill price
            Side.BUY,
            1000      // quantity
        );
        
        // Slippage = (actual - expected) / expected * 10000 (bps)
        // = (150.12 - 150.05) / 150.05 * 10000 = 4.67 bps
        assertThat(slippage.totalSlippageBps()).isCloseTo(4.67, within(0.01));
        assertThat(slippage.marketImpactBps()).isGreaterThan(0);
        assertThat(slippage.delayCostBps()).isGreaterThanOrEqualTo(0);
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage line, branch
     */
    @Test
    void testMarketImpactAnalysis() {
        List<Fill> fills = List.of(
            Fill.builder().quantity(100).price(150.00).timestamp(t(0)).build(),
            Fill.builder().quantity(100).price(150.05).timestamp(t(1)).build(),
            Fill.builder().quantity(100).price(150.12).timestamp(t(2)).build(),
            Fill.builder().quantity(100).price(150.18).timestamp(t(3)).build()
        );
        
        MarketImpactAnalysis impact = analyzer.analyzeMarketImpact(fills, 150.00);
        
        assertThat(impact.temporaryImpactBps()).isGreaterThan(0);
        assertThat(impact.permanentImpactBps()).isGreaterThan(0);
        assertThat(impact.impactCurve()).hasSize(4);
        assertThat(impact.totalCost()).isGreaterThan(0);
    }
}
```

---

### Week 3, Days 5-7: PMS, Risk, Compliance

Due to document length, I'll summarize the remaining domains with test file structure:

#### PMS (Portfolio Management System)
- `PortfolioValuationTest.java` - Real-time P&L, position valuation, exposure
- `PortfolioRiskTest.java` - VaR, expected shortfall, stress testing
- `RebalancingTest.java` - Drift-based rebalancing, cash equitization
- `PMSComplianceTest.java` - Investment guidelines, concentration limits

#### Risk Domain
- `MarketRiskTest.java` - Position risk, Greeks, sensitivity
- `CreditRiskTest.java` - Counterparty exposure, collateral, margin
- `OperationalRiskTest.java` - Loss events, risk indicators, control testing
- `RiskAggregationTest.java` - Firm-wide aggregation, reporting

#### Compliance Domain
- `TradeSurveillanceTest.java` - Wash trade, spoofing, layering detection
- `RegulatoryReportingTest.java` - MiFID II, EMIR, SFTR, CAT
- `SanctionsScreeningTest.java` - Real-time, batch, false positive management
- `ComplianceRuleEngineTest.java` - Rule evaluation, alerts, case management

---

### Week 4: Supporting Domains & Integration

#### AI Governance Expansion (Additional Tests)

**File**: `src/test/java/com/ghatana/finance/ai/ModelPerformanceTrackingTest.java`

```java
/**
 * @doc.type test
 * @doc.purpose Validates model performance tracking and alerting
 * @doc.layer product
 * @doc.domain AI Governance
 */
@DisplayName("Model Performance Tracking Tests")
class ModelPerformanceTrackingTest extends EventloopTestBase {
    
    private ModelPerformanceRepository repository;
    private AlertService alertService;
    private FinanceModelGovernanceImpl governance;
    
    @BeforeEach
    void setup() {
        repository = new InMemoryModelPerformanceRepository();
        alertService = mock(AlertService.class);
        governance = new FinanceModelGovernanceImpl(repository, alertService);
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage line, branch
     */
    @Test
    void testPerformanceMetricRecording() {
        String modelId = "fraud-detection-v1";
        
        ModelPerformanceRecord record = ModelPerformanceRecord.builder()
            .modelId(modelId)
            .timestamp(Instant.now())
            .accuracy(0.96)
            .precision(0.94)
            .recall(0.92)
            .f1Score(0.93)
            .latencyMs(45)
            .throughputPerSecond(1000)
            .build();
        
        runPromise(() -> governance.recordPerformance(record)).assertComplete();
        
        List<ModelPerformanceRecord> history = runPromise(() -> 
            repository.findByModelId(modelId, Duration.ofDays(1))
        );
        
        assertThat(history).contains(record);
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage line, branch
     */
    @Test
    void testPerformanceDegradationDetection() {
        String modelId = "fraud-detection-v1";
        
        // Historical baseline: 95% accuracy
        setupBaselinePerformance(modelId, 0.95);
        
        // Recent performance degraded to 91%
        List<ModelPerformanceRecord> recentRecords = IntStream.range(0, 10)
            .mapToObj(i -> ModelPerformanceRecord.builder()
                .modelId(modelId)
                .timestamp(Instant.now().minus(i, ChronoUnit.MINUTES))
                .accuracy(0.91) // Below 95% threshold
                .build())
            .toList();
        
        recentRecords.forEach(r -> 
            runPromise(() -> repository.save(r)).assertComplete()
        );
        
        DegradationResult degradation = runPromise(() -> 
            governance.checkForDegradation(modelId)
        );
        
        assertThat(degradation.isDegraded()).isTrue();
        assertThat(degradation.degradationPercentage()).isCloseTo(4.2, within(0.1));
        assertThat(degradation.baselineAccuracy()).isEqualTo(0.95);
        assertThat(degradation.currentAccuracy()).isEqualTo(0.91);
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage branch
     */
    @Test
    void testAlertGenerationOnDegradation() {
        String modelId = "fraud-detection-v1";
        
        setupBaselinePerformance(modelId, 0.95);
        
        // Trigger degradation
        ModelPerformanceRecord degraded = ModelPerformanceRecord.builder()
            .modelId(modelId)
            .timestamp(Instant.now())
            .accuracy(0.90) // 5% degradation, above 5% threshold
            .build();
        
        runPromise(() -> governance.recordPerformance(degraded)).assertComplete();
        
        // Verify alert generated
        verify(alertService).sendAlert(argThat(alert -> 
            alert.severity() == AlertSeverity.HIGH &&
            alert.message().contains("Performance degradation detected") &&
            alert.modelId().equals(modelId)
        ));
    }
    
    /**
     * @doc.test_type positive
     * @doc.coverage branch
     */
    @Test
    void testLatencySLAMonitoring() {
        String modelId = "fraud-detection-v1";
        
        // SLA: 95th percentile < 100ms
        List<ModelPerformanceRecord> records = IntStream.range(0, 100)
            .mapToObj(i -> ModelPerformanceRecord.builder()
                .modelId(modelId)
                .timestamp(Instant.now().minus(i, ChronoUnit.SECONDS))
                .latencyMs(i < 5 ? 150 : 50) // 5% above SLA
                .build())
            .toList();
        
        records.forEach(r -> runPromise(() -> repository.save(r)).assertComplete());
        
        SLAViolationResult sla = runPromise(() -> governance.checkLatencySLA(modelId));
        
        assertThat(sla.isCompliant()).isFalse();
        assertThat(sla.p95LatencyMs()).isGreaterThan(100);
        assertThat(sla.violationPercentage()).isGreaterThan(0);
    }
}
```

---

## Test Coverage Targets

### Finance Product Coverage Matrix

| Domain | Current Tests | Target Tests | Coverage Target |
|--------|--------------|--------------|-----------------|
| AI Governance | 5 | 10 | 100% |
| Service Layer | 1 | 5 | 100% |
| Contracts | 1 | 5 | 100% |
| OMS | 0 | 15 | 100% |
| EMS | 0 | 15 | 100% |
| PMS | 0 | 15 | 100% |
| Risk | 0 | 12 | 100% |
| Compliance | 0 | 12 | 100% |
| Market Data | 0 | 10 | 100% |
| Post-Trade | 0 | 10 | 100% |
| Pricing | 0 | 10 | 100% |
| Reconciliation | 0 | 10 | 100% |
| Reference Data | 0 | 10 | 100% |
| Regulatory Reporting | 0 | 10 | 100% |
| Sanctions | 0 | 12 | 100% |
| Surveillance | 0 | 10 | 100% |
| Corporate Actions | 0 | 8 | 100% |
| **Total** | **13** | **179** | **100%** |

---

## SOX Compliance Test Requirements

### Model Governance SOX Tests

```java
/**
 * @doc.type test
 * @doc.purpose Validates SOX compliance for AI model governance
 * @doc.layer product
 * @doc.domain AI Governance
 * @doc.compliance SOX
 */
@DisplayName("SOX Compliance - Model Governance Tests")
class SOXModelGovernanceTest extends EventloopTestBase {
    
    /**
     * @doc.test_type compliance
     * @doc.coverage line, branch
     */
    @Test
    void testModelApprovalRequiredForProduction() {
        // Unapproved model cannot be used in production
        Model unapprovedModel = createUnapprovedModel("unapproved-model");
        
        assertThatThrownBy(() -> 
            runPromise(() -> governance.validateProductionUse(unapprovedModel.id()))
        )
            .isInstanceOf(ModelNotApprovedException.class)
            .hasMessageContaining("SOX compliance: Model requires approval");
    }
    
    /**
     * @doc.test_type compliance
     * @doc.coverage line, branch
     */
    @Test
    void testSOXAuditTrailForModelDecisions() {
        // Every model decision must be logged with explanation
        String modelId = "approved-fraud-model";
        ModelDecision decision = ModelDecision.builder()
            .modelId(modelId)
            .input(Map.of("amount", 100000, "country", "high-risk"))
            .output(Map.of("risk_score", 0.85, "decision", "REVIEW"))
            .explanation("High amount + high-risk country")
            .timestamp(Instant.now())
            .build();
        
        runPromise(() -> governance.logModelDecision(decision)).assertComplete();
        
        // Verify audit trail entry
        List<AuditEntry> auditTrail = runPromise(() -> 
            auditService.findModelDecisions(modelId, today())
        );
        
        assertThat(auditTrail).hasSize(1);
        assertThat(auditTrail.get(0).explanation()).isNotNull();
        assertThat(auditTrail.get(0).soxCompliant()).isTrue();
    }
    
    /**
     * @doc.test_type compliance
     * @doc.coverage line, branch
     */
    @Test
    void testSevenYearRetentionPolicy() {
        // Verify 7-year retention is configured
        RetentionPolicy policy = governance.getRetentionPolicy();
        
        assertThat(policy.modelDecisionRetention()).isEqualTo(Duration.ofDays(7 * 365));
        assertThat(policy.auditTrailRetention()).isEqualTo(Duration.ofDays(7 * 365));
    }
}
```

---

## Success Criteria

1. **Test Count**: 13 → 179+ test files
2. **Line Coverage**: 100% for all domains
3. **Branch Coverage**: 100% for critical paths
4. **SOX Compliance**: All compliance tests passing
5. **Performance**: <5ms model approval, <100ms fraud detection
6. **Documentation**: All tests annotated with `@doc.*` tags

---

## Appendix: Domain Test Template

Each domain test suite should follow this structure:

```java
/**
 * @doc.type test
 * @doc.purpose {clear description}
 * @doc.layer product
 * @doc.domain {domain name}
 */
@DisplayName("{Domain} {Component} Tests")
class {Domain}{Component}Test extends EventloopTestBase {
    
    // Test cases covering:
    // - Happy path scenarios
    // - Edge cases and boundary conditions
    // - Error conditions and exceptions
    // - Integration with kernel platform
    // - Performance characteristics
}
```

---

*Document Version: 1.0*  
*Last Updated: April 4, 2026*
