# LOW-LEVEL DESIGN: K-04 PLUGIN RUNTIME & SDK

**Module**: K-04 Plugin Runtime & SDK  
**Layer**: Kernel  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Platform Core Team

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

The Plugin Runtime provides a **secure, versioned extension mechanism** allowing third-party and internal plugins to extend Project Siddhanta's capabilities without modifying core code.

**Core Responsibilities**:
- Plugin registration and lifecycle management
- Cryptographic verification of plugin signatures
- Capability-based access control (T1/T2/T3 isolation)
- Version compatibility enforcement
- Hot-swap plugin updates without downtime
- Graceful degradation when plugins fail
- SDK for plugin development

**Invariants**:
1. All plugins MUST be cryptographically signed
2. Plugin execution MUST be isolated per tier (T1=process, T2=sandbox, T3=network)
3. Plugin failures MUST NOT crash host service
4. Plugin API versions MUST be backward compatible
5. Plugin state MUST be externalized (no in-memory state)

### 1.2 Explicit Non-Goals

- ❌ Plugin marketplace/discovery (future phase)
- ❌ Plugin billing/metering (delegated to separate service)
- ❌ Plugin-to-plugin direct communication (use event bus)

### 1.3 Dependencies

| Dependency | Purpose | Readiness Gate |
|------------|---------|----------------|
| K-02 Config Engine | Plugin configuration storage | K-02 stable |
| K-05 Event Bus | Plugin lifecycle events | K-05 stable |
| K-07 Audit Framework | Plugin action audit trail | K-07 stable |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 REST API Endpoints

```yaml
POST /api/v1/plugins/register
Authorization: Bearer {admin_token}
Content-Type: multipart/form-data

Request:
{
  "plugin_id": "np_settlement_calculator",
  "version": "1.2.0",
  "tier": "T2",
  "capabilities": ["SETTLEMENT_CALCULATION"],
  "manifest": {
    "name": "Nepal Settlement Calculator",
    "description": "Calculates settlement amounts for NEPSE trades",
    "author": "vendor@example.com",
    "sdk_version": "1.0.0",
    "entry_point": "index.js",
    "dependencies": {
      "lodash": "^4.17.21"
    }
  },
  "artifact": <binary>,
  "signature": "ed25519:abc123...",
  "public_key": "ed25519:def456..."
}

Response 201:
{
  "plugin_id": "np_settlement_calculator",
  "version": "1.2.0",
  "status": "REGISTERED",
  "verification_status": "VERIFIED",
  "registered_at": "2025-03-02T10:30:00Z"
}

Response 400:
{
  "error": "SIGNATURE_VERIFICATION_FAILED",
  "message": "Plugin signature invalid",
  "code": "PLUGIN_E001"
}
```

```yaml
POST /api/v1/plugins/{plugin_id}/invoke
Authorization: Bearer {service_token}

Request:
{
  "method": "calculate_settlement",
  "params": {
    "trade_id": "TRD-123",
    "quantity": 100,
    "price": 1250.50
  },
  "timeout_ms": 5000,
  "trace_id": "abc-123"
}

Response 200:
{
  "result": {
    "settlement_amount": 125050.00,
    "fees": 125.05,
    "net_amount": 125175.05
  },
  "execution_time_ms": 23.4,
  "plugin_version": "1.2.0"
}

Response 504:
{
  "error": "PLUGIN_TIMEOUT",
  "message": "Plugin execution exceeded 5000ms",
  "code": "PLUGIN_E003"
}
```

### 2.2 gRPC Service Definition

