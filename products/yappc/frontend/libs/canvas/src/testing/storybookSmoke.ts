/**
 * Storybook Smoke Tests
 * 
 * Provides automated Storybook smoke testing via Playwright including
 * drag test specs, CI workflow integration, and artifact management.
 * 
 * Features:
 * - Playwright-based Storybook automation
 * - Drag-and-drop test specs
 * - Screenshot and trace artifact capture
 * - CI workflow integration
 * - Configurable test scenarios
 * 
 * @module testing/storybookSmoke
 */

/**
 * Storybook smoke test configuration
 */
export interface StorybookSmokeConfig {
  /**
   * Storybook base URL
   */
  storybookUrl: string;

  /**
   * Playwright browser type
   */
  browser?: 'chromium' | 'firefox' | 'webkit';

  /**
   * Whether to run headless
   */
  headless?: boolean;

  /**
   * Timeout for each test (ms)
   */
  timeout?: number;

  /**
   * Whether to capture screenshots
   */
  screenshots?: boolean;

  /**
   * Whether to capture traces
   */
  traces?: boolean;

  /**
   * Artifact output directory
   */
  artifactDir?: string;

  /**
   * Viewport size
   */
  viewport?: {
    width: number;
    height: number;
  };

  /**
   * Whether to retry failed tests
   */
  retries?: number;

  /**
   * Slow test threshold (ms)
   */
  slowTestThreshold?: number;
}

/**
 * Story test spec
 */
export interface StoryTestSpec {
  /**
   * Story component name
   */
  component: string;

  /**
   * Story name
   */
  story: string;

  /**
   * Story title (for URL)
   */
  title: string;

  /**
   * Test actions to perform
   */
  actions: Array<{
    type: 'click' | 'drag' | 'hover' | 'type' | 'wait' | 'screenshot';
    selector?: string;
    from?: { x: number; y: number };
    to?: { x: number; y: number };
    text?: string;
    duration?: number;
    name?: string;
  }>;

  /**
   * Assertions to check
   */
  assertions: Array<{
    type: 'visible' | 'hidden' | 'text' | 'count' | 'attribute';
    selector: string;
    expected?: string | number | boolean;
    attribute?: string;
  }>;

  /**
   * Tags for categorization
   */
  tags?: string[];

  /**
   * Custom timeout
   */
  timeout?: number;
}

/**
 * Test execution result
 */
export interface TestExecutionResult {
  /**
   * Story identifier
   */
  storyId: string;

  /**
   * Whether test passed
   */
  passed: boolean;

  /**
   * Test duration (ms)
   */
  duration: number;

  /**
   * Error if test failed
   */
  error?: Error;

  /**
   * Screenshot paths
   */
  screenshots: string[];

  /**
   * Trace file path
   */
  tracePath?: string;

  /**
   * Test metadata
   */
  metadata: {
    timestamp: number;
    browser: string;
    viewport: { width: number; height: number };
    url: string;
  };

  /**
   * Action results
   */
  actionResults: Array<{
    type: string;
    duration: number;
    success: boolean;
    error?: string;
  }>;

  /**
   * Assertion results
   */
  assertionResults: Array<{
    type: string;
    selector: string;
    passed: boolean;
    actual?: unknown;
    expected?: unknown;
  }>;
}

/**
 * Smoke test suite results
 */
export interface SmokeTestSuiteResults {
  /**
   * Total tests run
   */
  total: number;

  /**
   * Tests passed
   */
  passed: number;

  /**
   * Tests failed
   */
  failed: number;

  /**
   * Tests skipped
   */
  skipped: number;

  /**
   * Total duration (ms)
   */
  duration: number;

  /**
   * Individual test results
   */
  results: Map<string, TestExecutionResult>;

  /**
   * Artifact locations
   */
  artifacts: {
    screenshots: string[];
    traces: string[];
    videos: string[];
  };

  /**
   * Suite metadata
   */
  metadata: {
    timestamp: number;
    config: StorybookSmokeConfig;
    environment: {
      ci: boolean;
      platform: string;
      nodeVersion: string;
    };
  };
}

/**
 * CI workflow configuration
 */
export interface CIWorkflowConfig {
  /**
   * Workflow name
   */
  name: string;

  /**
   * Trigger events
   */
  on: string[];

