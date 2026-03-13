# LOW-LEVEL DESIGN: K-03 POLICY/RULES ENGINE

**Module**: K-03 Policy/Rules Engine  
**Layer**: Kernel  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Platform Core Team

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

The Rules Engine provides **declarative policy evaluation** using OPA/Rego for all compliance, authorization, and business rule decisions across Project Siddhanta.

**Core Responsibilities**:
- Evaluate policies written in Rego DSL
- Sandboxed execution (T2 isolation)
- Hot-reload of rule packs without service restart
- Audit trail of all policy decisions
- Jurisdiction-specific rule routing
- Maker-checker workflow for rule deployment

**Invariants**:
1. All policy evaluations MUST be deterministic and reproducible
2. Rule execution MUST be sandboxed (no external network/file access)
3. Policy decisions MUST be audited to K-07
4. Rule changes MUST be versioned and immutable
5. Evaluation latency MUST be < 10ms (P99)

### 1.2 Explicit Non-Goals

- ❌ Business data storage (rules reference data from K-02 Config Engine)
- ❌ Workflow orchestration (delegated to K-05)
- ❌ Real-time streaming analytics (use dedicated analytics engine)

### 1.3 Dependencies

| Dependency | Purpose | Readiness Gate |
|------------|---------|----------------|
| K-02 Config Engine | Rule pack storage and versioning | K-02 stable |
| K-05 Event Bus | Hot-reload event delivery | K-05 stable |
| K-07 Audit Framework | Policy decision audit trail | K-07 stable |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 REST API Endpoints

```yaml
POST /api/v1/rules/evaluate
Authorization: Bearer {service_token}
Content-Type: application/json

Request:
{
  "policy_id": "order_validation",
  "input": {
    "order": {
      "quantity": 5,
      "instrument_id": "NABIL",
      "order_type": "LIMIT"
    },
    "tenant_id": "tenant_np_1",
    "jurisdiction": "NP"
  },
  "trace_id": "abc-123"
}

Response 200:
{
  "decision": {
    "allow": false,
    "deny": ["Quantity must be multiple of 10"],
    "warnings": []
  },
  "policy_version": "np_order_validation_v2.1.0",
  "evaluation_time_ms": 3.2,
  "trace_id": "abc-123"
}

Response 400:
{
  "error": "POLICY_NOT_FOUND",
  "message": "Policy 'order_validation' not found for jurisdiction NP",
  "code": "RULE_E002"
}
```

```yaml
POST /api/v1/rules/packs
Authorization: Bearer {admin_token}

Request:
{
  "pack_id": "np_order_validation_v2.1.0",
  "jurisdiction": "NP",
  "policy_id": "order_validation",
  "rego_code": "package order_validation\n\nmin_lot_size := 10\n\ndeny[msg] {\n  input.order.quantity % min_lot_size != 0\n  msg := sprintf(\"Quantity must be multiple of %v\", [min_lot_size])\n}",
  "test_cases": [...],
  "maker_id": "user_123",
  "effective_date": {
    "bs": "2081-11-17",
    "gregorian": "2025-03-02T00:00:00Z"
  }
}

Response 202:
{
  "pack_id": "np_order_validation_v2.1.0",
  "status": "PENDING_APPROVAL",
  "test_results": {
    "passed": 15,
    "failed": 0
  }
}
```

### 2.2 gRPC Service Definition

```protobuf
syntax = "proto3";

package siddhanta.rules.v1;

service RulesService {
  rpc EvaluatePolicy(EvaluatePolicyRequest) returns (EvaluatePolicyResponse);
  rpc BatchEvaluate(BatchEvaluateRequest) returns (BatchEvaluateResponse);
  rpc ValidateRulePack(ValidateRulePackRequest) returns (ValidateRulePackResponse);
}

message EvaluatePolicyRequest {
  string policy_id = 1;
  google.protobuf.Struct input = 2;
  string tenant_id = 3;
  optional string jurisdiction = 4;
  string trace_id = 5;
}

message EvaluatePolicyResponse {
  PolicyDecision decision = 1;
  string policy_version = 2;
  double evaluation_time_ms = 3;
  string trace_id = 4;
}

message PolicyDecision {
  bool allow = 1;
  repeated string deny = 2;
  repeated string warnings = 3;
  google.protobuf.Struct metadata = 4;
}
```

