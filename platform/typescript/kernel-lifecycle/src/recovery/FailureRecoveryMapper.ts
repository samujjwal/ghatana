/**
 * Failure recovery mapper for actionable recovery steps.
 *
 * Maps failure classifications to concrete recovery actions that users can take
 * to resolve the issue and continue with lifecycle execution.
 *
 * @doc.type class
 * @doc.purpose Map failures to concrete recovery actions
 * @doc.layer kernel-lifecycle
 * @doc.pattern Service
 */

import type { FailureClassification, FailureCategory, FailureSource } from "./FailureType.js";
import type { RecoveryAction } from "./RecoveryAction.js";

/**
 * Maps failure classifications to recovery actions.
 */
export class FailureRecoveryMapper {
  private readonly recoveryMap: Map<string, RecoveryAction> = new Map();

  constructor() {
    this.initializeDefaultMappings();
  }

  /**
   * Gets recovery actions for a failure classification.
   *
   * @param classification the failure classification
   * @returns the recovery actions
   */
  getRecoveryActions(classification: FailureClassification): readonly RecoveryAction[] {
    const key = this.buildKey(classification.category, classification.source, classification.reasonCode);
    const specificAction = this.recoveryMap.get(key);

    if (specificAction) {
      return [specificAction];
    }

    // Fall back to category-level recovery
    const categoryKey = this.buildKey(classification.category, classification.source, "*");
    const categoryAction = this.recoveryMap.get(categoryKey);

    if (categoryAction) {
      return [categoryAction];
    }

    // Fall back to generic recovery
    return this.getGenericRecovery(classification);
  }

  private buildKey(category: FailureCategory, source: FailureSource, reasonCode: string): string {
    return `${category}:${source}:${reasonCode}`;
  }

  private getGenericRecovery(classification: FailureClassification): RecoveryAction[] {
    const actions: RecoveryAction[] = [];

    switch (classification.category) {
      case "toolchain-missing":
        actions.push({
          type: "configure-toolchain",
          description: "Install the required toolchain",
          steps: [
            `Install ${classification.source} toolchain`,
            "Verify installation with version check",
            "Retry the lifecycle phase",
          ],
          estimatedDuration: "5-10 minutes",
          automated: false,
        });
        break;

      case "dependency-missing":
        actions.push({
          type: "install-dependency",
          description: "Install missing dependencies",
          steps: [
            "Run dependency installation command for the toolchain",
            "Verify all dependencies are installed",
            "Retry the lifecycle phase",
          ],
          estimatedDuration: "2-5 minutes",
          automated: true,
        });
        break;

      case "permission-error":
        actions.push({
          type: "fix-permission",
          description: "Fix file or resource permissions",
          steps: [
            "Check file/directory permissions",
            "Grant required read/write/execute permissions",
            "Retry the lifecycle phase",
          ],
          estimatedDuration: "1-2 minutes",
          automated: false,
        });
        break;

      case "configuration-error":
        actions.push({
          type: "fix-configuration",
          description: "Fix configuration errors",
          steps: [
            "Review configuration file for syntax errors",
            "Validate configuration against schema",
            "Fix identified issues",
            "Retry the lifecycle phase",
          ],
          estimatedDuration: "5-15 minutes",
          automated: false,
        });
        break;

      case "network-error":
        actions.push({
          type: "check-network",
          description: "Check network connectivity",
          steps: [
            "Verify internet connection",
            "Check proxy settings if applicable",
            "Retry the lifecycle phase",
          ],
          estimatedDuration: "2-5 minutes",
          automated: false,
        });
        break;

      case "resource-exhaustion":
        actions.push({
          type: "increase-resources",
          description: "Increase available resources",
          steps: [
            "Increase memory allocation",
            "Increase disk space if needed",
            "Retry the lifecycle phase",
          ],
          estimatedDuration: "5-10 minutes",
          automated: false,
        });
        break;

      default:
        actions.push({
          type: "manual-intervention",
          description: "Manual investigation required",
          steps: [
            "Review error logs for details",
            "Check system status",
            "Contact support if issue persists",
          ],
          estimatedDuration: "15-30 minutes",
          automated: false,
        });
    }

    return actions;
  }