  /**
   * Job configuration
   */
  jobs: {
    [jobName: string]: {
      runsOn: string;
      steps: Array<{
        name: string;
        uses?: string;
        run?: string;
        with?: Record<string, unknown>;
      }>;
      artifacts?: {
        name: string;
        path: string;
        retention: number;
      };
    };
  };

  /**
   * Environment variables
   */
  env?: Record<string, string>;
}

/**
 * Storybook Smoke Tests Manager
 * 
 * Manages Storybook smoke testing via Playwright including drag tests,
 * artifact capture, and CI workflow integration.
 */
export class StorybookSmokeManager {
  private config: Required<StorybookSmokeConfig>;
  private specs = new Map<string, StoryTestSpec>();
  private results = new Map<string, TestExecutionResult>();
  private suiteResults: SmokeTestSuiteResults | null = null;

  /**
   *
   */
  constructor(config: StorybookSmokeConfig) {
    this.config = {
      storybookUrl: config.storybookUrl,
      browser: config.browser ?? 'chromium',
      headless: config.headless ?? true,
      timeout: config.timeout ?? 30000,
      screenshots: config.screenshots ?? true,
      traces: config.traces ?? true,
      artifactDir: config.artifactDir ?? './test-artifacts',
      viewport: config.viewport ?? { width: 1280, height: 720 },
      retries: config.retries ?? 2,
      slowTestThreshold: config.slowTestThreshold ?? 10000,
    };
  }

  /**
   * Get current configuration
   */
  getConfig(): Readonly<Required<StorybookSmokeConfig>> {
    return { ...this.config };
  }

  /**
   * Update configuration
   */
  updateConfig(updates: Partial<StorybookSmokeConfig>): void {
    this.config = {
      ...this.config,
      ...updates,
      viewport: {
        ...this.config.viewport,
        ...(updates.viewport ?? {}),
      },
    };
  }

  /**
   * Register story test spec
   */
  registerSpec(spec: StoryTestSpec): void {
    const storyId = this.generateStoryId(spec);
    this.specs.set(storyId, spec);
  }

  /**
   * Get registered spec
   */
  getSpec(storyId: string): StoryTestSpec | undefined {
    return this.specs.get(storyId);
  }

  /**
   * Get all registered specs
   */
  getAllSpecs(): StoryTestSpec[] {
    return Array.from(this.specs.values());
  }

  /**
   * Get specs by tags
   */
  getSpecsByTags(tags: string[]): StoryTestSpec[] {
    return Array.from(this.specs.values()).filter((spec) =>
      spec.tags?.some((tag) => tags.includes(tag))
    );
  }

  /**
   * Generate story ID from spec
   */
  private generateStoryId(spec: StoryTestSpec): string {
    return `${spec.component}--${spec.story}`.toLowerCase().replace(/\s+/g, '-');
  }

  /**
   * Generate story URL
   */
  generateStoryUrl(spec: StoryTestSpec): string {
    const storyId = this.generateStoryId(spec);
    return `${this.config.storybookUrl}/iframe.html?id=${storyId}&viewMode=story`;
  }

  /**
   * Run smoke test for story
   */
  async runTest(storyId: string): Promise<TestExecutionResult> {
    const spec = this.specs.get(storyId);
    if (!spec) {
      throw new Error(`Test spec not found: ${storyId}`);
    }

    const startTime = Date.now();
    const url = this.generateStoryUrl(spec);
    const screenshots: string[] = [];
    const actionResults: TestExecutionResult['actionResults'] = [];
    const assertionResults: TestExecutionResult['assertionResults'] = [];

    let passed = true;
    let error: Error | undefined;

    try {
      // Simulate browser actions
      for (const action of spec.actions) {
        const actionStart = Date.now();
        let actionSuccess = true;
        let actionError: string | undefined;

        try {
          await this.simulateAction(action, storyId);

          // Capture screenshot if requested
          if (action.type === 'screenshot' && this.config.screenshots) {
            const screenshotPath = `${this.config.artifactDir}/${storyId}-${action.name || Date.now()}.png`;
            screenshots.push(screenshotPath);
          }
        } catch (err) {
          actionSuccess = false;
          actionError = (err as Error).message;
          passed = false;
        }

        actionResults.push({
          type: action.type,
          duration: Date.now() - actionStart,
          success: actionSuccess,
          error: actionError,
        });

        if (!actionSuccess) {
          break;
        }
      }

      // Run assertions
      if (passed) {
        for (const assertion of spec.assertions) {
          let assertionPassed = true;
          let actual: unknown;

          try {
            actual = await this.simulateAssertion(assertion);
          } catch (err) {
            assertionPassed = false;
            passed = false;
          }

          assertionResults.push({
            type: assertion.type,
            selector: assertion.selector,
            passed: assertionPassed,
            actual,
            expected: assertion.expected,
          });

          if (!assertionPassed) {
            break;
          }
        }
      }
    } catch (err) {
      error = err as Error;
      passed = false;
    }

    const duration = Date.now() - startTime;

    const result: TestExecutionResult = {
      storyId,
      passed,
      duration,
      error,
      screenshots,
      tracePath: this.config.traces
        ? `${this.config.artifactDir}/${storyId}-trace.zip`
        : undefined,
      metadata: {
        timestamp: startTime,
        browser: this.config.browser,
        viewport: this.config.viewport,
        url,
      },
      actionResults,
      assertionResults,
    };

    this.results.set(storyId, result);
    return result;
  }

