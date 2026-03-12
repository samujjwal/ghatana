# LOW-LEVEL DESIGN: D-01 ORDER MANAGEMENT SYSTEM (OMS)

**Module**: D-01 Order Management System  
**Layer**: Domain  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Trading Domain Team

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

The Order Management System (OMS) provides **order lifecycle management, pre-trade validation, routing, execution tracking, and position updates** for all trading activities across Project Siddhanta.

**Core Responsibilities**:
- Order capture and validation
- Order state machine (NEW → PENDING → EXECUTED → SETTLED)
- Pre-trade risk checks via K-03 Rules Engine
- Maker-checker workflow for large orders
- Order routing to exchanges/brokers
- Execution tracking and partial fills
- Position updates via event sourcing
- Dual-calendar timestamping (BS + Gregorian)
- AI-powered order suggestions via K-09
- Extension points for jurisdiction-specific logic

**Invariants**:
1. Order state transitions MUST follow defined state machine
2. All orders MUST pass pre-trade validation
3. Position updates MUST be event-sourced
4. Large orders (> threshold) MUST require maker-checker approval
5. All order actions MUST be audited to K-07

### 1.2 Explicit Non-Goals

- ❌ Market data feed management (separate Market Data module)
- ❌ Settlement processing (separate Settlement module)
- ❌ Custody management (separate Custody module)

### 1.3 Dependencies

| Dependency | Purpose | Readiness Gate |
|------------|---------|----------------|
| K-02 Config Engine | Order limits, thresholds | K-02 stable |
| K-03 Rules Engine | Pre-trade validation | K-03 stable |
| K-05 Event Bus | Order events, position updates | K-05 stable |
| K-07 Audit Framework | Order action audit | K-07 stable |
| K-09 AI Governance | Order suggestions | K-09 stable |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 REST API Endpoints

```yaml
POST /api/v1/orders
Authorization: Bearer {user_token}
Content-Type: application/json

Request:
{
  "instrument_id": "NABIL",
  "order_type": "LIMIT",
  "side": "BUY",
  "quantity": 100,
  "price": 1250.50,
  "validity": "DAY",
  "client_order_id": "client_123",
  "account_id": "acc_456",
  "metadata": {
    "notes": "Long-term investment"
  }
}

Response 201:
{
  "order_id": "ord_7a8b9c0d",
  "status": "PENDING_VALIDATION",
  "client_order_id": "client_123",
  "created_at_bs": "2081-11-17",
  "created_at_gregorian": "2025-03-02T10:30:00Z",
  "validation_required": true
}

Response 400:
{
  "error": "PRE_TRADE_VALIDATION_FAILED",
  "message": "Insufficient buying power",
  "code": "OMS_E001",
  "validation_errors": [
    {
      "rule": "buying_power_check",
      "message": "Required: 125050.00, Available: 100000.00"
    }
  ]
}
```

```yaml
GET /api/v1/orders/{order_id}
Authorization: Bearer {user_token}

Response 200:
{
  "order_id": "ord_7a8b9c0d",
  "instrument_id": "NABIL",
  "order_type": "LIMIT",
  "side": "BUY",
  "quantity": 100,
  "price": 1250.50,
  "status": "EXECUTED",
  "filled_quantity": 100,
  "average_price": 1250.00,
  "executions": [
    {
      "execution_id": "exec_1",
      "quantity": 50,
      "price": 1249.50,
      "executed_at": "2025-03-02T10:31:00Z"
    },
    {
      "execution_id": "exec_2",
      "quantity": 50,
      "price": 1250.50,
      "executed_at": "2025-03-02T10:32:00Z"
    }
  ],
  "created_at_bs": "2081-11-17",
  "created_at_gregorian": "2025-03-02T10:30:00Z"
}
```

```yaml
PUT /api/v1/orders/{order_id}/cancel
Authorization: Bearer {user_token}

Request:
{
  "reason": "Changed investment strategy"
}

Response 200:
{
  "order_id": "ord_7a8b9c0d",
  "status": "CANCELLED",
  "cancelled_at": "2025-03-02T10:35:00Z"
}
```

```yaml
POST /api/v1/orders/{order_id}/approve
Authorization: Bearer {checker_token}

Request:
{
  "checker_id": "user_789",
  "comments": "Approved after risk review"
}

Response 200:
{
  "order_id": "ord_7a8b9c0d",
  "status": "APPROVED",
  "approved_at": "2025-03-02T10:33:00Z"
}
```

### 2.2 gRPC Service Definition

