import { promises as fs } from 'node:fs';
import { ProductLifecycleResult } from '../domain/ProductLifecyclePhase.js';

/**
 * Result writer
 */
export class ResultWriter {
  /**
   * Write lifecycle result to file
   */
  async writeResult(result: ProductLifecycleResult, outputPath: string): Promise<void> {
    const dir = outputPath.substring(0, outputPath.lastIndexOf('/'));
    await fs.mkdir(dir, { recursive: true });

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
    const dir = outputPath.substring(0, outputPath.lastIndexOf('/'));
    await fs.mkdir(dir, { recursive: true });
    await fs.writeFile(outputPath, summary, 'utf-8');
  }

  /**
   * Generate human-readable summary
   */
  private generateSummary(result: ProductLifecycleResult): string {
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
      const statusIcon = step.status === 'succeeded' ? '✓' : step.status === 'failed' ? '✗' : '○';
      lines.push(`${statusIcon} ${step.stepId} (${step.status}) - ${step.durationMs}ms`);

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
      const statusIcon = gate.status === 'passed' ? '✓' : gate.status === 'failed' ? '✗' : '○';
      lines.push(`${statusIcon} ${gate.gateName} (${gate.status})`);
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
    }

    return lines.join('\n');
  }
}
