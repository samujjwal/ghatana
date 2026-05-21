import { ProductFailurePolicy } from '../domain/ProductLifecyclePhase.js';
import { ExecutionLogger } from '../domain/ProductLifecyclePhase.js';
import {
  LifecycleFailureClassifier,
} from '@ghatana/kernel-product-contracts';

/**
 * Execution failure handler with enhanced classification
 */
export class ExecutionFailureHandler {
  private failurePolicy: ProductFailurePolicy;

  constructor(failurePolicy: ProductFailurePolicy) {
    this.failurePolicy = failurePolicy;
  }

  /**
   * Handle a step failure with classification
   */
  async handleFailure(
    stepId: string,
    error: Error,
    logger: ExecutionLogger,
  ): Promise<FailureHandlingResult> {
    const classifier = this.classifyFailure(error, stepId);
    
    logger.error(`Step ${stepId} failed`, {
      error: error.message,
      stack: error.stack,
      category: classifier.category,
      severity: classifier.severity,
      retryable: classifier.retryable,
      requiresHumanIntervention: classifier.requiresHumanIntervention,
    });

    // Use classifier to determine action if policy allows
    if (this.failurePolicy.strategy === 'fail-closed') {
      // Fail-closed: stop unless classifier indicates it's retryable and we have retries left
      if (classifier.retryable && this.canRetry(0)) {
        return {
          action: 'retry',
          reason: `Retryable failure (${classifier.category}): ${classifier.remediationSteps?.join(', ') || 'No remediation steps'}`,
          classifier,
        };
      }
      return {
        action: 'stop',
        reason: `Fail-closed policy: stopping on ${classifier.severity} ${classifier.category} failure`,
        classifier,
      };
    }

    if (this.failurePolicy.strategy === 'fail-open') {
      return {
        action: 'continue',
        reason: `Fail-open policy: continuing despite ${classifier.category} failure`,
        classifier,
      };
    }

    if (this.failurePolicy.strategy === 'continue-on-error') {
      return {
        action: 'continue',
        reason: `Continue-on-error policy: continuing on ${classifier.severity} failure`,
        classifier,
      };
    }

    return {
      action: 'stop',
      reason: `Unknown failure policy: ${this.failurePolicy.strategy}`,
      classifier,
    };
  }

  /**
   * Classify a failure into category, severity, and remediation
   */
  classifyFailure(error: Error, stepId: string): LifecycleFailureClassifier {
    const category = this.determineCategory(error, stepId);
    const severity = this.determineSeverity(error, category);
    const retryable = this.isRetryable(error, category);
    const requiresHumanIntervention = this.requiresHumanIntervention(error, category);
    const component = this.extractComponent(error, stepId);
    
    return {
      category,
      severity,
      retryable,
      requiresHumanIntervention,
      remediationSteps: this.getRemediationSteps(error, category),
      relatedFailureCodes: this.getRelatedFailureCodes(category),
      component,
      knownWorkaround: this.findKnownWorkaround(error, category),
    };
  }

  /**
   * Determine failure category from error patterns
   */
  private determineCategory(error: Error, stepId: string): LifecycleFailureClassifier['category'] {
    const message = error.message.toLowerCase();
    const stepIdLower = stepId.toLowerCase();

    // Security errors - highest priority
    if (message.includes('security') || message.includes('auth') || message.includes('token') || message.includes('credential')) {
      return 'security';
    }

    // Policy errors
    if (message.includes('policy') || message.includes('compliance') || message.includes('violation')) {
      return 'policy';
    }

    // Approval errors
    if (message.includes('approval') || message.includes('authorize')) {
      return 'approval';
    }

    // Gate errors
    if (message.includes('gate')) {
      return 'gate';
    }

    // Config errors
    if (message.includes('config') || message.includes('schema')) {
      return 'config';
    }

    // Invalid/missing errors - could be config or artifact
    if (message.includes('invalid') || message.includes('missing')) {
      // Check if it's artifact-related
      if (message.includes('artifact') || message.includes('file') || message.includes('path')) {
        return 'artifact';
      }
      return 'config';
    }

    // Adapter errors
    if (stepIdLower.includes('adapter') || message.includes('adapter') || message.includes('adapter-failed')) {
      return 'adapter';
    }

    // Command errors
    if (message.includes('command') || message.includes('exit code') || message.includes('non-zero')) {
      return 'command';
    }

    // Artifact errors
    if (message.includes('artifact') || message.includes('file') || message.includes('path')) {
      return 'artifact';
    }

    // Dependency errors
    if (message.includes('dependency') || message.includes('module') || message.includes('import')) {
      return 'dependency';
    }

    // Environment errors
    if (message.includes('environment') || message.includes('env') || message.includes('docker') || message.includes('network')) {
      return 'environment';
    }

    // Provider errors
    if (message.includes('provider') || message.includes('service') || message.includes('unavailable')) {
      return 'provider';
    }

    // Infrastructure errors
    if (message.includes('disk') || message.includes('memory') || message.includes('timeout') || message.includes('resource')) {
      return 'infrastructure';
    }

    return 'unknown';
  }