```protobuf
syntax = "proto3";

package siddhanta.plugins.v1;

service PluginRuntimeService {
  rpc RegisterPlugin(RegisterPluginRequest) returns (RegisterPluginResponse);
  rpc InvokePlugin(InvokePluginRequest) returns (InvokePluginResponse);
  rpc ListPlugins(ListPluginsRequest) returns (ListPluginsResponse);
  rpc EnablePlugin(EnablePluginRequest) returns (EnablePluginResponse);
  rpc DisablePlugin(DisablePluginRequest) returns (DisablePluginResponse);
}

message RegisterPluginRequest {
  string plugin_id = 1;
  string version = 2;
  PluginTier tier = 3;
  repeated string capabilities = 4;
  PluginManifest manifest = 5;
  bytes artifact = 6;
  string signature = 7;
  string public_key = 8;
}

enum PluginTier {
  T1_PROCESS_ISOLATION = 0;
  T2_SANDBOX = 1;
  T3_NETWORK_ISOLATION = 2;
}

message InvokePluginRequest {
  string plugin_id = 1;
  string method = 2;
  google.protobuf.Struct params = 3;
  optional int32 timeout_ms = 4;
  string trace_id = 5;
}

message InvokePluginResponse {
  google.protobuf.Struct result = 1;
  double execution_time_ms = 2;
  string plugin_version = 3;
}
```

### 2.3 Plugin SDK Interface

```typescript
/**
 * Plugin SDK v1.0.0
 * Base interface all plugins must implement
 */
interface Plugin {
  /**
   * Plugin metadata
   */
  readonly manifest: PluginManifest;

  /**
   * Initialize plugin with runtime context
   * Called once during plugin load
   */
  initialize(context: PluginContext): Promise<void>;

  /**
   * Execute plugin method
   */
  invoke(method: string, params: unknown): Promise<unknown>;

  /**
   * Cleanup resources before plugin unload
   */
  shutdown(): Promise<void>;
}

interface PluginManifest {
  pluginId: string;
  version: string;
  sdkVersion: string;
  tier: 'T1' | 'T2' | 'T3';
  capabilities: string[];
  entryPoint: string;
  dependencies?: Record<string, string>;
}

interface PluginContext {
  /**
   * Access configuration (read-only)
   */
  config: ConfigClient;

  /**
   * Publish events
   */
  events: EventPublisher;

  /**
   * Audit actions
   */
  audit: AuditClient;

  /**
   * Logger
   */
  logger: Logger;

  /**
   * Metrics
   */
  metrics: MetricsClient;

  /**
   * Plugin metadata
   */
  metadata: {
    pluginId: string;
    version: string;
    tenantId?: string;
  };
}

/**
 * Example plugin implementation
 */
class SettlementCalculatorPlugin implements Plugin {
  manifest: PluginManifest = {
    pluginId: 'np_settlement_calculator',
    version: '1.2.0',
    sdkVersion: '1.0.0',
    tier: 'T2',
    capabilities: ['SETTLEMENT_CALCULATION'],
    entryPoint: 'index.js'
  };

  async initialize(context: PluginContext): Promise<void> {
    context.logger.info('Settlement calculator initialized');
  }

  async invoke(method: string, params: any): Promise<any> {
    if (method === 'calculate_settlement') {
      return this.calculateSettlement(params);
    }
    throw new Error(`Unknown method: ${method}`);
  }

  private async calculateSettlement(params: {
    quantity: number;
    price: number;
  }): Promise<{ settlement_amount: number; fees: number; net_amount: number }> {
    const settlementAmount = params.quantity * params.price;
    const fees = settlementAmount * 0.001; // 0.1% fee
    return {
      settlement_amount: settlementAmount,
      fees,
      net_amount: settlementAmount + fees
    };
  }

  async shutdown(): Promise<void> {
    // Cleanup
  }
}
```

### 2.4 Error Model

| Error Code | HTTP Status | Retryable | Description |
|------------|-------------|-----------|-------------|
| PLUGIN_E001 | 400 | No | Signature verification failed |
| PLUGIN_E002 | 400 | No | SDK version incompatible |
| PLUGIN_E003 | 504 | Yes | Plugin execution timeout |
| PLUGIN_E004 | 500 | Yes | Plugin crashed |
| PLUGIN_E005 | 403 | No | Capability not declared |
| PLUGIN_E006 | 404 | No | Plugin not found |
| PLUGIN_E007 | 409 | No | Plugin version conflict |

---

## 3. DATA MODEL

### 3.1 Event Schemas

#### PluginRegisteredEvent v1.0.0

> **K-05 Envelope Compliant** — all events use the standard envelope from LLD_K05_EVENT_BUS §3.1.

