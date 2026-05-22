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
  }
}
