/**
 * Lifecycle Recovery Guidance
 *
 * Provides specific, actionable recovery steps for different types of lifecycle failures.
 * Each failure type maps to concrete fixes that users can apply to resolve the issue.
 *
 * @doc.type module
 * @doc.purpose Failure-specific recovery guidance for lifecycle operations
 * @doc.layer kernel-lifecycle
 * @doc.pattern Guidance
 */

/**
 * Categories of lifecycle failures with specific recovery strategies.
 */
export type LifecycleFailureCategory =
  | "adapter-toolchain-missing"
  | "adapter-toolchain-version-mismatch"
  | "adapter-dependency-error"
  | "adapter-configuration-error"
  | "adapter-timeout"
  | "policy-authentication-failed"
  | "policy-tenant-isolation-violation"
  | "policy-purpose-of-use-violation"
  | "policy-consent-verification-failed"
  | "interaction-handler-not-found"
  | "interaction-handler-timeout"
  | "interaction-policy-denied"
  | "interaction-provider-unavailable"
  | "environment-blocked"
  | "dependency-blocked"
  | "configuration-blocked"
  | "test-failure"
  | "gate-failure"
  | "artifact-validation-failure"
  | "unknown";

/**
 * Recovery action type indicating the kind of action required.
 */
export type RecoveryActionType =
  | "install-toolchain"
  | "update-toolchain"
  | "fix-dependency"
  | "fix-configuration"
  | "increase-timeout"
  | "resolve-auth"
  | "resolve-tenant"
  | "resolve-purpose"
  | "resolve-consent"
  | "register-handler"
  | "check-provider"
  | "enable-environment"
  | "resolve-dependency"
  | "fix-config"
  | "fix-test"
  | "address-gate"
  | "regenerate-artifact"
  | "manual-intervention";

/**
 * A specific recovery action with a description and optional command.
 */
export interface RecoveryAction {
  readonly type: RecoveryActionType;
  readonly description: string;
  readonly command?: string | undefined;
  readonly referenceUrl?: string | undefined;
}

/**
 * Recovery guidance for a specific failure.
 */
export interface LifecycleRecoveryGuidance {
  readonly failureCategory: LifecycleFailureCategory;
  readonly title: string;
  readonly explanation: string;
  readonly actions: readonly RecoveryAction[];
  readonly estimatedComplexity: "simple" | "moderate" | "complex";
  readonly requiresIntervention: boolean;
}

/**
 * Recovery guidance registry for all failure categories.
 */
const RECOVERY_GUIDANCE_REGISTRY: Record<
  LifecycleFailureCategory,
  LifecycleRecoveryGuidance