```json
{
  "event_id": "uuid",
  "event_type": "PluginRegistered",
  "event_version": "1.0.0",
  "aggregate_id": "np_settlement_calculator",
  "aggregate_type": "Plugin",
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
    "plugin_id": "np_settlement_calculator",
    "version": "1.2.0",
    "tier": "T2",
    "capabilities": ["SETTLEMENT_CALCULATION"],
    "registered_by": "admin_user_123"
  }
}
```

#### PluginInvokedEvent v1.0.0

```json
{
  "event_id": "uuid",
  "event_type": "PluginInvoked",
  "event_version": "1.0.0",
  "aggregate_id": "np_settlement_calculator",
  "aggregate_type": "Plugin",
  "sequence_number": 2,
  "timestamp_bs": "2081-11-17T10:30:00",
  "timestamp_gregorian": "2025-03-02T10:30:00Z",
  "metadata": {
    "trace_id": "abc-123",
    "causation_id": "invoke-cmd-uuid",
    "correlation_id": "corr-uuid",
    "tenant_id": "tenant_np_1"
  },
  "data": {
    "plugin_id": "np_settlement_calculator",
    "version": "1.2.0",
    "method": "calculate_settlement",
    "execution_time_ms": 23.4,
    "status": "SUCCESS"
  }
}
```

### 3.2 Storage Tables

#### plugins

```sql
CREATE TABLE plugins (
  plugin_id VARCHAR(255) NOT NULL,
  version VARCHAR(50) NOT NULL,
  tier VARCHAR(10) NOT NULL CHECK (tier IN ('T1', 'T2', 'T3')),
  capabilities JSONB NOT NULL,
  manifest JSONB NOT NULL,
  artifact_url VARCHAR(500) NOT NULL,
  signature VARCHAR(500) NOT NULL,
  public_key VARCHAR(500) NOT NULL,
  status VARCHAR(20) NOT NULL CHECK (status IN ('REGISTERED', 'ENABLED', 'DISABLED', 'DEPRECATED')),
  registered_by VARCHAR(255) NOT NULL,
  registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  enabled_at TIMESTAMPTZ,
  PRIMARY KEY (plugin_id, version)
);

CREATE INDEX idx_plugins_status ON plugins(status, plugin_id);
```

#### plugin_invocations

```sql
CREATE TABLE plugin_invocations (
  invocation_id UUID PRIMARY KEY,
  plugin_id VARCHAR(255) NOT NULL,
  plugin_version VARCHAR(50) NOT NULL,
  method VARCHAR(255) NOT NULL,
  execution_time_ms FLOAT NOT NULL,
  status VARCHAR(20) NOT NULL CHECK (status IN ('SUCCESS', 'FAILURE', 'TIMEOUT')),
  error_message TEXT,
  trace_id VARCHAR(255),
  tenant_id VARCHAR(255),
  timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_plugin_invocations_trace ON plugin_invocations(trace_id);
CREATE INDEX idx_plugin_invocations_plugin ON plugin_invocations(plugin_id, timestamp);
```

---

## 4. CONTROL FLOW

### 4.1 Plugin Registration Flow

```
Admin → POST /api/v1/plugins/register
  ↓
PluginCommandHandler → Verify signature with public_key
  ↓ [Signature valid]
PluginCommandHandler → Validate SDK version compatibility
  ↓ [Compatible]
PluginCommandHandler → Extract and scan artifact (malware check)
  ↓ [Clean]
PluginCommandHandler → Upload artifact to object storage
  ↓
PluginCommandHandler → Insert plugin record (status=REGISTERED)
  ↓
PluginCommandHandler → Publish PluginRegisteredEvent
  ↓
PluginCommandHandler → Return success response
```

### 4.2 Plugin Invocation Flow (T2 Sandbox)

```
Service → PluginClient.invoke(plugin_id, method, params)
  ↓
PluginClient → Resolve plugin version (latest ENABLED)
  ↓
PluginClient → Check if plugin loaded in sandbox
  ↓ [Not loaded]
PluginClient → Download artifact from object storage
  ↓
PluginClient → Initialize V8 isolate (T2 sandbox)
  ↓
PluginClient → Load plugin code into isolate
  ↓
PluginClient → Call plugin.initialize(context)
  ↓
PluginClient → Cache loaded plugin
  ↓
PluginClient → Set execution timeout (default 5s)
  ↓
PluginClient → Call plugin.invoke(method, params) in isolate
  ↓
V8 Isolate → Execute plugin code (sandboxed)
  ↓
V8 Isolate → Return result
  ↓
PluginClient → Audit invocation to K-07 (async)
  ↓
PluginClient → Publish PluginInvokedEvent
  ↓
PluginClient → Return result to caller
```

