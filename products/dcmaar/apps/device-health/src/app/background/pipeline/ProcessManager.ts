/**
 * @fileoverview Process Manager
 *
 * Manages the execution of custom processes defined by ProcessContract.
 * Orchestrates the collect → analyze → report pipeline with security,
 * privacy, and resource management.
 *
 * @module pipeline/ProcessManager
 * @since 2.0.0
 */

import type { Event } from '@ghatana/dcmaar-connectors';
import {
  ProcessContract,
  ProcessExecutionContext,
  ProcessExecutionResult,
  StageExecutionResult,
  ProcessStage,
  ProcessStatus,
  evaluateCondition,
  validateProcessContract,
} from '../contracts/process';

/**
 * Process manager for orchestrating pipeline execution
 */
export class ProcessManager {
  private processes = new Map<string, ProcessContract>();
  private executions = new Map<string, ProcessExecutionResult>();
  private activeExecutions = new Map<string, AbortController>();
  private scheduledJobs = new Map<string, NodeJS.Timeout>();

  private collectors = new Map<string, ProcessCollector>();
  private analyzers = new Map<string, ProcessAnalyzer>();
  private reporters = new Map<string, ProcessReporter>();

  private logger: any;
  private metrics: any;
  private featureFlags: Record<string, boolean>;
  private featureResolver?: (feature: string) => boolean;

  constructor(config: { logger?: any; metrics?: any; featureFlags?: Record<string, boolean>; featureResolver?: (feature: string) => boolean } = {}) {
    this.logger = config.logger || console;
    this.metrics = config.metrics || {
      increment: () => {},
      gauge: () => {},
      timing: () => {},
    };
    this.featureFlags = { ...(config.featureFlags ?? {}) };
    this.featureResolver = config.featureResolver;
  }

  setFeatureResolver(resolver: (feature: string) => boolean): void {
    this.featureResolver = resolver;
  }

  updateFeatureFlags(flags: Record<string, boolean>): void {
    this.featureFlags = { ...flags };
  }

  private isFeatureEnabled(feature: string): boolean {
    if (this.featureResolver) {
      try {
        return this.featureResolver(feature);
      } catch (error) {
        this.logger.warn('Feature resolver threw error', { feature, error });
      }
    }

    const value = this.featureFlags[feature];
    return value !== false;
  }

  private requiredFeaturesEnabled(contract: ProcessContract): boolean {
    if (!contract.requiredFeatures?.length) {
      return true;
    }
    return contract.requiredFeatures.every((feature) => this.isFeatureEnabled(feature));
  }

  // ==========================================================================
  // Process Registration
  // ==========================================================================

  /**
   * Registers a process contract
   *
   * @param contract - Process contract to register
   * @returns Registration result
   */
  async registerProcess(contract: ProcessContract): Promise<{ success: boolean; error?: string }> {
    try {
      // Validate contract
      const validation = validateProcessContract(contract);
      if (!validation.valid) {
        return { success: false, error: validation.error };
      }

      // Check if already registered
      if (this.processes.has(contract.id)) {
        this.logger.warn(`Process ${contract.id} already registered, updating...`);
      }

      // Store contract
      this.processes.set(contract.id, contract);

      // Set up scheduled execution if configured
      const featuresAvailable = this.requiredFeaturesEnabled(contract);
      if (!featuresAvailable) {
        this.logger.info(`Process ${contract.id} registered but required features are disabled`, {
          requiredFeatures: contract.requiredFeatures,
        });
      } else if (contract.schedule && contract.enabled) {
        await this.scheduleProcess(contract.id);
      }

      this.logger.info(`Process registered: ${contract.id}`, {
        name: contract.metadata.name,
        version: contract.metadata.version,
      });

      this.metrics.increment('process.registered', 1, { processId: contract.id });

      return { success: true };
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      this.logger.error(`Failed to register process: ${errorMessage}`, { error });
      return { success: false, error: errorMessage };
    }
  }