  /**
   * Determine failure severity
   */
  private determineSeverity(error: Error, category: LifecycleFailureClassifier['category']): LifecycleFailureClassifier['severity'] {
    const message = error.message.toLowerCase();

    // Critical severity
    if (category === 'security' || category === 'policy') {
      return 'critical';
    }

    if (message.includes('critical') || message.includes('fatal') || message.includes('panic')) {
      return 'critical';
    }

    // High severity
    if (category === 'gate' || category === 'approval' || category === 'infrastructure') {
      return 'high';
    }

    if (message.includes('error') || message.includes('failed') || message.includes('denied')) {
      return 'high';
    }

    // Medium severity
    if (category === 'config' || category === 'dependency' || category === 'environment') {
      return 'medium';
    }

    if (message.includes('warning') || message.includes('warn')) {
      return 'medium';
    }

    // Low severity
    if (category === 'artifact' || message.includes('missing') || message.includes('not found')) {
      return 'low';
    }

    // Info severity
    if (message.includes('info') || message.includes('notice')) {
      return 'info';
    }

    return 'medium';
  }

  /**
   * Determine if failure is retryable
   */
  private isRetryable(error: Error, category: LifecycleFailureClassifier['category']): boolean {
    const message = error.message.toLowerCase();

    // Non-retryable categories
    if (category === 'config' || category === 'gate' || category === 'approval' || category === 'policy' || category === 'security') {
      return false;
    }

    // Non-retryable patterns
    if (message.includes('permission denied') || message.includes('access denied') || message.includes('invalid')) {
      return false;
    }

    // Retryable categories
    if (category === 'provider' || category === 'infrastructure' || category === 'environment') {
      return true;
    }

    // Retryable patterns
    if (message.includes('timeout') || message.includes('network') || message.includes('temporary') || message.includes('unavailable')) {
      return true;
    }

    return false;
  }

  /**
   * Determine if failure requires human intervention
   */
  private requiresHumanIntervention(error: Error, category: LifecycleFailureClassifier['category']): boolean {
    const message = error.message.toLowerCase();

    // Categories requiring human intervention
    if (category === 'gate' || category === 'approval' || category === 'policy' || category === 'security') {
      return true;
    }

    // Patterns requiring human intervention
    if (message.includes('manual') || message.includes('approval') || message.includes('review')) {
      return true;
    }

    return false;
  }

  /**
   * Extract component from error and step
   */
  private extractComponent(error: Error, stepId: string): string {
    // Try to extract component from stepId
    const parts = stepId.split('-');
    if (parts.length > 0) {
      return parts[0];
    }

    // Try to extract from error message
    const message = error.message;
    const componentMatch = message.match(/component:\s*(\w+)/i);
    if (componentMatch) {
      return componentMatch[1];
    }

    return stepId;
  }