### 4.3 Hot-Swap Flow

```
Admin → PUT /api/v1/plugins/{plugin_id}/enable (new version)
  ↓
PluginCommandHandler → Update status=ENABLED for new version
  ↓
PluginCommandHandler → Update status=DEPRECATED for old version
  ↓
PluginCommandHandler → Publish PluginEnabledEvent
  ↓
All PluginClients → Receive event
  ↓
PluginClients → Mark old version as deprecated in cache
  ↓
PluginClients → Next invocation uses new version
  ↓
PluginClients → After 5 minutes, unload old version
```

---

## 5. ALGORITHMS & POLICIES

### 5.1 Signature Verification

```python
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey

def verify_plugin_signature(
    artifact: bytes,
    signature: str,
    public_key: str
) -> bool:
    """
    Verify Ed25519 signature of plugin artifact.
    """
    try:
        # Parse public key
        key_bytes = bytes.fromhex(public_key.replace('ed25519:', ''))
        public_key_obj = Ed25519PublicKey.from_public_bytes(key_bytes)
        
        # Parse signature
        sig_bytes = bytes.fromhex(signature.replace('ed25519:', ''))
        
        # Verify
        public_key_obj.verify(sig_bytes, artifact)
        return True
    except Exception as e:
        logger.error(f"Signature verification failed: {e}")
        return False
```

### 5.2 SDK Version Compatibility Check

```python
import semver

def is_sdk_compatible(plugin_sdk_version: str, runtime_sdk_version: str) -> bool:
    """
    Check if plugin SDK version is compatible with runtime.
    
    Rules:
    - Major version must match
    - Plugin minor version <= runtime minor version
    """
    plugin_ver = semver.VersionInfo.parse(plugin_sdk_version)
    runtime_ver = semver.VersionInfo.parse(runtime_sdk_version)
    
    # Major version must match
    if plugin_ver.major != runtime_ver.major:
        return False
    
    # Plugin minor version must be <= runtime minor version
    if plugin_ver.minor > runtime_ver.minor:
        return False
    
    return True
```

### 5.3 Tier Isolation Enforcement

```python
class PluginIsolation:
    """
    Enforce tier-based isolation.
    """
    
    @staticmethod
    def create_runtime(tier: str, plugin_id: str):
        if tier == 'T1':
            # Process isolation - separate process
            return ProcessIsolatedRuntime(plugin_id)
        elif tier == 'T2':
            # Sandbox - V8 isolate
            return V8SandboxRuntime(plugin_id)
        elif tier == 'T3':
            # Network isolation - separate container
            return ContainerIsolatedRuntime(plugin_id)
        else:
            raise ValueError(f"Unknown tier: {tier}")

class V8SandboxRuntime:
    """
    T2 sandbox using V8 isolates.
    """
    
    def __init__(self, plugin_id: str):
        self.plugin_id = plugin_id
        self.isolate = v8.Isolate()
        
        # Restrict capabilities
        self.isolate.set_memory_limit(100 * 1024 * 1024)  # 100MB
        self.isolate.disable_eval()
        self.isolate.disable_wasm()
        
        # Whitelist allowed APIs
        self.isolate.expose_api('console.log', self._safe_log)
        self.isolate.expose_api('fetch', self._safe_fetch)
    
    def _safe_fetch(self, url: str):
        # Only allow fetching from approved domains
        if not url.startswith('https://api.siddhanta.internal/'):
            raise SecurityError("External fetch not allowed")
        return requests.get(url)
```

### 5.4 Graceful Degradation

