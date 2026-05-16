import type {
  ArtifactEntry,
  ArtifactFingerprint,
  ArtifactManifest,
  ArtifactPackaging,
  ArtifactTrustState,
  ArtifactType,
} from '../domain/ArtifactManifest.js';

/**
 * Policy rule for artifact trust enforcement.
 */
export interface ArtifactPolicyRule {
  requireTrustState?: readonly ArtifactTrustState[];
  requireSignature?: boolean;
  requireSbom?: boolean;
  requireAttestation?: boolean;
  requireDigestForContainers?: boolean;
  maxUnverifiedCount?: number;
  minArtifactCount?: number;
  expectedArtifacts?: readonly ExpectedArtifactDeclaration[];
}

/**
 * Result of a policy evaluation over an artifact manifest.
 */
export interface ArtifactPolicyResult {
  readonly compliant: boolean;
  readonly violations: ArtifactPolicyViolation[];
  readonly checkedCount: number;
  readonly compliantCount: number;
  readonly violatingArtifactIds: string[];
}

export interface ArtifactPolicyViolation {
  readonly artifactId: string;
  readonly reasonCode:
    | 'trust-state-below-policy'
    | 'signature-required'
    | 'sbom-required'
    | 'attestation-required'
    | 'container-digest-required'
    | 'artifact-count-below-policy'
    | 'expected-artifact-missing';
  readonly message: string;
}

/**
 * Product artifact validator
 */
export class ProductArtifactValidator {
  /**
   * Validate artifact entry
   */
  validateArtifact(artifact: ArtifactEntry): ValidationError[] {
    const errors: ValidationError[] = [];

    if (!artifact.id || artifact.id.trim().length === 0) {
      errors.push({ path: 'id', message: 'Artifact ID is required' });
    }

    if (!artifact.path || artifact.path.trim().length === 0) {
      errors.push({ path: 'path', message: 'Artifact path is required' });
    }

    if (!artifact.metadata.type) {
      errors.push({ path: 'metadata.type', message: 'Artifact type is required' });
    }

    if (!artifact.metadata.version) {
      errors.push({ path: 'metadata.version', message: 'Artifact version is required' });
    }

    if (!artifact.fingerprint.hash) {
      errors.push({ path: 'fingerprint.hash', message: 'Fingerprint hash is required' });
    }

    this.validateFingerprint(artifact.fingerprint, errors);

    if (artifact.metadata.sizeBytes < 0) {
      errors.push({ path: 'metadata.sizeBytes', message: 'Size bytes must be non-negative' });
    }

    return errors;
  }

  /**
   * Validate fingerprint
   */
  validateFingerprint(fingerprint: ArtifactFingerprint, errors: ValidationError[]): void {
    const validAlgorithms = ['sha256', 'sha512'];

    if (!validAlgorithms.includes(fingerprint.algorithm)) {
      errors.push({
        path: 'fingerprint.algorithm',
        message: `Invalid algorithm: ${fingerprint.algorithm}. Must be one of: ${validAlgorithms.join(', ')}`,
      });
    }

    if (fingerprint.hash.length === 0) {
      errors.push({ path: 'fingerprint.hash', message: 'Fingerprint hash is required' });
    }

    const expectedLengths: Record<string, number> = {
      sha256: 64,
      sha512: 128,
    };

    const expectedLength = expectedLengths[fingerprint.algorithm];
    if (fingerprint.hash.length !== expectedLength) {
      errors.push({
        path: 'fingerprint.hash',
        message: `Fingerprint hash must be ${expectedLength} characters for ${fingerprint.algorithm}`,
      });
    }

    if (!/^[a-f0-9]+$/i.test(fingerprint.hash)) {
      errors.push({ path: 'fingerprint.hash', message: 'Fingerprint hash must be a valid hexadecimal string' });
    }
  }

  /**
   * Validate artifact type
   */
  validateArtifactType(type: string): ValidationError[] {
    const errors: ValidationError[] = [];
    const validTypes = ['jvm-service', 'jvm-library', 'node-service', 'static-web-bundle', 'container-image', 'mobile-bundle', 'sdk-package', 'domain-pack', 'test-report', 'coverage-report', 'source-map', 'documentation'];

    if (!validTypes.includes(type)) {
      errors.push({
        path: 'type',
        message: `Invalid artifact type: ${type}. Must be one of: ${validTypes.join(', ')}`,
      });
    }

    return errors;
  }

  /**
   * Validate artifact path exists
   */
  async validateArtifactPathExists(path: string): Promise<ValidationError[]> {
    const errors: ValidationError[] = [];
    const { promises: fs } = await import('node:fs');

    try {
      await fs.access(path);
    } catch {
      errors.push({ path: 'path', message: `Artifact path does not exist: ${path}` });
    }

    return errors;
  }

