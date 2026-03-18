/**
 * Deployment Configuration for Ghatana Platform
 *
 * Canary deployments, feature flags, and automated rollback.
 *
 * @doc.type configuration
 * @doc.purpose Deployment strategy and feature management
 * @doc.layer infrastructure
 */

// ============================================================================
// Canary Deployment Configuration
// ============================================================================

export const CANARY_CONFIG = {
  // Canary stages
  stages: [
    { name: 'baseline', traffic: 0, duration: '5m' },
    { name: 'canary-5', traffic: 0.05, duration: '10m' },
    { name: 'canary-25', traffic: 0.25, duration: '15m' },
    { name: 'canary-50', traffic: 0.5, duration: '20m' },
    { name: 'full', traffic: 1, duration: '0m' },
  ],
  
  // Success criteria for promotion
  successCriteria: {
    // Error rate must be below threshold
    errorRate: {
      threshold: 0.001, // 0.1%
      duration: '5m',
    },
    // Latency must be within bounds
    latency: {
      p99: 500, // ms
      increase: 1.2, // 20% increase allowed
      duration: '5m',
    },
    // No increase in 5xx errors
    http5xx: {
      maxCount: 10,
      duration: '5m',
    },
    // Custom health checks
    healthCheck: {
      endpoint: '/health',
      expectedStatus: 200,
      interval: '10s',
    },
  },
  
  // Automatic rollback triggers
  rollbackTriggers: [
    {
      metric: 'error_rate',
      threshold: 0.01, // 1%
      duration: '2m',
      action: 'rollback',
    },
    {
      metric: 'latency_p99',
      threshold: 2000, // 2 seconds
      duration: '3m',
      action: 'rollback',
    },
    {
      metric: 'http_5xx_rate',
      threshold: 0.05, // 5%
      duration: '1m',
      action: 'rollback',
    },
  ],
};

// ============================================================================
// Feature Flag Configuration
// ============================================================================

export const FEATURE_FLAGS = {
  // Flag providers (in priority order)
  providers: [
    {
      name: 'launchdarkly',
      enabled: true,
      sdkKey: process.env.LAUNCHDARKLY_SDK_KEY,
    },
    {
      name: 'unleash',
      enabled: false,
      url: process.env.UNLEASH_URL,
      apiToken: process.env.UNLEASH_API_TOKEN,
    },
    {
      name: 'environment',
      enabled: true, // Fallback to env vars
    },
  ],
  
  // Default flags
  defaults: {
    // Platform features
    'platform.new-ui': false,
    'platform.dark-mode': true,
    'platform.realtime-collab': true,
    
    // Product features
    'yappc.ai-assistant': true,
    'yappc.advanced-canvas': false,
    'yappc.live-preview': true,
    
    // Infrastructure features
    'infra.canary-deployments': true,
    'infra.autoscaling': true,
    'infra.circuit-breaker': true,
  },
  
  // Flag rules
  rules: {
    // User targeting
    targeting: {
      enabled: true,
      attributes: ['userId', 'email', 'role', 'team', 'plan'],
    },
    
    // Percentage rollouts
    rollouts: {
      enabled: true,
      stages: [0, 5, 25, 50, 100],
    },
    
    // Scheduled flags
    scheduling: {
      enabled: true,
      timezone: 'UTC',
    },
  },
  
  // Flag evaluation
  evaluation: {
    // Cache duration
    cacheTTL: 60, // seconds
    
    // Offline mode
    offlineMode: process.env.NODE_ENV === 'development',
    
    // Default values when provider unavailable
    fallbackToDefaults: true,
  },
};

// ============================================================================
// Blue-Green Deployment Configuration
// ============================================================================

export const BLUE_GREEN_CONFIG = {
  enabled: true,
  
  // Health check before switching traffic
  healthCheck: {
    endpoint: '/health',
    interval: '10s',
    timeout: '5s',
    retries: 3,
    successThreshold: 2,
  },
  
  // Traffic switching
  trafficSwitch: {
    // Instant switch (vs gradual for canary)
    instant: false,
    // Gradual switch duration
    duration: '5m',
  },
  
  // Cleanup
  cleanup: {
    // Keep old version for rollback
    retention: '1h',
    // Auto-cleanup after retention
    autoCleanup: true,
  },
};

// ============================================================================
// Automated Rollback Configuration
// ============================================================================

export const ROLLBACK_CONFIG = {
  // Rollback strategies
  strategies: {
    immediate: {
      description: 'Instant rollback to previous version',
      trafficShift: 0, // 0% to new version
    },
    gradual: {
      description: 'Gradual traffic shift over 5 minutes',
      stages: [0.75, 0.5, 0.25, 0],
      duration: '5m',
    },
  },
  
  // Auto-rollback conditions
  autoRollback: {
    enabled: true,
    conditions: [
      {
        metric: 'error_rate',
        threshold: 0.05,
        duration: '2m',
        strategy: 'immediate',
      },
      {
        metric: 'availability',
        threshold: 0.95,
        duration: '1m',
        strategy: 'immediate',
      },
    ],
  },
  
  // Manual rollback
  manual: {
    // Require approval for production
    requireApproval: process.env.NODE_ENV === 'production',
    // Approval timeout
    approvalTimeout: '5m',
  },
  
  // Notifications
  notifications: {
    channels: ['slack', 'pagerduty', 'email'],
    onRollback: true,
    onSuccess: false,
  },
};