```python
class PluginInvoker:
    """
    Invoke plugin with fallback on failure.
    """
    
    async def invoke_with_fallback(
        self,
        plugin_id: str,
        method: str,
        params: dict,
        fallback_fn: Optional[Callable] = None
    ):
        try:
            result = await self.invoke(plugin_id, method, params)
            return result
        except PluginTimeout:
            logger.warning(f"Plugin {plugin_id} timed out, using fallback")
            if fallback_fn:
                return fallback_fn(params)
            raise
        except PluginCrashed:
            logger.error(f"Plugin {plugin_id} crashed, using fallback")
            if fallback_fn:
                return fallback_fn(params)
            raise
```

---

## 6. NFR BUDGETS

### 6.1 Latency Budgets

| Operation | P50 | P95 | P99 | Timeout |
|-----------|-----|-----|-----|---------|
| invoke() - cached plugin | 5ms | 20ms | 50ms | 5000ms |
| invoke() - cold start | 50ms | 200ms | 500ms | 5000ms |
| register() | 100ms | 500ms | 1000ms | 10000ms |

### 6.2 Throughput Targets

| Operation | Target TPS | Peak TPS |
|-----------|------------|----------|
| invoke() | 10,000 | 20,000 |
| register() | 10 | 50 |

### 6.3 Resource Limits

**Per Plugin Instance (T2)**:
- CPU: 1 core (limit)
- Memory: 100MB (limit)
- Execution timeout: 5s (default), 30s (max)

**Per Runtime Instance**:
- Max concurrent plugins: 100
- Max plugin cache size: 5GB

---

## 7. SECURITY DESIGN

### 7.1 Capability-Based Access Control

```typescript
interface PluginCapability {
  name: string;
  description: string;
  requiredPermissions: string[];
}

const CAPABILITIES: Record<string, PluginCapability> = {
  SETTLEMENT_CALCULATION: {
    name: 'SETTLEMENT_CALCULATION',
    description: 'Calculate trade settlement amounts',
    requiredPermissions: ['config:read', 'events:publish']
  },
  ORDER_ROUTING: {
    name: 'ORDER_ROUTING',
    description: 'Route orders to exchanges',
    requiredPermissions: ['config:read', 'events:publish', 'http:external']
  }
};

function checkCapability(plugin: Plugin, capability: string): void {
  if (!plugin.manifest.capabilities.includes(capability)) {
    throw new CapabilityNotDeclaredError(
      `Plugin ${plugin.manifest.pluginId} did not declare capability ${capability}`
    );
  }
}
```

### 7.2 Artifact Scanning

```python
import yara

def scan_plugin_artifact(artifact: bytes) -> ScanResult:
    """
    Scan plugin artifact for malware and suspicious patterns.
    """
    # Load YARA rules
    rules = yara.compile(filepath='/etc/siddhanta/yara/plugin_rules.yar')
    
    # Scan
    matches = rules.match(data=artifact)
    
    if matches:
        return ScanResult(
            clean=False,
            threats=[m.rule for m in matches]
        )
    
    # Check for suspicious patterns
    suspicious_patterns = [
        b'eval(',
        b'Function(',
        b'child_process',
        b'fs.writeFile'
    ]
    
    for pattern in suspicious_patterns:
        if pattern in artifact:
            return ScanResult(
                clean=False,
                threats=[f"Suspicious pattern: {pattern.decode()}"]
            )
    
    return ScanResult(clean=True, threats=[])
```

### 7.3 Network Isolation (T3)

```yaml
# Kubernetes NetworkPolicy for T3 plugins
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: plugin-t3-isolation
spec:
  podSelector:
    matchLabels:
      tier: T3
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: plugin-runtime
  egress:
  - to:
    - podSelector:
        matchLabels:
          app: api-gateway
    ports:
    - protocol: TCP
      port: 443
```

---

## 8. OBSERVABILITY & AUDIT

### 8.1 Metrics

```yaml
metrics:
  - name: plugin_invocation_latency_seconds
    type: histogram
    labels: [plugin_id, version, method, status]
    buckets: [0.005, 0.01, 0.05, 0.1, 0.5, 1.0, 5.0]
  
  - name: plugin_invocation_total
    type: counter
    labels: [plugin_id, version, method, status]
  
  - name: plugin_cache_hit_rate
    type: gauge
    labels: [plugin_id]
  
  - name: plugin_memory_usage_bytes
    type: gauge
    labels: [plugin_id, version]
  
  - name: plugin_crash_total
    type: counter
    labels: [plugin_id, version]
```