> = {
  "adapter-toolchain-missing": {
    failureCategory: "adapter-toolchain-missing",
    title: "Required toolchain is not installed",
    explanation:
      "The lifecycle adapter requires a specific toolchain (e.g., Java, Node.js, Cargo, Python) that is not available in the current environment.",
    actions: [
      {
        type: "install-toolchain",
        description: "Install the required toolchain using your system package manager or official installer",
        command: "Check the adapter documentation for specific installation instructions",
      },
      {
        type: "enable-environment",
        description: "Ensure the toolchain is available in your PATH",
        command: "export PATH=$PATH:/path/to/toolchain",
      },
    ],
    estimatedComplexity: "simple",
    requiresIntervention: false,
  },

  "adapter-toolchain-version-mismatch": {
    failureCategory: "adapter-toolchain-version-mismatch",
    title: "Toolchain version does not meet requirements",
    explanation:
      "The installed toolchain version does not match the version constraint specified in the product surface configuration.",
    actions: [
      {
        type: "update-toolchain",
        description: "Update the toolchain to the required version",
        command: "Use version manager (e.g., sdkman, nvm, pyenv) to install the specific version",
      },
      {
        type: "fix-configuration",
        description: "Update the product surface version constraint to match the installed version",
        command: "Edit kernel-product.yaml surface configuration",
      },
    ],
    estimatedComplexity: "simple",
    requiresIntervention: false,
  },

  "adapter-dependency-error": {
    failureCategory: "adapter-dependency-error",
    title: "Adapter dependency resolution failed",
    explanation:
      "The adapter failed to resolve or download required dependencies (e.g., Gradle dependencies, npm packages, Cargo crates).",
    actions: [
      {
        type: "fix-dependency",
        description: "Check network connectivity and dependency registry availability",
        command: "ping registry.npmjs.org or check Maven Central status",
      },
      {
        type: "fix-dependency",
        description: "Clear dependency cache and retry",
        command: "rm -rf node_modules, gradle clean, or cargo clean",
      },
      {
        type: "fix-configuration",
        description: "Verify dependency versions are compatible",
        command: "Check package.json, build.gradle, or Cargo.toml",
      },
    ],
    estimatedComplexity: "moderate",
    requiresIntervention: false,
  },

  "adapter-configuration-error": {
    failureCategory: "adapter-configuration-error",
    title: "Adapter configuration is invalid",
    explanation:
      "The adapter configuration (e.g., kernel-product.yaml, surface settings) contains errors or missing required fields.",
    actions: [
      {
        type: "fix-configuration",
        description: "Validate the kernel-product.yaml against the schema",
        command: "pnpm check:product-registry",
      },
      {
        type: "fix-configuration",
        description: "Check for missing required fields in surface configuration",
        referenceUrl: "/docs/kernel-product-schema.md",
      },
    ],
    estimatedComplexity: "simple",
    requiresIntervention: false,
  },

  "adapter-timeout": {
    failureCategory: "adapter-timeout",
    title: "Adapter operation timed out",
    explanation:
      "The adapter operation (e.g., build, test) exceeded the configured timeout duration.",
    actions: [
      {
        type: "increase-timeout",
        description: "Increase the timeout in the adapter configuration",
        command: "Edit kernel-product.yaml adapter timeout setting",
      },
      {
        type: "fix-configuration",
        description: "Optimize the operation to reduce execution time",
        command: "Review build/test configuration for inefficiencies",
      },
    ],
    estimatedComplexity: "simple",
    requiresIntervention: false,
  },

  "policy-authentication-failed": {
    failureCategory: "policy-authentication-failed",
    title: "Authentication failed for interaction",
    explanation:
      "The product interaction request failed authentication checks. The actor credentials are invalid or expired.",
    actions: [
      {
        type: "resolve-auth",
        description: "Refresh or renew authentication credentials",
        command: "Check token expiration and re-authenticate",
      },
      {
        type: "resolve-auth",
        description: "Verify the actor has valid permissions",
        command: "Check identity provider configuration",
      },
    ],
    estimatedComplexity: "moderate",
    requiresIntervention: false,
  },

  "policy-tenant-isolation-violation": {
    failureCategory: "policy-tenant-isolation-violation",
    title: "Tenant isolation policy violation",
    explanation:
      "The interaction attempted to access resources across tenant boundaries, which is not allowed by policy.",
    actions: [
      {
        type: "resolve-tenant",
        description: "Ensure the interaction is scoped to the correct tenant",
        command: "Verify tenantId in interaction request",
      },
      {
        type: "resolve-tenant",
        description: "Request cross-tenant access if business-justified",
        command: "Contact platform administrator for tenant access grant",
      },
    ],
    estimatedComplexity: "moderate",
    requiresIntervention: true,
  },

  "policy-purpose-of-use-violation": {
    failureCategory: "policy-purpose-of-use-violation",
    title: "Purpose of use policy violation",
    explanation:
      "The interaction purpose does not match the allowed purposes for the data or operation being accessed.",
    actions: [
      {
        type: "resolve-purpose",
        description: "Specify a valid purpose of use for the interaction",
        command: "Update interaction request with correct purpose",
      },
      {
        type: "resolve-purpose",
        description: "Request purpose exception if business-justified",
        command: "Contact platform administrator for purpose exception",
      },
    ],
    estimatedComplexity: "moderate",
    requiresIntervention: true,
  },

  "policy-consent-verification-failed": {
    failureCategory: "policy-consent-verification-failed",
    title: "Consent verification failed",
    explanation:
      "The interaction requires user consent that has not been granted, has been revoked, or has expired.",
    actions: [
      {
        type: "resolve-consent",
        description: "Obtain or renew user consent for the interaction",
        command: "Trigger consent request flow",
      },
      {
        type: "resolve-consent",
        description: "Verify consent status in the consent management system",
        command: "Check consent records for the subject",
      },
    ],
    estimatedComplexity: "moderate",
    requiresIntervention: false,
  },

  "interaction-handler-not-found": {
    failureCategory: "interaction-handler-not-found",
    title: "Interaction handler not registered",
    explanation:
      "No handler is registered for the requested interaction contract ID. The interaction cannot be processed.",
    actions: [
      {
        type: "register-handler",
        description: "Register the interaction handler for the contract",
        command: "Implement and register handler in the product or plugin",
      },
      {
        type: "fix-configuration",
        description: "Verify the contract ID is correct",
        command: "Check interaction contract documentation",
      },
    ],
    estimatedComplexity: "moderate",
    requiresIntervention: false,
  },

  "interaction-handler-timeout": {
    failureCategory: "interaction-handler-timeout",
    title: "Interaction handler timed out",
    explanation:
      "The interaction handler exceeded the configured timeout duration while processing the request.",
    actions: [
      {
        type: "increase-timeout",
        description: "Increase the handler timeout configuration",
        command: "Edit handler timeout setting in broker configuration",
      },
      {
        type: "fix-configuration",
        description: "Optimize handler implementation to reduce processing time",
        command: "Review handler code for performance bottlenecks",
      },
    ],
    estimatedComplexity: "moderate",
    requiresIntervention: false,
  },

  "interaction-policy-denied": {
    failureCategory: "interaction-policy-denied",
    title: "Interaction denied by policy",
    explanation:
      "The interaction was denied by the policy enforcer due to authentication, tenant, purpose, or consent violations.",
    actions: [
      {
        type: "resolve-auth",
        description: "Check authentication credentials",
        command: "Verify actor identity and permissions",
      },
      {
        type: "resolve-tenant",
        description: "Verify tenant scope",
        command: "Ensure interaction is within tenant boundaries",
      },
      {
        type: "resolve-purpose",
        description: "Verify purpose of use",
        command: "Check purpose matches allowed purposes",
      },
      {
        type: "resolve-consent",
        description: "Verify consent status",
        command: "Check consent is granted and valid",
      },
    ],
    estimatedComplexity: "moderate",
    requiresIntervention: false,
  },

  "interaction-provider-unavailable": {
    failureCategory: "interaction-provider-unavailable",
    title: "Interaction provider is unavailable",
    explanation:
      "The interaction provider (e.g., Data Cloud, external service) is not responding or is experiencing an outage.",
    actions: [
      {
        type: "check-provider",
        description: "Check provider service status",
        command: "Verify Data Cloud or external service health",
      },
      {
        type: "manual-intervention",
        description: "Retry the interaction after provider recovery",
        command: "Wait for provider to recover and retry",
      },
    ],
    estimatedComplexity: "simple",
    requiresIntervention: false,
  },

  "environment-blocked": {
    failureCategory: "environment-blocked",
    title: "Environment is blocked for this operation",
    explanation:
      "The current environment (e.g., Docker, Rust, Python) is not available or is blocked by policy.",
    actions: [
      {
        type: "enable-environment",
        description: "Enable the required environment",
        command: "Start Docker daemon, install Rust, or configure Python environment",
      },
      {
        type: "fix-configuration",
        description: "Check environment policy configuration",
        command: "Verify environment is not blocked by security policy",
      },
    ],
    estimatedComplexity: "simple",
    requiresIntervention: false,
  },

  "dependency-blocked": {
    failureCategory: "dependency-blocked",
    title: "Dependency is blocked by policy",
    explanation:
      "A required dependency is blocked by security policy or is not available in the approved registry.",
    actions: [
      {
        type: "resolve-dependency",
        description: "Request approval for the blocked dependency",
        command: "Submit dependency approval request",
      },
      {
        type: "fix-dependency",
        description: "Use an alternative approved dependency",
        command: "Find and use an approved alternative",
      },
    ],
    estimatedComplexity: "moderate",
    requiresIntervention: true,
  },

  "configuration-blocked": {
    failureCategory: "configuration-blocked",
    title: "Configuration is blocked by policy",
    explanation:
      "The configuration contains settings that are not allowed by policy (e.g., insecure settings, blocked values).",
    actions: [
      {
        type: "fix-configuration",
        description: "Update configuration to comply with policy",
        command: "Review and fix blocked configuration values",
      },
      {
        type: "fix-configuration",
        description: "Request policy exception if business-justified",
        command: "Contact platform administrator for policy exception",
      },
    ],
    estimatedComplexity: "moderate",
    requiresIntervention: true,
  },

  "test-failure": {
    failureCategory: "test-failure",
    title: "Tests failed during lifecycle phase",
    explanation:
      "One or more tests failed during the test phase. The lifecycle cannot proceed until tests pass.",
    actions: [
      {
        type: "fix-test",
        description: "Review test failure logs to identify the root cause",
        command: "Check test output for specific failure details",
      },
      {
        type: "fix-test",
        description: "Fix the failing tests or the code they test",
        command: "Update test code or implementation to fix failures",
      },
      {
        type: "fix-test",
        description: "Run tests locally to verify the fix",
        command: "Run test command locally before retrying lifecycle",
      },
    ],
    estimatedComplexity: "moderate",
    requiresIntervention: false,
  },

  "gate-failure": {
    failureCategory: "gate-failure",
    title: "Quality gate failed during lifecycle phase",
    explanation:
      "A quality gate (e.g., coverage, lint, security scan) failed. The lifecycle cannot proceed until the gate passes.",
    actions: [
      {
        type: "address-gate",
        description: "Review gate failure details to understand the violation",
        command: "Check gate output for specific violation details",
      },
      {
        type: "address-gate",
        description: "Fix the code or configuration to address the gate violation",
        command: "Update code or configuration to meet gate requirements",
      },
      {
        type: "address-gate",
        description: "Request gate exception if the violation is acceptable",
        command: "Contact platform administrator for gate exception",
      },
    ],
    estimatedComplexity: "moderate",
    requiresIntervention: true,
  },

  "artifact-validation-failure": {
    failureCategory: "artifact-validation-failure",
    title: "Artifact validation failed",
    explanation:
      "The generated artifact failed validation (e.g., checksum mismatch, missing metadata, invalid format).",
    actions: [
      {
        type: "regenerate-artifact",
        description: "Regenerate the artifact",
        command: "Re-run the build/package phase",
      },
      {
        type: "fix-configuration",
        description: "Check artifact manifest configuration",
        command: "Verify artifact manifest fields are correct",
      },
      {
        type: "regenerate-artifact",
        description: "Check for corruption in the build cache",
        command: "Clear build cache and regenerate",
      },
    ],
    estimatedComplexity: "simple",
    requiresIntervention: false,
  },

  unknown: {
    failureCategory: "unknown",
    title: "Unknown failure type",
    explanation:
      "The failure type is not recognized by the recovery guidance system. Manual investigation is required.",
    actions: [
      {
        type: "manual-intervention",
        description: "Review the full error logs and stack traces",
        command: "Check kernel logs for detailed error information",
      },
      {
        type: "manual-intervention",
        description: "Contact platform support for assistance",
        command: "Escalate to platform engineering team",
      },
    ],
    estimatedComplexity: "complex",
    requiresIntervention: true,
  },
};