```protobuf
syntax = "proto3";

package siddhanta.oms.v1;

service OrderManagementService {
  rpc PlaceOrder(PlaceOrderRequest) returns (PlaceOrderResponse);
  rpc GetOrder(GetOrderRequest) returns (Order);
  rpc CancelOrder(CancelOrderRequest) returns (CancelOrderResponse);
  rpc ApproveOrder(ApproveOrderRequest) returns (ApproveOrderResponse);
  rpc ListOrders(ListOrdersRequest) returns (ListOrdersResponse);
  rpc GetPosition(GetPositionRequest) returns (Position);
}

message PlaceOrderRequest {
  string instrument_id = 1;
  OrderType order_type = 2;
  Side side = 3;
  int32 quantity = 4;
  optional double price = 5;
  Validity validity = 6;
  string client_order_id = 7;
  string account_id = 8;
  google.protobuf.Struct metadata = 9;
}

enum OrderType {
  MARKET = 0;
  LIMIT = 1;
  STOP = 2;
  STOP_LIMIT = 3;
}

enum Side {
  BUY = 0;
  SELL = 1;
}

enum Validity {
  DAY = 0;
  GTC = 1;
  IOC = 2;
  FOK = 3;
}

message PlaceOrderResponse {
  string order_id = 1;
  OrderStatus status = 2;
  string client_order_id = 3;
  string created_at_bs = 4;
  google.protobuf.Timestamp created_at_gregorian = 5;
  bool validation_required = 6;
}

enum OrderStatus {
  PENDING_VALIDATION = 0;
  PENDING_APPROVAL = 1;
  APPROVED = 2;
  SUBMITTED = 3;
  PARTIALLY_FILLED = 4;
  EXECUTED = 5;
  CANCELLED = 6;
  REJECTED = 7;
  EXPIRED = 8;
}

message Order {
  string order_id = 1;
  string instrument_id = 2;
  OrderType order_type = 3;
  Side side = 4;
  int32 quantity = 5;
  optional double price = 6;
  OrderStatus status = 7;
  int32 filled_quantity = 8;
  optional double average_price = 9;
  repeated Execution executions = 10;
  string created_at_bs = 11;
  google.protobuf.Timestamp created_at_gregorian = 12;
}

message Execution {
  string execution_id = 1;
  int32 quantity = 2;
  double price = 3;
  google.protobuf.Timestamp executed_at = 4;
}
```

### 2.3 SDK Method Signatures

```typescript
interface OMSClient {
  /**
   * Place new order
   * @throws ValidationError, InsufficientFundsError
   */
  placeOrder(order: OrderRequest): Promise<OrderResponse>;

  /**
   * Get order by ID
   */
  getOrder(orderId: string): Promise<Order>;

  /**
   * Cancel order
   */
  cancelOrder(orderId: string, reason: string): Promise<void>;

  /**
   * Approve order (checker in maker-checker)
   */
  approveOrder(orderId: string, checkerId: string, comments?: string): Promise<void>;

  /**
   * List orders with filters
   */
  listOrders(filters: OrderFilters): Promise<Order[]>;

  /**
   * Get current position for instrument
   */
  getPosition(accountId: string, instrumentId: string): Promise<Position>;

  /**
   * Get AI order suggestion
   */
  getOrderSuggestion(context: OrderContext): Promise<OrderSuggestion>;
}

interface OrderRequest {
  instrumentId: string;
  orderType: 'MARKET' | 'LIMIT' | 'STOP' | 'STOP_LIMIT';
  side: 'BUY' | 'SELL';
  quantity: number;
  price?: number;
  validity: 'DAY' | 'GTC' | 'IOC' | 'FOK';
  clientOrderId?: string;
  accountId: string;
  metadata?: Record<string, unknown>;
}

interface OrderResponse {
  orderId: string;
  status: OrderStatus;
  clientOrderId?: string;
  createdAtBs: string;
  createdAtGregorian: Date;
  validationRequired: boolean;
}

interface Order {
  orderId: string;
  instrumentId: string;
  orderType: string;
  side: string;
  quantity: number;
  price?: number;
  status: OrderStatus;
  filledQuantity: number;
  averagePrice?: number;
  executions: Execution[];
  createdAtBs: string;
  createdAtGregorian: Date;
}

interface Position {
  accountId: string;
  instrumentId: string;
  quantity: number;
  averageCost: number;
  currentValue: number;
  unrealizedPnL: number;
  realizedPnL: number;
}
```

### 2.4 Error Model

| Error Code | HTTP Status | Retryable | Description |
|------------|-------------|-----------|-------------|
| OMS_E001 | 400 | No | Pre-trade validation failed |
| OMS_E002 | 400 | No | Invalid order parameters |
| OMS_E003 | 404 | No | Order not found |
| OMS_E004 | 409 | No | Invalid state transition |
| OMS_E005 | 403 | No | Maker-checker approval required |
| OMS_E006 | 500 | Yes | Exchange routing failed |
| OMS_E007 | 409 | No | Duplicate client order ID |
| OMS_E008 | 400 | No | Instrument not tradable |

---

## 3. DATA MODEL