  /**
   * Validate artifact size matches expected
   */
  async validateArtifactSize(path: string, expectedSize: number): Promise<ValidationError[]> {
    const errors: ValidationError[] = [];
    const { promises: fs } = await import('node:fs');

    try {
      const stats = await fs.stat(path);
      const actualSize = stats.size;

      if (actualSize !== expectedSize) {
        errors.push({
          path: 'size',
          message: `Artifact size mismatch: expected ${expectedSize} bytes, got ${actualSize} bytes`,
        });
      }
    } catch {
      errors.push({ path: 'path', message: `Cannot read artifact size: ${path}` });
    }

    return errors;
  }

  validatePackaging(packaging: string): ValidationError[] {
    const errors: ValidationError[] = [];
    const validPackagings = ['jar', 'distribution', 'static-files', 'container', 'npm', 'maven', 'apk', 'aab', 'ipa', 'json', 'xml'];

    if (!validPackagings.includes(packaging)) {
      errors.push({
        path: 'packaging',
        message: `Invalid artifact packaging: ${packaging}. Must be one of: ${validPackagings.join(', ')}`,
      });
    }

    return errors;
  }

  validateArtifactPolicy(
    manifest: ArtifactManifest,
    policy: ArtifactPolicyRule,
  ): ArtifactPolicyResult {
    const violations: ArtifactPolicyViolation[] = [];
    const violatingIds = new Set<string>();
    let compliantCount = 0;

    const artifactsById = new Map(manifest.artifacts.map((artifact) => [artifact.id, artifact]));
    const artifactsByRef = new Map(
      manifest.artifacts
        .filter((artifact) => artifact.metadata.artifactRef)
        .map((artifact) => [artifact.metadata.artifactRef as string, artifact]),
    );

    const requiredExpectedArtifacts = (policy.expectedArtifacts ?? []).filter(
      (expected) => expected.required,
    );
    for (const expected of requiredExpectedArtifacts) {
      const artifact = artifactsById.get(expected.id) ??
        (expected.artifactRef ? artifactsByRef.get(expected.artifactRef) : undefined);

      if (!artifact || !artifact.found) {
        violations.push({
          artifactId: expected.id,
          reasonCode: 'expected-artifact-missing',
          message: `Required expected artifact '${expected.id}' is missing from manifest evidence`,
        });
        violatingIds.add(expected.id);
      }
    }

    const requiredArtifactCount = this.resolveRequiredArtifactCount(policy);
    if (manifest.artifacts.length < requiredArtifactCount) {
      violations.push({
        artifactId: '__manifest__',
        reasonCode: 'artifact-count-below-policy',
        message: `Manifest has ${manifest.artifacts.length} artifacts but policy requires at least ${requiredArtifactCount}`,
      });
    }

    const allowedTrustStates = policy.requireTrustState;
    let unverifiedCount = 0;

    for (const artifact of manifest.artifacts) {
      const artifactViolations: ArtifactPolicyViolation[] = [];
      const trustState = artifact.metadata.trustState ?? 'unverified';

      if (trustState === 'unverified') {
        unverifiedCount++;
      }

      if (allowedTrustStates !== undefined && allowedTrustStates.length > 0) {
        if (!allowedTrustStates.includes(trustState)) {
          artifactViolations.push({
            artifactId: artifact.id,
            reasonCode: 'trust-state-below-policy',
            message: `Artifact '${artifact.id}' has trustState='${trustState}' but policy requires one of: ${allowedTrustStates.join(', ')}`,
          });
        }
      }

      if (policy.requireSignature === true && artifact.metadata.signature === undefined) {
        artifactViolations.push({
          artifactId: artifact.id,
          reasonCode: 'signature-required',
          message: `Artifact '${artifact.id}' is missing a required cryptographic signature`,
        });
      }

      if (policy.requireSbom === true && artifact.metadata.sbomRef === undefined) {
        artifactViolations.push({
          artifactId: artifact.id,
          reasonCode: 'sbom-required',
          message: `Artifact '${artifact.id}' is missing a required SBOM reference`,
        });
      }

      if (policy.requireAttestation === true && artifact.metadata.attestation === undefined) {
        artifactViolations.push({
          artifactId: artifact.id,
          reasonCode: 'attestation-required',
          message: `Artifact '${artifact.id}' is missing a required attestation`,
        });
      }

      if (
        policy.requireDigestForContainers === true &&
        artifact.metadata.type === 'container-image'
      ) {
        const imageRef = artifact.metadata.artifactRef ?? artifact.path;
        if (!imageRef.includes('@sha256:')) {
          artifactViolations.push({
            artifactId: artifact.id,
            reasonCode: 'container-digest-required',
            message: `Container artifact '${artifact.id}' must include a sha256 digest reference`,
          });
        }
      }

      if (artifactViolations.length > 0) {
        violations.push(...artifactViolations);
        violatingIds.add(artifact.id);
      } else {
        compliantCount++;
      }
    }

    const maxUnverified = policy.maxUnverifiedCount;
    if (maxUnverified !== undefined && unverifiedCount > maxUnverified) {
      violations.push({
        artifactId: '__manifest__',
        reasonCode: 'trust-state-below-policy',
        message: `Manifest has ${unverifiedCount} unverified artifacts but policy allows at most ${maxUnverified}`,
      });
    }

    return {
      compliant: violations.length === 0,
      violations,
      checkedCount: manifest.artifacts.length,
      compliantCount,
      violatingArtifactIds: [...violatingIds],
    };
  }

