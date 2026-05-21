import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import { ProductLifecyclePlan } from '../domain/ProductLifecyclePhase.js';

/**
 * Plan writer
 */
export class PlanWriter {
  /**
   * Write lifecycle plan to file
   */
  async writePlan(plan: ProductLifecyclePlan, outputPath: string): Promise<void> {
    await fs.mkdir(path.dirname(outputPath), { recursive: true });

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
    await fs.mkdir(path.dirname(outputPath), { recursive: true });
    await fs.writeFile(outputPath, summary, 'utf-8');
  }

  /**
   * Generate human-readable summary
   */
  generateSummary(plan: ProductLifecyclePlan): string {
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
      const dependencyText = step.dependsOn.length > 0 ? `, depends on ${step.dependsOn.join(', ')}` : '';
      lines.push(`- ${step.id}: ${step.description} [${step.adapter}${dependencyText}]`);
    }
    if (plan.interactionPreflights !== undefined && plan.interactionPreflights.length > 0) {
      lines.push('');
      lines.push(`## Interaction Preflights (${plan.interactionPreflights.length})`);
      lines.push('');
      for (const preflight of plan.interactionPreflights) {
        const requiredText = preflight.required ? 'required' : 'optional';
        const reasonText = preflight.reasonCode !== undefined ? `, reason: ${preflight.reasonCode}` : '';
        lines.push(`- ${preflight.contractId}: ${preflight.status} (${requiredText}${reasonText})`);
      }
    }
    if (plan.interactionRollbackImpact !== undefined && plan.interactionRollbackImpact.length > 0) {
      lines.push('');
      lines.push(`## Interaction Rollback Impact (${plan.interactionRollbackImpact.length})`);
      lines.push('');
      for (const impact of plan.interactionRollbackImpact) {
        const requiredText = impact.required ? 'required' : 'optional';
        const reasonText = impact.reasonCode !== undefined ? `, reason: ${impact.reasonCode}` : '';
        lines.push(`- ${impact.contractId}: ${impact.impactLevel} (${requiredText}${reasonText})`);
      }
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
    if (plan.blockingReasons.length > 0) {
      lines.push('');
      lines.push('## Blocking Reasons');
      lines.push('');
      for (const reason of plan.blockingReasons) {
        lines.push(`- ${reason}`);
      }
    }
    if (plan.warnings.length > 0) {
      lines.push('');
      lines.push('## Warnings');
      lines.push('');
      for (const warning of plan.warnings) {
        lines.push(`- ${warning}`);
      }
    }

    return lines.join('\n');
  }
}