### 8.2 Structured Logs

```json
{
  "timestamp": "2025-03-02T10:30:00.123Z",
  "level": "INFO",
  "service": "plugin-runtime",
  "trace_id": "abc-123",
  "action": "PLUGIN_INVOKED",
  "plugin_id": "np_settlement_calculator",
  "plugin_version": "1.2.0",
  "method": "calculate_settlement",
  "execution_time_ms": 23.4,
  "status": "SUCCESS"
}
```

### 8.3 Audit Events

```json
{
  "audit_id": "uuid",
  "timestamp_gregorian": "2025-03-02T10:30:00Z",
  "timestamp_bs": "2081-11-17",
  "action": "PLUGIN_REGISTERED",
  "plugin_id": "np_settlement_calculator",
  "version": "1.2.0",
  "tier": "T2",
  "registered_by": "admin_user_123",
  "signature_verified": true
}
```

---

## 9. EXTENSIBILITY & EVOLUTION

### 9.1 SDK Versioning Strategy

**Semantic Versioning**:
- **Major**: Breaking API changes (e.g., remove PluginContext method)
- **Minor**: Additive changes (e.g., add new capability)
- **Patch**: Bug fixes

**Compatibility Matrix**:
| Runtime SDK | Plugin SDK | Compatible |
|-------------|------------|------------|
| 1.0.x | 1.0.x | ✅ |
| 1.1.x | 1.0.x | ✅ |
| 1.1.x | 1.1.x | ✅ |
| 2.0.x | 1.x.x | ❌ |

### 9.2 Plugin Lifecycle Hooks

```typescript
interface PluginLifecycle {
  /**
   * Called before plugin initialization
   */
  onBeforeInit?(context: PluginContext): Promise<void>;

  /**
   * Called after successful initialization
   */
  onAfterInit?(context: PluginContext): Promise<void>;

  /**
   * Called before plugin invocation
   */
  onBeforeInvoke?(method: string, params: unknown): Promise<void>;

  /**
   * Called after plugin invocation
   */
  onAfterInvoke?(method: string, result: unknown): Promise<void>;

  /**
   * Called on plugin error
   */
  onError?(error: Error): Promise<void>;

  /**
   * Called before plugin shutdown
   */
  onBeforeShutdown?(): Promise<void>;
}
```

### 9.3 Custom Capability Registration

```typescript
/**
 * Register custom capability at runtime
 */
async function registerCapability(
  capability: PluginCapability
): Promise<void> {
  // Validate capability definition
  if (!capability.name || !capability.requiredPermissions) {
    throw new Error('Invalid capability definition');
  }

  // Store in K-02 Config Engine
  await configClient.set({
    key: `capabilities/${capability.name}`,
    value: capability,
    scope: 'GLOBAL'
  });

  // Publish event
  await eventBus.publish({
    type: 'CapabilityRegistered',
    data: capability
  });
}
```

---

## 10. TEST PLAN

### 10.1 Unit Tests

```typescript
describe('PluginRuntime', () => {
  it('should verify valid plugin signature', async () => {
    const artifact = Buffer.from('plugin code');
    const { signature, publicKey } = signArtifact(artifact);

    const isValid = await verifyPluginSignature(
      artifact,
      signature,
      publicKey
    );

    expect(isValid).toBe(true);
  });

  it('should reject invalid signature', async () => {
    const artifact = Buffer.from('plugin code');
    const { publicKey } = signArtifact(artifact);
    const fakeSignature = 'ed25519:fakesig';

    const isValid = await verifyPluginSignature(
      artifact,
      fakeSignature,
      publicKey
    );

    expect(isValid).toBe(false);
  });

  it('should enforce SDK version compatibility', () => {
    expect(isSdkCompatible('1.0.0', '1.0.0')).toBe(true);
    expect(isSdkCompatible('1.0.0', '1.1.0')).toBe(true);
    expect(isSdkCompatible('1.1.0', '1.0.0')).toBe(false);
    expect(isSdkCompatible('2.0.0', '1.0.0')).toBe(false);
  });
});
```