### 2.3 SDK Method Signatures

```typescript
interface RulesClient {
  /**
   * Evaluate policy synchronously
   * @throws PolicyNotFoundError, PolicyEvaluationError
   */
  evaluate<T>(request: EvaluateRequest): Promise<PolicyDecision<T>>;

  /**
   * Batch evaluate multiple policies
   */
  batchEvaluate(requests: EvaluateRequest[]): Promise<PolicyDecision[]>;

  /**
   * Watch for rule pack updates
   */
  watch(policyIds: string[], jurisdiction: string): AsyncIterator<RulePackUpdateEvent>;
}

interface EvaluateRequest {
  policyId: string;
  input: unknown;
  tenantId: string;
  jurisdiction?: string;
  traceId?: string;
}

interface PolicyDecision<T = unknown> {
  allow: boolean;
  deny: string[];
  warnings: string[];
  metadata?: T;
  policyVersion: string;
  evaluationTimeMs: number;
}
```

### 2.4 Error Model

| Error Code | HTTP Status | Retryable | Description |
|------------|-------------|-----------|-------------|
| RULE_E001 | 400 | No | Invalid policy input |
| RULE_E002 | 404 | No | Policy not found |
| RULE_E003 | 500 | Yes | Policy evaluation timeout |
| RULE_E004 | 400 | No | Rego syntax error |
| RULE_E005 | 403 | No | Maker-checker approval required |
| RULE_E006 | 500 | Yes | Sandbox initialization failed |

---

## 3. DATA MODEL

### 3.1 Event Schemas

#### RulePackActivatedEvent v1.0.0

> **K-05 Envelope Compliant** — all events use the standard envelope from LLD_K05_EVENT_BUS §3.1.

```json
{
  "event_id": "uuid",
  "event_type": "RulePackActivated",
  "event_version": "1.0.0",
  "aggregate_id": "np_order_validation_v2.1.0",
  "aggregate_type": "RulePack",
  "sequence_number": 1,
  "timestamp_bs": "2081-11-17T10:30:00",
  "timestamp_gregorian": "2025-03-02T10:30:00Z",
  "metadata": {
    "trace_id": "trace-uuid",
    "causation_id": "cmd-uuid",
    "correlation_id": "corr-uuid",
    "tenant_id": "tenant_np_1"
  },
  "data": {
    "pack_id": "np_order_validation_v2.1.0",
    "jurisdiction": "NP",
    "policy_id": "order_validation",
    "effective_date_bs": "2081-11-17",
    "effective_date_gregorian": "2025-03-02T00:00:00Z",
    "activated_by": "user_789"
  }
}
```

### 3.2 Storage Tables

#### rule_packs

```sql
CREATE TABLE rule_packs (
  pack_id VARCHAR(255) PRIMARY KEY,
  jurisdiction VARCHAR(10) NOT NULL,
  policy_id VARCHAR(255) NOT NULL,
  rego_code TEXT NOT NULL,
  compiled_bundle BYTEA,
  status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'ACTIVE', 'DEPRECATED')),
  effective_date_bs VARCHAR(10) NOT NULL,
  effective_date_gregorian TIMESTAMPTZ NOT NULL,
  maker_id VARCHAR(255) NOT NULL,
  checker_id VARCHAR(255),
  approved_at TIMESTAMPTZ,
  activated_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rule_packs_active ON rule_packs(jurisdiction, policy_id, status) 
  WHERE status = 'ACTIVE';
```

#### policy_decisions_audit

```sql
CREATE TABLE policy_decisions_audit (
  decision_id UUID PRIMARY KEY,
  policy_id VARCHAR(255) NOT NULL,
  policy_version VARCHAR(255) NOT NULL,
  input_hash VARCHAR(64) NOT NULL,
  decision JSONB NOT NULL,
  evaluation_time_ms FLOAT NOT NULL,
  tenant_id VARCHAR(255) NOT NULL,
  trace_id VARCHAR(255),
  timestamp_bs VARCHAR(10) NOT NULL,
  timestamp_gregorian TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_policy_audit_tenant ON policy_decisions_audit(tenant_id, timestamp_gregorian);
CREATE INDEX idx_policy_audit_trace ON policy_decisions_audit(trace_id);
```