/**
 * Gets recovery guidance for a specific failure category.
 *
 * @param category - The failure category
 * @returns Recovery guidance for the category
 */
export function getRecoveryGuidance(
  category: LifecycleFailureCategory
): LifecycleRecoveryGuidance {
  return RECOVERY_GUIDANCE_REGISTRY[category] || RECOVERY_GUIDANCE_REGISTRY.unknown;
}

/**
 * Infers the failure category from an error message or status code.
 *
 * @param errorMessage - The error message
 * @param statusCode - Optional status code
 * @returns The inferred failure category
 */
export function inferFailureCategory(
  errorMessage: string,
  statusCode?: number
): LifecycleFailureCategory {
  const lowerMessage = errorMessage.toLowerCase();

  // Toolchain errors
  if (lowerMessage.includes("toolchain") || lowerMessage.includes("not found") || lowerMessage.includes("command not found")) {
    return "adapter-toolchain-missing";
  }
  if (lowerMessage.includes("version") && (lowerMessage.includes("mismatch") || lowerMessage.includes("required"))) {
    return "adapter-toolchain-version-mismatch";
  }

  // Dependency errors
  if (lowerMessage.includes("dependency") || lowerMessage.includes("npm") || lowerMessage.includes("gradle") || lowerMessage.includes("cargo")) {
    return "adapter-dependency-error";
  }

  // Configuration errors
  if (lowerMessage.includes("configuration") || lowerMessage.includes("invalid") || lowerMessage.includes("schema")) {
    return "adapter-configuration-error";
  }

  // Timeout errors
  if (lowerMessage.includes("timeout") || lowerMessage.includes("timed out")) {
    return "adapter-timeout";
  }

  // Policy errors
  if (lowerMessage.includes("authentication") || lowerMessage.includes("unauthorized") || (statusCode === 401)) {
    return "policy-authentication-failed";
  }
  if (lowerMessage.includes("tenant") || lowerMessage.includes("isolation")) {
    return "policy-tenant-isolation-violation";
  }
  if (lowerMessage.includes("purpose") || lowerMessage.includes("use")) {
    return "policy-purpose-of-use-violation";
  }
  if (lowerMessage.includes("consent")) {
    return "policy-consent-verification-failed";
  }

  // Interaction errors
  if (lowerMessage.includes("handler") && lowerMessage.includes("not found")) {
    return "interaction-handler-not-found";
  }
  if (lowerMessage.includes("provider") && lowerMessage.includes("unavailable")) {
    return "interaction-provider-unavailable";
  }

  // Environment errors
  if (lowerMessage.includes("environment") || lowerMessage.includes("docker") || lowerMessage.includes("blocked")) {
    return "environment-blocked";
  }

  // Test errors
  if (lowerMessage.includes("test") && lowerMessage.includes("fail")) {
    return "test-failure";
  }

  // Gate errors
  if (lowerMessage.includes("gate") || lowerMessage.includes("coverage") || lowerMessage.includes("lint")) {
    return "gate-failure";
  }

  // Artifact errors
  if (lowerMessage.includes("artifact") || lowerMessage.includes("checksum") || lowerMessage.includes("validation")) {
    return "artifact-validation-failure";
  }

  return "unknown";
}

/**
 * Formats recovery guidance as a human-readable summary.
 *
 * @param guidance - The recovery guidance
 * @returns A formatted summary string
 */
export function formatRecoveryGuidance(
  guidance: LifecycleRecoveryGuidance
): string {
  const lines: string[] = [];
  lines.push(`## ${guidance.title}`);
  lines.push("");
  lines.push(guidance.explanation);
  lines.push("");
  lines.push("### Recovery Actions:");
  lines.push("");

  guidance.actions.forEach((action, index) => {
    lines.push(`${index + 1}. **${action.description}**`);
    if (action.command) {
      lines.push(`   Command: \`${action.command}\``);
    }
    if (action.referenceUrl) {
      lines.push(`   Reference: ${action.referenceUrl}`);
    }
    lines.push("");
  });

  lines.push(`**Estimated Complexity:** ${guidance.estimatedComplexity}`);
  if (guidance.requiresIntervention) {
    lines.push("**⚠️ Requires manual intervention or approval**");
  }

  return lines.join("\n");
}
