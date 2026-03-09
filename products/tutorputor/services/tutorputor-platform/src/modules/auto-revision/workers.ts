/**
 * @doc.type module
 * @doc.purpose Auto-Revision background workers
 * @doc.layer product
 * @doc.pattern Worker
 */

import type { AutoRevisionService } from './service';
import type { AutoRevisionModuleConfig } from './types';
import type { Logger } from 'pino';

// ============================================================================
// Drift Monitor Worker
// ============================================================================

export class DriftMonitorWorker {
    private interval: NodeJS.Timeout | null = null;
    private isRunning = false;

    constructor(
        private readonly autoRevisionService: AutoRevisionService,
        private readonly config: AutoRevisionModuleConfig['driftMonitoring'],
        private readonly logger: Logger,
    ) { }

    start(): void {
        if (this.isRunning || !this.config.enabled) {
            return;
        }

        this.isRunning = true;
        this.logger.info('Starting drift monitor worker');

        // Schedule periodic drift checks
        this.interval = setInterval(
            async () => {
                try {
                    this.logger.debug('Running drift monitoring check');
                    const candidates = await this.autoRevisionService.monitorDrift();
                    this.logger.info({ candidates: candidates.length }, 'Detected experiences needing regeneration');
                } catch (error) {
                    this.logger.error({ err: error }, 'Drift monitoring error');
                }
            },
            this.config.intervalHours * 60 * 60 * 1000
        );

        // Run immediately on start
        this.runDriftCheck();
    }

    stop(): void {
        if (this.interval) {
            clearInterval(this.interval);
            this.interval = null;
        }
        this.isRunning = false;
        this.logger.info('Drift monitor worker stopped');
    }

    private async runDriftCheck(): Promise<void> {
        try {
            const candidates = await this.autoRevisionService.monitorDrift();
            this.logger.info(
                { candidates: candidates.length },
                'Drift check completed',
            );
        } catch (error) {
            this.logger.error({ err: error }, 'Initial drift check failed');
        }
    }

    getStatus(): { running: boolean; lastCheck?: Date; nextCheck?: Date } {
        return {
            running: this.isRunning,
            // Add timestamp tracking if needed
        };
    }
}

// ============================================================================
// Regeneration Worker
// ============================================================================

export class RegenerationWorker {
    private interval: NodeJS.Timeout | null = null;
    private isRunning = false;

    constructor(
        private readonly autoRevisionService: AutoRevisionService,
        private readonly config: AutoRevisionModuleConfig['regeneration'],
        private readonly logger: Logger,
    ) { }

    start(): void {
        if (this.isRunning || !this.config.enabled) {
            return;
        }

        this.isRunning = true;
        this.logger.info('Starting regeneration worker');

        // Process queue every 5 minutes
        this.interval = setInterval(
            async () => {
                try {
                    this.logger.debug('Processing regeneration queue');
                    await this.autoRevisionService.processRegenerationQueue();
                    this.logger.info('Regeneration queue processed');
                } catch (error) {
                    this.logger.error({ err: error }, 'Regeneration processing error');
                }
            },
            5 * 60 * 1000 // 5 minutes
        );

        // Process immediately on start
        this.processQueue();
    }

    stop(): void {
        if (this.interval) {
            clearInterval(this.interval);
            this.interval = null;
        }
        this.isRunning = false;
        this.logger.info('Regeneration worker stopped');
    }

    private async processQueue(): Promise<void> {
        try {
            await this.autoRevisionService.processRegenerationQueue();
            this.logger.info('Initial regeneration queue processed');
        } catch (error) {
            this.logger.error({ err: error }, 'Initial regeneration processing failed');
        }
    }

    getStatus(): { running: boolean; processingJobs: number } {
        return {
            running: this.isRunning,
            processingJobs: 0, // Add job tracking if needed
        };
    }
}

// ============================================================================
// A/B Test Evaluator Worker
// ============================================================================

export class ABTestEvaluatorWorker {
    private interval: NodeJS.Timeout | null = null;
    private isRunning = false;

    constructor(
        private readonly autoRevisionService: AutoRevisionService,
        private readonly config: AutoRevisionModuleConfig['abTesting'],
        private readonly logger: Logger,
    ) { }

    start(): void {
        if (this.isRunning || !this.config.enabled) {
            return;
        }

        this.isRunning = true;
        this.logger.info('Starting A/B test evaluator worker');

        // Evaluate experiments every hour
        this.interval = setInterval(
            async () => {
                try {
                    this.logger.debug('Evaluating A/B experiments');
                    await this.autoRevisionService.evaluateABExperiments();
                    this.logger.info('A/B experiments evaluated');
                } catch (error) {
                    this.logger.error({ err: error }, 'A/B test evaluation error');
                }
            },
            60 * 60 * 1000 // 1 hour
        );

        // Evaluate immediately on start
        this.evaluateExperiments();
    }

    stop(): void {
        if (this.interval) {
            clearInterval(this.interval);
            this.interval = null;
        }
        this.isRunning = false;
        this.logger.info('A/B test evaluator worker stopped');
    }

    private async evaluateExperiments(): Promise<void> {
        try {
            await this.autoRevisionService.evaluateABExperiments();
            this.logger.info('Initial A/B experiment evaluation completed');
        } catch (error) {
            this.logger.error({ err: error }, 'Initial A/B experiment evaluation failed');
        }
    }

    getStatus(): { running: boolean; runningExperiments: number } {
        return {
            running: this.isRunning,
            runningExperiments: 0, // Add experiment tracking if needed
        };
    }
}

// ============================================================================
// Worker Manager
// ============================================================================

export class AutoRevisionWorkerManager {
    private driftMonitor: DriftMonitorWorker;
    private regenerationWorker: RegenerationWorker;
    private abTestEvaluator: ABTestEvaluatorWorker;

    constructor(
        autoRevisionService: AutoRevisionService,
        config: AutoRevisionModuleConfig,
        private readonly logger: Logger,
    ) {
        this.driftMonitor = new DriftMonitorWorker(autoRevisionService, config.driftMonitoring, logger);
        this.regenerationWorker = new RegenerationWorker(autoRevisionService, config.regeneration, logger);
        this.abTestEvaluator = new ABTestEvaluatorWorker(autoRevisionService, config.abTesting, logger);
    }

    start(): void {
        this.logger.info('Starting Auto-Revision worker manager');
        this.driftMonitor.start();
        this.regenerationWorker.start();
        this.abTestEvaluator.start();
        this.logger.info('Auto-Revision worker manager started');
    }

    stop(): void {
        this.logger.info('Stopping Auto-Revision worker manager');
        this.driftMonitor.stop();
        this.regenerationWorker.stop();
        this.abTestEvaluator.stop();
        this.logger.info('Auto-Revision worker manager stopped');
    }

    getStatus(): {
        driftMonitor: ReturnType<DriftMonitorWorker['getStatus']>;
        regenerationWorker: ReturnType<RegenerationWorker['getStatus']>;
        abTestEvaluator: ReturnType<ABTestEvaluatorWorker['getStatus']>;
    } {
        return {
            driftMonitor: this.driftMonitor.getStatus(),
            regenerationWorker: this.regenerationWorker.getStatus(),
            abTestEvaluator: this.abTestEvaluator.getStatus(),
        };
    }
}
