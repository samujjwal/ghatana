/**
 * Artifact compiler runtime health checks.
 *
 * @doc.type service
 * @doc.purpose Identify required artifact compiler/synthesis runtimes for governed imports
 * @doc.layer product
 */

export type ArtifactCompilerRuntimeStatus = 'available' | 'unavailable';

export interface ArtifactCompilerRuntimeRequirement {
  readonly id: string;
  readonly label: string;
  readonly required: boolean;
  readonly endpoint: string;
  readonly healthUrl: string;
  readonly description: string;
}

export interface ArtifactCompilerRuntimeHealth {
  readonly status: ArtifactCompilerRuntimeStatus;
  readonly checkedAt: string;
  readonly requirements: readonly ArtifactCompilerRuntimeRequirement[];
  readonly unavailableRequirements: readonly ArtifactCompilerRuntimeRequirement[];
  readonly message: string;
}

export interface ArtifactCompilerRuntimeHealthOptions {
  readonly artifactApiBaseUrl?: string;
  readonly analyzerHealthUrl?: string;
  readonly fetcher?: typeof fetch;
}

const DEFAULT_ARTIFACT_API_BASE_URL = 'http://localhost:8080/api/v1/yappc/artifact';
const DEFAULT_ANALYZER_HEALTH_URL = 'http://localhost:8080/health';

function getRuntimeEnv(): {
  readonly VITE_YAPPC_ARTIFACT_API_BASE_URL?: string;
  readonly VITE_YAPPC_ARTIFACT_ANALYZER_HEALTH_URL?: string;
} {
  return import.meta.env as {
    readonly VITE_YAPPC_ARTIFACT_API_BASE_URL?: string;
    readonly VITE_YAPPC_ARTIFACT_ANALYZER_HEALTH_URL?: string;
  };
}

export function getArtifactCompilerRuntimeRequirements(
  options: ArtifactCompilerRuntimeHealthOptions = {},
): readonly ArtifactCompilerRuntimeRequirement[] {
  const env = getRuntimeEnv();
  const artifactApiBaseUrl =
    options.artifactApiBaseUrl ?? env.VITE_YAPPC_ARTIFACT_API_BASE_URL ?? DEFAULT_ARTIFACT_API_BASE_URL;
  const analyzerHealthUrl =
    options.analyzerHealthUrl ?? env.VITE_YAPPC_ARTIFACT_ANALYZER_HEALTH_URL ?? DEFAULT_ANALYZER_HEALTH_URL;

  return [
    {
      id: 'artifact-analyzer-http',
      label: 'YAPPC artifact analyzer HTTP runtime',
      required: true,
      endpoint: artifactApiBaseUrl,
      healthUrl: analyzerHealthUrl,
      description:
        'Required for governed source import, artifact graph/residual analysis, and synthesis orchestration.',
    },
  ];
}

export async function checkArtifactCompilerRuntimeHealth(
  options: ArtifactCompilerRuntimeHealthOptions = {},
): Promise<ArtifactCompilerRuntimeHealth> {
  const requirements = getArtifactCompilerRuntimeRequirements(options);
  const fetcher = options.fetcher ?? (typeof fetch === 'function' ? fetch : null);

  if (!fetcher) {
    return {
      status: 'unavailable',
      checkedAt: new Date().toISOString(),
      requirements,
      unavailableRequirements: requirements,
      message: 'Artifact compiler runtime health cannot be checked because fetch is unavailable.',
    };
  }

  const unavailableRequirements: ArtifactCompilerRuntimeRequirement[] = [];

  for (const requirement of requirements) {
    try {
      const response = await fetcher(requirement.healthUrl, {
        method: 'GET',
        credentials: 'omit',
      });

      if (!response.ok) {
        unavailableRequirements.push(requirement);
      }
    } catch {
      unavailableRequirements.push(requirement);
    }
  }

  const status: ArtifactCompilerRuntimeStatus =
    unavailableRequirements.length === 0 ? 'available' : 'unavailable';

  return {
    status,
    checkedAt: new Date().toISOString(),
    requirements,
    unavailableRequirements,
    message:
      status === 'available'
        ? 'Artifact compiler runtime is available.'
        : formatArtifactCompilerRuntimeUnavailableMessage(unavailableRequirements),
  };
}

export function formatArtifactCompilerRuntimeUnavailableMessage(
  unavailableRequirements: readonly ArtifactCompilerRuntimeRequirement[],
): string {
  if (unavailableRequirements.length === 0) {
    return 'Artifact compiler runtime is available.';
  }

  const requirementList = unavailableRequirements
    .map((requirement) => `${requirement.label} (${requirement.healthUrl})`)
    .join(', ');

  return `Artifact compiler runtime unavailable. Start or configure: ${requirementList}.`;
}
