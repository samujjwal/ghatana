# Architecture & Design Documentation Suite for Project Siddhanta
## Part 1: Sections 4-5

**Document Version:** 2.1  
**Date:** March 5, 2026  
**Status:** Implementation-Ready (Post-ARB Remediation)  
**Change Log:** v2.1 reconciles configuration hierarchy with LLD K-02 (5-level: GLOBAL→JURISDICTION→TENANT→USER→SESSION), adds T1/T2/T3 plugin tier taxonomy, cryptographic signing, and air-gap support

---

## Table of Contents - Part 1 (Sections 4-5)
4. [Configuration Resolution Architecture](#4-configuration-resolution-architecture)
5. [Plugin Runtime Architecture](#5-plugin-runtime-architecture)

---

## 4. Configuration Resolution Architecture

### 4.1 Overview

The Configuration Resolution Architecture provides a **hierarchical, environment-aware, and dynamic configuration management system** that supports:
- Multi-environment deployments (dev, staging, production)
- Feature flags for gradual rollouts
- Runtime configuration updates without redeployment
- Secrets management and encryption
- Configuration versioning and audit trails
- Validation and type safety

### 4.2 Configuration Hierarchy

Configuration is resolved through two complementary hierarchies:

#### 4.2.1 Infrastructure Precedence (Service-Level)
Infrastructure configuration for service deployment, resolved with increasing precedence:

```
Default Config (Lowest Priority)
    ↓
Environment Config (dev/staging/prod)
    ↓
Service-Specific Config
    ↓
Feature Flags
    ↓
Runtime Overrides
    ↓
Environment Variables (Highest Priority)
```

#### 4.2.2 Business Configuration Hierarchy (Domain-Level)
Business rules and operational parameters, resolved per LLD K-02's 5-level override model:

```
GLOBAL (Platform-wide defaults)
    ↓
JURISDICTION (Country/regulator-specific — T1/T2 packs)
    ↓
TENANT (Broker/institution-specific)
    ↓
USER (Individual user preferences)
    ↓
SESSION (Ephemeral per-session overrides)
```

**Invariant**: The business hierarchy supports **dual-calendar effective dates** — every configuration change records both Gregorian and Bikram Sambat timestamps. Jurisdiction-level overrides are delivered as signed T1 Config Packs, enabling air-gapped deployments with cryptographic verification.

### 4.3 Configuration Sources

| Source | Purpose | Priority | Update Frequency |
|--------|---------|----------|------------------|
| **Default Config Files** | Base configuration | 1 (Lowest) | On deployment |
| **Environment Config** | Environment-specific settings | 2 | On deployment |
| **ConfigMap (K8s)** | Service configuration | 3 | Dynamic |
| **Secrets (Vault)** | Sensitive data | 4 | Dynamic |
| **Feature Flags** | Feature toggles | 5 | Real-time |
| **Environment Variables** | Override settings | 6 (Highest) | On pod restart |

### 4.4 Configuration Schema

**Base Configuration Structure**:
```typescript
interface ServiceConfiguration {
  service: {
    name: string;
    version: string;
    environment: 'development' | 'staging' | 'production';
    port: number;
    logLevel: 'debug' | 'info' | 'warn' | 'error';
  };
  
  database: {
    host: string;
    port: number;
    name: string;
    username: string;
    password: string;  // From Vault
    poolSize: number;
    connectionTimeout: number;
    ssl: boolean;
  };
  
  kafka: {
    brokers: string[];
    clientId: string;
    groupId: string;
    topics: {
      [key: string]: {
        partitions: number;
        replicationFactor: number;
      };
    };
  };
  
  redis: {
    host: string;
    port: number;
    password: string;  // From Vault
    db: number;
    ttl: number;
  };
  
  api: {
    rateLimit: {
      windowMs: number;
      maxRequests: number;
    };
    timeout: number;
    cors: {
      origins: string[];
      credentials: boolean;
    };
  };
  
  security: {
    jwtSecret: string;  // From Vault
    jwtExpiration: number;
    encryptionKey: string;  // From Vault
  };
  
  observability: {
    metrics: {
      enabled: boolean;
      port: number;
      path: string;
    };
    tracing: {
      enabled: boolean;
      endpoint: string;
      sampleRate: number;
    };
    logging: {
      format: 'json' | 'text';
      destination: 'stdout' | 'file' | 'elasticsearch';
    };
  };
  
  featureFlags: {
    [key: string]: boolean | number | string;
  };
}
```

### 4.5 Configuration Loading Strategy

**Initialization Flow**:
```typescript
class ConfigurationManager {
  private config: ServiceConfiguration;
  private vaultClient: VaultClient;
  private featureFlagClient: FeatureFlagClient;
  private watchers: ConfigWatcher[] = [];
  
  async initialize(): Promise<void> {
    // Step 1: Load default configuration
    const defaultConfig = await this.loadDefaultConfig();
    
    // Step 2: Load environment-specific configuration
    const envConfig = await this.loadEnvironmentConfig();
    
    // Step 3: Merge configurations
    let mergedConfig = this.merge(defaultConfig, envConfig);
    
    // Step 4: Load secrets from Vault
    mergedConfig = await this.injectSecrets(mergedConfig);
    
    // Step 5: Apply environment variable overrides
    mergedConfig = this.applyEnvironmentOverrides(mergedConfig);
    
    // Step 6: Load feature flags
    const featureFlags = await this.featureFlagClient.getAll();
    mergedConfig.featureFlags = featureFlags;
    
    // Step 7: Validate configuration
    this.validate(mergedConfig);
    
    // Step 8: Store final configuration
    this.config = mergedConfig;
    
    // Step 9: Start watchers for dynamic updates
    await this.startWatchers();
    
    logger.info('Configuration initialized successfully', {
      service: this.config.service.name,
      environment: this.config.service.environment,
      version: this.config.service.version
    });
  }
  
  private async loadDefaultConfig(): Promise<Partial<ServiceConfiguration>> {
    const configPath = path.join(__dirname, '../config/default.yaml');
    const content = await fs.readFile(configPath, 'utf-8');
    return yaml.parse(content);
  }
  
  private async loadEnvironmentConfig(): Promise<Partial<ServiceConfiguration>> {
    const env = process.env.NODE_ENV || 'development';
    const configPath = path.join(__dirname, `../config/${env}.yaml`);
    
    if (!await fs.pathExists(configPath)) {
      logger.warn(`Environment config not found: ${configPath}`);
      return {};
    }
    
    const content = await fs.readFile(configPath, 'utf-8');
    return yaml.parse(content);
  }
  
  private merge(
    base: Partial<ServiceConfiguration>,
    override: Partial<ServiceConfiguration>
  ): ServiceConfiguration {
    return deepMerge(base, override);
  }
  
  private async injectSecrets(
    config: ServiceConfiguration
  ): Promise<ServiceConfiguration> {
    const secretPaths = this.extractSecretPaths(config);
    
    for (const secretPath of secretPaths) {
      const secretValue = await this.vaultClient.read(secretPath);
      this.setValueAtPath(config, secretPath, secretValue);
    }
    
    return config;
  }
  
  private applyEnvironmentOverrides(
    config: ServiceConfiguration
  ): ServiceConfiguration {
    // Support environment variables in format: SERVICE_DATABASE_HOST
    const envPrefix = 'SERVICE_';
    
    for (const [key, value] of Object.entries(process.env)) {
      if (key.startsWith(envPrefix)) {
        const configPath = key
          .substring(envPrefix.length)
          .toLowerCase()
          .split('_')
          .join('.');
        
        this.setValueAtPath(config, configPath, value);
      }
    }
    
    return config;
  }
  
  private validate(config: ServiceConfiguration): void {
    const schema = Joi.object({
      service: Joi.object({
        name: Joi.string().required(),
        version: Joi.string().required(),
        environment: Joi.string().valid('development', 'staging', 'production').required(),
        port: Joi.number().port().required(),
        logLevel: Joi.string().valid('debug', 'info', 'warn', 'error').required()
      }).required(),
      
      database: Joi.object({
        host: Joi.string().required(),
        port: Joi.number().port().required(),
        name: Joi.string().required(),
        username: Joi.string().required(),
        password: Joi.string().required(),
        poolSize: Joi.number().min(1).max(100).required(),
        connectionTimeout: Joi.number().min(1000).required(),
        ssl: Joi.boolean().required()
      }).required(),
      
      // ... additional validation rules
    });
    
    const { error } = schema.validate(config);
    
    if (error) {
      throw new ConfigurationValidationError(
        `Configuration validation failed: ${error.message}`
      );
    }
  }
  
  private async startWatchers(): Promise<void> {
    // Watch for ConfigMap changes
    const configMapWatcher = new ConfigMapWatcher(this.config.service.name);
    configMapWatcher.on('change', async (newConfig) => {
      await this.handleConfigUpdate(newConfig);
    });
    this.watchers.push(configMapWatcher);
    
    // Watch for feature flag changes
    const featureFlagWatcher = new FeatureFlagWatcher();
    featureFlagWatcher.on('change', async (flags) => {
      this.config.featureFlags = flags;
      this.notifyConfigChange('featureFlags', flags);
    });
    this.watchers.push(featureFlagWatcher);
    
    // Start all watchers
    await Promise.all(this.watchers.map(w => w.start()));
  }
  
  private async handleConfigUpdate(newConfig: Partial<ServiceConfiguration>): Promise<void> {
    logger.info('Configuration update detected');
    
    // Merge with existing config
    const updatedConfig = this.merge(this.config, newConfig);
    
    // Validate
    this.validate(updatedConfig);
    
    // Apply update
    const oldConfig = this.config;
    this.config = updatedConfig;
    
    // Notify listeners
    this.notifyConfigChange('full', { oldConfig, newConfig: updatedConfig });
  }
  
  get<T = any>(path: string, defaultValue?: T): T {
    return this.getValueAtPath(this.config, path) ?? defaultValue;
  }
  
  getAll(): ServiceConfiguration {
    return { ...this.config };
  }
  
  isFeatureEnabled(featureName: string): boolean {
    return this.config.featureFlags[featureName] === true;
  }
  
  getFeatureValue<T = any>(featureName: string, defaultValue?: T): T {
    return (this.config.featureFlags[featureName] as T) ?? defaultValue;
  }
}
```

### 4.6 Secrets Management with HashiCorp Vault

**Vault Integration**:
```typescript
class VaultClient {
  private client: vault.Client;
  
  constructor(config: VaultConfig) {
    this.client = vault({
      apiVersion: 'v1',
      endpoint: config.endpoint,
      token: config.token
    });
  }
  
  async read(path: string): Promise<string> {
    try {
      const result = await this.client.read(path);
      return result.data.value;
    } catch (error) {
      logger.error('Failed to read secret from Vault', {
        path,
        error: error.message
      });
      throw new VaultReadError(`Failed to read secret: ${path}`);
    }
  }
  
  async write(path: string, value: string): Promise<void> {
    try {
      await this.client.write(path, { value });
      logger.info('Secret written to Vault', { path });
    } catch (error) {
      logger.error('Failed to write secret to Vault', {
        path,
        error: error.message
      });
      throw new VaultWriteError(`Failed to write secret: ${path}`);
    }
  }
  
  async delete(path: string): Promise<void> {
    try {
      await this.client.delete(path);
      logger.info('Secret deleted from Vault', { path });
    } catch (error) {
      logger.error('Failed to delete secret from Vault', {
        path,
        error: error.message
      });
      throw new VaultDeleteError(`Failed to delete secret: ${path}`);
    }
  }
  
  async renewToken(): Promise<void> {
    try {
      await this.client.tokenRenewSelf();
      logger.info('Vault token renewed successfully');
    } catch (error) {
      logger.error('Failed to renew Vault token', {
        error: error.message
      });
      throw new VaultTokenRenewalError('Failed to renew token');
    }
  }
}

// Vault Secret Paths
const VAULT_PATHS = {
  DATABASE_PASSWORD: 'secret/data/order-service/database/password',
  JWT_SECRET: 'secret/data/order-service/security/jwt-secret',
  ENCRYPTION_KEY: 'secret/data/order-service/security/encryption-key',
  REDIS_PASSWORD: 'secret/data/order-service/redis/password',
  API_KEY: 'secret/data/order-service/external/api-key'
};
```

### 4.7 Feature Flags

**Feature Flag Service**:
```typescript
interface FeatureFlag {
  name: string;
  enabled: boolean;
  value?: any;
  rolloutPercentage?: number;
  targetUsers?: string[];
  targetEnvironments?: string[];
  createdAt: Date;
  updatedAt: Date;
}

class FeatureFlagService {
  private flags: Map<string, FeatureFlag> = new Map();
  private redis: Redis;
  
  constructor(redis: Redis) {
    this.redis = redis;
  }
  
  async initialize(): Promise<void> {
    // Load all feature flags from Redis
    const keys = await this.redis.keys('feature-flag:*');
    
    for (const key of keys) {
      const flagData = await this.redis.get(key);
      const flag: FeatureFlag = JSON.parse(flagData);
      this.flags.set(flag.name, flag);
    }
    
    logger.info('Feature flags initialized', {
      count: this.flags.size
    });
  }
  
  isEnabled(flagName: string, context?: EvaluationContext): boolean {
    const flag = this.flags.get(flagName);
    
    if (!flag) {
      logger.warn('Feature flag not found', { flagName });
      return false;
    }
    
    if (!flag.enabled) {
      return false;
    }
    
    // Check environment targeting
    if (flag.targetEnvironments && flag.targetEnvironments.length > 0) {
      if (!flag.targetEnvironments.includes(process.env.NODE_ENV)) {
        return false;
      }
    }
    
    // Check user targeting
    if (context?.userId && flag.targetUsers && flag.targetUsers.length > 0) {
      if (!flag.targetUsers.includes(context.userId)) {
        return false;
      }
    }
    
    // Check rollout percentage
    if (flag.rolloutPercentage !== undefined && flag.rolloutPercentage < 100) {
      if (context?.userId) {
        const hash = this.hashUserId(context.userId, flagName);
        const bucket = hash % 100;
        return bucket < flag.rolloutPercentage;
      }
      return false;
    }
    
    return true;
  }
  
  getValue<T = any>(flagName: string, defaultValue: T): T {
    const flag = this.flags.get(flagName);
    return (flag?.value as T) ?? defaultValue;
  }
  
  async setFlag(flag: FeatureFlag): Promise<void> {
    this.flags.set(flag.name, flag);
    await this.redis.set(
      `feature-flag:${flag.name}`,
      JSON.stringify(flag)
    );
    
    logger.info('Feature flag updated', {
      name: flag.name,
      enabled: flag.enabled
    });
  }
  
  private hashUserId(userId: string, flagName: string): number {
    const hash = crypto
      .createHash('md5')
      .update(`${userId}:${flagName}`)
      .digest('hex');
    return parseInt(hash.substring(0, 8), 16);
  }
}

interface EvaluationContext {
  userId?: string;
  environment?: string;
  attributes?: Record<string, any>;
}
```

**Usage Example**:
```typescript
class OrderService {
  constructor(
    private config: ConfigurationManager,
    private featureFlags: FeatureFlagService
  ) {}
  
  async placeOrder(order: OrderRequest): Promise<OrderResponse> {
    // Check if new order validation is enabled
    if (this.featureFlags.isEnabled('enhanced-order-validation')) {
      await this.enhancedValidation(order);
    } else {
      await this.standardValidation(order);
    }
    
    // Get smart routing threshold from feature flag
    const smartRoutingThreshold = this.featureFlags.getValue(
      'smart-routing-threshold',
      10000
    );
    
    if (order.quantity > smartRoutingThreshold) {
      return await this.smartRouting(order);
    }
    
    return await this.standardRouting(order);
  }
}
```

### 4.8 Configuration Versioning

**Version Control for Configurations**:
```typescript
interface ConfigurationVersion {
  version: number;
  config: ServiceConfiguration;
  createdBy: string;
  createdAt: Date;
  description: string;
  tags: string[];
}

class ConfigurationVersionStore {
  private db: Database;
  
  async saveVersion(
    serviceName: string,
    config: ServiceConfiguration,
    metadata: {
      createdBy: string;
      description: string;
      tags?: string[];
    }
  ): Promise<number> {
    const result = await this.db.query(`
      INSERT INTO configuration_versions (
        service_name, version, config_data, created_by, description, tags, created_at
      )
      SELECT 
        $1,
        COALESCE(MAX(version), 0) + 1,
        $2,
        $3,
        $4,
        $5,
        NOW()
      FROM configuration_versions
      WHERE service_name = $1
      RETURNING version
    `, [
      serviceName,
      JSON.stringify(config),
      metadata.createdBy,
      metadata.description,
      metadata.tags || []
    ]);
    
    return result.rows[0].version;
  }
  
  async getVersion(
    serviceName: string,
    version: number
  ): Promise<ConfigurationVersion> {
    const result = await this.db.query(`
      SELECT version, config_data, created_by, created_at, description, tags
      FROM configuration_versions
      WHERE service_name = $1 AND version = $2
    `, [serviceName, version]);
    
    if (result.rows.length === 0) {
      throw new ConfigurationVersionNotFoundError(serviceName, version);
    }
    
    const row = result.rows[0];
    return {
      version: row.version,
      config: JSON.parse(row.config_data),
      createdBy: row.created_by,
      createdAt: row.created_at,
      description: row.description,
      tags: row.tags
    };
  }
  
  async getLatestVersion(serviceName: string): Promise<ConfigurationVersion> {
    const result = await this.db.query(`
      SELECT version, config_data, created_by, created_at, description, tags
      FROM configuration_versions
      WHERE service_name = $1
      ORDER BY version DESC
      LIMIT 1
    `, [serviceName]);
    
    if (result.rows.length === 0) {
      throw new ConfigurationVersionNotFoundError(serviceName);
    }
    
    const row = result.rows[0];
    return {
      version: row.version,
      config: JSON.parse(row.config_data),
      createdBy: row.created_by,
      createdAt: row.created_at,
      description: row.description,
      tags: row.tags
    };
  }
  
  async rollback(serviceName: string, targetVersion: number): Promise<void> {
    const configVersion = await this.getVersion(serviceName, targetVersion);
    
    // Create a new version with the rolled-back config
    await this.saveVersion(serviceName, configVersion.config, {
      createdBy: 'system',
      description: `Rollback to version ${targetVersion}`,
      tags: ['rollback']
    });
    
    logger.info('Configuration rolled back', {
      serviceName,
      targetVersion
    });
  }
}
```

### 4.9 Kubernetes ConfigMap Integration

**ConfigMap Structure**:
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: order-service-config
  namespace: trading
data:
  application.yaml: |
    service:
      name: order-service
      version: 1.0.0
      environment: production
      port: 8080
      logLevel: info
    
    database:
      host: postgres-primary.database.svc.cluster.local
      port: 5432
      name: trading_db
      poolSize: 20
      connectionTimeout: 5000
      ssl: true
    
    kafka:
      brokers:
        - kafka-0.kafka-headless:9092
        - kafka-1.kafka-headless:9092
        - kafka-2.kafka-headless:9092
      clientId: order-service
      groupId: order-service-group
    
    api:
      rateLimit:
        windowMs: 60000
        maxRequests: 1000
      timeout: 30000
      cors:
        origins:
          - https://app.siddhanta.io
        credentials: true
```

**ConfigMap Watcher**:
```typescript
class ConfigMapWatcher extends EventEmitter {
  private k8sApi: k8s.CoreV1Api;
  private namespace: string;
  private configMapName: string;
  private watch: any;
  
  constructor(serviceName: string) {
    super();
    const kc = new k8s.KubeConfig();
    kc.loadFromDefault();
    this.k8sApi = kc.makeApiClient(k8s.CoreV1Api);
    this.namespace = process.env.NAMESPACE || 'default';
    this.configMapName = `${serviceName}-config`;
  }
  
  async start(): Promise<void> {
    const watch = new k8s.Watch(this.k8sApi.kubeConfig);
    
    const req = await watch.watch(
      `/api/v1/namespaces/${this.namespace}/configmaps`,
      {},
      (type, apiObj) => {
        if (apiObj.metadata.name === this.configMapName) {
          if (type === 'MODIFIED') {
            this.handleConfigMapUpdate(apiObj);
          }
        }
      },
      (err) => {
        if (err) {
          logger.error('ConfigMap watch error', { error: err.message });
          // Restart watch
          setTimeout(() => this.start(), 5000);
        }
      }
    );
    
    this.watch = req;
    logger.info('ConfigMap watcher started', {
      namespace: this.namespace,
      configMapName: this.configMapName
    });
  }
  
  private handleConfigMapUpdate(configMap: k8s.V1ConfigMap): void {
    try {
      const configData = configMap.data['application.yaml'];
      const newConfig = yaml.parse(configData);
      
      logger.info('ConfigMap updated', {
        configMapName: this.configMapName
      });
      
      this.emit('change', newConfig);
    } catch (error) {
      logger.error('Failed to parse ConfigMap update', {
        error: error.message
      });
    }
  }
  
  stop(): void {
    if (this.watch) {
      this.watch.abort();
      logger.info('ConfigMap watcher stopped');
    }
  }
}
```

### 4.10 Environment-Specific Configuration Examples

**Development Environment**:
```yaml
# config/development.yaml
service:
  environment: development
  logLevel: debug

database:
  host: localhost
  port: 5432
  ssl: false

kafka:
  brokers:
    - localhost:9092

observability:
  tracing:
    enabled: true
    sampleRate: 1.0  # 100% sampling in dev
```

**Production Environment**:
```yaml
# config/production.yaml
service:
  environment: production
  logLevel: info

database:
  host: postgres-primary.database.svc.cluster.local
  port: 5432
  ssl: true
  poolSize: 50

kafka:
  brokers:
    - kafka-0.kafka-headless:9092
    - kafka-1.kafka-headless:9092
    - kafka-2.kafka-headless:9092

observability:
  tracing:
    enabled: true
    sampleRate: 0.1  # 10% sampling in prod
```

---

## 5. Plugin Runtime Architecture

### 5.1 Overview

The Plugin Runtime Architecture enables **extensibility and customization** of Project Siddhanta without modifying core platform code. It implements the **Content Pack Taxonomy** — the cornerstone of jurisdiction-neutral operation — and supports:
- **T1 (Config Packs)**: Data-only packs — tax tables, dual-calendar mappings, exchange parameters, thresholds
- **T2 (Rule Packs)**: Declarative logic — OPA/Rego compliance rules, validation schemas, routing policies
- **T3 (Executable Packs)**: Signed code — exchange adapters, pricing models, settlement processors, custom algorithms
- Dynamic loading and unloading with hot-swap (zero downtime)
- Cryptographic signature verification (Ed25519) for all T3 packs
- Tier-based isolation (T1=in-process, T2=sandboxed OPA, T3=process/network isolation)
- Capability-based access control — plugins declare required permissions
- Version compatibility enforcement (semver) with graceful degradation
- Inter-plugin communication via event bus (K-05)
- Maker-checker approval for T2/T3 pack deployment in production

**Invariant**: Nepal is the first instantiation. The NEPSE exchange adapter, SEBON compliance rules, and NRB KYC/AML rules are all delivered as packs — zero jurisdiction logic in platform core.

### 5.2 Plugin Tier Model (T1/T2/T3)

| Tier | Type | Isolation | Signing | Approval | Hot-Swap | Examples |
|------|------|-----------|---------|----------|----------|----------|
| **T1** | Config Pack | In-process (data only) | Optional | Auto for dev, maker-checker for prod | Yes | Tax rate tables, BS↔AD calendar mappings, circuit breaker thresholds, exchange trading hours |
| **T2** | Rule Pack | Sandboxed OPA/Rego | Required (SHA-256) | Maker-checker | Yes | SEBON margin rules, NRB KYC validation, pre-trade compliance checks, settlement eligibility |
| **T3** | Executable Pack | Process/network isolation | Required (Ed25519) | Maker-checker + security review | Yes (graceful drain) | NEPSE FIX adapter, pricing model, custom algo strategies, settlement processor |

### 5.3 Plugin Types

| Plugin Type | Purpose | Examples |
|-------------|---------|----------|
| **Order Validators** | Custom order validation logic | Sector-specific rules, client-specific limits |
| **Execution Strategies** | Custom execution algorithms | TWAP, VWAP, iceberg orders |
| **Risk Calculators** | Custom risk metrics | VaR, stress testing, scenario analysis |
| **Market Data Processors** | Custom data transformations | Derived indicators, custom analytics |
| **Corporate Action Handlers** | Custom CA processing | Complex dividend schemes, custom entitlements |
| **Compliance Checkers** | Custom compliance rules | Internal policies, client-specific requirements |
| **Notification Handlers** | Custom notification channels | SMS, WhatsApp, custom integrations |
| **Report Generators** | Custom report formats | Client-specific reports, regulatory formats |

### 5.3 Plugin Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Plugin Registry                         │
│  (Metadata, Versions, Dependencies, Permissions)            │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                    Plugin Loader                             │
│  (Discovery, Loading, Validation, Dependency Resolution)    │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                    Plugin Sandbox                            │
│  (Isolated Execution, Resource Limits, Security)            │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                    Plugin Lifecycle Manager                  │
│  (Install, Enable, Disable, Uninstall, Update)              │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                    Plugin Communication Bus                  │
│  (Events, RPC, Shared State)                                │
└─────────────────────────────────────────────────────────────┘
```

### 5.4 Plugin Manifest

**Plugin Descriptor** (`plugin.json`):
```json
{
  "name": "advanced-order-validator",
  "version": "1.2.0",
  "description": "Advanced order validation with sector-specific rules",
  "author": "Siddhanta Team",
  "license": "MIT",
  
  "type": "order-validator",
  "entryPoint": "dist/index.js",
  
  "dependencies": {
    "@siddhanta/plugin-sdk": "^2.0.0",
    "lodash": "^4.17.21"
  },
  
  "peerDependencies": {
    "@siddhanta/core": "^3.0.0"
  },
  
  "permissions": [
    "order:read",
    "order:validate",
    "client:read",
    "instrument:read"
  ],
  
  "configuration": {
    "schema": {
      "type": "object",
      "properties": {
        "maxOrderValue": {
          "type": "number",
          "description": "Maximum order value in base currency",
          "default": 10000000
        },
        "restrictedSectors": {
          "type": "array",
          "items": { "type": "string" },
          "description": "List of restricted sectors",
          "default": []
        }
      }
    }
  },
  
  "hooks": {
    "onInstall": "scripts/install.js",
    "onEnable": "scripts/enable.js",
    "onDisable": "scripts/disable.js",
    "onUninstall": "scripts/uninstall.js"
  },
  
  "resources": {
    "memory": "256Mi",
    "cpu": "100m"
  },
  
  "metadata": {
    "tags": ["validation", "compliance", "risk"],
    "category": "trading",
    "homepage": "https://github.com/siddhanta/plugins/advanced-order-validator",
    "documentation": "https://docs.siddhanta.io/plugins/advanced-order-validator"
  }
}
```

### 5.5 Plugin SDK

**Base Plugin Interface**:
```typescript
// @siddhanta/plugin-sdk

export interface Plugin {
  name: string;
  version: string;
  
  initialize(context: PluginContext): Promise<void>;
  destroy(): Promise<void>;
}

export interface PluginContext {
  config: PluginConfiguration;
  logger: Logger;
  eventBus: EventBus;
  services: ServiceRegistry;
  storage: PluginStorage;
}

export interface PluginConfiguration {
  get<T = any>(key: string, defaultValue?: T): T;
  set(key: string, value: any): Promise<void>;
  getAll(): Record<string, any>;
}

export interface ServiceRegistry {
  get<T>(serviceName: string): T;
  register<T>(serviceName: string, service: T): void;
}

export interface PluginStorage {
  get(key: string): Promise<any>;
  set(key: string, value: any, ttl?: number): Promise<void>;
  delete(key: string): Promise<void>;
  keys(pattern: string): Promise<string[]>;
}
```

**Order Validator Plugin Interface**:
```typescript
export interface OrderValidatorPlugin extends Plugin {
  validate(order: Order, context: ValidationContext): Promise<ValidationResult>;
}

export interface ValidationContext {
  client: Client;
  instrument: Instrument;
  portfolio: Portfolio;
  marketData: MarketData;
}

export interface ValidationResult {
  isValid: boolean;
  errors?: ValidationError[];
  warnings?: ValidationWarning[];
  metadata?: Record<string, any>;
}

export interface ValidationError {
  code: string;
  message: string;
  field?: string;
  severity: 'error' | 'critical';
}
```

**Example Plugin Implementation**:
```typescript
// advanced-order-validator/src/index.ts

import { OrderValidatorPlugin, PluginContext, ValidationResult } from '@siddhanta/plugin-sdk';

export default class AdvancedOrderValidator implements OrderValidatorPlugin {
  name = 'advanced-order-validator';
  version = '1.2.0';
  
  private context: PluginContext;
  private maxOrderValue: number;
  private restrictedSectors: string[];
  
  async initialize(context: PluginContext): Promise<void> {
    this.context = context;
    
    // Load configuration
    this.maxOrderValue = context.config.get('maxOrderValue', 10000000);
    this.restrictedSectors = context.config.get('restrictedSectors', []);
    
    context.logger.info('Advanced Order Validator initialized', {
      maxOrderValue: this.maxOrderValue,
      restrictedSectors: this.restrictedSectors
    });
    
    // Subscribe to configuration changes
    context.eventBus.on('config:updated', (newConfig) => {
      this.maxOrderValue = newConfig.maxOrderValue;
      this.restrictedSectors = newConfig.restrictedSectors;
    });
  }
  
  async validate(order: Order, context: ValidationContext): Promise<ValidationResult> {
    const errors: ValidationError[] = [];
    const warnings: ValidationWarning[] = [];
    
    // Validate order value
    const orderValue = order.quantity * order.price;
    if (orderValue > this.maxOrderValue) {
      errors.push({
        code: 'ORDER_VALUE_EXCEEDED',
        message: `Order value ${orderValue} exceeds maximum ${this.maxOrderValue}`,
        field: 'quantity',
        severity: 'error'
      });
    }
    
    // Check restricted sectors
    if (this.restrictedSectors.includes(context.instrument.sector)) {
      errors.push({
        code: 'RESTRICTED_SECTOR',
        message: `Trading in sector ${context.instrument.sector} is restricted`,
        field: 'instrumentId',
        severity: 'critical'
      });
    }
    
    // Check client-specific limits
    const clientLimit = await this.getClientLimit(context.client.id);
    if (orderValue > clientLimit) {
      errors.push({
        code: 'CLIENT_LIMIT_EXCEEDED',
        message: `Order value exceeds client limit of ${clientLimit}`,
        field: 'quantity',
        severity: 'error'
      });
    }
    
    // Check market hours
    if (!this.isMarketOpen(context.instrument.exchange)) {
      warnings.push({
        code: 'MARKET_CLOSED',
        message: `Market ${context.instrument.exchange} is currently closed`,
        severity: 'warning'
      });
    }
    
    return {
      isValid: errors.length === 0,
      errors,
      warnings,
      metadata: {
        validatedAt: new Date(),
        validatorVersion: this.version
      }
    };
  }
  
  private async getClientLimit(clientId: string): Promise<number> {
    const cached = await this.context.storage.get(`client-limit:${clientId}`);
    if (cached) return cached;
    
    const clientService = this.context.services.get<ClientService>('ClientService');
    const client = await clientService.getById(clientId);
    const limit = client.tradingLimits.maxOrderValue;
    
    await this.context.storage.set(`client-limit:${clientId}`, limit, 300);
    return limit;
  }
  
  private isMarketOpen(exchange: string): boolean {
    const now = new Date();
    const marketHours = {
      'NSE': { open: 9.25, close: 15.30 },
      'BSE': { open: 9.25, close: 15.30 }
    };
    
    const hours = marketHours[exchange];
    if (!hours) return true;
    
    const currentTime = now.getHours() + now.getMinutes() / 60;
    return currentTime >= hours.open && currentTime <= hours.close;
  }
  
  async destroy(): Promise<void> {
    this.context.logger.info('Advanced Order Validator destroyed');
  }
}
```

### 5.6 Plugin Loader

**Plugin Discovery and Loading**:
```typescript
class PluginLoader {
  private pluginRegistry: PluginRegistry;
  private loadedPlugins: Map<string, LoadedPlugin> = new Map();
  
  constructor(
    private pluginDirectory: string,
    private registry: PluginRegistry
  ) {}
  
  async discoverPlugins(): Promise<PluginManifest[]> {
    const pluginDirs = await fs.readdir(this.pluginDirectory);
    const manifests: PluginManifest[] = [];
    
    for (const dir of pluginDirs) {
      const manifestPath = path.join(this.pluginDirectory, dir, 'plugin.json');
      
      if (await fs.pathExists(manifestPath)) {
        const manifestData = await fs.readFile(manifestPath, 'utf-8');
        const manifest: PluginManifest = JSON.parse(manifestData);
        
        // Validate manifest
        this.validateManifest(manifest);
        
        manifests.push(manifest);
      }
    }
    
    return manifests;
  }
  
  async loadPlugin(pluginName: string): Promise<void> {
    // Check if already loaded
    if (this.loadedPlugins.has(pluginName)) {
      throw new PluginAlreadyLoadedError(pluginName);
    }
    
    // Get manifest from registry
    const manifest = await this.pluginRegistry.getManifest(pluginName);
    
    // Resolve dependencies
    await this.resolveDependencies(manifest);
    
    // Load plugin module
    const pluginPath = path.join(
      this.pluginDirectory,
      pluginName,
      manifest.entryPoint
    );
    
    const PluginClass = await this.loadModule(pluginPath);
    
    // Create plugin instance
    const plugin: Plugin = new PluginClass();
    
    // Create plugin context
    const context = this.createPluginContext(manifest);
    
    // Initialize plugin
    await plugin.initialize(context);
    
    // Store loaded plugin
    this.loadedPlugins.set(pluginName, {
      manifest,
      instance: plugin,
      context,
      loadedAt: new Date()
    });
    
    logger.info('Plugin loaded successfully', {
      name: pluginName,
      version: manifest.version
    });
  }
  
  async unloadPlugin(pluginName: string): Promise<void> {
    const loadedPlugin = this.loadedPlugins.get(pluginName);
    
    if (!loadedPlugin) {
      throw new PluginNotLoadedError(pluginName);
    }
    
    // Destroy plugin
    await loadedPlugin.instance.destroy();
    
    // Remove from loaded plugins
    this.loadedPlugins.delete(pluginName);
    
    logger.info('Plugin unloaded successfully', { name: pluginName });
  }
  
  async reloadPlugin(pluginName: string): Promise<void> {
    await this.unloadPlugin(pluginName);
    await this.loadPlugin(pluginName);
  }
  
  getPlugin<T extends Plugin>(pluginName: string): T {
    const loadedPlugin = this.loadedPlugins.get(pluginName);
    
    if (!loadedPlugin) {
      throw new PluginNotLoadedError(pluginName);
    }
    
    return loadedPlugin.instance as T;
  }
  
  private async resolveDependencies(manifest: PluginManifest): Promise<void> {
    for (const [dep, version] of Object.entries(manifest.dependencies || {})) {
      // Check if dependency is satisfied
      // This is a simplified version; real implementation would use semver
      logger.debug('Resolving dependency', { dependency: dep, version });
    }
  }
  
  private async loadModule(modulePath: string): Promise<any> {
    try {
      // Use dynamic import for ES modules
      const module = await import(modulePath);
      return module.default || module;
    } catch (error) {
      logger.error('Failed to load plugin module', {
        path: modulePath,
        error: error.message
      });
      throw new PluginLoadError(`Failed to load module: ${modulePath}`);
    }
  }
  
  private createPluginContext(manifest: PluginManifest): PluginContext {
    return {
      config: new PluginConfigurationImpl(manifest.name),
      logger: createLogger(manifest.name),
      eventBus: globalEventBus,
      services: globalServiceRegistry,
      storage: new PluginStorageImpl(manifest.name)
    };
  }
  
  private validateManifest(manifest: PluginManifest): void {
    const schema = Joi.object({
      name: Joi.string().required(),
      version: Joi.string().required(),
      type: Joi.string().required(),
      entryPoint: Joi.string().required(),
      permissions: Joi.array().items(Joi.string()),
      // ... additional validation
    });
    
    const { error } = schema.validate(manifest);
    
    if (error) {
      throw new InvalidPluginManifestError(
        `Invalid plugin manifest: ${error.message}`
      );
    }
  }
}
```

### 5.7 Plugin Sandbox

**Sandboxed Execution for Security**:
```typescript
class PluginSandbox {
  private vm: VM;
  private resourceLimits: ResourceLimits;
  
  constructor(manifest: PluginManifest) {
    this.resourceLimits = {
      memory: this.parseMemoryLimit(manifest.resources.memory),
      cpu: this.parseCpuLimit(manifest.resources.cpu),
      timeout: 30000  // 30 seconds
    };
    
    this.vm = new VM({
      timeout: this.resourceLimits.timeout,
      sandbox: this.createSandbox(manifest)
    });
  }
  
  async execute<T>(code: string): Promise<T> {
    try {
      const result = await this.vm.run(code);
      return result;
    } catch (error) {
      if (error.message.includes('timeout')) {
        throw new PluginTimeoutError('Plugin execution exceeded timeout');
      }
      throw new PluginExecutionError(error.message);
    }
  }
  
  private createSandbox(manifest: PluginManifest): any {
    return {
      console: {
        log: (...args) => logger.info('[Plugin]', { plugin: manifest.name, args }),
        error: (...args) => logger.error('[Plugin]', { plugin: manifest.name, args }),
        warn: (...args) => logger.warn('[Plugin]', { plugin: manifest.name, args })
      },
      
      // Provide limited access to Node.js APIs
      Buffer,
      setTimeout,
      setInterval,
      clearTimeout,
      clearInterval,
      
      // Provide plugin SDK
      require: (moduleName: string) => {
        if (this.isAllowedModule(moduleName, manifest)) {
          return require(moduleName);
        }
        throw new Error(`Module ${moduleName} is not allowed`);
      }
    };
  }
  
  private isAllowedModule(moduleName: string, manifest: PluginManifest): boolean {
    const allowedModules = [
      '@siddhanta/plugin-sdk',
      ...Object.keys(manifest.dependencies || {})
    ];
    
    return allowedModules.includes(moduleName);
  }
  
  private parseMemoryLimit(limit: string): number {
    const match = limit.match(/^(\d+)(Mi|Gi)$/);
    if (!match) throw new Error(`Invalid memory limit: ${limit}`);
    
    const value = parseInt(match[1]);
    const unit = match[2];
    
    return unit === 'Mi' ? value * 1024 * 1024 : value * 1024 * 1024 * 1024;
  }
  
  private parseCpuLimit(limit: string): number {
    const match = limit.match(/^(\d+)m$/);
    if (!match) throw new Error(`Invalid CPU limit: ${limit}`);
    
    return parseInt(match[1]) / 1000;
  }
}
```

### 5.8 Plugin Lifecycle Management

**Lifecycle States**:
```
INSTALLED → ENABLED → RUNNING → DISABLED → UNINSTALLED
```

**Lifecycle Manager**:
```typescript
class PluginLifecycleManager {
  private pluginLoader: PluginLoader;
  private pluginRegistry: PluginRegistry;
  
  async install(pluginPackage: string): Promise<void> {
    // Download and extract plugin package
    const pluginDir = await this.downloadPlugin(pluginPackage);
    
    // Load manifest
    const manifest = await this.loadManifest(pluginDir);
    
    // Validate plugin
    await this.validatePlugin(manifest);
    
    // Run install hook
    if (manifest.hooks?.onInstall) {
      await this.runHook(pluginDir, manifest.hooks.onInstall);
    }
    
    // Register plugin
    await this.pluginRegistry.register(manifest);
    
    logger.info('Plugin installed', {
      name: manifest.name,
      version: manifest.version
    });
  }
  
  async enable(pluginName: string): Promise<void> {
    const manifest = await this.pluginRegistry.getManifest(pluginName);
    
    // Run enable hook
    if (manifest.hooks?.onEnable) {
      await this.runHook(
        path.join(this.pluginLoader.pluginDirectory, pluginName),
        manifest.hooks.onEnable
      );
    }
    
    // Load plugin
    await this.pluginLoader.loadPlugin(pluginName);
    
    // Update status
    await this.pluginRegistry.updateStatus(pluginName, 'ENABLED');
    
    logger.info('Plugin enabled', { name: pluginName });
  }
  
  async disable(pluginName: string): Promise<void> {
    const manifest = await this.pluginRegistry.getManifest(pluginName);
    
    // Unload plugin
    await this.pluginLoader.unloadPlugin(pluginName);
    
    // Run disable hook
    if (manifest.hooks?.onDisable) {
      await this.runHook(
        path.join(this.pluginLoader.pluginDirectory, pluginName),
        manifest.hooks.onDisable
      );
    }
    
    // Update status
    await this.pluginRegistry.updateStatus(pluginName, 'DISABLED');
    
    logger.info('Plugin disabled', { name: pluginName });
  }
  
  async uninstall(pluginName: string): Promise<void> {
    // Disable if enabled
    const status = await this.pluginRegistry.getStatus(pluginName);
    if (status === 'ENABLED') {
      await this.disable(pluginName);
    }
    
    const manifest = await this.pluginRegistry.getManifest(pluginName);
    
    // Run uninstall hook
    if (manifest.hooks?.onUninstall) {
      await this.runHook(
        path.join(this.pluginLoader.pluginDirectory, pluginName),
        manifest.hooks.onUninstall
      );
    }
    
    // Remove plugin directory
    await fs.remove(path.join(this.pluginLoader.pluginDirectory, pluginName));
    
    // Unregister plugin
    await this.pluginRegistry.unregister(pluginName);
    
    logger.info('Plugin uninstalled', { name: pluginName });
  }
  
  async update(pluginName: string, newVersion: string): Promise<void> {
    // Download new version
    const newPluginPackage = `${pluginName}@${newVersion}`;
    
    // Disable current version
    await this.disable(pluginName);
    
    // Install new version
    await this.install(newPluginPackage);
    
    // Enable new version
    await this.enable(pluginName);
    
    logger.info('Plugin updated', {
      name: pluginName,
      version: newVersion
    });
  }
  
  private async runHook(pluginDir: string, hookScript: string): Promise<void> {
    const scriptPath = path.join(pluginDir, hookScript);
    
    if (!await fs.pathExists(scriptPath)) {
      logger.warn('Hook script not found', { path: scriptPath });
      return;
    }
    
    // Execute hook script
    await exec(`node ${scriptPath}`);
  }
}
```

### 5.9 Plugin Registry

**Plugin Metadata Storage**:
```typescript
interface PluginRegistryEntry {
  manifest: PluginManifest;
  status: 'INSTALLED' | 'ENABLED' | 'DISABLED' | 'ERROR';
  installedAt: Date;
  updatedAt: Date;
  enabledAt?: Date;
  disabledAt?: Date;
  error?: string;
}

class PluginRegistry {
  private db: Database;
  
  async register(manifest: PluginManifest): Promise<void> {
    await this.db.query(`
      INSERT INTO plugin_registry (
        name, version, type, manifest, status, installed_at, updated_at
      ) VALUES ($1, $2, $3, $4, $5, NOW(), NOW())
      ON CONFLICT (name) DO UPDATE
      SET version = $2, manifest = $4, updated_at = NOW()
    `, [
      manifest.name,
      manifest.version,
      manifest.type,
      JSON.stringify(manifest),
      'INSTALLED'
    ]);
  }
  
  async getManifest(pluginName: string): Promise<PluginManifest> {
    const result = await this.db.query(`
      SELECT manifest FROM plugin_registry WHERE name = $1
    `, [pluginName]);
    
    if (result.rows.length === 0) {
      throw new PluginNotFoundError(pluginName);
    }
    
    return JSON.parse(result.rows[0].manifest);
  }
  
  async updateStatus(
    pluginName: string,
    status: 'ENABLED' | 'DISABLED' | 'ERROR',
    error?: string
  ): Promise<void> {
    const statusField = status === 'ENABLED' ? 'enabled_at' :
                       status === 'DISABLED' ? 'disabled_at' : null;
    
    await this.db.query(`
      UPDATE plugin_registry
      SET status = $2, ${statusField ? `${statusField} = NOW(),` : ''} error = $3
      WHERE name = $1
    `, [pluginName, status, error]);
  }
  
  async listPlugins(filter?: PluginFilter): Promise<PluginRegistryEntry[]> {
    let query = 'SELECT * FROM plugin_registry WHERE 1=1';
    const params: any[] = [];
    
    if (filter?.type) {
      params.push(filter.type);
      query += ` AND type = $${params.length}`;
    }
    
    if (filter?.status) {
      params.push(filter.status);
      query += ` AND status = $${params.length}`;
    }
    
    const result = await this.db.query(query, params);
    
    return result.rows.map(row => ({
      manifest: JSON.parse(row.manifest),
      status: row.status,
      installedAt: row.installed_at,
      updatedAt: row.updated_at,
      enabledAt: row.enabled_at,
      disabledAt: row.disabled_at,
      error: row.error
    }));
  }
}
```

### 5.10 Plugin Communication

**Inter-Plugin Communication via Event Bus**:
```typescript
class PluginEventBus {
  private eventEmitter: EventEmitter;
  private subscriptions: Map<string, Set<string>> = new Map();
  
  subscribe(pluginName: string, eventType: string, handler: EventHandler): void {
    this.eventEmitter.on(eventType, handler);
    
    if (!this.subscriptions.has(eventType)) {
      this.subscriptions.set(eventType, new Set());
    }
    this.subscriptions.get(eventType).add(pluginName);
    
    logger.debug('Plugin subscribed to event', {
      plugin: pluginName,
      eventType
    });
  }
  
  publish(eventType: string, data: any, source: string): void {
    logger.debug('Plugin event published', {
      eventType,
      source,
      subscribers: this.subscriptions.get(eventType)?.size || 0
    });
    
    this.eventEmitter.emit(eventType, {
      type: eventType,
      data,
      source,
      timestamp: new Date()
    });
  }
  
  unsubscribe(pluginName: string, eventType: string): void {
    this.subscriptions.get(eventType)?.delete(pluginName);
    
    logger.debug('Plugin unsubscribed from event', {
      plugin: pluginName,
      eventType
    });
  }
}
```

---

## Summary

This document (Part 1, Sections 4-5) covers:

4. **Configuration Resolution Architecture**: Hierarchical configuration management with multiple sources (default, environment, ConfigMap, Vault, feature flags), dynamic updates, secrets management, versioning, and Kubernetes integration.

5. **Plugin Runtime Architecture**: Extensible plugin system with dynamic loading, sandboxed execution, lifecycle management, plugin SDK, registry, and inter-plugin communication.

**Completed**: Part 1 (Sections 1-5)

**Next**: Part 2 (Sections 6-10): AI Governance, Data Architecture, Deployment Architecture, Security Architecture, and Observability Architecture.