### 10.2 Integration Tests

```typescript
describe('Plugin Invocation', () => {
  it('should invoke plugin successfully', async () => {
    // Register plugin
    await pluginClient.register({
      pluginId: 'test_calculator',
      version: '1.0.0',
      tier: 'T2',
      capabilities: ['CALCULATION'],
      artifact: testPluginArtifact,
      signature: testSignature,
      publicKey: testPublicKey
    });

    // Enable plugin
    await pluginClient.enable('test_calculator', '1.0.0');

    // Invoke
    const result = await pluginClient.invoke({
      pluginId: 'test_calculator',
      method: 'add',
      params: { a: 5, b: 3 }
    });

    expect(result).toEqual({ sum: 8 });
  });

  it('should timeout long-running plugin', async () => {
    await expect(
      pluginClient.invoke({
        pluginId: 'slow_plugin',
        method: 'slow_operation',
        params: {},
        timeoutMs: 100
      })
    ).rejects.toThrow(PluginTimeoutError);
  });
});
```

### 10.3 Security Tests

```typescript
describe('Plugin Security', () => {
  it('should block plugin from accessing filesystem', async () => {
    const maliciousPlugin = `
      const fs = require('fs');
      module.exports = {
        invoke: () => {
          fs.writeFileSync('/tmp/evil.txt', 'hacked');
        }
      };
    `;

    await expect(
      pluginClient.register({
        pluginId: 'malicious',
        artifact: Buffer.from(maliciousPlugin),
        tier: 'T2'
      })
    ).rejects.toThrow(SecurityViolationError);
  });

  it('should enforce capability checks', async () => {
    // Plugin without HTTP capability
    await pluginClient.register({
      pluginId: 'no_http',
      capabilities: ['CALCULATION']
    });

    await expect(
      pluginClient.invoke({
        pluginId: 'no_http',
        method: 'fetch_data' // Requires HTTP capability
      })
    ).rejects.toThrow(CapabilityNotDeclaredError);
  });
});
```

### 10.4 Chaos Tests

```typescript
describe('Plugin Resilience', () => {
  it('should gracefully handle plugin crash', async () => {
    const crashingPlugin = `
      module.exports = {
        invoke: () => {
          throw new Error('Crash!');
        }
      };
    `;

    await pluginClient.register({
      pluginId: 'crasher',
      artifact: Buffer.from(crashingPlugin)
    });

    const result = await pluginClient.invokeWithFallback({
      pluginId: 'crasher',
      method: 'crash',
      fallback: () => ({ status: 'fallback' })
    });

    expect(result).toEqual({ status: 'fallback' });
  });

  it('should hot-swap plugin without downtime', async () => {
    // Enable v1
    await pluginClient.enable('calculator', '1.0.0');

    // Start invoking v1 in background
    const invocations = [];
    for (let i = 0; i < 100; i++) {
      invocations.push(
        pluginClient.invoke({
          pluginId: 'calculator',
          method: 'add',
          params: { a: i, b: 1 }
        })
      );
    }

    // Enable v2 mid-flight
    await pluginClient.enable('calculator', '2.0.0');

    // All invocations should succeed
    const results = await Promise.all(invocations);
    expect(results.every(r => r !== null)).toBe(true);
  });
});
```

---

## 11. VALIDATION QUESTIONS & ASSUMPTIONS

### Assumptions

1. **[ASSUMPTION]** Plugins are stateless (no in-memory state between invocations)
   - **Validation**: Do plugins need to maintain session state?
   - **Impact**: May need plugin state management service

2. **[ASSUMPTION]** Plugin artifacts are < 50MB
   - **Validation**: Are there large ML model plugins?
   - **Impact**: May need chunked upload/download

3. **[ASSUMPTION]** Plugin invocations are synchronous (< 5s)
   - **Validation**: Are there long-running batch plugins?
   - **Impact**: May need async invocation queue

4. **[ASSUMPTION]** Ed25519 signatures are sufficient
   - **Validation**: Are other signature algorithms required?
   - **Impact**: May need multi-algorithm support

---

**END OF LLD: K-04 PLUGIN RUNTIME & SDK**