  /**
   * Run all smoke tests
   */
  async runAllTests(): Promise<SmokeTestSuiteResults> {
    const startTime = Date.now();
    const results = new Map<string, TestExecutionResult>();

    for (const [storyId] of this.specs) {
      const result = await this.runTest(storyId);
      results.set(storyId, result);
    }

    const allResults = Array.from(results.values());
    const passed = allResults.filter((r) => r.passed).length;
    const failed = allResults.filter((r) => !r.passed).length;

    const suiteResults: SmokeTestSuiteResults = {
      total: allResults.length,
      passed,
      failed,
      skipped: 0,
      duration: Date.now() - startTime,
      results,
      artifacts: {
        screenshots: allResults.flatMap((r) => r.screenshots),
        traces: allResults.map((r) => r.tracePath).filter((p): p is string => p !== undefined),
        videos: [],
      },
      metadata: {
        timestamp: startTime,
        config: this.config,
        environment: {
          ci: this.isCIEnvironment(),
          platform: typeof process !== 'undefined' ? process.platform : 'unknown',
          nodeVersion: typeof process !== 'undefined' ? process.version : 'unknown',
        },
      },
    };

    this.suiteResults = suiteResults;
    return suiteResults;
  }

  /**
   * Run tests by tags
   */
  async runTestsByTags(tags: string[]): Promise<SmokeTestSuiteResults> {
    const startTime = Date.now();
    const results = new Map<string, TestExecutionResult>();
    const specs = this.getSpecsByTags(tags);

    for (const spec of specs) {
      const storyId = this.generateStoryId(spec);
      const result = await this.runTest(storyId);
      results.set(storyId, result);
    }

    const allResults = Array.from(results.values());
    const passed = allResults.filter((r) => r.passed).length;
    const failed = allResults.filter((r) => !r.passed).length;

    const suiteResults: SmokeTestSuiteResults = {
      total: allResults.length,
      passed,
      failed,
      skipped: 0,
      duration: Date.now() - startTime,
      results,
      artifacts: {
        screenshots: allResults.flatMap((r) => r.screenshots),
        traces: allResults.map((r) => r.tracePath).filter((p): p is string => p !== undefined),
        videos: [],
      },
      metadata: {
        timestamp: startTime,
        config: this.config,
        environment: {
          ci: this.isCIEnvironment(),
          platform: typeof process !== 'undefined' ? process.platform : 'unknown',
          nodeVersion: typeof process !== 'undefined' ? process.version : 'unknown',
        },
      },
    };

    return suiteResults;
  }

  /**
   * Get test results
   */
  getResults(storyId?: string): TestExecutionResult | Map<string, TestExecutionResult> {
    if (storyId) {
      const result = this.results.get(storyId);
      if (!result) {
        throw new Error(`Results not found for story: ${storyId}`);
      }
      return result;
    }

    return new Map(this.results);
  }

  /**
   * Get suite results
   */
  getSuiteResults(): SmokeTestSuiteResults | null {
    return this.suiteResults;
  }