### 3.1 Event Schemas

#### OrderPlacedEvent v1.0.0

```json
{
  "event_id": "uuid",
  "event_type": "OrderPlaced",
  "event_version": "1.0.0",
  "aggregate_id": "ord_7a8b9c0d",
  "aggregate_type": "Order",
  "sequence_number": 1,
  "causality_id": "uuid",
  "trace_id": "uuid",
  "timestamp_bs": "2081-11-17",
  "timestamp_gregorian": "2025-03-02T10:30:00Z",
  "payload": {
    "order_id": "ord_7a8b9c0d",
    "instrument_id": "NABIL",
    "order_type": "LIMIT",
    "side": "BUY",
    "quantity": 100,
    "price": 1250.50,
    "validity": "DAY",
    "account_id": "acc_456",
    "placed_by": "user_123"
  }
}
```

> **Note**: All events conform to the K-05 standard envelope schema: `event_id`, `event_type`, `aggregate_id`, `aggregate_type`, `sequence_number`, `causality_id`, `trace_id`, `timestamp_bs`, `timestamp_gregorian`, `payload`.

#### OrderExecutedEvent v1.0.0

```json
{
  "event_id": "uuid",
  "event_type": "OrderExecuted",
  "event_version": "1.0.0",
  "aggregate_id": "ord_7a8b9c0d",
  "aggregate_type": "Order",
  "sequence_number": 3,
  "causality_id": "uuid",
  "trace_id": "uuid",
  "timestamp_bs": "2081-11-17",
  "timestamp_gregorian": "2025-03-02T10:31:00Z",
  "payload": {
    "order_id": "ord_7a8b9c0d",
    "execution_id": "exec_1",
    "quantity": 50,
    "price": 1249.50,
    "exchange": "NEPSE"
  }
}
```

#### PositionUpdatedEvent v1.0.0

```json
{
  "event_id": "uuid",
  "event_type": "PositionUpdated",
  "event_version": "1.0.0",
  "aggregate_id": "pos_acc456_NABIL",
  "aggregate_type": "Position",
  "sequence_number": 5,
  "causality_id": "uuid",
  "trace_id": "uuid",
  "timestamp_bs": "2081-11-17",
  "timestamp_gregorian": "2025-03-02T10:31:00Z",
  "payload": {
    "account_id": "acc_456",
    "instrument_id": "NABIL",
    "quantity_delta": 50,
    "new_quantity": 150,
    "cost_basis_delta": 62475.00,
    "new_average_cost": 1249.83,
    "caused_by_order": "ord_7a8b9c0d"
  }
}
```

### 3.2 Storage Tables

#### orders

```sql
CREATE TABLE orders (
  order_id VARCHAR(255) PRIMARY KEY,
  client_order_id VARCHAR(255),
  instrument_id VARCHAR(100) NOT NULL,
  order_type VARCHAR(20) NOT NULL CHECK (order_type IN ('MARKET', 'LIMIT', 'STOP', 'STOP_LIMIT')),
  side VARCHAR(10) NOT NULL CHECK (side IN ('BUY', 'SELL')),
  quantity INT NOT NULL,
  price DECIMAL(18, 4),
  validity VARCHAR(10) NOT NULL CHECK (validity IN ('DAY', 'GTC', 'IOC', 'FOK')),
  status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING_VALIDATION', 'PENDING_APPROVAL', 'APPROVED', 'SUBMITTED', 'PARTIALLY_FILLED', 'EXECUTED', 'CANCELLED', 'REJECTED', 'EXPIRED')),
  filled_quantity INT DEFAULT 0,
  average_price DECIMAL(18, 4),
  account_id VARCHAR(255) NOT NULL,
  placed_by VARCHAR(255) NOT NULL,
  maker_id VARCHAR(255),
  checker_id VARCHAR(255),
  approved_at TIMESTAMPTZ,
  submitted_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  metadata JSONB,
  created_at_bs VARCHAR(10) NOT NULL,
  created_at_gregorian TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (client_order_id)
);

CREATE INDEX idx_orders_account ON orders(account_id, created_at_gregorian);
CREATE INDEX idx_orders_instrument ON orders(instrument_id, created_at_gregorian);
CREATE INDEX idx_orders_status ON orders(status, created_at_gregorian);
```

#### executions

```sql
CREATE TABLE executions (
  execution_id VARCHAR(255) PRIMARY KEY,
  order_id VARCHAR(255) NOT NULL REFERENCES orders(order_id),
  quantity INT NOT NULL,
  price DECIMAL(18, 4) NOT NULL,
  exchange VARCHAR(50) NOT NULL,
  exchange_execution_id VARCHAR(255),
  executed_at_bs VARCHAR(10) NOT NULL,
  executed_at_gregorian TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_executions_order ON executions(order_id, executed_at_gregorian);
```