---

## 4. CONTROL FLOW

### 4.1 Policy Evaluation Flow

```
Client → RulesClient.evaluate(policyId, input)
  ↓
RulesClient → Resolve jurisdiction from tenantId (via K-02)
  ↓
RulesClient → Load active rule pack for (jurisdiction, policyId)
  ↓ [Pack cached in memory]
RulesClient → Initialize OPA sandbox
  ↓
RulesClient → Execute Rego policy with input
  ↓
OPA Engine → Evaluate rules (sandboxed, no I/O)
  ↓
OPA Engine → Return decision {allow, deny[], warnings[]}
  ↓
RulesClient → Audit decision to K-07 (async)
  ↓
RulesClient → Return PolicyDecision to caller
```

### 4.2 Rule Pack Deployment Flow

```
Admin (Maker) → POST /api/v1/rules/packs
  ↓
RulesCommandHandler → Validate Rego syntax
  ↓
RulesCommandHandler → Compile Rego to OPA bundle
  ↓
RulesCommandHandler → Run test cases
  ↓ [Tests pass]
RulesCommandHandler → Create pack with status=PENDING_APPROVAL
  ↓
RulesCommandHandler → Publish RulePackPendingApprovalEvent
  ↓
Admin (Checker) → PUT /api/v1/rules/packs/{pack_id}/approve
  ↓
RulesCommandHandler → Verify checker != maker
  ↓
RulesCommandHandler → Update status=APPROVED
  ↓
Scheduler (at effective_date) → Update status=ACTIVE
  ↓
Scheduler → Publish RulePackActivatedEvent
  ↓
All RulesClients → Receive event, reload rule pack
```

### 4.3 Hot Reload Flow

```
RulePackActivatedEvent published
  ↓
RulesClient → Receive event via K-05 subscription
  ↓
RulesClient → Download new rule pack
  ↓
RulesClient → Compile Rego to OPA bundle
  ↓
RulesClient → Swap in-memory policy (atomic)
  ↓
RulesClient → Log reload success
  ↓
RulesClient → Trigger callback: onPolicyReloaded(policyId)
```

---

## 5. ALGORITHMS & POLICIES

### 5.1 Policy Resolution Algorithm

```python
def resolve_policy(policy_id: str, jurisdiction: str) -> RulePack:
    """
    Resolve active rule pack for jurisdiction and policy.
    Falls back to global if jurisdiction-specific not found.
    """
    # Try jurisdiction-specific first
    pack = query_rule_pack(
        policy_id=policy_id,
        jurisdiction=jurisdiction,
        status="ACTIVE"
    )
    
    if pack:
        return pack
    
    # Fallback to global
    pack = query_rule_pack(
        policy_id=policy_id,
        jurisdiction="GLOBAL",
        status="ACTIVE"
    )
    
    if not pack:
        raise PolicyNotFoundError(f"Policy {policy_id} not found")
    
    return pack
```

### 5.2 Rego Evaluation with Timeout

```python
import opa
import signal

def evaluate_policy(rego_code: str, input_data: dict, timeout_ms: int = 100) -> dict:
    """
    Evaluate Rego policy with timeout protection.
    """
    def timeout_handler(signum, frame):
        raise PolicyEvaluationTimeout()
    
    # Set timeout
    signal.signal(signal.SIGALRM, timeout_handler)
    signal.setitimer(signal.ITIMER_REAL, timeout_ms / 1000.0)
    
    try:
        # Initialize OPA engine
        engine = opa.OPA()
        engine.load_policy(rego_code)
        
        # Evaluate
        result = engine.evaluate(input_data)
        
        return result
    finally:
        # Cancel timeout
        signal.setitimer(signal.ITIMER_REAL, 0)
```

### 5.3 Sandbox Enforcement

