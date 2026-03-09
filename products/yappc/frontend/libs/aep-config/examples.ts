/**
 * AEP Mode Integration Examples for YAPPC
 * 
 * Shows how to integrate AEP in both library and service modes
 */

import {
  AepMode,
  AepConfig,
  getAepConfig,
  formatAepConfig,
  isLibraryMode,
  isServiceMode,
  createAepClient,
  getGlobalAepClient,
  type AepClient,
} from '@ghatana/yappc-aep-config';

// =============================================================================
// EXAMPLE 1: Automatic Mode Detection (Recommended)
// =============================================================================
// YAPPC automatically picks the right mode based on NODE_ENV
//
// Development: NODE_ENV=development → Uses Library mode (no service needed)
// Staging:    NODE_ENV=staging → Uses Service mode (connects to AEP service)
// Production: NODE_ENV=production → Uses Service mode (connects to AEP service)

export async function initializeAepAutomatic(): Promise<void> {
  // Get configuration - automatically picks mode based on NODE_ENV
  const config = getAepConfig();

  console.log(formatAepConfig(config));

  // Create client for current mode
  const client = createAepClient(config);

  // Initialize client
  await client.initialize();

  // Use the client
  await client.publishEvent('app:started', {
    version: '1.0.0',
    mode: client.getMode(),
  });

  console.log(`✅ AEP initialized in ${client.getMode()} mode`);
}

// =============================================================================
// EXAMPLE 2: Explicit Library Mode (Dev with Override)
// =============================================================================
// You can force library mode even in other environments for testing

export async function initializeLibraryMode(): Promise<void> {
  const config = getAepConfig();

  // Force library mode
  config.mode = AepMode.LIBRARY;

  const client = createAepClient(config);
  await client.initialize();

  // Library mode is perfect for:
  // - Local development without external services
  // - Unit testing without mocks
  // - Rapid prototyping

  console.log('📚 Using AEP in Library mode');
  return;
}

// =============================================================================
// EXAMPLE 3: Explicit Service Mode
// =============================================================================
// Force service mode to connect to external AEP service

export async function initializeServiceMode(): Promise<void> {
  const config = getAepConfig();

  // Force service mode
  config.mode = AepMode.SERVICE;

  // Optionally override service host/port
  if (config.service) {
    config.service.host = 'aep-prod.internal';
    config.service.port = 7106;
  }

  const client = createAepClient(config);

  try {
    await client.initialize();
    console.log('🔗 Connected to external AEP service');
  } catch (error) {
    console.error('❌ Failed to connect to AEP service:', error);
    // Could fallback to library mode here
  }
}

// =============================================================================
// EXAMPLE 4: Using Global AEP Client
// =============================================================================
// Recommended for singleton usage across your app

export async function useGlobalAepClient(): Promise<void> {
  // Get or create global instance (initialized on first access)
  const client = await getGlobalAepClient();

  // Use it anywhere in your app
  await client.executeAgent('my-agent', { input: 'data' });

  // Same instance reused across the application
  const client2 = await getGlobalAepClient();
  console.log('Same instance:', client === client2); // true
}

// =============================================================================
// EXAMPLE 5: Mode-Specific Logic
// =============================================================================
// Implement different behavior based on mode

export async function implementModeSpecificLogic(): Promise<void> {
  const config = getAepConfig();

  if (isLibraryMode(config)) {
    console.log('📚 In library mode - using in-process client');
    // Library mode optimizations:
    // - Cache results locally
    // - Skip network retries
    // - Use memory storage
  } else if (isServiceMode(config)) {
    console.log('🔗 In service mode - using remote client');
    // Service mode considerations:
    // - Implement circuit breakers
    // - Handle network failures
    // - Use distributed caching
  }
}

// =============================================================================
// EXAMPLE 6: React Component Integration
// =============================================================================

import { useEffect, useState } from 'react';