#### positions

```sql
CREATE TABLE positions (
  position_id VARCHAR(255) PRIMARY KEY,
  account_id VARCHAR(255) NOT NULL,
  instrument_id VARCHAR(100) NOT NULL,
  quantity INT NOT NULL DEFAULT 0,
  average_cost DECIMAL(18, 4) NOT NULL DEFAULT 0,
  realized_pnl DECIMAL(18, 4) NOT NULL DEFAULT 0,
  last_updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (account_id, instrument_id)
);

CREATE INDEX idx_positions_account ON positions(account_id);
```

---

## 4. CONTROL FLOW

### 4.1 Order Placement Flow

```
User → OMSClient.placeOrder(orderRequest)
  ↓
OMSClient → Validate order parameters
  ↓ [Valid]
OMSClient → Generate order_id
  ↓
OMSClient → Publish OrderPlacedEvent
  ↓
OrderCommandHandler → Receive OrderPlacedEvent
  ↓
OrderCommandHandler → Run pre-trade validation (K-03 Rules Engine)
  ↓
RulesEngine → Evaluate policies:
  - buying_power_check
  - position_limit_check
  - instrument_tradability_check
  - market_hours_check
  ↓ [All pass]
OrderCommandHandler → Check if maker-checker required (order value > threshold)
  ↓ [Required]
OrderCommandHandler → Update status=PENDING_APPROVAL
  ↓
OrderCommandHandler → Publish OrderPendingApprovalEvent
  ↓
OrderCommandHandler → Notify checker
  ↓
Checker → OMSClient.approveOrder(order_id)
  ↓
OrderCommandHandler → Verify checker != maker
  ↓ [Valid]
OrderCommandHandler → Update status=APPROVED
  ↓
OrderCommandHandler → Publish OrderApprovedEvent
  ↓
OrderRoutingService → Route order to exchange
  ↓
OrderRoutingService → Update status=SUBMITTED
```

### 4.2 Order Execution Flow

```
Exchange → Send execution report
  ↓
ExchangeGateway → Parse execution report
  ↓
ExchangeGateway → Publish OrderExecutedEvent
  ↓
OrderCommandHandler → Receive OrderExecutedEvent
  ↓
OrderCommandHandler → Update order (filled_quantity, average_price)
  ↓
OrderCommandHandler → Check if fully filled
  ↓ [Fully filled]
OrderCommandHandler → Update status=EXECUTED
  ↓
OrderCommandHandler → Publish PositionUpdateCommand
  ↓
PositionCommandHandler → Calculate position delta
  ↓
PositionCommandHandler → Update position (quantity, average_cost)
  ↓
PositionCommandHandler → Publish PositionUpdatedEvent
  ↓
PositionCommandHandler → Audit to K-07
```

### 4.3 AI Order Suggestion Flow

```
User → OMSClient.getOrderSuggestion(context)
  ↓
OMSClient → Call K-09 AI Governance
  ↓
AIGovernance → Load order_suggestion_model
  ↓
AIGovernance → Predict optimal order parameters:
  - suggested_quantity
  - suggested_price
  - suggested_timing
  ↓
AIGovernance → Generate explanation (SHAP)
  ↓
AIGovernance → Return suggestion with confidence
  ↓
OMSClient → Return OrderSuggestion to user
  ↓
User → Review suggestion
  ↓
User → Place order (with or without modifications)
```

---

## 5. ALGORITHMS & POLICIES

### 5.1 Order State Machine

```python
from enum import Enum
from typing import Optional

class OrderStatus(Enum):
    PENDING_VALIDATION = "PENDING_VALIDATION"
    PENDING_APPROVAL = "PENDING_APPROVAL"
    APPROVED = "APPROVED"
    SUBMITTED = "SUBMITTED"
    PARTIALLY_FILLED = "PARTIALLY_FILLED"
    EXECUTED = "EXECUTED"
    CANCELLED = "CANCELLED"
    REJECTED = "REJECTED"
    EXPIRED = "EXPIRED"

class OrderStateMachine:
    """
    Enforce valid order state transitions.
    """
    
    VALID_TRANSITIONS = {
        OrderStatus.PENDING_VALIDATION: [
            OrderStatus.PENDING_APPROVAL,
            OrderStatus.APPROVED,
            OrderStatus.REJECTED
        ],
        OrderStatus.PENDING_APPROVAL: [
            OrderStatus.APPROVED,
            OrderStatus.REJECTED,
            OrderStatus.CANCELLED
        ],
        OrderStatus.APPROVED: [
            OrderStatus.SUBMITTED,
            OrderStatus.CANCELLED
        ],
        OrderStatus.SUBMITTED: [
            OrderStatus.PARTIALLY_FILLED,
            OrderStatus.EXECUTED,
            OrderStatus.CANCELLED,
            OrderStatus.REJECTED,
            OrderStatus.EXPIRED
        ],
        OrderStatus.PARTIALLY_FILLED: [
            OrderStatus.EXECUTED,
            OrderStatus.CANCELLED,
            OrderStatus.EXPIRED
        ],
        OrderStatus.EXECUTED: [],
        OrderStatus.CANCELLED: [],
        OrderStatus.REJECTED: [],
        OrderStatus.EXPIRED: []
    }
    
    @staticmethod
    def can_transition(from_status: OrderStatus, to_status: OrderStatus) -> bool:
        """
        Check if transition is valid.
        """
        return to_status in OrderStateMachine.VALID_TRANSITIONS.get(from_status, [])
    
    @staticmethod
    def transition(order: Order, to_status: OrderStatus) -> Order:
        """
        Transition order to new status.
        """
        if not OrderStateMachine.can_transition(order.status, to_status):
            raise InvalidStateTransitionError(
                f"Cannot transition from {order.status} to {to_status}"
            )
        
        order.status = to_status
        return order
```