  /**
   * Get remediation steps for failure
   */
  private getRemediationSteps(_error: Error, category: LifecycleFailureClassifier['category']): string[] {
    const steps: string[] = [];

    switch (category) {
      case 'config':
        steps.push('Review configuration files for syntax errors');
        steps.push('Validate configuration against schema');
        steps.push('Check for missing required fields');
        break;

      case 'adapter':
        steps.push('Verify adapter is properly registered');
        steps.push('Check adapter dependencies are installed');
        steps.push('Review adapter configuration');
        break;

      case 'command':
        steps.push('Review command syntax and arguments');
        steps.push('Check if command exists in PATH');
        steps.push('Verify command has required permissions');
        break;

      case 'gate':
        steps.push('Review gate evaluation criteria');
        steps.push('Check gate policy pack configuration');
        steps.push('Obtain required approvals if needed');
        break;

      case 'artifact':
        steps.push('Verify artifact path is correct');
        steps.push('Check artifact exists and is accessible');
        steps.push('Validate artifact fingerprint');
        break;

      case 'dependency':
        steps.push('Check dependency installation');
        steps.push('Verify dependency versions are compatible');
        steps.push('Review dependency lockfile');
        break;

      case 'environment':
        steps.push('Check environment variables are set');
        steps.push('Verify required services are running');
        steps.push('Review environment configuration');
        break;

      case 'approval':
        steps.push('Submit for approval if required');
        steps.push('Contact approvers');
        steps.push('Review approval policy');
        break;

      case 'policy':
        steps.push('Review policy violation details');
        steps.push('Address policy compliance issues');
        steps.push('Request policy exception if needed');
        break;

      case 'security':
        steps.push('Review security credentials');
        steps.push('Check authentication tokens');
        steps.push('Review access permissions');
        break;

      case 'provider':
        steps.push('Check provider service status');
        steps.push('Verify provider configuration');
        steps.push('Review provider connectivity');
        break;

      case 'infrastructure':
        steps.push('Check system resources (disk, memory)');
        steps.push('Review infrastructure logs');
        steps.push('Scale infrastructure if needed');
        break;

      default:
        steps.push('Review error logs for details');
        steps.push('Check system status');
    }

    return steps;
  }

  /**
   * Get related failure codes for grouping
   */
  private getRelatedFailureCodes(category: LifecycleFailureClassifier['category']): string[] {
    const codeMap: Record<string, string[]> = {
      config: ['config-invalid', 'schema-validation-failed'],
      adapter: ['adapter-failed', 'adapter-missing', 'missing-adapter'],
      command: ['command-failed', 'exit-code-non-zero'],
      gate: ['gate-failed', 'approval-required', 'approval-missing'],
      artifact: ['artifact-missing', 'output-missing'],
      dependency: ['dependency-blocked'],
      environment: ['environment-blocked'],
      approval: ['approval-required', 'approval-missing'],
      policy: ['policy-denied', 'security-policy-denied'],
      security: ['security-policy-denied'],
      provider: ['provider-unavailable'],
      infrastructure: ['provider-unavailable', 'environment-blocked'],
      unknown: ['unknown'],
    };

    return codeMap[category] || ['unknown'];
  }

  /**
   * Find known workaround for failure
   */
  private findKnownWorkaround(error: Error, category: LifecycleFailureClassifier['category']): { description: string; workaroundSteps: string[] } | undefined {
    const message = error.message.toLowerCase();

    // Known workarounds for common issues
    if (category === 'environment' && message.includes('docker')) {
      return {
        description: 'Docker daemon unavailable',
        workaroundSteps: [
          'Start Docker Desktop',
          'Run: docker ps to verify connectivity',
          'Retry the operation',
        ],
      };
    }

    if (category === 'dependency' && message.includes('module not found')) {
      return {
        description: 'Missing dependency',
        workaroundSteps: [
          'Run: pnpm install',
          'Check package.json for missing dependencies',
          'Clear node_modules and reinstall',
        ],
      };
    }

    if (category === 'command' && message.includes('permission denied')) {
      return {
        description: 'Permission denied',
        workaroundSteps: [
          'Check file permissions',
          'Run with appropriate permissions',
          'Review sudo requirements',
        ],
      };
    }

    return undefined;
  }

  /**
   * Check if retry is possible
   */
  canRetry(attemptCount: number): boolean {
    if (!this.failurePolicy.retryConfig) {
      return false;
    }

    return attemptCount < this.failurePolicy.retryConfig.maxRetries;
  }

  /**
   * Get retry delay
   */
  getRetryDelay(attemptCount: number): number {
    if (!this.failurePolicy.retryConfig) {
      return 0;
    }

    const { maxRetries, backoffMs } = this.failurePolicy.retryConfig;
    return Math.min(backoffMs * Math.pow(2, attemptCount), backoffMs * maxRetries);
  }

  /**
   * Check if notification is required
   */
  shouldNotify(): boolean {
    return this.failurePolicy.notifyOnFailure === true;
  }
}

/**
 * Failure handling result with classifier
 */
export interface FailureHandlingResult {
  action: 'stop' | 'continue' | 'retry';
  reason: string;
  classifier?: LifecycleFailureClassifier;
}