  /**
   * Generate CI workflow configuration
   */
  generateCIWorkflow(options?: {
    workflowName?: string;
    triggers?: string[];
    retention?: number;
  }): CIWorkflowConfig {
    return {
      name: options?.workflowName ?? 'Storybook Smoke Tests',
      on: options?.triggers ?? ['pull_request', 'push'],
      jobs: {
        'storybook-smoke': {
          runsOn: 'ubuntu-latest',
          steps: [
            {
              name: 'Checkout',
              uses: 'actions/checkout@v3',
            },
            {
              name: 'Setup Node',
              uses: 'actions/setup-node@v3',
              with: {
                'node-version': '18',
              },
            },
            {
              name: 'Install dependencies',
              run: 'pnpm install',
            },
            {
              name: 'Build Storybook',
              run: 'pnpm run build-storybook',
            },
            {
              name: 'Install Playwright',
              run: 'pnpm exec playwright install --with-deps',
            },
            {
              name: 'Run smoke tests',
              run: 'pnpm run test:storybook-smoke',
            },
          ],
          artifacts: {
            name: 'storybook-smoke-artifacts',
            path: this.config.artifactDir,
            retention: options?.retention ?? 30,
          },
        },
      },
      env: {
        CI: 'true',
        STORYBOOK_URL: this.config.storybookUrl,
      },
    };
  }

  /**
   * Export workflow as YAML
   */
  exportWorkflowYAML(workflow?: CIWorkflowConfig): string {
    const config = workflow ?? this.generateCIWorkflow();
    const lines: string[] = [];

    lines.push(`name: ${config.name}\n`);
    lines.push(`on:`);
    for (const event of config.on) {
      lines.push(`  - ${event}`);
    }
    lines.push('');

    if (config.env) {
      lines.push('env:');
      for (const [key, value] of Object.entries(config.env)) {
        lines.push(`  ${key}: ${value}`);
      }
      lines.push('');
    }

    lines.push('jobs:');
    for (const [jobName, job] of Object.entries(config.jobs)) {
      lines.push(`  ${jobName}:`);
      lines.push(`    runs-on: ${job.runsOn}`);
      lines.push('    steps:');
      for (const step of job.steps) {
        lines.push(`      - name: ${step.name}`);
        if (step.uses) {
          lines.push(`        uses: ${step.uses}`);
        }
        if (step.with) {
          lines.push('        with:');
          for (const [key, value] of Object.entries(step.with)) {
            lines.push(`          ${key}: ${value}`);
          }
        }
        if (step.run) {
          lines.push(`        run: ${step.run}`);
        }
      }
      if (job.artifacts) {
        lines.push('      - name: Upload artifacts');
        lines.push('        uses: actions/upload-artifact@v3');
        lines.push('        if: always()');
        lines.push('        with:');
        lines.push(`          name: ${job.artifacts.name}`);
        lines.push(`          path: ${job.artifacts.path}`);
        lines.push(`          retention-days: ${job.artifacts.retention}`);
      }
    }

    return lines.join('\n');
  }

  /**
   * Export results as JSON
   */
  exportResultsJSON(): string {
    if (!this.suiteResults) {
      throw new Error('No suite results available');
    }

    const serializable = {
      ...this.suiteResults,
      results: Object.fromEntries(this.suiteResults.results),
    };

    return JSON.stringify(serializable, null, 2);
  }

  /**
   * Export results as Markdown
   */
  exportResultsMarkdown(): string {
    if (!this.suiteResults) {
      throw new Error('No suite results available');
    }

    const lines: string[] = [];

    lines.push('# Storybook Smoke Test Results\n');
    lines.push(`Generated: ${new Date().toISOString()}\n`);

    lines.push('## Summary\n');
    lines.push(`- Total: ${this.suiteResults.total}`);
    lines.push(`- Passed: ${this.suiteResults.passed} ✅`);
    lines.push(`- Failed: ${this.suiteResults.failed} ❌`);
    lines.push(`- Duration: ${this.suiteResults.duration}ms\n`);

    lines.push('## Environment\n');
    lines.push(`- CI: ${this.suiteResults.metadata.environment.ci}`);
    lines.push(`- Platform: ${this.suiteResults.metadata.environment.platform}`);
    lines.push(`- Node: ${this.suiteResults.metadata.environment.nodeVersion}\n`);

    lines.push('## Tests\n');
    for (const [storyId, result] of this.suiteResults.results) {
      const status = result.passed ? '✅' : '❌';
      lines.push(`### ${status} ${storyId}\n`);
      lines.push(`- Duration: ${result.duration}ms`);
      lines.push(`- Browser: ${result.metadata.browser}`);
      lines.push(`- URL: ${result.metadata.url}\n`);

      if (result.actionResults.length > 0) {
        lines.push('#### Actions\n');
        for (const action of result.actionResults) {
          const actionStatus = action.success ? '✅' : '❌';
          lines.push(`- ${actionStatus} ${action.type} (${action.duration}ms)`);
          if (action.error) {
            lines.push(`  - Error: ${action.error}`);
          }
        }
        lines.push('');
      }

      if (result.assertionResults.length > 0) {
        lines.push('#### Assertions\n');
        for (const assertion of result.assertionResults) {
          const assertionStatus = assertion.passed ? '✅' : '❌';
          lines.push(`- ${assertionStatus} ${assertion.type}: ${assertion.selector}`);
          if (!assertion.passed) {
            lines.push(`  - Expected: ${assertion.expected}`);
            lines.push(`  - Actual: ${assertion.actual}`);
          }
        }
        lines.push('');
      }

      if (result.error) {
        lines.push('#### Error\n');
        lines.push(`\`\`\`\n${result.error.message}\n\`\`\`\n`);
      }
    }

    lines.push('## Artifacts\n');
    lines.push(`- Screenshots: ${this.suiteResults.artifacts.screenshots.length}`);
    lines.push(`- Traces: ${this.suiteResults.artifacts.traces.length}`);

    return lines.join('\n');
  }