### 5.2 Position Calculation

```python
from decimal import Decimal

class PositionCalculator:
    """
    Calculate position updates from executions.
    """
    
    @staticmethod
    def update_position(
        current_position: Position,
        execution: Execution,
        side: str
    ) -> Position:
        """
        Update position with new execution.
        
        Uses weighted average for cost basis.
        """
        if side == 'BUY':
            # Increase position
            new_quantity = current_position.quantity + execution.quantity
            
            # Weighted average cost
            total_cost = (
                current_position.quantity * current_position.average_cost +
                execution.quantity * execution.price
            )
            new_average_cost = total_cost / new_quantity if new_quantity > 0 else Decimal(0)
            
            return Position(
                account_id=current_position.account_id,
                instrument_id=current_position.instrument_id,
                quantity=new_quantity,
                average_cost=new_average_cost,
                realized_pnl=current_position.realized_pnl
            )
        
        else:  # SELL
            # Decrease position
            new_quantity = current_position.quantity - execution.quantity
            
            # Calculate realized P&L
            realized_pnl = (
                execution.price - current_position.average_cost
            ) * execution.quantity
            
            return Position(
                account_id=current_position.account_id,
                instrument_id=current_position.instrument_id,
                quantity=new_quantity,
                average_cost=current_position.average_cost,  # Cost basis unchanged
                realized_pnl=current_position.realized_pnl + realized_pnl
            )
```

### 5.3 Maker-Checker Threshold

```python
class MakerCheckerPolicy:
    """
    Determine if order requires maker-checker approval.
    """
    
    @staticmethod
    async def requires_approval(order: Order, config: ConfigClient) -> bool:
        """
        Check if order requires checker approval.
        """
        # Get threshold from K-02 Config Engine
        threshold = await config.get(
            key=f"order_approval_threshold.{order.instrument_id}",
            default=1000000  # Default 1M
        )
        
        # Calculate order value
        order_value = order.quantity * order.price if order.price else 0
        
        return order_value > threshold
```

### 5.4 Pre-Trade Validation

```python
class PreTradeValidator:
    """
    Validate order before submission.
    """
    
    def __init__(self, rules_client: RulesClient):
        self.rules_client = rules_client
    
    async def validate(self, order: Order, account: Account) -> ValidationResult:
        """
        Run pre-trade validation rules.
        """
        # Prepare input for rules engine
        input_data = {
            'order': {
                'instrument_id': order.instrument_id,
                'side': order.side,
                'quantity': order.quantity,
                'price': order.price,
                'order_value': order.quantity * order.price if order.price else 0
            },
            'account': {
                'account_id': account.account_id,
                'buying_power': account.buying_power,
                'positions': account.positions
            }
        }
        
        # Evaluate rules
        result = await self.rules_client.evaluate(
            policy_id='pre_trade_validation',
            input=input_data
        )
        
        if not result.allow:
            return ValidationResult(
                valid=False,
                errors=result.deny
            )
        
        return ValidationResult(valid=True, warnings=result.warnings)
```

---

## 6. NFR BUDGETS

### 6.1 Latency Budgets

| Operation | P50 | P95 | P99 | Timeout | Notes |
|-----------|-----|-----|-----|---------|-------|
| processOrder() - OMS internal | 0.5ms | 1ms | 2ms | 100ms | Capture → event emission only |
| placeOrder() - end-to-end | 5ms | 8ms | 12ms | 1000ms | Includes K-03 pre-trade (D-06 risk + D-07 compliance) |
| placeOrder() - with maker-checker | 5ms | 8ms | 12ms | 1000ms | Routes to approval queue; same initial latency |
| getOrder() | 1ms | 3ms | 5ms | 100ms | Read from projection |
| cancelOrder() | 1ms | 3ms | 5ms | 500ms | Cancel → event emission |
| getPositions() | 1ms | 3ms | 5ms | 100ms | Read from materialized view |

