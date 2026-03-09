import { ExtensionConfig, BootstrapConfigSchema, RuntimeConfigSchema } from './contracts/config';

// Create a valid bootstrap config
const bootstrapConfig = BootstrapConfigSchema.parse({
  version: '1.0.0',
  source: {
    sourceId: 'self',
    sourceType: 'ipc',
    connectionOptions: {
      id: 'self-connection',
      type: 'ipc',
      maxRetries: 3,
      timeout: 30000,
      secure: true,
      debug: false
    },
    autoReconnect: true,
    reconnectDelayMs: 2000,
    maxReconnectAttempts: 10
  },
  waitForSourceConnection: false
});

// Create a valid runtime config
const runtimeConfig = RuntimeConfigSchema.parse({
  version: '1.0.0',
  sinks: [
    {
      sinkId: 'self',
      sinkType: 'memory',
      connectionOptions: {
        id: 'self-storage',
        type: 'memory',
        maxRetries: 3,
        timeout: 30000,
        secure: true,
        debug: false
      },
      enabled: true
    }
  ]
});

export const DEFAULT_CONFIG: ExtensionConfig = {
  bootstrap: bootstrapConfig,
  runtime: runtimeConfig
};

export const saveDefaultConfig = async () => {
  const result = await browser.storage.local.get('dcmaar_config');
  if (!result.dcmaar_config) {
    await browser.storage.local.set({ dcmaar_config: DEFAULT_CONFIG });
    return true;
  }
  return false;
};
