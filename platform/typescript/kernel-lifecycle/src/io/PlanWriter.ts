import { promises as fs } from 'node:fs';
import { ProductLifecyclePlan } from '../domain/ProductLifecyclePhase.js';

/**
 * Plan writer
 */
export class PlanWriter {
  /**
   * Write lifecycle plan to file
   */
  async writePlan(plan: ProductLifecyclePlan, outputPath: string): Promise<void> {
    const dir = outputPath.substring(0, outputPath.lastIndexOf('/'));
    await fs.mkdir(dir, { recursive: true });

    const content = JSON.stringify(plan, null, 2);
    await fs.writeFile(outputPath, content, 'utf-8');
  }

  /**
   * Read lifecycle plan from file
   */
  async readPlan(inputPath: string): Promise<ProductLifecyclePlan> {
    const content = await fs.readFile(inputPath, 'utf-8');
    return JSON.parse(content) as ProductLifecyclePlan;
  }

  /**
   * Write plan summary
   */
  async writeSummary(plan: ProductLifecyclePlan, outputPath: string): Promise<void> {
    const summary = this.generateSummary(plan);
    const dir = outputPath.substring(0, outputPath.lastIndexOf('/'));
    await fs.mkdir(dir, { recursive: true });
    await fs.writeFile(outputPath, summary, 'utf-8');
  }

  /**
   * Generate human-readable summary
   */
  private generateSummary(plan: ProductLifecyclePlan): string {
    const lines: string[] = [];

    lines.push('# Lifecycle Plan Summary');
    lines.push('');
    lines.push(`Product: ${plan.productId}`);
    lines.push(`Phase: ${plan.phase}`);
    lines.push(`Profile: ${plan.lifecycleProfile}`);
    if (plan.environment) {
      lines.push(`Environment: ${plan.environment}`);
    }
    if (plan.sourceRef) {
      lines.push(`Source Ref: ${plan.sourceRef}`);
    }
    lines.push('');
    lines.push(`## Surfaces (${plan.surfaces.length})`);
    lines.push('');
    for (const surface of plan.surfaces) {
      lines.push(`- ${surface.surface} (${surface.type})`);
    }
    lines.push('');
    lines.push(`## Gates (${plan.gates.length})`);
    lines.push('');
    for (const gate of plan.gates) {
      lines.push(`- ${gate.gateName} (${gate.required ? 'required' : 'optional'})`);
    }
    lines.push('');
    lines.push(`## Steps (${plan.steps.length})`);
    lines.push('');
    for (const step of plan.steps) {
      lines.push(`- ${step.id}: ${step.description}`);
    }
    lines.push('');
    lines.push(`## Expected Artifacts (${plan.expectedArtifacts.length})`);
    lines.push('');
    for (const artifact of plan.expectedArtifacts) {
      lines.push(`- ${artifact.type} for ${artifact.surface} (${artifact.required ? 'required' : 'optional'})`);
    }
    lines.push('');
    lines.push(`Output Directory: ${plan.outputDirectory}`);
    lines.push(`Estimated Duration: ${Math.round(plan.estimatedDurationMs / 1000)}s`);

    return lines.join('\n');
  }
}