> **Latency Budget Decomposition (placeOrder end-to-end P99 = 12ms)**:
> - OMS internal processing: 2ms
> - K-03 pre-trade evaluation: 5ms (D-06 risk: 2ms + D-07 compliance: 3ms in parallel)
> - K-05 event publish: 2ms
> - Network/serialization overhead: 3ms

### 6.2 Throughput Targets

| Operation | Target TPS | Peak TPS |
|-----------|------------|----------|
| placeOrder() | 50,000 | 100,000 |
| getOrder() | 100,000 | 200,000 |
| Position updates | 50,000 | 100,000 |

### 6.3 Availability

- **Uptime SLA**: 99.999% during trading hours (critical financial path)
- **RTO**: < 5 minutes
- **RPO**: 0 (no data loss)
- **Data Retention**: 10 years (SEBON/SEBI cross-jurisdiction safety margin)

---

## 7. SECURITY DESIGN

### 7.1 Order Authorization

```typescript
interface OrderPermissions {
  canPlaceOrder: (userId: string, accountId: string) => boolean;
  canCancelOrder: (userId: string, orderId: string) => boolean;
  canApproveOrder: (userId: string, orderId: string) => boolean;
}

class OrderAccessControl implements OrderPermissions {
  canPlaceOrder(userId: string, accountId: string): boolean {
    // User must have TRADER role for account
    return this.hasAccountRole(userId, accountId, ['TRADER', 'PORTFOLIO_MANAGER']);
  }

  canCancelOrder(userId: string, orderId: string): boolean {
    const order = this.getOrder(orderId);
    
    // User must be order creator or have ADMIN role
    return (
      order.placedBy === userId ||
      this.hasRole(userId, ['ADMIN', 'COMPLIANCE_OFFICER'])
    );
  }

  canApproveOrder(userId: string, orderId: string): boolean {
    const order = this.getOrder(orderId);
    
    // Checker cannot be maker
    if (order.makerId === userId) {
      return false;
    }
    
    // User must have CHECKER role
    return this.hasRole(userId, ['CHECKER', 'SENIOR_TRADER']);
  }
}
```

### 7.2 Order Tampering Prevention

```python
import hmac
import hashlib

class OrderIntegrity:
    """
    Ensure order data integrity.
    """
    
    def __init__(self, secret_key: bytes):
        self.secret_key = secret_key
    
    def sign_order(self, order: Order) -> str:
        """
        Sign order with HMAC.
        """
        message = f"{order.order_id}:{order.instrument_id}:{order.quantity}:{order.price}".encode()
        signature = hmac.new(self.secret_key, message, hashlib.sha256).hexdigest()
        return signature
    
    def verify_order(self, order: Order, signature: str) -> bool:
        """
        Verify order signature.
        """
        expected_signature = self.sign_order(order)
        return hmac.compare_digest(signature, expected_signature)
```

---

## 8. OBSERVABILITY & AUDIT

### 8.1 Metrics

```yaml
metrics:
  - name: orders_placed_total
    type: counter
    labels: [instrument_id, order_type, side, status]
  
  - name: order_placement_latency_seconds
    type: histogram
    labels: [validation_required]
    buckets: [0.02, 0.05, 0.1, 0.3, 0.5, 1.0]
  
  - name: orders_executed_total
    type: counter
    labels: [instrument_id, side]
  
  - name: order_fill_rate
    type: gauge
    labels: [instrument_id]
  
  - name: position_updates_total
    type: counter
    labels: [account_id, instrument_id]
  
  - name: maker_checker_approval_time_seconds
    type: histogram
    labels: [instrument_id]
```

### 8.2 Structured Logs

```json
{
  "timestamp": "2025-03-02T10:30:00.123Z",
  "level": "INFO",
  "service": "oms",
  "trace_id": "abc-123",
  "action": "ORDER_PLACED",
  "order_id": "ord_7a8b9c0d",
  "instrument_id": "NABIL",
  "side": "BUY",
  "quantity": 100,
  "price": 1250.50,
  "account_id": "acc_456",
  "placed_by": "user_123"
}
```

### 8.3 Audit Events

All order actions are audited to K-07:
- ORDER_PLACED
- ORDER_VALIDATED
- ORDER_APPROVED
- ORDER_SUBMITTED
- ORDER_EXECUTED
- ORDER_CANCELLED
- POSITION_UPDATED

---

## 9. EXTENSIBILITY & EVOLUTION

### 9.1 Extension Points