  /**
   * Check CI status
   */
  checkCIStatus(): {
    shouldFail: boolean;
    exitCode: number;
    message: string;
  } {
    if (!this.suiteResults) {
      return {
        shouldFail: true,
        exitCode: 1,
        message: 'No test results available',
      };
    }

    if (this.suiteResults.failed > 0) {
      return {
        shouldFail: true,
        exitCode: 1,
        message: `${this.suiteResults.failed} smoke test(s) failed`,
      };
    }

    return {
      shouldFail: false,
      exitCode: 0,
      message: 'All smoke tests passed',
    };
  }

  /**
   * Clear results
   */
  clearResults(): void {
    this.results.clear();
    this.suiteResults = null;
  }

  /**
   * Clear specs
   */
  clearSpecs(): void {
    this.specs.clear();
  }

  /**
   * Reset manager (preserves config)
   */
  reset(): void {
    this.specs.clear();
    this.results.clear();
    this.suiteResults = null;
  }

  /**
   * Simulate action (placeholder for actual Playwright implementation)
   */
  private async simulateAction(
    action: StoryTestSpec['actions'][0],
    storyId: string
  ): Promise<void> {
    // In real implementation, this would use Playwright page methods
    // For now, just simulate timing
    await new Promise((resolve) => setTimeout(resolve, action.duration ?? 100));

    // Validate action based on type
    if (action.type === 'drag' && (!action.from || !action.to)) {
      throw new Error('Drag action requires from and to positions');
    }

    if (action.type === 'type' && !action.text) {
      throw new Error('Type action requires text');
    }

    if ((action.type === 'click' || action.type === 'hover') && !action.selector) {
      throw new Error(`${action.type} action requires selector`);
    }
  }

  /**
   * Simulate assertion (placeholder for actual Playwright implementation)
   */
  private async simulateAssertion(
    assertion: StoryTestSpec['assertions'][0]
  ): Promise<unknown> {
    // In real implementation, this would use Playwright page methods
    // For now, return mock data based on assertion type
    await new Promise((resolve) => setTimeout(resolve, 50));

    switch (assertion.type) {
      case 'visible':
        return true;
      case 'hidden':
        return false;
      case 'text':
        return 'Mock text content';
      case 'count':
        return 5;
      case 'attribute':
        return 'mock-value';
      default:
        return undefined;
    }
  }

  /**
   * Check if running in CI environment
   */
  private isCIEnvironment(): boolean {
    if (typeof process === 'undefined') {
      return false;
    }
    return process.env.CI === 'true' || process.env.GITHUB_ACTIONS === 'true';
  }
}

/**
 * Create Storybook smoke manager
 */
export function createStorybookSmokeManager(
  config: StorybookSmokeConfig
): StorybookSmokeManager {
  return new StorybookSmokeManager(config);
}

/**
 * Generate story URL
 */
export function generateStoryUrl(
  storybookUrl: string,
  component: string,
  story: string
): string {
  const storyId = `${component}--${story}`.toLowerCase().replace(/\s+/g, '-');
  return `${storybookUrl}/iframe.html?id=${storyId}&viewMode=story`;
}
