import * as fs from "node:fs/promises";
import * as path from "node:path";
import type {
  ToolchainAdapter,
  ToolchainAdapterContext,
  ToolchainPlanStep,
  ToolchainExecutionResult,
  ToolchainOutputValidationResult,
  ProductLifecyclePhase,
  ProductSurfaceType,
  AdapterPreflightResult,
  LifecycleFailureClassifier,
} from "../ToolchainAdapter.js";
import { SpawnCommandRunner } from "../execution/SpawnCommandRunner.js";
import type { CommandRunner } from "../execution/CommandRunner.js";
import {
  createCommandObservability,
  createDryRunObservability,
  createToolchainExecutionResult,
  truncateToolchainOutput,
} from "../execution/ToolchainExecutionResultFactory.js";
import {
  createDefaultPreflightResult,
  createDefaultFailureClassifier,
} from "../ToolchainAdapter.js";

/** Container image artifact produced by a successful Docker build. */
export interface ContainerImageArtifact {
  readonly type: "container-image";
  readonly image: string;
  readonly tag: string;
  /** Digest filled in after validateOutputs inspects the local image. */
  digest?: string;
  localImageId?: string;
}

/**
 * @doc.type class
 * @doc.purpose Builds product surface container images via `docker buildx build --load`.
 *   Reads image/tag/dockerfile/context from the adapter context (populated by the planner
 *   from the product's kernel-product.yaml package section).
 * @doc.layer platform
 * @doc.pattern Adapter
 */
export class DockerBuildxAdapter implements ToolchainAdapter {
  readonly id = "docker-buildx";
  readonly supportedPhases: ProductLifecyclePhase[] = ["package"];
  readonly supportedSurfaceTypes: ProductSurfaceType[] = [
    "backend-api",
    "worker",
    "operator",
    "web",
  ];

  private readonly repoRoot: string;
  private readonly commandRunner: CommandRunner;

  constructor(
    options: { repoRoot?: string; commandRunner?: CommandRunner } = {},
  ) {
    this.repoRoot = options.repoRoot ?? process.cwd();
    this.commandRunner = options.commandRunner ?? new SpawnCommandRunner();
  }

