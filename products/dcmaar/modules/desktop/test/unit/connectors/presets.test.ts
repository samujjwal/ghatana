/**
 * Unit tests for connector presets
 * Tests preset creation, validation, config generation for all 5 presets
 */

import { describe, it, expect } from 'vitest';
import {
  createAgentSourceConfig,
  createAgentSinkConfig,
  createExtensionSourceConfig,
  createExtensionSinkConfig,
  createConfigFromPreset,
  getPreset,
  getRecommendedPresets,
  validateConnectorConfig,
  AGENT_ONLY_PRESET,
  EXTENSION_ONLY_PRESET,
  AGENT_AND_EXTENSION_PRESET,
  MULTI_AGENT_PRESET,
  DEVELOPMENT_PRESET,
  ALL_PRESETS,
  type PresetType,
} from '../../../src/libs/connectors/presets';

describe('Connector Presets', () => {
  describe('Agent Config Creation', () => {
    it('should create agent source config with defaults', () => {
      const config = createAgentSourceConfig();

      expect(config.id).toContain('agent-source');
      expect(config.name).toContain('Agent Source');
      expect(config.type).toBe('agent');
      expect(config.enabled).toBe(true);
      expect(config.options).toHaveProperty('transportOptions');
    });

    it('should create agent source config with custom options', () => {
      const config = createAgentSourceConfig({
        agentId: 'custom-agent',
        port: 8080,
        host: '192.168.1.100',
        secure: true,
      });

      expect(config.id).toContain('custom-agent');
      expect(config.options).toHaveProperty('transportOptions');
      const transportOpts = (config.options as any).transportOptions;
      expect(transportOpts.url).toContain('8080');
      expect(transportOpts.url).toContain('192.168.1.100');
      expect(transportOpts.url).toContain('wss://');
    });

    it('should create agent sink config', () => {
      const config = createAgentSinkConfig();

      expect(config.id).toContain('agent-sink');
      expect(config.name).toContain('Agent Sink');
      expect(config.type).toBe('agent');
      expect(config.enabled).toBe(true);
    });
  });

  describe('Extension Config Creation', () => {
    it('should create extension source config with defaults', () => {
      const config = createExtensionSourceConfig();

      expect(config.id).toContain('extension-source');
      expect(config.name).toContain('Extension Source');
      expect(config.type).toBe('extension');
      expect(config.enabled).toBe(true);
      expect(config.options).toHaveProperty('transportOptions');
    });

    it('should create extension source config with custom browser', () => {
      const config = createExtensionSourceConfig({
        extensionId: 'firefox-ext',
        browser: 'firefox',
        port: 9999,
      });

      expect(config.id).toContain('firefox-ext');
      expect(config.options).toHaveProperty('transportOptions');
      const transportOpts = (config.options as any).transportOptions;
      expect(transportOpts.url).toContain('9999');
    });

    it('should create extension sink config', () => {
      const config = createExtensionSinkConfig();

      expect(config.id).toContain('extension-sink');
      expect(config.name).toContain('Extension Sink');
      expect(config.type).toBe('extension');
      expect(config.enabled).toBe(true);
    });
  });

  describe('Preset Retrieval', () => {
    it('should get agent-only preset', () => {
      const preset = getPreset('agent-only');

      expect(preset).toBeDefined();
      expect(preset?.id).toBe('agent-only');
      expect(preset?.name).toBe('Agent Only');
      expect(preset?.recommended).toBe(true);
    });

    it('should get extension-only preset', () => {
      const preset = getPreset('extension-only');

      expect(preset).toBeDefined();
      expect(preset?.id).toBe('extension-only');
      expect(preset?.name).toBe('Extension Only');
    });

    it('should get agent-and-extension preset', () => {
      const preset = getPreset('agent-and-extension');

      expect(preset).toBeDefined();
      expect(preset?.id).toBe('agent-and-extension');
      expect(preset?.name).toBe('Agent + Extension');
    });

    it('should get multi-agent preset', () => {
      const preset = getPreset('multi-agent');

      expect(preset).toBeDefined();
      expect(preset?.id).toBe('multi-agent');
      expect(preset?.name).toBe('Multi-Agent');
    });

    it('should get development preset', () => {
      const preset = getPreset('development');

      expect(preset).toBeDefined();
      expect(preset?.id).toBe('development');
      expect(preset?.name).toBe('Development (Mock)');
    });

    it('should return undefined for invalid preset', () => {
      const preset = getPreset('invalid-preset' as PresetType);

      expect(preset).toBeUndefined();
    });

    it('should return all recommended presets', () => {
      const recommended = getRecommendedPresets();

      expect(recommended.length).toBeGreaterThan(0);
      expect(recommended.every(p => p.recommended)).toBe(true);
      expect(recommended.some(p => p.id === 'agent-only')).toBe(true);
      expect(recommended.some(p => p.id === 'extension-only')).toBe(true);
    });

    it('should have all 5 presets available', () => {
      expect(ALL_PRESETS).toHaveLength(5);
      expect(ALL_PRESETS.map(p => p.id)).toContain('agent-only');
      expect(ALL_PRESETS.map(p => p.id)).toContain('extension-only');
      expect(ALL_PRESETS.map(p => p.id)).toContain('agent-and-extension');
      expect(ALL_PRESETS.map(p => p.id)).toContain('multi-agent');
      expect(ALL_PRESETS.map(p => p.id)).toContain('development');
    });
  });

  describe('Config Generation from Presets', () => {
    const workspaceId = 'test-workspace';

    it('should create config from agent-only preset', () => {
      const config = createConfigFromPreset('agent-only', workspaceId);

      expect(config.workspaceId).toBe(workspaceId);
      expect(config.sources).toHaveLength(1);
      expect(config.sinks).toHaveLength(1);
      expect(config.sources[0].type).toBe('agent');
      expect(config.sinks[0].type).toBe('agent');
      expect(config.autoStart).toBe(true);
    });

    it('should create config from extension-only preset', () => {
      const config = createConfigFromPreset('extension-only', workspaceId);

      expect(config.workspaceId).toBe(workspaceId);
      expect(config.sources).toHaveLength(1);
      expect(config.sinks).toHaveLength(1);
      expect(config.sources[0].type).toBe('extension');
      expect(config.sinks[0].type).toBe('extension');
    });

    it('should create config from agent-and-extension preset', () => {
      const config = createConfigFromPreset('agent-and-extension', workspaceId);

      expect(config.workspaceId).toBe(workspaceId);
      expect(config.sources).toHaveLength(2);
      expect(config.sinks).toHaveLength(2);
      expect(config.sources.map(s => s.type)).toContain('agent');
      expect(config.sources.map(s => s.type)).toContain('extension');
      expect(config.sinks.map(s => s.type)).toContain('agent');
      expect(config.sinks.map(s => s.type)).toContain('extension');
    });

    it('should create config from multi-agent preset', () => {
      const config = createConfigFromPreset('multi-agent', workspaceId);

      expect(config.workspaceId).toBe(workspaceId);
      expect(config.sources.length).toBeGreaterThanOrEqual(2);
      expect(config.sinks.length).toBeGreaterThanOrEqual(2);
      expect(config.sources.every(s => s.type === 'agent')).toBe(true);
    });

    it('should create config from development preset', () => {
      const config = createConfigFromPreset('development', workspaceId);

      expect(config.workspaceId).toBe(workspaceId);
      expect(config.sources).toHaveLength(1);
      expect(config.sinks).toHaveLength(1);
      expect(config.sources[0].type).toBe('mock');
      expect(config.sinks[0].type).toBe('mock');
    });

    it('should apply custom options to preset config', () => {
      const config = createConfigFromPreset('agent-only', workspaceId, {
        autoStart: false,
        healthCheckInterval: 60000,
      });

      expect(config.autoStart).toBe(false);
      expect(config.healthCheckInterval).toBe(60000);
    });

    it('should throw error for invalid preset', () => {
      expect(() => {
        createConfigFromPreset('invalid' as PresetType, workspaceId);
      }).toThrow();
    });
  });

  describe('Config Validation', () => {
    it('should validate valid config', () => {
      const config = createConfigFromPreset('agent-only', 'test');

      const result = validateConnectorConfig(config);

      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it('should detect missing workspaceId', () => {
      const config: any = {
        sources: [],
        sinks: [],
      };

      const result = validateConnectorConfig(config);

      expect(result.valid).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
      expect(result.errors.some(e => e.includes('workspaceId'))).toBe(true);
    });

    it('should detect missing sources', () => {
      const config: any = {
        workspaceId: 'test',
        sources: [],
        sinks: [],
      };

      const result = validateConnectorConfig(config);

      expect(result.valid).toBe(false);
      expect(result.errors.some(e => e.includes('source'))).toBe(true);
    });

    it('should detect duplicate connector IDs', () => {
      const config: any = {
        workspaceId: 'test',
        sources: [
          {
            id: 'duplicate',
            name: 'Source 1',
            type: 'agent',
            enabled: true,
            options: {},
          },
          {
            id: 'duplicate', // Same ID
            name: 'Source 2',
            type: 'agent',
            enabled: true,
            options: {},
          },
        ],
        sinks: [],
      };

      const result = validateConnectorConfig(config);

      expect(result.valid).toBe(false);
      expect(result.errors.some(e => e.toLowerCase().includes('duplicate'))).toBe(true);
    });

    it('should validate multi-connector configs', () => {
      const config = createConfigFromPreset('agent-and-extension', 'test');

      const result = validateConnectorConfig(config);

      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });
  });

  describe('Preset Characteristics', () => {
    it('should have correct agent-only preset structure', () => {
      const preset = AGENT_ONLY_PRESET;

      expect(preset.id).toBe('agent-only');
      expect(preset.recommended).toBe(true);
      expect(preset.description).toBeTruthy();
      expect(preset.tags.length).toBeGreaterThan(0);
      expect(preset.config.sources).toHaveLength(1);
      expect(preset.config.sinks).toHaveLength(1);
    });

    it('should have correct extension-only preset structure', () => {
      const preset = EXTENSION_ONLY_PRESET;

      expect(preset.id).toBe('extension-only');
      expect(preset.recommended).toBe(true);
      expect(preset.config.sources).toHaveLength(1);
      expect(preset.config.sinks).toHaveLength(1);
    });

    it('should have correct agent-and-extension preset structure', () => {
      const preset = AGENT_AND_EXTENSION_PRESET;

      expect(preset.id).toBe('agent-and-extension');
      expect(preset.recommended).toBe(true);
      expect(preset.config.sources).toHaveLength(2);
      expect(preset.config.sinks).toHaveLength(2);
    });

    it('should have correct multi-agent preset structure', () => {
      const preset = MULTI_AGENT_PRESET;

      expect(preset.id).toBe('multi-agent');
      expect(preset.config.sources.length).toBeGreaterThanOrEqual(2);
      expect(preset.config.sinks.length).toBeGreaterThanOrEqual(2);
    });

    it('should have development preset marked for testing', () => {
      const preset = DEVELOPMENT_PRESET;

      expect(preset.id).toBe('development');
      expect(preset.config.sources).toHaveLength(1);
      expect(preset.config.sinks).toHaveLength(1);
      expect(preset.description.toLowerCase()).toContain('mock');
    });
  });

  describe('Preset Options', () => {
    it('should support custom logging level', () => {
      const config = createConfigFromPreset('agent-only', 'test', {
        logging: {
          level: 'debug',
          enabled: true,
        },
      });

      expect(config.logging?.level).toBe('debug');
    });

    it('should support autoStart override', () => {
      const config = createConfigFromPreset('development', 'test', {
        autoStart: false,
      });

      expect(config.autoStart).toBe(false);
    });

    it('should support health check interval override', () => {
      const config = createConfigFromPreset('agent-only', 'test', {
        healthCheckInterval: 5000,
      });

      expect(config.healthCheckInterval).toBe(5000);
    });
  });
});