// ============================================================================
// Deployment Pipeline Configuration
// ============================================================================

export const PIPELINE_CONFIG = {
  // Stages
  stages: [
    {
      name: 'build',
      steps: ['install', 'compile', 'test:unit'],
      required: true,
    },
    {
      name: 'quality-gates',
      steps: ['lint', 'typecheck', 'arch:fitness', 'deps:policy'],
      required: true,
    },
    {
      name: 'security',
      steps: ['security:scan', 'sbom:generate', 'license:check'],
      required: true,
    },
    {
      name: 'integration-tests',
      steps: ['test:integration', 'test:contract'],
      required: true,
    },
    {
      name: 'staging',
      steps: ['deploy:staging', 'smoke:tests', 'e2e:tests'],
      required: true,
    },
    {
      name: 'production',
      steps: ['deploy:canary', 'monitor', 'promote'],
      required: true,
      manualApproval: true,
    },
  ],
  
  // Parallel execution
  parallel: {
    unitTests: 4,
    integrationTests: 2,
    securityScans: 2,
  },
  
  // Timeouts
  timeouts: {
    build: '15m',
    tests: '30m',
    deploy: '20m',
  },
};

// ============================================================================
// Environment Configuration
// ============================================================================

export const ENVIRONMENT_CONFIG = {
  // Environment definitions
  environments: {
    development: {
      replicas: 1,
      resources: { cpu: '250m', memory: '512Mi' },
      autoScaling: false,
    },
    staging: {
      replicas: 2,
      resources: { cpu: '500m', memory: '1Gi' },
      autoScaling: { min: 2, max: 4 },
    },
    production: {
      replicas: 3,
      resources: { cpu: '1000m', memory: '2Gi' },
      autoScaling: { min: 3, max: 20 },
      pdb: { minAvailable: 2 }, // Pod Disruption Budget
    },
  },
  
  // Namespace configuration
  namespaces: {
    enabled: true,
    naming: '{product}-{environment}',
    labels: {
      'app.kubernetes.io/part-of': 'ghatana',
      'app.kubernetes.io/managed-by': 'argocd',
    },
  },
};

// ============================================================================
// GitOps Configuration
// ============================================================================

export const GITOPS_CONFIG = {
  // ArgoCD integration
  argocd: {
    enabled: true,
    server: process.env.ARGOCD_SERVER,
    authToken: process.env.ARGOCD_AUTH_TOKEN,
    
    // Application configuration
    applications: [
      {
        name: 'ghatana-platform',
        path: 'k8s/overlays/{environment}',
        repoURL: 'https://github.com/ghatana/ghatana-gitops.git',
        targetRevision: 'HEAD',
      },
    ],
    
    // Sync policies
    syncPolicy: {
      automated: {
        prune: true,
        selfHeal: true,
      },
      syncOptions: ['CreateNamespace=true'],
    },
  },
  
  // Image updates
  imageUpdates: {
    enabled: true,
    strategy: 'semver',
    constraints: {
      production: '~1.0.0',
      staging: '^1.0.0',
    },
  },
};

// ============================================================================
// Helper Functions
// ============================================================================

export function shouldUseCanary(environment: string): boolean {
  if (environment === 'production') {
    return CANARY_CONFIG.stages.length > 0;
  }
  return false;
}

export function getDeploymentStrategy(environment: string): 'canary' | 'blue-green' | 'rolling' {
  if (environment === 'production' && CANARY_CONFIG.stages.length > 0) {
    return 'canary';
  }
  if (BLUE_GREEN_CONFIG.enabled) {
    return 'blue-green';
  }
  return 'rolling';
}

export function isFeatureEnabled(flagName: string, context?: Record<string, unknown>): boolean {
  // Check environment variable fallback
  const envVar = `FEATURE_${flagName.toUpperCase().replace(/-/g, '_')}`;
  const envValue = process.env[envVar];
  
  if (envValue !== undefined) {
    return envValue === 'true' || envValue === '1';
  }
  
  // Return default value
  return FEATURE_FLAGS.defaults[flagName as keyof typeof FEATURE_FLAGS.defaults] ?? false;
}

// ============================================================================
// Initialization
// ============================================================================

export function initializeDeployment(): void {
  console.log('🚀 Initializing deployment configuration...');
  
  const environment = process.env.NODE_ENV || 'development';
  const strategy = getDeploymentStrategy(environment);
  
  console.log(`  Environment: ${environment}`);
  console.log(`  Strategy: ${strategy}`);
  console.log(`  Canary: ${shouldUseCanary(environment)}`);
  console.log(`  Auto-rollback: ${ROLLBACK_CONFIG.autoRollback.enabled}`);
  console.log(`  Feature flags: ${Object.keys(FEATURE_FLAGS.defaults).length} configured`);
  
  console.log('✅ Deployment configuration initialized');
}

// Auto-initialize if run directly
if (import.meta.url === `file://${process.argv[1]}`) {
  initializeDeployment();
}