  /**
   * Unregisters a process
   *
   * @param processId - Process ID to unregister
   */
  async unregisterProcess(processId: string): Promise<void> {
    // Cancel scheduled job
    const timeout = this.scheduledJobs.get(processId);
    if (timeout) {
      clearTimeout(timeout);
      this.scheduledJobs.delete(processId);
    }

    // Abort active executions
    const controller = this.activeExecutions.get(processId);
    if (controller) {
      controller.abort();
      this.activeExecutions.delete(processId);
    }

    // Remove process
    this.processes.delete(processId);

    this.logger.info(`Process unregistered: ${processId}`);
    this.metrics.increment('process.unregistered', 1, { processId });
  }

  /**
   * Lists all registered processes
   */
  listProcesses(): ProcessContract[] {
    return Array.from(this.processes.values());
  }

  /**
   * Gets a process by ID
   */
  getProcess(processId: string): ProcessContract | undefined {
    return this.processes.get(processId);
  }

  // ==========================================================================
  // Process Execution
  // ==========================================================================

  /**
   * Executes a process
   *
   * @param processId - Process ID to execute
   * @param variables - Runtime variables to pass to process
   * @returns Execution result
   */
  async executeProcess(
    processId: string,
    variables: Record<string, any> = {}
  ): Promise<ProcessExecutionResult> {
    const contract = this.processes.get(processId);
    if (!contract) {
      throw new Error(`Process not found: ${processId}`);
    }

    if (!contract.enabled) {
      throw new Error(`Process disabled: ${processId}`);
    }

    if (!this.requiredFeaturesEnabled(contract)) {
      throw new Error(`Required features disabled: ${processId}`);
    }

    // Check concurrent execution limit
    const activeCount = Array.from(this.activeExecutions.keys()).filter(
      (id) => id.startsWith(processId)
    ).length;

    if (activeCount >= contract.maxConcurrentExecutions) {
      throw new Error(
        `Max concurrent executions reached for process ${processId}: ${contract.maxConcurrentExecutions}`
      );
    }

    const executionId = `${processId}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
    const abortController = new AbortController();
    this.activeExecutions.set(executionId, abortController);

    const startTime = Date.now();
    const stages: StageExecutionResult[] = [];
    const errors: Error[] = [];

    const context: ProcessExecutionContext = {
      processId,
      executionId,
      currentStage: 'collect',
      startTime,
      variables: { ...contract.variables, ...variables },
      logger: this.logger,
      metrics: this.metrics,
      signal: abortController.signal,
    };

    let status: ProcessStatus = 'running';
    let output: any = null;

    this.logger.info(`Executing process: ${processId}`, { executionId });
    this.metrics.increment('process.execution.started', 1, { processId });

    try {
      // Set global timeout
      const timeoutPromise = new Promise<never>((_, reject) => {
        setTimeout(() => reject(new Error('Process timeout')), contract.timeoutMs);
      });

      // Execute pipeline with timeout
      await Promise.race([
        this.executePipeline(contract, context, stages, errors),
        timeoutPromise,
      ]);

      status = errors.length > 0 ? 'error' : 'completed';
      output = context.analysisResults;
    } catch (error) {
      status = 'error';
      errors.push(error instanceof Error ? error : new Error(String(error)));
      this.logger.error(`Process execution failed: ${processId}`, { error, executionId });
    } finally {
      this.activeExecutions.delete(executionId);
    }

    const endTime = Date.now();
    const duration = endTime - startTime;

    const result: ProcessExecutionResult = {
      processId,
      executionId,
      status,
      startTime,
      endTime,
      duration,
      stages,
      output,
      errors,
      summary: {
        eventsCollected: context.collectedData?.length || 0,
        eventsAnalyzed: context.analysisResults?.length || 0,
        reportsGenerated: stages.filter((s) => s.stage === 'report' && s.status === 'success')
          .length,
        errorsEncountered: errors.length,
      },
      metadata: {
        processName: contract.metadata.name,
        processVersion: contract.metadata.version,
      },
    };

    this.executions.set(executionId, result);
    this.metrics.increment('process.execution.completed', 1, {
      processId,
      status,
    });
    this.metrics.timing('process.execution.duration', duration, { processId });

    return result;
  }

  /**
   * Executes the full pipeline (collect → analyze → report)
   */
  private async executePipeline(
    contract: ProcessContract,
    context: ProcessExecutionContext,
    stages: StageExecutionResult[],
    errors: Error[]
  ): Promise<void> {
    // COLLECT STAGE
    context.currentStage = 'collect';
    const collectResult = await this.executeStage(
      'collect',
      async () => this.executeCollectStage(contract, context),
      contract.collect.timeoutMs
    );
    stages.push(collectResult);

    if (collectResult.status === 'error') {
      errors.push(collectResult.error!);
      if (contract.onError === 'halt') return;
    } else {
      context.collectedData = collectResult.data;
    }

    // ANALYZE STAGE
    context.currentStage = 'analyze';
    const analyzeResult = await this.executeStage(
      'analyze',
      async () => this.executeAnalyzeStage(contract, context),
      contract.analyze.timeoutMs
    );
    stages.push(analyzeResult);

    if (analyzeResult.status === 'error') {
      errors.push(analyzeResult.error!);
      if (contract.onError === 'halt') return;
    } else {
      context.analysisResults = analyzeResult.data;
    }

    // REPORT STAGE
    context.currentStage = 'report';
    const reportResult = await this.executeStage(
      'report',
      async () => this.executeReportStage(contract, context),
      contract.report.timeoutMs
    );
    stages.push(reportResult);

    if (reportResult.status === 'error') {
      errors.push(reportResult.error!);
    }
  }

  /**
   * Executes a single stage with timeout and error handling
   */
  private async executeStage(
    stage: ProcessStage,
    executor: () => Promise<any>,
    timeoutMs: number
  ): Promise<StageExecutionResult> {
    const startTime = Date.now();

    try {
      const timeoutPromise = new Promise<never>((_, reject) => {
        setTimeout(() => reject(new Error(`Stage timeout: ${stage}`)), timeoutMs);
      });

      const data = await Promise.race([executor(), timeoutPromise]);

      const endTime = Date.now();
      return {
        stage,
        status: 'success',
        startTime,
        endTime,
        duration: endTime - startTime,
        data,
      };
    } catch (error) {
      const endTime = Date.now();
      const err = error instanceof Error ? error : new Error(String(error));

      return {
        stage,
        status: err.message.includes('timeout') ? 'timeout' : 'error',
        startTime,
        endTime,
        duration: endTime - startTime,
        error: err,
      };
    }
  }

  /**
   * Executes the collect stage
   */
  private async executeCollectStage(
    contract: ProcessContract,
    context: ProcessExecutionContext
  ): Promise<Event[]> {
    const { collect } = contract;

    // Check execution conditions
    if (collect.when && !evaluateCondition(collect.when, context.variables)) {
      this.logger.debug('Collect stage skipped (condition not met)');
      return [];
    }

    const collectedEvents: Event[] = [];

    // Collect from all sources
    for (const source of collect.sources) {
      try {
        const collector = this.getCollector(source.type);
        const events = await collector.collect(source, context);
        collectedEvents.push(...events);
      } catch (error) {
        this.logger.error(`Collection failed for source ${source.id}`, { error });
        if (contract.onError === 'halt') throw error;
      }
    }

    // Apply privacy controls
    const sanitizedEvents = await this.applyPrivacyControls(collectedEvents, contract.privacy);

    return sanitizedEvents;
  }

  /**
   * Executes the analyze stage
   */
  private async executeAnalyzeStage(
    contract: ProcessContract,
    context: ProcessExecutionContext
  ): Promise<any> {
    const { analyze } = contract;

    // Check execution conditions
    if (analyze.when && !evaluateCondition(analyze.when, context.variables)) {
      this.logger.debug('Analyze stage skipped (condition not met)');
      return context.collectedData;
    }

    if (!context.collectedData || context.collectedData.length === 0) {
      this.logger.warn('No data to analyze');
      return [];
    }

    let analysisResults = context.collectedData;

    // Sort operations by order
    const sortedOps = [...analyze.operations].sort((a, b) => a.order - b.order);

    // Execute operations
    if (analyze.parallel) {
      // Parallel execution
      const results = await Promise.all(
        sortedOps.map(async (op) => {
          try {
            const analyzer = this.getAnalyzer(op.strategy);
            return await analyzer.analyze(analysisResults, op, context);
          } catch (error) {
            if (!op.continueOnError) throw error;
            this.logger.warn(`Analysis operation ${op.id} failed, continuing...`, { error });
            return analysisResults;
          }
        })
      );
      analysisResults = results[results.length - 1]; // Take last result
    } else {
      // Sequential execution
      for (const op of sortedOps) {
        try {
          const analyzer = this.getAnalyzer(op.strategy);
          analysisResults = await analyzer.analyze(analysisResults, op, context);
        } catch (error) {
          if (!op.continueOnError) throw error;
          this.logger.warn(`Analysis operation ${op.id} failed, continuing...`, { error });
        }
      }
    }

    return analysisResults;
  }

  /**
   * Executes the report stage
   */
  private async executeReportStage(
    contract: ProcessContract,
    context: ProcessExecutionContext
  ): Promise<void> {
    const { report } = contract;

    // Check execution conditions
    if (report.when && !evaluateCondition(report.when, context.variables)) {
      this.logger.debug('Report stage skipped (condition not met)');
      return;
    }

    const reportData = {
      processId: context.processId,
      executionId: context.executionId,
      timestamp: Date.now(),
      data: context.analysisResults,
      metadata: report.includeMetadata
        ? {
            processName: contract.metadata.name,
            processVersion: contract.metadata.version,
            variables: context.variables,
          }
        : undefined,
      summary: report.includeSummary
        ? {
            eventsCollected: context.collectedData?.length || 0,
            eventsAnalyzed: Array.isArray(context.analysisResults)
              ? context.analysisResults.length
              : 1,
          }
        : undefined,
    };

    // Send to all destinations
    await Promise.all(
      report.destinations.map(async (dest) => {
        // Check destination-specific conditions
        if (dest.when && !evaluateCondition(dest.when, reportData)) {
          this.logger.debug(`Report destination ${dest.id} skipped (condition not met)`);
          return;
        }

        try {
          const reporter = this.getReporter(dest.type);
          await reporter.report(reportData, dest, context);
        } catch (error) {
          this.logger.error(`Report failed for destination ${dest.id}`, { error });
          if (contract.onError === 'halt') throw error;
        }
      })
    );
  }

  // ==========================================================================
  // Privacy & Security
  // ==========================================================================

  /**
   * Applies privacy controls to events
   */
  private async applyPrivacyControls(
    events: Event[],
    privacy: ProcessContract['privacy']
  ): Promise<Event[]> {
    return events.map((event) => {
      let sanitized = { ...event };

      // Apply redaction rules
      for (const rule of privacy.redactionRules) {
        const regex = new RegExp(rule.pattern, 'gi');

        // Redact in payload
        if (typeof sanitized.payload === 'string') {
          sanitized.payload = sanitized.payload.replace(regex, rule.replacement);
        } else if (typeof sanitized.payload === 'object') {
          sanitized.payload = this.redactObject(sanitized.payload, regex, rule.replacement);
        }

        // Redact in metadata
        if (sanitized.metadata) {
          sanitized.metadata = this.redactObject(sanitized.metadata, regex, rule.replacement);
        }
      }

      return sanitized;
    });
  }

  /**
   * Recursively redacts strings in object
   */
  private redactObject(obj: any, regex: RegExp, replacement: string): any {
    if (typeof obj === 'string') {
      return obj.replace(regex, replacement);
    }

    if (Array.isArray(obj)) {
      return obj.map((item) => this.redactObject(item, regex, replacement));
    }

    if (obj && typeof obj === 'object') {
      const result: any = {};
      for (const [key, value] of Object.entries(obj)) {
        result[key] = this.redactObject(value, regex, replacement);
      }
      return result;
    }

    return obj;
  }

  // ==========================================================================
  // Component Registration
  // ==========================================================================

  /**
   * Registers a collector
   */
  registerCollector(type: string, collector: ProcessCollector): void {
    this.collectors.set(type, collector);
  }

  /**
   * Registers an analyzer
   */
  registerAnalyzer(strategy: string, analyzer: ProcessAnalyzer): void {
    this.analyzers.set(strategy, analyzer);
  }

  /**
   * Registers a reporter
   */
  registerReporter(type: string, reporter: ProcessReporter): void {
    this.reporters.set(type, reporter);
  }

  /**
   * Gets a collector
   */
  private getCollector(type: string): ProcessCollector {
    const collector = this.collectors.get(type);
    if (!collector) {
      throw new Error(`Collector not found: ${type}`);
    }
    return collector;
  }

  /**
   * Gets an analyzer
   */
  private getAnalyzer(strategy: string): ProcessAnalyzer {
    const analyzer = this.analyzers.get(strategy);
    if (!analyzer) {
      throw new Error(`Analyzer not found: ${strategy}`);
    }
    return analyzer;
  }

  /**
   * Gets a reporter
   */
  private getReporter(type: string): ProcessReporter {
    const reporter = this.reporters.get(type);
    if (!reporter) {
      throw new Error(`Reporter not found: ${type}`);
    }
    return reporter;
  }

  // ==========================================================================
  // Scheduling
  // ==========================================================================

  /**
   * Schedules a process for execution
   */
  private async scheduleProcess(processId: string): Promise<void> {
    const contract = this.processes.get(processId);
    if (!contract) {
      this.logger.warn('Attempted to schedule unknown process', { processId });
      return;
    }

    if (!this.requiredFeaturesEnabled(contract)) {
      this.logger.info('Skipping schedule for process due to disabled features', {
        processId,
        requiredFeatures: contract.requiredFeatures,
      });
      return;
    }

    // For now, just log - proper cron scheduling would require a library like node-cron
    this.logger.info(`Process scheduled: ${processId}`);
    // TODO: Implement actual cron scheduling
  }

  /**
   * Gets execution history
   */
  getExecutions(processId?: string): ProcessExecutionResult[] {
    const results = Array.from(this.executions.values());
    return processId ? results.filter((r) => r.processId === processId) : results;
  }

  /**
   * Clears execution history
   */
  clearExecutions(processId?: string): void {
    if (processId) {
      for (const [id, result] of this.executions) {
        if (result.processId === processId) {
          this.executions.delete(id);
        }
      }
    } else {
      this.executions.clear();
    }
  }
}

// ============================================================================
// Component Interfaces
// ============================================================================

/**
 * Process collector interface
 */
export interface ProcessCollector {
  collect(
    source: any,
    context: ProcessExecutionContext
  ): Promise<Event[]>;
}

/**
 * Process analyzer interface
 */
export interface ProcessAnalyzer {
  analyze(
    data: any,
    operation: any,
    context: ProcessExecutionContext
  ): Promise<any>;
}

/**
 * Process reporter interface
 */
export interface ProcessReporter {
  report(
    data: any,
    destination: any,
    context: ProcessExecutionContext
  ): Promise<void>;
}