  private initializeDefaultMappings(): void {
    // Cargo Rust adapter specific mappings
    this.recoveryMap.set(
      "toolchain-missing:cargo-rust-adapter:cargo-not-found",
      {
        type: "configure-toolchain",
        description: "Install Rust and Cargo",
        command: "curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh",
        steps: [
          "Install Rust using rustup",
          "Verify installation with `rustc --version` and `cargo --version`",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "5-10 minutes",
        automated: false,
      }
    );

    this.recoveryMap.set(
      "dependency-missing:cargo-rust-adapter:build-failed",
      {
        type: "update-dependencies",
        description: "Update Rust dependencies",
        command: "cargo update",
        steps: [
          "Run `cargo update` to update dependencies",
          "Run `cargo build` to verify build",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "3-10 minutes",
        automated: true,
      }
    );

    // Python Pyproject adapter specific mappings
    this.recoveryMap.set(
      "toolchain-missing:python-pyproject-adapter:python-not-found",
      {
        type: "configure-toolchain",
        description: "Install Python",
        steps: [
          "Install Python 3.8 or later",
          "Verify installation with `python --version`",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "5-10 minutes",
        automated: false,
      }
    );

    this.recoveryMap.set(
      "dependency-missing:python-pyproject-adapter:import-error",
      {
        type: "install-dependency",
        description: "Install Python dependencies",
        command: "pip install -e .",
        steps: [
          "Run `pip install -e .` to install package in development mode",
          "Verify dependencies with `pip list`",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "2-5 minutes",
        automated: true,
      }
    );

    // pnpm Node API adapter specific mappings
    this.recoveryMap.set(
      "toolchain-missing:pnpm-node-api-adapter:node-not-found",
      {
        type: "configure-toolchain",
        description: "Install Node.js and pnpm",
        steps: [
          "Install Node.js 18 or later",
          "Install pnpm with `npm install -g pnpm`",
          "Verify installations",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "5-10 minutes",
        automated: false,
      }
    );

    this.recoveryMap.set(
      "dependency-missing:pnpm-node-api-adapter:install-failed",
      {
        type: "install-dependency",
        description: "Install Node dependencies",
        command: "pnpm install",
        steps: [
          "Run `pnpm install` to install dependencies",
          "Verify with `pnpm list`",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "2-10 minutes",
        automated: true,
      }
    );

    // Policy evaluation specific mappings
    this.recoveryMap.set(
      "policy-denied:policy-evaluator:tenant_mismatch",
      {
        type: "fix-configuration",
        description: "Fix tenant ID configuration",
        steps: [
          "Verify tenant ID in product configuration",
          "Ensure tenant ID matches request context",
          "Retry the interaction",
        ],
        estimatedDuration: "5-10 minutes",
        automated: false,
      }
    );

    this.recoveryMap.set(
      "policy-denied:policy-evaluator:consent_denied",
      {
        type: "manual-intervention",
        description: "Obtain required consent",
        steps: [
          "Contact user for consent",
          "Update consent status in system",
          "Retry the interaction",
        ],
        estimatedDuration: "Variable",
        automated: false,
      }
    );

    // Lifecycle service specific mappings
    this.recoveryMap.set(
      "adapter-execute:lifecycle-executor:registry-provider-fallback",
      {
        type: "fix-configuration",
        description: "Fix registry provider configuration",
        steps: [
          "Verify registry provider is properly configured",
          "Check provider connection settings",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "5-10 minutes",
        automated: false,
      }
    );

    this.recoveryMap.set(
      "policy-denied:interaction-broker:product_interaction.provider_not_enabled",
      {
        type: "fix-configuration",
        description: "Enable product interaction provider",
        steps: [
          "Check provider configuration in kernel-product.yaml",
          "Enable the required interaction provider",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "5-10 minutes",
        automated: false,
      }
    );

    this.recoveryMap.set(
      "policy-denied:interaction-broker:product_interaction.rollback_provider_not_enabled",
      {
        type: "fix-configuration",
        description: "Enable rollback interaction provider",
        steps: [
          "Check rollback provider configuration",
          "Enable the rollback interaction provider",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "5-10 minutes",
        automated: false,
      }
    );

    this.recoveryMap.set(
      "evidence-write-failure:evidence-writer:manifest-write-failed",
      {
        type: "fix-configuration",
        description: "Fix manifest write permissions",
        steps: [
          "Check output directory permissions",
          "Ensure sufficient disk space",
          "Verify manifest path is valid",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "2-5 minutes",
        automated: false,
      }
    );

    this.recoveryMap.set(
      "policy-denied:policy-evaluator:gate-failed",
      {
        type: "address-gate",
        description: "Address failed quality gate",
        steps: [
          "Review gate failure details",
          "Fix the code or configuration causing gate failure",
          "Re-run the gate",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "10-30 minutes",
        automated: false,
      }
    );

    this.recoveryMap.set(
      "policy-denied:policy-evaluator:approval-required",
      {
        type: "manual-intervention",
        description: "Obtain required approval",
        steps: [
          "Submit approval request",
          "Wait for approval from authorized approver",
          "Retry the lifecycle phase with approval ID",
        ],
        estimatedDuration: "Variable",
        automated: false,
      }
    );

    this.recoveryMap.set(
      "adapter-execute:lifecycle-executor:lifecycle-run-index-unavailable",
      {
        type: "fix-configuration",
        description: "Fix lifecycle run index",
        steps: [
          "Check run index file permissions",
          "Verify run index file exists",
          "Rebuild run index if corrupted",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "5-10 minutes",
        automated: false,
      }
    );

    this.recoveryMap.set(
      "adapter-execute:lifecycle-executor:lifecycle-manifest-corrupt",
      {
        type: "fix-configuration",
        description: "Fix corrupt lifecycle manifest",
        steps: [
          "Verify manifest file integrity",
          "Restore from backup if available",
          "Re-run the lifecycle phase to regenerate",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "5-15 minutes",
        automated: false,
      }
    );

    this.recoveryMap.set(
      "adapter-execute:lifecycle-executor:run-not-found",
      {
        type: "fix-configuration",
        description: "Fix missing lifecycle run",
        steps: [
          "Verify run ID is correct",
          "Check run directory exists",
          "Re-run the lifecycle phase if run is missing",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "2-5 minutes",
        automated: false,
      }
    );

    this.recoveryMap.set(
      "configuration-error:lifecycle-executor:unsafe-output-path",
      {
        type: "fix-configuration",
        description: "Fix unsafe output path configuration",
        steps: [
          "Verify output path is within kernel output root",
          "Update output path in configuration",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "2-5 minutes",
        automated: false,
      }
    );

    this.recoveryMap.set(
      "adapter-execute:lifecycle-executor:provider-unavailable",
      {
        type: "fix-configuration",
        description: "Fix unavailable provider",
        steps: [
          "Verify provider is properly configured",
          "Check provider service status",
          "Restart provider if needed",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "5-10 minutes",
        automated: false,
      }
    );

    this.recoveryMap.set(
      "adapter-execute:lifecycle-executor:manifest-not-found",
      {
        type: "fix-configuration",
        description: "Fix missing manifest",
        steps: [
          "Verify manifest path is correct",
          "Re-run the lifecycle phase to regenerate manifest",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "2-5 minutes",
        automated: false,
      }
    );

    this.recoveryMap.set(
      "adapter-execute:lifecycle-executor:artifact-missing",
      {
        type: "regenerate-artifact",
        description: "Regenerate missing artifact",
        steps: [
          "Re-run the build/package phase",
          "Verify artifact is generated",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "5-15 minutes",
        automated: true,
      }
    );

    this.recoveryMap.set(
      "adapter-execute:lifecycle-executor:execution-failed",
      {
        type: "manual-intervention",
        description: "Fix execution failure",
        steps: [
          "Review execution logs for error details",
          "Fix the root cause of the failure",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "Variable",
        automated: false,
      }
    );

    this.recoveryMap.set(
      "evidence-write-failure:evidence-writer:lifecycle-truth-read-failed",
      {
        type: "fix-configuration",
        description: "Fix lifecycle truth read failure",
        steps: [
          "Verify truth file exists and is readable",
          "Check file permissions",
          "Restore from backup if corrupted",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "5-10 minutes",
        automated: false,
      }
    );

    this.recoveryMap.set(
      "adapter-execute:lifecycle-executor:platform-provider-context-missing",
      {
        type: "fix-configuration",
        description: "Fix platform provider context",
        steps: [
          "Verify platform mode is properly configured",
          "Check Data Cloud connection settings",
          "Ensure required platform providers are available",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "5-10 minutes",
        automated: false,
      }
    );

    this.recoveryMap.set(
      "adapter-execute:lifecycle-executor:platform-provider-context-mode-mismatch",
      {
        type: "fix-configuration",
        description: "Fix provider context mode mismatch",
        steps: [
          "Verify provider mode matches expected mode",
          "Update mode configuration",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "2-5 minutes",
        automated: false,
      }
    );

    this.recoveryMap.set(
      "adapter-execute:lifecycle-executor:platform-required-providers-missing",
      {
        type: "fix-configuration",
        description: "Fix missing platform providers",
        steps: [
          "Identify missing required providers",
          "Configure missing providers",
          "Verify provider availability",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "10-20 minutes",
        automated: false,
      }
    );

    this.recoveryMap.set(
      "policy-denied:policy-evaluator:required-gate-provider-missing",
      {
        type: "fix-configuration",
        description: "Fix missing required gate provider",
        steps: [
          "Identify missing gate provider",
          "Configure the required gate provider",
          "Verify provider is available",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "5-10 minutes",
        automated: false,
      }
    );

    this.recoveryMap.set(
      "policy-denied:policy-evaluator:optional-gate-provider-missing",
      {
        type: "fix-configuration",
        description: "Fix missing optional gate provider",
        steps: [
          "Identify missing gate provider",
          "Configure the optional gate provider if needed",
          "Verify provider is available",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "5-10 minutes",
        automated: false,
      }
    );

    this.recoveryMap.set(
      "policy-denied:policy-evaluator:gate-provider-failed",
      {
        type: "fix-configuration",
        description: "Fix failed gate provider",
        steps: [
          "Review gate provider error logs",
          "Fix the root cause of provider failure",
          "Verify provider is working",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "5-15 minutes",
        automated: false,
      }
    );

    this.recoveryMap.set(
      "policy-denied:policy-evaluator:gate-evaluation-failed",
      {
        type: "address-gate",
        description: "Fix gate evaluation failure",
        steps: [
          "Review gate evaluation error details",
          "Fix the code or configuration causing evaluation failure",
          "Re-run the gate",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "10-30 minutes",
        automated: false,
      }
    );

    this.recoveryMap.set(
      "policy-denied:policy-evaluator:required-gate-missing-evidence",
      {
        type: "fix-configuration",
        description: "Fix missing gate evidence",
        steps: [
          "Identify missing evidence references",
          "Configure evidence sources",
          "Verify evidence is available",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "5-10 minutes",
        automated: false,
      }
    );

    this.recoveryMap.set(
      "adapter-execute:lifecycle-executor:adapter-failed",
      {
        type: "manual-intervention",
        description: "Fix adapter failure",
        steps: [
          "Review adapter error logs",
          "Fix the root cause of adapter failure",
          "Verify adapter configuration",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "Variable",
        automated: false,
      }
    );

    this.recoveryMap.set(
      "evidence-write-failure:evidence-writer:write-error",
      {
        type: "fix-configuration",
        description: "Fix write error",
        steps: [
          "Check file system permissions",
          "Verify sufficient disk space",
          "Check for file locks",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "2-5 minutes",
        automated: false,
      }
    );

    this.recoveryMap.set(
      "evidence-write-failure:evidence-writer:serialization-error",
      {
        type: "fix-configuration",
        description: "Fix serialization error",
        steps: [
          "Review data being serialized",
          "Fix data structure issues",
          "Verify schema compatibility",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "5-15 minutes",
        automated: false,
      }
    );

    this.recoveryMap.set(
      "evidence-write-failure:evidence-writer:directory-create-error",
      {
        type: "fix-configuration",
        description: "Fix directory creation error",
        steps: [
          "Check parent directory permissions",
          "Verify path is valid",
          "Create directory manually if needed",
          "Retry the lifecycle phase",
        ],
        estimatedDuration: "2-5 minutes",
        automated: false,
      }
    );
  }
}