export function AepStatus(): JSX.Element {
  const [aepClient, setAepClient] = useState<AepClient | null>(null);
  const [health, setHealth] = useState<string>('loading');

  useEffect(() => {
    (async () => {
      const client = await getGlobalAepClient();
      setAepClient(client);

      // Check health
      const status = await client.getHealth();
      setHealth(status.status);
    })();
  }, []);

  if (!aepClient) return <div>Initializing AEP...</div>;

  return (
    <div>
      <h3>AEP Status</h3>
      <p>Mode: {aepClient.getMode()}</p>
      <p>Health: {health}</p>
      <p>Ready: {aepClient.isReady() ? '✅' : '❌'}</p>
    </div>
  );
}

// =============================================================================
// EXAMPLE 7: Canvas Integration
// =============================================================================
// How to use AEP within YAPPC Canvas operations

export async function integrateAepWithCanvas(): Promise<void> {
  const client = await getGlobalAepClient();

  // When user creates a frame
  async function onCreateFrame(frameData: Record<string, unknown>): Promise<void> {
    // Publish event to AEP
    await client.publishEvent('canvas:frame:created', {
      frameId: frameData.id,
      name: frameData.name,
      timestamp: new Date().toISOString(),
    });
  }

  // When user executes a workflow
  async function executeWorkflow(workflowId: string): Promise<void> {
    const result = await client.executeAgent('workflow-executor', {
      workflowId,
      context: 'canvas-operation',
    });

    console.log('Workflow result:', result);
  }

  // When user searches for patterns
  async function findPatterns(): Promise<void> {
    const patterns = await client.getPatterns();
    console.log('Found patterns:', patterns);
  }
}

// =============================================================================
// EXAMPLE 8: Environment-Specific Configuration
// =============================================================================

// .env.development
// NODE_ENV=development
// (AEP_MODE not set - defaults to library mode)

// .env.staging
// NODE_ENV=staging
// AEP_SERVICE_HOST=aep-staging.internal
// AEP_SERVICE_PORT=7106

// .env.production
// NODE_ENV=production
// AEP_SERVICE_HOST=aep-prod.internal
// AEP_SERVICE_PORT=7106
// AEP_SERVICE_TIMEOUT=60000
// AEP_SERVICE_MAX_RETRIES=5

export async function environmentAwareInitialization(): Promise<void> {
  const config = getAepConfig();
  console.log('Current configuration:', formatAepConfig(config));

  const client = createAepClient(config);
  await client.initialize();

  console.log(`✅ AEP initialized for ${process.env.NODE_ENV} environment`);
}

// =============================================================================
// EXAMPLE 9: Error Handling
// =============================================================================

export async function handleAepErrors(): Promise<void> {
  try {
    const client = await getGlobalAepClient();

    // If in service mode, might fail to connect
    if (!client.isReady()) {
      throw new Error('AEP client not ready');
    }

    // Execute with error handling
    const result = await client.executeAgent('my-agent', { data: 'test' });
    console.log('Success:', result);
  } catch (error) {
    console.error('AEP error:', error);

    // Could implement fallback:
    // - Retry with exponential backoff
    // - Fall back to library mode
    // - Use cached results
  }
}

// =============================================================================
// EXAMPLE 10: Testing
// =============================================================================

import { resetGlobalAepClient } from '@ghatana/yappc-aep-config';

export async function testWithAep(): Promise<void> {
  // Each test gets a fresh AEP client
  beforeEach(() => {
    resetGlobalAepClient();
  });

  // Test library mode
  test('should work in library mode', async () => {
    const config: AepConfig = {
      mode: AepMode.LIBRARY,
      environment: 'development',
      library: {
        debug: true,
        cacheSize: 100,
        patternDetectionInterval: 1000,
        maxConcurrentOps: 5,
      },
    };

    const client = createAepClient(config);
    await client.initialize();

    expect(client.getMode()).toBe(AepMode.LIBRARY);
    expect(client.isReady()).toBe(true);
  });

  // Test mode detection
  test('should use library mode in development', async () => {
    process.env.NODE_ENV = 'development';
    const config = getAepConfig();

    expect(config.mode).toBe(AepMode.LIBRARY);
  });

  test('should use service mode in production', async () => {
    process.env.NODE_ENV = 'production';
    const config = getAepConfig();

    expect(config.mode).toBe(AepMode.SERVICE);
  });
}