```python
class SecureOPASandbox:
    """
    OPA sandbox with restricted capabilities.
    """
    ALLOWED_BUILTINS = [
        "count", "sum", "max", "min", "sort",
        "concat", "contains", "startswith", "endswith",
        "regex.match", "sprintf", "to_number"
    ]
    
    DENIED_BUILTINS = [
        "http.send",  # No external HTTP calls
        "opa.runtime",  # No runtime introspection
        "trace"  # No debug output
    ]
    
    def __init__(self):
        self.engine = opa.OPA()
        self.engine.set_builtins(self.ALLOWED_BUILTINS)
    
    def evaluate(self, rego_code: str, input_data: dict) -> dict:
        # Validate no denied builtins used
        for denied in self.DENIED_BUILTINS:
            if denied in rego_code:
                raise SecurityViolation(f"Builtin {denied} not allowed")
        
        # Evaluate in isolated context
        return self.engine.evaluate(rego_code, input_data)
```

---

## 6. NFR BUDGETS

### 6.1 Latency Budgets

| Operation | P50 | P95 | P99 | Timeout |
|-----------|-----|-----|-----|---------|
| evaluate() - cached policy | 1ms | 3ms | 10ms | 100ms |
| evaluate() - cold start | 5ms | 15ms | 30ms | 100ms |
| batchEvaluate() - 10 policies | 10ms | 30ms | 50ms | 500ms |

### 6.2 Throughput Targets

| Operation | Target TPS | Peak TPS |
|-----------|------------|----------|
| evaluate() | 50,000 | 100,000 |
| batchEvaluate() | 5,000 | 10,000 |

### 6.3 Resource Limits

**Per Instance**:
- CPU: 4 cores (request), 8 cores (limit)
- Memory: 8GB (request), 16GB (limit)
- Policy cache: 2GB max

**Horizontal Scaling**:
- Min replicas: 3
- Max replicas: 30
- Scale-up trigger: CPU > 70% or P99 latency > 20ms

---

## 7. SECURITY DESIGN

### 7.1 Sandbox Isolation

**OPA Sandbox Restrictions**:
- No network I/O (http.send disabled)
- No file system access
- No external process execution
- CPU time limit: 100ms per evaluation
- Memory limit: 100MB per evaluation

**Enforcement**:
```python
# OPA configuration
{
  "capabilities": {
    "builtins": [
      {"name": "count"},
      {"name": "sum"},
      # ... allowed builtins only
    ]
  },
  "limits": {
    "max_execution_time_ms": 100,
    "max_memory_bytes": 104857600
  }
}
```

### 7.2 Code Signing for Rule Packs

```python
def sign_rule_pack(pack_id: str, rego_code: str, private_key: bytes) -> str:
    """
    Sign rule pack with Ed25519 signature.
    """
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
    
    key = Ed25519PrivateKey.from_private_bytes(private_key)
    message = f"{pack_id}:{rego_code}".encode()
    signature = key.sign(message)
    
    return signature.hex()

def verify_rule_pack(pack_id: str, rego_code: str, signature: str, public_key: bytes) -> bool:
    """
    Verify rule pack signature.
    """
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey
    
    key = Ed25519PublicKey.from_public_bytes(public_key)
    message = f"{pack_id}:{rego_code}".encode()
    
    try:
        key.verify(bytes.fromhex(signature), message)
        return True
    except:
        return False
```

---

## 8. OBSERVABILITY & AUDIT

### 8.1 Metrics

```yaml
metrics:
  - name: rules_evaluation_latency_seconds
    type: histogram
    labels: [policy_id, jurisdiction, decision]
    buckets: [0.001, 0.005, 0.01, 0.05, 0.1]
  
  - name: rules_evaluation_total
    type: counter
    labels: [policy_id, jurisdiction, result]
  
  - name: rules_deny_rate
    type: gauge
    labels: [policy_id, jurisdiction]
  
  - name: rules_pack_reload_total
    type: counter
    labels: [policy_id, jurisdiction, status]
```

### 8.2 Structured Logs

```json
{
  "timestamp": "2025-03-02T10:30:00.123Z",
  "level": "INFO",
  "service": "rules-engine",
  "trace_id": "abc-123",
  "action": "POLICY_EVALUATED",
  "policy_id": "order_validation",
  "policy_version": "np_order_validation_v2.1.0",
  "decision": "DENY",
  "evaluation_time_ms": 3.2,
  "tenant_id": "tenant_np_1"
}
```

### 8.3 Audit Events