```typescript
/**
 * Extension point: Custom order validation
 */
interface OrderValidator {
  validate(order: Order, context: ValidationContext): Promise<ValidationResult>;
}

class NepalMarketValidator implements OrderValidator {
  async validate(order: Order, context: ValidationContext): Promise<ValidationResult> {
    // Nepal-specific validation
    // - Check if instrument is NEPSE-listed
    // - Validate lot size (multiples of 10)
    // - Check circuit breaker limits
    
    const instrument = await this.getInstrument(order.instrumentId);
    
    if (instrument.exchange !== 'NEPSE') {
      return { valid: false, errors: ['Instrument not listed on NEPSE'] };
    }
    
    if (order.quantity % 10 !== 0) {
      return { valid: false, errors: ['Quantity must be multiple of 10'] };
    }
    
    return { valid: true };
  }
}

// Register validator
omsClient.registerValidator(new NepalMarketValidator());
```

### 9.2 Custom Order Types

```typescript
/**
 * Extension point: Custom order type
 */
interface OrderTypeHandler {
  orderType: string;
  validate(order: Order): Promise<boolean>;
  route(order: Order): Promise<RoutingDecision>;
}

class IcebergOrderHandler implements OrderTypeHandler {
  orderType = 'ICEBERG';

  async validate(order: Order): Promise<boolean> {
    // Validate iceberg-specific parameters
    const displayQuantity = order.metadata?.displayQuantity;
    return displayQuantity && displayQuantity < order.quantity;
  }

  async route(order: Order): Promise<RoutingDecision> {
    // Split order into smaller slices
    const displayQuantity = order.metadata.displayQuantity;
    const slices = Math.ceil(order.quantity / displayQuantity);
    
    return {
      strategy: 'SLICE',
      slices,
      sliceSize: displayQuantity
    };
  }
}

// Register custom order type
omsClient.registerOrderType(new IcebergOrderHandler());
```

### 9.3 AI Integration Hooks

```typescript
/**
 * Extension point: AI-powered order optimization
 */
interface OrderOptimizer {
  optimize(order: Order, marketData: MarketData): Promise<OptimizedOrder>;
}

class VWAPOptimizer implements OrderOptimizer {
  async optimize(order: Order, marketData: MarketData): Promise<OptimizedOrder> {
    // Call K-09 AI Governance for VWAP prediction
    const suggestion = await aiClient.predict('vwap_optimizer', {
      instrument_id: order.instrumentId,
      quantity: order.quantity,
      market_volume: marketData.volume,
      volatility: marketData.volatility
    });

    return {
      ...order,
      suggestedSlices: suggestion.prediction.slices,
      suggestedTiming: suggestion.prediction.timing,
      explanation: suggestion.explanation
    };
  }
}

// Register optimizer
omsClient.registerOptimizer(new VWAPOptimizer());
```

---

## 10. TEST PLAN

### 10.1 Unit Tests

```typescript
describe('OrderStateMachine', () => {
  it('should allow valid state transitions', () => {
    const order = createOrder({ status: 'PENDING_VALIDATION' });
    
    const updated = OrderStateMachine.transition(order, 'APPROVED');
    
    expect(updated.status).toBe('APPROVED');
  });

  it('should reject invalid state transitions', () => {
    const order = createOrder({ status: 'EXECUTED' });
    
    expect(() => {
      OrderStateMachine.transition(order, 'CANCELLED');
    }).toThrow(InvalidStateTransitionError);
  });
});

describe('PositionCalculator', () => {
  it('should calculate position for buy execution', () => {
    const position = new Position({
      quantity: 100,
      averageCost: 1200.00,
      realizedPnL: 0
    });

    const execution = new Execution({
      quantity: 50,
      price: 1300.00
    });

    const updated = PositionCalculator.updatePosition(position, execution, 'BUY');

    expect(updated.quantity).toBe(150);
    expect(updated.averageCost).toBeCloseTo(1233.33, 2);
  });

  it('should calculate realized PnL for sell execution', () => {
    const position = new Position({
      quantity: 100,
      averageCost: 1200.00,
      realizedPnL: 0
    });

    const execution = new Execution({
      quantity: 50,
      price: 1300.00
    });

    const updated = PositionCalculator.updatePosition(position, execution, 'SELL');

    expect(updated.quantity).toBe(50);
    expect(updated.realizedPnL).toBe(5000.00); // (1300 - 1200) * 50
  });
});
```

### 10.2 Integration Tests

