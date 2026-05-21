/**
 * Tests for ArtifactLinkage contracts.
 */

import { describe, it, expect } from "vitest";
import {
  ArtifactSourceLinkage,
  ArtifactSourceLinkageSchema,
  ArtifactDeploymentLinkage,
  ArtifactDeploymentLinkageSchema,
  ArtifactTrustChain,
  ArtifactTrustChainSchema,
  LinkageVerificationResult,
  ArtifactLinkageVerifier,
} from "../ArtifactLinkage";

describe("ArtifactLinkage", () => {
  describe("ArtifactSourceLinkage", () => {
    it("should create a valid source linkage", () => {
      const linkage: ArtifactSourceLinkage = {
        gitCommit: "abc123def456",
        gitBranch: "main",
        gitRepository: "https://github.com/example/repo",
        sourceManifestRef: "manifest-123",
        sourceFingerprint: {
          algorithm: "sha256",
          hash: "def456abc123",
        },
        committedAt: "2024-01-01T00:00:00.000Z",
        author: "test@example.com",
        message: "Test commit",
      };

      expect(linkage.gitCommit).toBe("abc123def456");
      expect(linkage.gitBranch).toBe("main");
      expect(linkage.gitRepository).toBe("https://github.com/example/repo");
    });

    it("should validate source linkage with schema", () => {
      const linkage = {
        gitCommit: "abc123def4567890123456789012345678901234",
        gitBranch: "main",
        gitRepository: "https://github.com/example/repo",
        committedAt: "2024-01-01T00:00:00.000Z",
      };

      const result = ArtifactSourceLinkageSchema.parse(linkage);
      expect(result.gitCommit).toBe("abc123def4567890123456789012345678901234");
    });

    it("should reject invalid git commit format", () => {
      const linkage = {
        gitCommit: "invalid",
        gitBranch: "main",
        gitRepository: "https://github.com/example/repo",
        committedAt: "2024-01-01T00:00:00.000Z",
      };

      expect(() => ArtifactSourceLinkageSchema.parse(linkage)).toThrow();
    });

    it("should reject invalid git repository URL", () => {
      const linkage = {
        gitCommit: "abc123def456",
        gitBranch: "main",
        gitRepository: "not-a-url",
        committedAt: "2024-01-01T00:00:00.000Z",
      };

      expect(() => ArtifactSourceLinkageSchema.parse(linkage)).toThrow();
    });
  });

  describe("ArtifactDeploymentLinkage", () => {
    it("should create a valid deployment linkage", () => {
      const linkage: ArtifactDeploymentLinkage = {
        deploymentId: "deploy-123",
        environment: "production",
        deploymentManifestRef: "manifest-456",
        artifactRef: "artifact-789",
        previousArtifactRef: "artifact-old",
        status: "deployed",
        deployedAt: "2024-01-01T00:00:00.000Z",
        completedAt: "2024-01-01T00:05:00.000Z",
        deployedBy: "test@example.com",
        metadata: { key: "value" },
      };

      expect(linkage.deploymentId).toBe("deploy-123");
      expect(linkage.environment).toBe("production");
      expect(linkage.status).toBe("deployed");
    });

    it("should validate deployment linkage with schema", () => {
      const linkage = {
        deploymentId: "deploy-123",
        environment: "production",
        artifactRef: "artifact-789",
        status: "deployed" as const,
        deployedAt: "2024-01-01T00:00:00.000Z",
      };

      const result = ArtifactDeploymentLinkageSchema.parse(linkage);
      expect(result.deploymentId).toBe("deploy-123");
    });

    it("should reject invalid deployment status", () => {
      const linkage = {
        deploymentId: "deploy-123",
        environment: "production",
        artifactRef: "artifact-789",
        status: "invalid-status",
        deployedAt: "2024-01-01T00:00:00.000Z",
      };

      expect(() => ArtifactDeploymentLinkageSchema.parse(linkage)).toThrow();
    });
  });

  describe("ArtifactTrustChain", () => {
    it("should create a valid trust chain", () => {
      const sourceLinkage: ArtifactSourceLinkage = {
        gitCommit: "abc123def456",
        gitBranch: "main",
        gitRepository: "https://github.com/example/repo",
        committedAt: "2024-01-01T00:00:00.000Z",
      };

      const chain: ArtifactTrustChain = {
        source: sourceLinkage,
        artifactRef: "artifact-789",
        artifactFingerprint: {
          algorithm: "sha256",
          hash: "hash123",
        },
        trustState: "verified",
      };

      expect(chain.source.gitCommit).toBe("abc123def456");
      expect(chain.artifactRef).toBe("artifact-789");
      expect(chain.trustState).toBe("verified");
    });

    it("should validate trust chain with schema", () => {
      const chain = {
        source: {
          gitCommit: "abc123def4567890123456789012345678901234",
          gitBranch: "main",
          gitRepository: "https://github.com/example/repo",
          committedAt: "2024-01-01T00:00:00.000Z",
        },
        artifactRef: "artifact-789",
        artifactFingerprint: {
          algorithm: "sha256" as const,
          hash: "hash123",
        },
        trustState: "verified" as const,
      };

      const result = ArtifactTrustChainSchema.parse(chain);
      expect(result.artifactRef).toBe("artifact-789");
    });

    it("should accept trust chain with signature", () => {
      const chain = {
        source: {
          gitCommit: "abc123def4567890123456789012345678901234",
          gitBranch: "main",
          gitRepository: "https://github.com/example/repo",
          committedAt: "2024-01-01T00:00:00.000Z",
        },
        artifactRef: "artifact-789",
        artifactFingerprint: {
          algorithm: "sha256" as const,
          hash: "hash123",
        },
        trustState: "signed" as const,
        signature: {
          algorithm: "cosign" as const,
          signedAt: "2024-01-01T00:00:00.000Z",
        },
      };

      const result = ArtifactTrustChainSchema.parse(chain);
      expect(result.signature).toBeDefined();
      expect(result.signature?.algorithm).toBe("cosign");
    });
  });

  describe("ArtifactLinkageVerifier", () => {
    const verifier = new ArtifactLinkageVerifier();

    describe("verifySourceLinkage", () => {
      it("should verify valid source linkage", () => {
        const linkage: ArtifactSourceLinkage = {
          gitCommit: "abc123def4567890123456789012345678901234",
          gitBranch: "main",
          gitRepository: "https://github.com/example/repo",
          committedAt: "2024-01-01T00:00:00.000Z",
        };

        const result = verifier.verifySourceLinkage(linkage);
        expect(result.valid).toBe(true);
        expect(result.errors).toHaveLength(0);
      });

      it("should reject invalid git commit format", () => {
        const linkage: ArtifactSourceLinkage = {
          gitCommit: "invalid",
          gitBranch: "main",
          gitRepository: "https://github.com/example/repo",
          committedAt: "2024-01-01T00:00:00.000Z",
        };

        const result = verifier.verifySourceLinkage(linkage);
        expect(result.valid).toBe(false);
        expect(result.errors.length).toBeGreaterThan(0);
      });

      it("should reject invalid git repository URL", () => {
        const linkage: ArtifactSourceLinkage = {
          gitCommit: "abc123def4567890123456789012345678901234",
          gitBranch: "main",
          gitRepository: "not-a-url",
          committedAt: "2024-01-01T00:00:00.000Z",
        };

        const result = verifier.verifySourceLinkage(linkage);
        expect(result.valid).toBe(false);
        expect(result.errors.length).toBeGreaterThan(0);
      });

      it("should reject invalid timestamp", () => {
        const linkage: ArtifactSourceLinkage = {
          gitCommit: "abc123def4567890123456789012345678901234",
          gitBranch: "main",
          gitRepository: "https://github.com/example/repo",
          committedAt: "invalid-date",
        };

        const result = verifier.verifySourceLinkage(linkage);
        expect(result.valid).toBe(false);
        expect(result.errors.length).toBeGreaterThan(0);
      });
    });

    describe("verifyDeploymentLinkage", () => {
      it("should verify valid deployment linkage", () => {
        const linkage: ArtifactDeploymentLinkage = {
          deploymentId: "deploy-123",
          environment: "production",
          artifactRef: "artifact-789",
          status: "deployed",
          deployedAt: "2024-01-01T00:00:00.000Z",
        };

        const result = verifier.verifyDeploymentLinkage(linkage);
        expect(result.valid).toBe(true);
        expect(result.errors).toHaveLength(0);
      });

      it("should reject missing deployment ID", () => {
        const linkage: ArtifactDeploymentLinkage = {
          deploymentId: "",
          environment: "production",
          artifactRef: "artifact-789",
          status: "deployed",
          deployedAt: "2024-01-01T00:00:00.000Z",
        };

        const result = verifier.verifyDeploymentLinkage(linkage);
        expect(result.valid).toBe(false);
        expect(result.errors.length).toBeGreaterThan(0);
      });

      it("should reject missing environment", () => {
        const linkage: ArtifactDeploymentLinkage = {
          deploymentId: "deploy-123",
          environment: "",
          artifactRef: "artifact-789",
          status: "deployed",
          deployedAt: "2024-01-01T00:00:00.000Z",
        };

        const result = verifier.verifyDeploymentLinkage(linkage);
        expect(result.valid).toBe(false);
        expect(result.errors.length).toBeGreaterThan(0);
      });

      it("should reject completedAt before deployedAt", () => {
        const linkage: ArtifactDeploymentLinkage = {
          deploymentId: "deploy-123",
          environment: "production",
          artifactRef: "artifact-789",
          status: "deployed",
          deployedAt: "2024-01-01T00:05:00.000Z",
          completedAt: "2024-01-01T00:00:00.000Z",
        };

        const result = verifier.verifyDeploymentLinkage(linkage);
        expect(result.valid).toBe(false);
        expect(result.errors.length).toBeGreaterThan(0);
      });
    });

    describe("verifyTrustChain", () => {
      it("should verify valid trust chain", () => {
        const sourceLinkage: ArtifactSourceLinkage = {
          gitCommit: "abc123def4567890123456789012345678901234",
          gitBranch: "main",
          gitRepository: "https://github.com/example/repo",
          committedAt: "2024-01-01T00:00:00.000Z",
        };

        const chain: ArtifactTrustChain = {
          source: sourceLinkage,
          artifactRef: "artifact-789",
          artifactFingerprint: {
            algorithm: "sha256",
            hash: "abc123",
          },
          trustState: "verified",
        };

        const result = verifier.verifyTrustChain(chain);
        expect(result.valid).toBe(true);
        expect(result.errors).toHaveLength(0);
      });

      it("should reject trust chain with invalid source", () => {
        const sourceLinkage: ArtifactSourceLinkage = {
          gitCommit: "invalid",
          gitBranch: "main",
          gitRepository: "https://github.com/example/repo",
          committedAt: "2024-01-01T00:00:00.000Z",
        };

        const chain: ArtifactTrustChain = {
          source: sourceLinkage,
          artifactRef: "artifact-789",
          artifactFingerprint: {
            algorithm: "sha256",
            hash: "abc123",
          },
          trustState: "verified",
        };

        const result = verifier.verifyTrustChain(chain);
        expect(result.valid).toBe(false);
        expect(result.errors.length).toBeGreaterThan(0);
      });

      it("should reject trust chain with invalid fingerprint hash", () => {
        const sourceLinkage: ArtifactSourceLinkage = {
          gitCommit: "abc123def4567890123456789012345678901234",
          gitBranch: "main",
          gitRepository: "https://github.com/example/repo",
          committedAt: "2024-01-01T00:00:00.000Z",
        };

        const chain: ArtifactTrustChain = {
          source: sourceLinkage,
          artifactRef: "artifact-789",
          artifactFingerprint: {
            algorithm: "sha256",
            hash: "not-a-hash",
          },
          trustState: "verified",
        };

        const result = verifier.verifyTrustChain(chain);
        expect(result.valid).toBe(false);
        expect(result.errors.length).toBeGreaterThan(0);
      });

      it("should warn when signature present but trust state is not signed", () => {
        const sourceLinkage: ArtifactSourceLinkage = {
          gitCommit: "abc123def4567890123456789012345678901234",
          gitBranch: "main",
          gitRepository: "https://github.com/example/repo",
          committedAt: "2024-01-01T00:00:00.000Z",
        };

        const chain: ArtifactTrustChain = {
          source: sourceLinkage,
          artifactRef: "artifact-789",
          artifactFingerprint: {
            algorithm: "sha256",
            hash: "abc123",
          },
          trustState: "verified",
          signature: {
            algorithm: "cosign",
            signedAt: "2024-01-01T00:00:00.000Z",
          },
        };

        const result = verifier.verifyTrustChain(chain);
        expect(result.valid).toBe(true);
        expect(result.warnings.length).toBeGreaterThan(0);
      });
    });

    describe("buildTrustChain", () => {
      it("should build trust chain from parameters", () => {
        const sourceLinkage: ArtifactSourceLinkage = {
          gitCommit: "abc123def4567890123456789012345678901234",
          gitBranch: "main",
          gitRepository: "https://github.com/example/repo",
          committedAt: "2024-01-01T00:00:00.000Z",
        };

        const chain = verifier.buildTrustChain({
          sourceLinkage,
          artifactRef: "artifact-789",
          artifactFingerprint: {
            algorithm: "sha256",
            hash: "abc123",
          },
          trustState: "verified",
        });

        expect(chain.source.gitCommit).toBe("abc123def4567890123456789012345678901234");
        expect(chain.artifactRef).toBe("artifact-789");
        expect(chain.trustState).toBe("verified");
      });

      it("should build trust chain with signature", () => {
        const sourceLinkage: ArtifactSourceLinkage = {
          gitCommit: "abc123def4567890123456789012345678901234",
          gitBranch: "main",
          gitRepository: "https://github.com/example/repo",
          committedAt: "2024-01-01T00:00:00.000Z",
        };

        const chain = verifier.buildTrustChain({
          sourceLinkage,
          artifactRef: "artifact-789",
          artifactFingerprint: {
            algorithm: "sha256",
            hash: "abc123",
          },
          trustState: "signed",
          signature: {
            algorithm: "cosign",
            signedAt: "2024-01-01T00:00:00.000Z",
          },
        });

        expect(chain.signature).toBeDefined();
        expect(chain.signature?.algorithm).toBe("cosign");
      });

      it("should build trust chain with deployment linkage", () => {
        const sourceLinkage: ArtifactSourceLinkage = {
          gitCommit: "abc123def4567890123456789012345678901234",
          gitBranch: "main",
          gitRepository: "https://github.com/example/repo",
          committedAt: "2024-01-01T00:00:00.000Z",
        };

        const deploymentLinkage: ArtifactDeploymentLinkage = {
          deploymentId: "deploy-123",
          environment: "production",
          artifactRef: "artifact-789",
          status: "deployed",
          deployedAt: "2024-01-01T00:00:00.000Z",
        };

        const chain = verifier.buildTrustChain({
          sourceLinkage,
          artifactRef: "artifact-789",
          artifactFingerprint: {
            algorithm: "sha256",
            hash: "abc123",
          },
          trustState: "verified",
          deploymentLinkage,
        });

        expect(chain.deployment).toBeDefined();
        expect(chain.deployment?.deploymentId).toBe("deploy-123");
      });
    });
  });
});