  async plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]> {
    const { image, tag, dockerfile, dockerContext } =
      this.resolveConfig(context);
    const imageRef = `${image}:${tag}`;
    const args = this.buildArgs(context);
    const labels = this.lifecycleLabels(context);
    const platformArgs = this.platformArgs(context);
    const builderArgs = this.builderArgs(context);

    const command: string[] = [
      "docker",
      "buildx",
      "build",
      ...builderArgs,
      "--load",
      "-t",
      imageRef,
      "-f",
      dockerfile,
      ...labels,
      ...platformArgs,
      ...args,
      dockerContext,
    ];

    return [
      {
        id: `docker-buildx-package-${context.surface.type}`,
        description: `Build container image ${imageRef} from ${dockerfile}`,
        command,
        workingDirectory: this.repoRoot,
      },
    ];
  }

  async execute(
    context: ToolchainAdapterContext,
  ): Promise<ToolchainExecutionResult> {
    const startedAt = Date.now();
    const [step] = await this.plan(context);
    const timeoutMs = this.resolveTimeoutMs(context.surfaceConfig);

    if (context.dryRun) {
      context.logger.info(
        `[DRY-RUN] Would execute: ${this.safeCommand(step.command)}`,
      );
      return createToolchainExecutionResult(context, {
        status: "skipped",
        steps: [{ stepId: step.id, status: "skipped", durationMs: 0 }],
        artifacts: [],
        durationMs: 0,
        observability: createDryRunObservability(step.id),
      });
    }

    const preflight = await this.validatePreconditions(context);
    if (preflight.status === "invalid") {
      return createToolchainExecutionResult(context, {
        status: "failed",
        steps: [{ stepId: step.id, status: "failed", durationMs: 0 }],
        artifacts: [],
        durationMs: Date.now() - startedAt,
        failure: {
          stepId: step.id,
          message: preflight.errors.map((error) => error.message).join("; "),
        },
        warnings: preflight.unexpectedArtifacts,
        observability: createDryRunObservability(step.id),
      });
    }

    context.logger.info(
      `Building container image: ${this.safeCommand(step.command)}`,
    );

    const commandResult = await this.commandRunner.run(
      step.command[0],
      step.command.slice(1),
      {
        cwd: step.workingDirectory,
        env: { ...process.env, ...step.env },
        timeoutMs,
        commandId: step.id,
        redact: (value) => this.redactCommandText(value),
      },
    );

    const durationMs = commandResult.durationMs || Date.now() - startedAt;

    if (commandResult.exitCode !== 0) {
      context.logger.error(
        `docker buildx build failed (exit ${commandResult.exitCode})`,
      );
      return createToolchainExecutionResult(context, {
        status: "failed",
        steps: [
          {
            stepId: step.id,
            status: "failed",
            exitCode: commandResult.exitCode,
            stdout: truncateToolchainOutput(commandResult.stdout),
            stderr: truncateToolchainOutput(commandResult.stderr),
            durationMs,
          },
        ],
        artifacts: [],
        durationMs,
        failure: {
          stepId: step.id,
          message: `docker-buildx-failed: docker buildx build exited with code ${commandResult.exitCode}`,
          cause: truncateToolchainOutput(commandResult.stderr),
        },
        observability: createCommandObservability(
          step.id,
          commandResult,
          durationMs,
        ),
      });
    }

    const validation = await this.validateOutputs(context);
    const artifacts = await this.extractArtifacts(context);

    if (validation.status === "invalid") {
      return createToolchainExecutionResult(context, {
        status: "failed",
        steps: [
          {
            stepId: step.id,
            status: "failed",
            exitCode: commandResult.exitCode,
            stdout: truncateToolchainOutput(commandResult.stdout),
            stderr: truncateToolchainOutput(commandResult.stderr),
            durationMs,
          },
        ],
        artifacts,
        durationMs,
        failure: {
          stepId: step.id,
          message: validation.errors.map((e) => e.message).join("; "),
        },
        observability: createCommandObservability(
          step.id,
          commandResult,
          durationMs,
        ),
      });
    }

    context.logger.info(`Container image build succeeded in ${durationMs}ms`);
    return createToolchainExecutionResult(context, {
      status: "succeeded",
      steps: [
        {
          stepId: step.id,
          status: "succeeded",
          exitCode: 0,
          stdout: truncateToolchainOutput(commandResult.stdout),
          stderr: truncateToolchainOutput(commandResult.stderr),
          durationMs,
        },
      ],
      artifacts,
      durationMs,
      evidenceRefs: artifacts.map((artifact) => `container-image:${artifact}`),
      observability: createCommandObservability(
        step.id,
        commandResult,
        durationMs,
      ),
    });
  }

  async validateOutputs(
    context: ToolchainAdapterContext,
  ): Promise<ToolchainOutputValidationResult> {
    const { imageRef } = this.resolveImageRef(context);
    const result = await this.commandRunner.run(
      "docker",
      [
        "image",
        "inspect",
        "--format",
        "{{json .RepoDigests}}|{{.Id}}",
        imageRef,
      ],
      { cwd: this.repoRoot, env: { ...process.env } },
    );

    if (result.exitCode !== 0 || result.stdout.trim().length === 0) {
      return {
        status: "invalid",
        errors: [
          {
            path: "container-image",
            message:
              `Image "${imageRef}" not found in local Docker daemon after build. ` +
              "Ensure --load flag is used with docker buildx.",
          },
        ],
        missingArtifacts: [imageRef],
        unexpectedArtifacts: [],
      };
    }
    const artifactRef = this.resolveDigestArtifactRef(imageRef, result.stdout);
    if (this.requiresDigest(context) && artifactRef === imageRef) {
      return {
        status: "invalid",
        errors: [
          {
            path: "container-image.digest",
            message: `container-image-digest-missing: required image "${imageRef}" did not expose a sha256 digest`,
          },
        ],
        missingArtifacts: [`${imageRef}@sha256`],
        unexpectedArtifacts: [],
      };
    }

    return {
      status: "valid",
      errors: [],
      missingArtifacts: [],
      unexpectedArtifacts: [],
    };
  }

  async preflight(_context: ToolchainAdapterContext): Promise<AdapterPreflightResult> {
    return createDefaultPreflightResult();
  }

  async classifyFailure(error: Error, _context: ToolchainAdapterContext): Promise<LifecycleFailureClassifier> {
    return createDefaultFailureClassifier(error, this.id);
  }

  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  // Private helpers
  // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  private async extractArtifacts(
    context: ToolchainAdapterContext,
  ): Promise<string[]> {
    const { imageRef } = this.resolveImageRef(context);

    const idResult = await this.commandRunner.run(
      "docker",
      [
        "image",
        "inspect",
        "--format",
        "{{json .RepoDigests}}|{{.Id}}",
        imageRef,
      ],
      { cwd: this.repoRoot, env: { ...process.env } },
    );

    if (idResult.exitCode === 0 && idResult.stdout.trim().length > 0) {
      return [this.resolveDigestArtifactRef(imageRef, idResult.stdout)];
    }
    return [imageRef];
  }

  /** Resolve image, tag, dockerfile, and context from adapter context config. */
  private resolveConfig(context: ToolchainAdapterContext): {
    image: string;
    tag: string;
    dockerfile: string;
    dockerContext: string;
  } {
    const cfg = context.surfaceConfig;

    const image = this.requireConfigString(cfg, "image");
    const tag = this.requireConfigString(cfg, "tag");
    const rawDockerfile = this.requireConfigString(cfg, "dockerfile");
    const rawContext = this.requireConfigString(cfg, "context");

    const dockerfile = path.isAbsolute(rawDockerfile)
      ? rawDockerfile
      : path.join(this.repoRoot, rawDockerfile);

    const dockerContext = path.isAbsolute(rawContext)
      ? rawContext
      : path.join(this.repoRoot, rawContext);

    return { image, tag, dockerfile, dockerContext };
  }

  private async validatePreconditions(
    context: ToolchainAdapterContext,
  ): Promise<ToolchainOutputValidationResult> {
    const { image, tag, dockerfile, dockerContext } =
      this.resolveConfig(context);
    const errors: { path: string; message: string }[] = [];
    const warnings: string[] = [];

    if (!isValidImageName(image)) {
      errors.push({
        path: "surfaceConfig.image",
        message: `invalid-image-ref: DockerBuildxAdapter received invalid image "${image}"`,
      });
    }
    if (!isValidTag(tag)) {
      errors.push({
        path: "surfaceConfig.tag",
        message: `invalid-image-tag: DockerBuildxAdapter received invalid tag "${tag}"`,
      });
    }
    if (context.surfaceConfig.sbom === true) {
      errors.push({
        path: "surfaceConfig.sbom",
        message:
          "sbom-not-ready: Docker Buildx SBOM export is declared but feature-gated off",
      });
    }

    await this.expectPath(dockerfile, "surfaceConfig.dockerfile", errors);
    await this.expectPath(dockerContext, "surfaceConfig.context", errors);

    if (Array.isArray(context.surfaceConfig.platforms)) {
      warnings.push("docker-buildx-platforms-declared");
    }
    const builder = context.surfaceConfig.builder;
    if (builder !== undefined && !isValidBuilderName(String(builder))) {
      errors.push({
        path: "surfaceConfig.builder",
        message: `invalid-buildx-builder: DockerBuildxAdapter received invalid builder "${String(builder)}"`,
      });
    }
    if (builder !== undefined && errors.length === 0) {
      const builderName = String(builder).trim();
      if (builderName.length > 0) {
        const builderCheck = await this.commandRunner.run(
          "docker",
          ["buildx", "inspect", builderName, "--bootstrap"],
          {
            cwd: this.repoRoot,
            env: { ...process.env },
            timeoutMs: 30_000,
            commandId: `docker-buildx-inspect-${builderName}`,
          },
        );
        const builderCheckOutput = `${builderCheck.stdout}\n${builderCheck.stderr}`;
        if (
          builderCheck.exitCode !== 0 ||
          hasBuildxInspectError(builderCheckOutput)
        ) {
          errors.push({
            path: "surfaceConfig.builder",
            message:
              `docker-buildx-builder-unavailable: builder "${builderName}" is not healthy. ` +
              truncateToolchainOutput(
                builderCheck.stderr ||
                  builderCheck.stdout ||
                  "docker buildx inspect failed",
              ),
          });
        }
      }
    }

    return {
      status: errors.length > 0 ? "invalid" : "valid",
      errors,
      missingArtifacts: errors.map((error) => error.path),
      unexpectedArtifacts: warnings,
    };
  }

  private resolveTimeoutMs(surfaceConfig: Record<string, unknown>): number {
    const configured = surfaceConfig.timeoutMs;
    if (
      typeof configured === "number" &&
      Number.isFinite(configured) &&
      configured >= 60_000
    ) {
      return configured;
    }
    return 900_000;
  }

  private async expectPath(
    filePath: string,
    field: string,
    errors: { path: string; message: string }[],
  ): Promise<void> {
    try {
      await fs.access(filePath);
    } catch {
      errors.push({
        path: field,
        message: `${field}-not-found: ${filePath}`,
      });
    }
  }

  private requireConfigString(
    config: Record<string, unknown>,
    key: string,
  ): string {
    const value = config[key];
    if (typeof value !== "string" || value.trim().length === 0) {
      throw new Error(`DockerBuildxAdapter requires surfaceConfig.${key}`);
    }
    return value;
  }

  private resolveImageRef(context: ToolchainAdapterContext): {
    imageRef: string;
  } {
    const { image, tag } = this.resolveConfig(context);
    return { imageRef: `${image}:${tag}` };
  }

  private resolveDigestArtifactRef(
    imageRef: string,
    inspectOutput: string,
  ): string {
    const [repoDigestsRaw] = inspectOutput.trim().split("|");
    if (!repoDigestsRaw) {
      return imageRef;
    }

    try {
      const repoDigests = JSON.parse(repoDigestsRaw) as unknown;
      if (Array.isArray(repoDigests)) {
        const digestRef = repoDigests.find(
          (value): value is string =>
            typeof value === "string" && value.includes("@sha256:"),
        );
        return digestRef ?? imageRef;
      }
    } catch {
      return imageRef;
    }

    return imageRef;
  }

  /** Build `--build-arg KEY=VALUE` args array from surfaceConfig.buildArgs. */
  private buildArgs(context: ToolchainAdapterContext): string[] {
    const buildArgs = context.surfaceConfig.buildArgs as
      | Record<string, string>
      | undefined;
    if (!buildArgs) return [];
    return Object.entries(buildArgs).flatMap(([k, v]) => [
      "--build-arg",
      `${k}=${v}`,
    ]);
  }

  private platformArgs(context: ToolchainAdapterContext): string[] {
    const platforms = context.surfaceConfig.platforms;
    if (!Array.isArray(platforms) || platforms.length === 0) {
      return [];
    }
    const normalized = platforms.filter(
      (platform): platform is string =>
        typeof platform === "string" && platform.trim().length > 0,
    );
    return normalized.length === 0 ? [] : ["--platform", normalized.join(",")];
  }

  private builderArgs(context: ToolchainAdapterContext): string[] {
    const configured = context.surfaceConfig.builder;
    if (configured === undefined) {
      return [];
    }
    const builder = String(configured).trim();
    return builder.length === 0 ? [] : ["--builder", builder];
  }

  private lifecycleLabels(context: ToolchainAdapterContext): string[] {
    const labelEntries: [string, string][] = [
      ["ghatana.productUnit", context.productId],
      ["ghatana.surface", context.surface.type],
    ];
    if (context.runId) {
      labelEntries.push(["ghatana.kernel.runId", context.runId]);
    }
    if (context.correlationId) {
      labelEntries.push([
        "ghatana.kernel.correlationId",
        context.correlationId,
      ]);
    }
    return labelEntries.flatMap(([key, value]) => [
      "--label",
      `${key}=${value}`,
    ]);
  }

  private requiresDigest(context: ToolchainAdapterContext): boolean {
    return context.surfaceConfig.requiredDigest !== false;
  }

  private safeCommand(command: readonly string[]): string {
    return this.redactCommandText(command.join(" "));
  }

  private redactCommandText(value: string): string {
    return value.replace(/(--build-arg\s+[^=\s]+=)([^\s]+)/g, "$1<redacted>");
  }
}

function isValidImageName(value: string): boolean {
  return /^[a-z0-9]+(?:(?:[._/-][a-z0-9]+)+)?$/.test(value);
}

function isValidTag(value: string): boolean {
  return /^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$/.test(value);
}

function isValidBuilderName(value: string): boolean {
  return /^[A-Za-z0-9_.-]{1,128}$/.test(value);
}

function hasBuildxInspectError(output: string): boolean {
  return /(?:^|\n)Error:\s|Cannot connect to the Docker daemon|error reading from server|EOF/.test(
    output,
  );
}
