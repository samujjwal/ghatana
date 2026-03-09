/**
 * Storybook Smoke Tests - Test Suite
 * 
 * Comprehensive tests for Storybook smoke testing utilities.
 */

import { describe, it, expect, beforeEach } from 'vitest';

import {
  createStorybookSmokeManager,
  generateStoryUrl,
  type StorybookSmokeConfig,
  type StoryTestSpec,

  StorybookSmokeManager} from '../storybookSmoke';

describe('StorybookSmokeManager', () => {
  const defaultConfig: StorybookSmokeConfig = {
    storybookUrl: 'http://localhost:6006',
  };

  describe('Initialization', () => {
    it('should initialize with configuration', () => {
      const manager = createStorybookSmokeManager(defaultConfig);
      const config = manager.getConfig();

      expect(config.storybookUrl).toBe('http://localhost:6006');
      expect(config.browser).toBe('chromium');
      expect(config.headless).toBe(true);
      expect(config.timeout).toBe(30000);
      expect(config.screenshots).toBe(true);
      expect(config.traces).toBe(true);
    });

    it('should set default values for optional config', () => {
      const manager = createStorybookSmokeManager(defaultConfig);
      const config = manager.getConfig();

      expect(config.viewport).toEqual({ width: 1280, height: 720 });
      expect(config.artifactDir).toBe('./test-artifacts');
      expect(config.retries).toBe(2);
      expect(config.slowTestThreshold).toBe(10000);
    });

    it('should accept custom configuration', () => {
      const customConfig: StorybookSmokeConfig = {
        storybookUrl: 'http://localhost:9009',
        browser: 'firefox',
        headless: false,
        timeout: 60000,
        screenshots: false,
        viewport: { width: 1920, height: 1080 },
      };

      const manager = createStorybookSmokeManager(customConfig);
      const config = manager.getConfig();

      expect(config.storybookUrl).toBe('http://localhost:9009');
      expect(config.browser).toBe('firefox');
      expect(config.headless).toBe(false);
      expect(config.timeout).toBe(60000);
      expect(config.screenshots).toBe(false);
      expect(config.viewport).toEqual({ width: 1920, height: 1080 });
    });

    it('should update configuration', () => {
      const manager = createStorybookSmokeManager(defaultConfig);

      manager.updateConfig({
        timeout: 45000,
        viewport: { width: 1600, height: 900 },
      });

      const config = manager.getConfig();
      expect(config.timeout).toBe(45000);
      expect(config.viewport).toEqual({ width: 1600, height: 900 });
    });
  });

  describe('Spec Management', () => {
    let manager: StorybookSmokeManager;

    beforeEach(() => {
      manager = createStorybookSmokeManager(defaultConfig);
    });

    it('should register test spec', () => {
      const spec: StoryTestSpec = {
        component: 'Button',
        story: 'Primary',
        title: 'Components/Button',
        actions: [
          { type: 'click', selector: 'button' },
        ],
        assertions: [
          { type: 'visible', selector: 'button' },
        ],
      };

      manager.registerSpec(spec);

      const registered = manager.getSpec('button--primary');
      expect(registered).toEqual(spec);
    });

    it('should get all specs', () => {
      const specs: StoryTestSpec[] = [
        {
          component: 'Button',
          story: 'Primary',
          title: 'Components/Button',
          actions: [],
          assertions: [],
        },
        {
          component: 'Input',
          story: 'Default',
          title: 'Components/Input',
          actions: [],
          assertions: [],
        },
      ];

      specs.forEach((s) => manager.registerSpec(s));

      expect(manager.getAllSpecs()).toHaveLength(2);
    });

    it('should get specs by tags', () => {
      const specs: StoryTestSpec[] = [
        {
          component: 'Button',
          story: 'Primary',
          title: 'Components/Button',
          actions: [],
          assertions: [],
          tags: ['smoke', 'critical'],
        },
        {
          component: 'Input',
          story: 'Default',
          title: 'Components/Input',
          actions: [],
          assertions: [],
          tags: ['integration'],
        },
      ];

      specs.forEach((s) => manager.registerSpec(s));

      const smokeSpecs = manager.getSpecsByTags(['smoke']);
      expect(smokeSpecs).toHaveLength(1);
      expect(smokeSpecs[0].component).toBe('Button');
    });

    it('should generate story URL', () => {
      const spec: StoryTestSpec = {
        component: 'Button',
        story: 'Primary',
        title: 'Components/Button',
        actions: [],
        assertions: [],
      };

      const url = manager.generateStoryUrl(spec);

      expect(url).toBe('http://localhost:6006/iframe.html?id=button--primary&viewMode=story');
    });

    it('should handle component names with spaces', () => {
      const spec: StoryTestSpec = {
        component: 'Custom Button',
        story: 'Large Size',
        title: 'Components/CustomButton',
        actions: [],
        assertions: [],
      };

      const url = manager.generateStoryUrl(spec);

      expect(url).toContain('custom-button--large-size');
    });
  });

  describe('Test Execution', () => {
    let manager: StorybookSmokeManager;

    beforeEach(() => {
      manager = createStorybookSmokeManager(defaultConfig);
    });

    it('should run test successfully', async () => {
      const spec: StoryTestSpec = {
        component: 'Button',
        story: 'Primary',
        title: 'Components/Button',
        actions: [
          { type: 'click', selector: 'button' },
        ],
        assertions: [
          { type: 'visible', selector: 'button' },
        ],
      };

      manager.registerSpec(spec);
      const result = await manager.runTest('button--primary');

      expect(result.passed).toBe(true);
      expect(result.storyId).toBe('button--primary');
      expect(result.duration).toBeGreaterThan(0);
      expect(result.actionResults).toHaveLength(1);
      expect(result.assertionResults).toHaveLength(1);
    });

    it('should track action results', async () => {
      const spec: StoryTestSpec = {
        component: 'Button',
        story: 'Primary',
        title: 'Components/Button',
        actions: [
          { type: 'click', selector: 'button' },
          { type: 'hover', selector: '.tooltip' },
        ],
        assertions: [],
      };

      manager.registerSpec(spec);
      const result = await manager.runTest('button--primary');

      expect(result.actionResults).toHaveLength(2);
      expect(result.actionResults[0].type).toBe('click');
      expect(result.actionResults[0].success).toBe(true);
      expect(result.actionResults[1].type).toBe('hover');
      expect(result.actionResults[1].success).toBe(true);
    });

    it('should track assertion results', async () => {
      const spec: StoryTestSpec = {
        component: 'Button',
        story: 'Primary',
        title: 'Components/Button',
        actions: [],
        assertions: [
          { type: 'visible', selector: 'button' },
          { type: 'text', selector: 'button', expected: 'Click me' },
        ],
      };

      manager.registerSpec(spec);
      const result = await manager.runTest('button--primary');

      expect(result.assertionResults).toHaveLength(2);
      expect(result.assertionResults[0].type).toBe('visible');
      expect(result.assertionResults[0].passed).toBe(true);
      expect(result.assertionResults[1].type).toBe('text');
    });

    it('should capture screenshots', async () => {
      const spec: StoryTestSpec = {
        component: 'Button',
        story: 'Primary',
        title: 'Components/Button',
        actions: [
          { type: 'screenshot', name: 'initial' },
        ],
        assertions: [],
      };

      manager.registerSpec(spec);
      const result = await manager.runTest('button--primary');

      expect(result.screenshots).toHaveLength(1);
      expect(result.screenshots[0]).toContain('button--primary');
      expect(result.screenshots[0]).toContain('initial');
    });

    it('should include trace path when traces enabled', async () => {
      const spec: StoryTestSpec = {
        component: 'Button',
        story: 'Primary',
        title: 'Components/Button',
        actions: [],
        assertions: [],
      };

      manager.registerSpec(spec);
      const result = await manager.runTest('button--primary');

      expect(result.tracePath).toBeDefined();
      expect(result.tracePath).toContain('button--primary-trace.zip');
    });

    it('should include metadata', async () => {
      const spec: StoryTestSpec = {
        component: 'Button',
        story: 'Primary',
        title: 'Components/Button',
        actions: [],
        assertions: [],
      };

      manager.registerSpec(spec);
      const result = await manager.runTest('button--primary');

      expect(result.metadata.browser).toBe('chromium');
      expect(result.metadata.viewport).toEqual({ width: 1280, height: 720 });
      expect(result.metadata.url).toContain('button--primary');
      expect(result.metadata.timestamp).toBeGreaterThan(0);
    });

    it('should fail on invalid drag action', async () => {
      const spec: StoryTestSpec = {
        component: 'Canvas',
        story: 'Drag Test',
        title: 'Components/Canvas',
        actions: [
          { type: 'drag', selector: '.node' }, // Missing from/to
        ],
        assertions: [],
      };

      manager.registerSpec(spec);
      const result = await manager.runTest('canvas--drag-test');

      expect(result.passed).toBe(false);
      expect(result.actionResults[0].success).toBe(false);
      expect(result.actionResults[0].error).toContain('from and to positions');
    });

    it('should fail on invalid type action', async () => {
      const spec: StoryTestSpec = {
        component: 'Input',
        story: 'Default',
        title: 'Components/Input',
        actions: [
          { type: 'type', selector: 'input' }, // Missing text
        ],
        assertions: [],
      };

      manager.registerSpec(spec);
      const result = await manager.runTest('input--default');

      expect(result.passed).toBe(false);
      expect(result.actionResults[0].success).toBe(false);
      expect(result.actionResults[0].error).toContain('requires text');
    });

    it('should fail on invalid click action', async () => {
      const spec: StoryTestSpec = {
        component: 'Button',
        story: 'Primary',
        title: 'Components/Button',
        actions: [
          { type: 'click' } as unknown, // Missing selector
        ],
        assertions: [],
      };

      manager.registerSpec(spec);
      const result = await manager.runTest('button--primary');

      expect(result.passed).toBe(false);
      expect(result.actionResults[0].success).toBe(false);
      expect(result.actionResults[0].error).toContain('requires selector');
    });
  });

  describe('Suite Execution', () => {
    let manager: StorybookSmokeManager;

    beforeEach(() => {
      manager = createStorybookSmokeManager(defaultConfig);
    });

    it('should run all tests', async () => {
      const specs: StoryTestSpec[] = [
        {
          component: 'Button',
          story: 'Primary',
          title: 'Components/Button',
          actions: [],
          assertions: [],
        },
        {
          component: 'Input',
          story: 'Default',
          title: 'Components/Input',
          actions: [],
          assertions: [],
        },
      ];

      specs.forEach((s) => manager.registerSpec(s));
      const suiteResults = await manager.runAllTests();

      expect(suiteResults.total).toBe(2);
      expect(suiteResults.passed).toBe(2);
      expect(suiteResults.failed).toBe(0);
      expect(suiteResults.results.size).toBe(2);
    });

    it('should run tests by tags', async () => {
      const specs: StoryTestSpec[] = [
        {
          component: 'Button',
          story: 'Primary',
          title: 'Components/Button',
          actions: [],
          assertions: [],
          tags: ['smoke'],
        },
        {
          component: 'Input',
          story: 'Default',
          title: 'Components/Input',
          actions: [],
          assertions: [],
          tags: ['integration'],
        },
      ];

      specs.forEach((s) => manager.registerSpec(s));
      const suiteResults = await manager.runTestsByTags(['smoke']);

      expect(suiteResults.total).toBe(1);
      expect(suiteResults.results.has('button--primary')).toBe(true);
      expect(suiteResults.results.has('input--default')).toBe(false);
    });

    it('should track suite duration', async () => {
      const spec: StoryTestSpec = {
        component: 'Button',
        story: 'Primary',
        title: 'Components/Button',
        actions: [],
        assertions: [],
      };

      manager.registerSpec(spec);
      const suiteResults = await manager.runAllTests();

      expect(suiteResults.duration).toBeGreaterThanOrEqual(0);
    });

    it('should aggregate artifacts', async () => {
      const spec: StoryTestSpec = {
        component: 'Button',
        story: 'Primary',
        title: 'Components/Button',
        actions: [
          { type: 'screenshot', name: 'test' },
        ],
        assertions: [],
      };

      manager.registerSpec(spec);
      const suiteResults = await manager.runAllTests();

      expect(suiteResults.artifacts.screenshots).toHaveLength(1);
      expect(suiteResults.artifacts.traces).toHaveLength(1);
    });

    it('should include environment metadata', async () => {
      const spec: StoryTestSpec = {
        component: 'Button',
        story: 'Primary',
        title: 'Components/Button',
        actions: [],
        assertions: [],
      };

      manager.registerSpec(spec);
      const suiteResults = await manager.runAllTests();

      expect(suiteResults.metadata.environment).toBeDefined();
      expect(suiteResults.metadata.environment.platform).toBeDefined();
      expect(suiteResults.metadata.config).toEqual(manager.getConfig());
    });
  });

  describe('CI Workflow Generation', () => {
    let manager: StorybookSmokeManager;

    beforeEach(() => {
      manager = createStorybookSmokeManager(defaultConfig);
    });

    it('should generate CI workflow configuration', () => {
      const workflow = manager.generateCIWorkflow();

      expect(workflow.name).toBe('Storybook Smoke Tests');
      expect(workflow.on).toContain('pull_request');
      expect(workflow.on).toContain('push');
      expect(workflow.jobs['storybook-smoke']).toBeDefined();
    });

    it('should include required steps', () => {
      const workflow = manager.generateCIWorkflow();
      const job = workflow.jobs['storybook-smoke'];

      const stepNames = job.steps.map((s) => s.name);
      expect(stepNames).toContain('Checkout');
      expect(stepNames).toContain('Setup Node');
      expect(stepNames).toContain('Install dependencies');
      expect(stepNames).toContain('Build Storybook');
      expect(stepNames).toContain('Install Playwright');
      expect(stepNames).toContain('Run smoke tests');
    });

    it('should configure artifact upload', () => {
      const workflow = manager.generateCIWorkflow();
      const job = workflow.jobs['storybook-smoke'];

      expect(job.artifacts).toBeDefined();
      expect(job.artifacts?.name).toBe('storybook-smoke-artifacts');
      expect(job.artifacts?.path).toBe('./test-artifacts');
      expect(job.artifacts?.retention).toBe(30);
    });

    it('should accept custom workflow options', () => {
      const workflow = manager.generateCIWorkflow({
        workflowName: 'Custom Smoke Tests',
        triggers: ['workflow_dispatch'],
        retention: 7,
      });

      expect(workflow.name).toBe('Custom Smoke Tests');
      expect(workflow.on).toContain('workflow_dispatch');
      expect(workflow.jobs['storybook-smoke'].artifacts?.retention).toBe(7);
    });

    it('should export workflow as YAML', () => {
      const yaml = manager.exportWorkflowYAML();

      expect(yaml).toContain('name: Storybook Smoke Tests');
      expect(yaml).toContain('on:');
      expect(yaml).toContain('- pull_request');
      expect(yaml).toContain('jobs:');
      expect(yaml).toContain('storybook-smoke:');
      expect(yaml).toContain('runs-on: ubuntu-latest');
      expect(yaml).toContain('steps:');
    });
  });

  describe('Results Export', () => {
    let manager: StorybookSmokeManager;

    beforeEach(() => {
      manager = createStorybookSmokeManager(defaultConfig);
    });

    it('should export results as JSON', async () => {
      const spec: StoryTestSpec = {
        component: 'Button',
        story: 'Primary',
        title: 'Components/Button',
        actions: [],
        assertions: [],
      };

      manager.registerSpec(spec);
      await manager.runAllTests();

      const json = manager.exportResultsJSON();
      const results = JSON.parse(json);

      expect(results.total).toBe(1);
      expect(results.passed).toBe(1);
      expect(results.results).toBeDefined();
    });

    it('should export results as Markdown', async () => {
      const spec: StoryTestSpec = {
        component: 'Button',
        story: 'Primary',
        title: 'Components/Button',
        actions: [
          { type: 'click', selector: 'button' },
        ],
        assertions: [
          { type: 'visible', selector: 'button' },
        ],
      };

      manager.registerSpec(spec);
      await manager.runAllTests();

      const markdown = manager.exportResultsMarkdown();

      expect(markdown).toContain('# Storybook Smoke Test Results');
      expect(markdown).toContain('## Summary');
      expect(markdown).toContain('button--primary');
      expect(markdown).toContain('## Tests');
      expect(markdown).toContain('## Artifacts');
    });

    it('should throw if exporting without results', () => {
      expect(() => manager.exportResultsJSON()).toThrow('No suite results available');
      expect(() => manager.exportResultsMarkdown()).toThrow('No suite results available');
    });
  });

  describe('CI Status Check', () => {
    let manager: StorybookSmokeManager;

    beforeEach(() => {
      manager = createStorybookSmokeManager(defaultConfig);
    });

    it('should return success status when all tests pass', async () => {
      const spec: StoryTestSpec = {
        component: 'Button',
        story: 'Primary',
        title: 'Components/Button',
        actions: [],
        assertions: [],
      };

      manager.registerSpec(spec);
      await manager.runAllTests();

      const status = manager.checkCIStatus();

      expect(status.shouldFail).toBe(false);
      expect(status.exitCode).toBe(0);
      expect(status.message).toBe('All smoke tests passed');
    });

    it('should return failure status when tests fail', async () => {
      const spec: StoryTestSpec = {
        component: 'Button',
        story: 'Primary',
        title: 'Components/Button',
        actions: [
          { type: 'drag' } as unknown, // Invalid - will fail
        ],
        assertions: [],
      };

      manager.registerSpec(spec);
      await manager.runAllTests();

      const status = manager.checkCIStatus();

      expect(status.shouldFail).toBe(true);
      expect(status.exitCode).toBe(1);
      expect(status.message).toContain('smoke test(s) failed');
    });

    it('should return failure status when no results', () => {
      const status = manager.checkCIStatus();

      expect(status.shouldFail).toBe(true);
      expect(status.exitCode).toBe(1);
      expect(status.message).toBe('No test results available');
    });
  });

  describe('Helper Functions', () => {
    it('should generate story URL via helper', () => {
      const url = generateStoryUrl('http://localhost:6006', 'Button', 'Primary');

      expect(url).toBe('http://localhost:6006/iframe.html?id=button--primary&viewMode=story');
    });

    it('should handle spaces in helper function', () => {
      const url = generateStoryUrl('http://localhost:6006', 'Custom Button', 'Large Size');

      expect(url).toContain('custom-button--large-size');
    });
  });

  describe('Reset and Cleanup', () => {
    let manager: StorybookSmokeManager;

    beforeEach(() => {
      manager = createStorybookSmokeManager(defaultConfig);
    });

    it('should clear results', async () => {
      const spec: StoryTestSpec = {
        component: 'Button',
        story: 'Primary',
        title: 'Components/Button',
        actions: [],
        assertions: [],
      };

      manager.registerSpec(spec);
      await manager.runAllTests();

      manager.clearResults();

      expect(manager.getSuiteResults()).toBeNull();
      expect((manager.getResults() as Map<string, unknown>).size).toBe(0);
    });

    it('should clear specs', () => {
      const spec: StoryTestSpec = {
        component: 'Button',
        story: 'Primary',
        title: 'Components/Button',
        actions: [],
        assertions: [],
      };

      manager.registerSpec(spec);
      manager.clearSpecs();

      expect(manager.getAllSpecs()).toHaveLength(0);
    });

    it('should reset manager', async () => {
      const spec: StoryTestSpec = {
        component: 'Button',
        story: 'Primary',
        title: 'Components/Button',
        actions: [],
        assertions: [],
      };

      manager.registerSpec(spec);
      await manager.runAllTests();

      manager.reset();

      expect(manager.getAllSpecs()).toHaveLength(0);
      expect(manager.getSuiteResults()).toBeNull();
      expect((manager.getResults() as Map<string, unknown>).size).toBe(0);
    });

    it('should preserve config on reset', () => {
      manager.updateConfig({ timeout: 45000 });

      manager.reset();

      const config = manager.getConfig();
      expect(config.timeout).toBe(45000);
    });
  });
});