```typescript
describe('Order Lifecycle', () => {
  it('should complete full order lifecycle', async () => {
    // Place order
    const response = await omsClient.placeOrder({
      instrumentId: 'NABIL',
      orderType: 'LIMIT',
      side: 'BUY',
      quantity: 100,
      price: 1250.50,
      validity: 'DAY',
      accountId: 'acc_456'
    });

    expect(response.status).toBe('PENDING_VALIDATION');

    // Wait for validation
    await waitForStatus(response.orderId, 'APPROVED');

    // Simulate exchange execution
    await simulateExecution(response.orderId, {
      quantity: 100,
      price: 1250.00
    });

    // Verify order executed
    const order = await omsClient.getOrder(response.orderId);
    expect(order.status).toBe('EXECUTED');
    expect(order.filledQuantity).toBe(100);

    // Verify position updated
    const position = await omsClient.getPosition('acc_456', 'NABIL');
    expect(position.quantity).toBe(100);
  });

  it('should handle partial fills', async () => {
    const response = await omsClient.placeOrder({
      instrumentId: 'NABIL',
      orderType: 'LIMIT',
      side: 'BUY',
      quantity: 100,
      price: 1250.50,
      validity: 'DAY',
      accountId: 'acc_456'
    });

    // First partial fill
    await simulateExecution(response.orderId, {
      quantity: 50,
      price: 1249.50
    });

    let order = await omsClient.getOrder(response.orderId);
    expect(order.status).toBe('PARTIALLY_FILLED');
    expect(order.filledQuantity).toBe(50);

    // Second partial fill
    await simulateExecution(response.orderId, {
      quantity: 50,
      price: 1250.50
    });

    order = await omsClient.getOrder(response.orderId);
    expect(order.status).toBe('EXECUTED');
    expect(order.filledQuantity).toBe(100);
    expect(order.averagePrice).toBeCloseTo(1250.00, 2);
  });
});
```

### 10.3 Maker-Checker Tests

```typescript
describe('Maker-Checker Workflow', () => {
  it('should require approval for large orders', async () => {
    // Set threshold to 1M
    await configClient.set({
      key: 'order_approval_threshold.NABIL',
      value: 1000000
    });

    // Place large order (1.25M)
    const response = await omsClient.placeOrder({
      instrumentId: 'NABIL',
      orderType: 'LIMIT',
      side: 'BUY',
      quantity: 1000,
      price: 1250.00,
      validity: 'DAY',
      accountId: 'acc_456'
    });

    expect(response.status).toBe('PENDING_APPROVAL');
    expect(response.validationRequired).toBe(true);
  });

  it('should reject approval from same user', async () => {
    const response = await omsClient.placeOrder({
      instrumentId: 'NABIL',
      orderType: 'LIMIT',
      side: 'BUY',
      quantity: 1000,
      price: 1250.00,
      validity: 'DAY',
      accountId: 'acc_456'
    });

    // Try to approve as same user
    await expect(
      omsClient.approveOrder(response.orderId, 'user_123')
    ).rejects.toThrow(MakerCheckerViolationError);
  });

  it('should allow approval from different user', async () => {
    const response = await omsClient.placeOrder({
      instrumentId: 'NABIL',
      orderType: 'LIMIT',
      side: 'BUY',
      quantity: 1000,
      price: 1250.00,
      validity: 'DAY',
      accountId: 'acc_456'
    });

    // Approve as different user
    await omsClient.approveOrder(response.orderId, 'user_789');

    const order = await omsClient.getOrder(response.orderId);
    expect(order.status).toBe('APPROVED');
  });
});
```

### 10.4 AI Integration Tests

```typescript
describe('AI Order Suggestions', () => {
  it('should provide order suggestion with explanation', async () => {
    const suggestion = await omsClient.getOrderSuggestion({
      instrumentId: 'NABIL',
      side: 'BUY',
      targetQuantity: 1000,
      marketConditions: {
        volatility: 0.15,
        volume: 50000
      }
    });

    expect(suggestion.suggestedQuantity).toBeDefined();
    expect(suggestion.suggestedPrice).toBeDefined();
    expect(suggestion.confidence).toBeGreaterThan(0);
    expect(suggestion.explanation).toBeDefined();
    expect(suggestion.explanation.method).toBe('SHAP');
  });
});
```

---

## 11. VALIDATION QUESTIONS & ASSUMPTIONS

### Assumptions

1. **[ASSUMPTION]** Orders are synchronous (< 5s response time)
   - **Validation**: Are there long-running order workflows?
   - **Impact**: May need async order processing queue

2. **[ASSUMPTION]** Maker-checker threshold is per-instrument
   - **Validation**: Should threshold be account-level or global?
   - **Impact**: May need hierarchical threshold configuration

3. **[ASSUMPTION]** Position updates are eventually consistent
   - **Validation**: Is real-time position accuracy required?
   - **Impact**: May need synchronous position updates

4. **[ASSUMPTION]** Partial fills are common
   - **Validation**: What is typical fill rate?
   - **Impact**: May optimize for full fills if > 90%

5. **[ASSUMPTION]** AI suggestions are advisory (not auto-executed)
   - **Validation**: Should AI auto-execute in certain scenarios?
   - **Impact**: May need auto-execution workflow with safeguards

---

**END OF LLD: D-01 ORDER MANAGEMENT SYSTEM**
