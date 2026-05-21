import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import { ProductLifecycleResult } from '../domain/ProductLifecyclePhase.js';

/**
 * Result writer
 */
export class ResultWriter {
  /**
   * Write lifecycle result to file
   */
  async writeResult(result: ProductLifecycleResult, outputPath: string): Promise<void> {
    await fs.mkdir(path.dirname(outputPath), { recursive: true });

    const content = JSON.stringify(result, null, 2);
    await fs.writeFile(outputPath, content, 'utf-8');
  }

  /**
   * Read lifecycle result from file
   */
  async readResult(inputPath: string): Promise<ProductLifecycleResult> {
    const content = await fs.readFile(inputPath, 'utf-8');
    return JSON.parse(content) as ProductLifecycleResult;
  }

  /**
   * Write result summary
   */
  async writeSummary(result: ProductLifecycleResult, outputPath: string): Promise<void> {
    const summary = this.generateSummary(result);
    await fs.mkdir(path.dirname(outputPath), { recursive: true });
    await fs.writeFile(outputPath, summary, 'utf-8');
  }

  /**
   * Generate human-readable summary
   */
  generateSummary(result: ProductLifecycleResult): string {
    const lines: string[] = [];

    lines.push('# Lifecycle Execution Summary');
    lines.push('');
    lines.push(`Product: ${result.productId}`);
    lines.push(`Phase: ${result.phase}`);
    lines.push(`Status: ${result.status.toUpperCase()}`);
    lines.push(`Started: ${result.startedAt}`);
    lines.push(`Completed: ${result.completedAt}`);
    lines.push(`Duration: ${Math.round((new Date(result.completedAt).getTime() - new Date(result.startedAt).getTime()) / 1000)}s`);
    lines.push('');
    lines.push(`## Steps (${result.steps.length})`);
    lines.push('');

    let successCount = 0;
    let failureCount = 0;
    let skippedCount = 0;

    for (const step of result.steps) {
      lines.push(`[${this.statusLabel(step.status)}] ${step.stepId} (${step.status}) - ${step.durationMs}ms`);
      if (step.errors !== undefined && step.errors.length > 0) {
        for (const error of step.errors) {
          lines.push(`  Error: ${error}`);
        }
      }
      if (step.warnings !== undefined && step.warnings.length > 0) {
        for (const warning of step.warnings) {
          lines.push(`  Warning: ${warning}`);
        }
      }

      if (step.status === 'succeeded') successCount++;
      else if (step.status === 'failed') failureCount++;
      else skippedCount++;
    }

    lines.push('');
    lines.push(`Success: ${successCount}, Failed: ${failureCount}, Skipped: ${skippedCount}`);
    lines.push('');
    lines.push(`## Gates (${result.gates.length})`);
    lines.push('');

    for (const gate of result.gates) {
      lines.push(`[${this.gateStatusLabel(gate.status)}] ${gate.gateName} (${gate.status})`);
    }
    lines.push('');
    lines.push(`## Artifacts (${result.artifacts.length})`);
    lines.push('');

    for (const artifact of result.artifacts) {
      lines.push(`- ${artifact.id} (${artifact.type}) - ${artifact.path}`);
    }
    lines.push('');
    lines.push(`Output Directory: ${result.outputDirectory}`);

    if (result.failure) {
      lines.push('');
      lines.push('## Failure Details');
      lines.push('');
      lines.push(`Failed Step: ${result.failure.stepId}`);
      lines.push(`Message: ${result.failure.message}`);
      if (result.failure.cause) {
        lines.push(`Cause: ${result.failure.cause}`);
      }
      lines.push('');
      lines.push('## Recovery Guidance');
      lines.push('');
      for (const guidance of this.recoveryGuidance(result)) {
        lines.push(`- ${guidance}`);
      }
    }

    return lines.join('\n');
  }

  private statusLabel(status: ProductLifecycleResult['steps'][number]['status']): string {
    if (status === 'succeeded') {
      return 'PASS';
    }
    if (status === 'failed') {
      return 'FAIL';
    }
    return 'SKIP';
  }

  private gateStatusLabel(status: ProductLifecycleResult['gates'][number]['status']): string {
    if (status === 'passed') {
      return 'PASS';
    }
    if (status === 'failed') {
      return 'FAIL';
    }
    return 'SKIP';
  }

  private recoveryGuidance(result: ProductLifecycleResult): string[] {
    const failedStep = result.steps.find((step) => step.status === 'failed');
    const guidance = [
      `Inspect run ${result.runId} artifacts in ${result.outputDirectory}.`,
      'Re-run the same lifecycle phase after correcting the failed step.',
    ];
    if (result.failure?.reasonCode !== undefined) {
      guidance.push(`Use failure reason code ${result.failure.reasonCode} when searching logs and evidence.`);
    }
    if (failedStep?.adapter !== undefined) {
      guidance.push(`Start with adapter ${failedStep.adapter} for step ${failedStep.stepId}.`);
    }
    if (result.manifestRefs !== undefined) {
      guidance.push('Review emitted lifecycle manifests before retrying.');
    }
    return guidance;
  }
}