  private resolveRequiredArtifactCount(policy: ArtifactPolicyRule): number {
    if (policy.minArtifactCount !== undefined) {
      return policy.minArtifactCount;
    }

    const hasStrictRequirement =
      policy.requireSignature === true ||
      policy.requireSbom === true ||
      policy.requireAttestation === true ||
      policy.requireDigestForContainers === true ||
      (policy.requireTrustState !== undefined && policy.requireTrustState.length > 0);

    return hasStrictRequirement ? 1 : 0;
  }

  validateExpectedArtifacts(params: {
    manifest: ArtifactManifest;
    expectedArtifacts: readonly ExpectedArtifactDeclaration[];
  }): ProductArtifactValidationResult {
    const errors: ValidationError[] = [];
    const missing: MissingArtifactFailure[] = [];
    const artifactsById = new Map(params.manifest.artifacts.map((artifact) => [artifact.id, artifact]));
    const artifactsByRef = new Map(
      params.manifest.artifacts
        .filter((artifact) => artifact.metadata.artifactRef)
        .map((artifact) => [artifact.metadata.artifactRef as string, artifact]),
    );

    for (const expected of params.expectedArtifacts) {
      const artifact = artifactsById.get(expected.id) ??
        (expected.artifactRef ? artifactsByRef.get(expected.artifactRef) : undefined);

      if (!artifact || !artifact.found) {
        const failure: MissingArtifactFailure = {
          reasonCode: 'artifact-missing',
          artifactId: expected.id,
          required: expected.required,
          message: `Expected artifact ${expected.id} is missing`,
        };
        missing.push(failure);
        if (expected.required) {
          errors.push({ path: `artifacts.${expected.id}`, message: failure.message });
        }
        continue;
      }

      if (artifact.metadata.type !== expected.type) {
        errors.push({
          path: `artifacts.${expected.id}.metadata.type`,
          message: `Artifact ${expected.id} type mismatch: expected ${expected.type}, got ${artifact.metadata.type}`,
        });
      }

      if (artifact.metadata.packaging !== expected.packaging) {
        errors.push({
          path: `artifacts.${expected.id}.metadata.packaging`,
          message: `Artifact ${expected.id} packaging mismatch: expected ${expected.packaging}, got ${artifact.metadata.packaging}`,
        });
      }

      if (expected.requireDigest && artifact.metadata.type === 'container-image') {
        this.validateContainerDigest(expected, artifact, errors);
      }
    }

    return {
      valid: errors.length === 0,
      errors,
      missing,
    };
  }

  private validateContainerDigest(
    expected: ExpectedArtifactDeclaration,
    artifact: ArtifactEntry,
    errors: ValidationError[],
  ): void {
    const imageRef = artifact.metadata.artifactRef ?? artifact.path;
    if (!imageRef.includes('@sha256:')) {
      errors.push({
        path: `artifacts.${expected.id}.path`,
        message: `Container image artifact ${expected.id} must include a sha256 digest reference`,
      });
    }
  }
}

/**
 * Validation error
 */
export interface ValidationError {
  path: string;
  message: string;
}

export interface ExpectedArtifactDeclaration {
  id: string;
  type: ArtifactType;
  packaging: ArtifactPackaging;
  required: boolean;
  artifactRef?: string;
  requireDigest?: boolean;
}

export interface MissingArtifactFailure {
  reasonCode: 'artifact-missing';
  artifactId: string;
  required: boolean;
  message: string;
}

export interface ProductArtifactValidationResult {
  valid: boolean;
  errors: ValidationError[];
  missing: MissingArtifactFailure[];
}