```json
{
  "audit_id": "uuid",
  "timestamp_gregorian": "2025-03-02T10:30:00Z",
  "timestamp_bs": "2081-11-17",
  "action": "POLICY_DECISION",
  "policy_id": "order_validation",
  "policy_version": "np_order_validation_v2.1.0",
  "input_hash": "sha256:abc123...",
  "decision": {
    "allow": false,
    "deny": ["Quantity must be multiple of 10"]
  },
  "tenant_id": "tenant_np_1",
  "trace_id": "abc-123"
}
```

---

## 9. EXTENSIBILITY & EVOLUTION

### 9.1 Custom Rego Functions

```rego
# Custom function example (registered via plugin)
package custom.functions

# Check if date is trading day
is_trading_day(date) {
  # Call external service via allowed builtin
  calendar_data := data.trading_calendar[date]
  calendar_data.is_trading_day == true
}
```

### 9.2 Policy Versioning

**Semantic Versioning**:
- Major: Breaking changes (e.g., remove rule)
- Minor: Additive changes (e.g., new rule)
- Patch: Bug fixes (e.g., fix regex)

**Example**: `np_order_validation_v2.1.3`

### 9.3 Backward Compatibility

**Allowed Changes**:
- ✅ Add new deny rules (more restrictive)
- ✅ Add new warning rules
- ✅ Add metadata fields

**Prohibited Changes**:
- ❌ Remove existing deny rules (less restrictive)
- ❌ Change rule semantics without version bump

---

## 10. TEST PLAN

### 10.1 Unit Tests

```python
class TestPolicyEvaluation:
    def test_deny_invalid_lot_size(self):
        result = evaluate_policy(
            policy_id="order_validation",
            input={
                "order": {"quantity": 5, "instrument_id": "NABIL"},
                "jurisdiction": "NP"
            }
        )
        assert result.allow == False
        assert "multiple of 10" in result.deny[0]
    
    def test_allow_valid_order(self):
        result = evaluate_policy(
            policy_id="order_validation",
            input={
                "order": {"quantity": 10, "instrument_id": "NABIL"},
                "jurisdiction": "NP"
            }
        )
        assert result.allow == True
        assert len(result.deny) == 0
```

### 10.2 Integration Tests

```python
class TestRulePackDeployment:
    @pytest.mark.integration
    def test_hot_reload_propagation(self):
        # Deploy new rule pack
        response = admin_client.post("/api/v1/rules/packs", json={
            "pack_id": "np_order_validation_v2.2.0",
            "rego_code": "...",
            "maker_id": "maker_1"
        })
        
        # Approve
        admin_client.put(f"/api/v1/rules/packs/{response.json()['pack_id']}/approve", 
                        json={"checker_id": "checker_1"})
        
        # Wait for activation
        time.sleep(2)
        
        # Verify new policy active
        result = rules_client.evaluate(
            policy_id="order_validation",
            input={"order": {...}},
            tenant_id="tenant_np_1"
        )
        
        assert result.policy_version == "np_order_validation_v2.2.0"
```

### 10.3 Security Tests

```python
class TestSandboxSecurity:
    def test_http_send_blocked(self):
        malicious_rego = """
        package test
        deny[msg] {
          response := http.send({"method": "GET", "url": "http://evil.com"})
          msg := "blocked"
        }
        """
        
        with pytest.raises(SecurityViolation):
            evaluate_policy("test", malicious_rego, {})
    
    def test_execution_timeout(self):
        infinite_loop_rego = """
        package test
        deny[msg] {
          count([x | x := numbers.range(1, 999999999)])
          msg := "timeout"
        }
        """
        
        with pytest.raises(PolicyEvaluationTimeout):
            evaluate_policy("test", infinite_loop_rego, {})
```

---

## 11. VALIDATION QUESTIONS & ASSUMPTIONS

### Assumptions

1. **[ASSUMPTION]** Policy evaluation is synchronous (< 100ms)
   - **Validation**: Are there long-running policy evaluations?
   - **Impact**: May need async evaluation queue

2. **[ASSUMPTION]** Rule packs are < 1MB
   - **Validation**: Are there large rule sets?
   - **Impact**: May need chunked loading

3. **[ASSUMPTION]** Jurisdiction-to-rule-pack mapping is 1:1
   - **Validation**: Can multiple jurisdictions share rule packs?
   - **Impact**: May need rule pack inheritance

---

**END OF LLD: K-03 POLICY/RULES ENGINE**
