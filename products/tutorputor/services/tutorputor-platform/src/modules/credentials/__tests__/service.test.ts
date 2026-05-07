import { describe, it, expect, vi, beforeEach } from "vitest";
import { CredentialService } from "../service.js";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
function makeCredential(overrides: Record<string, any> = {}): any {
    return {
        id: "cred-1",
        userId: "user-1",
        tenantId: "tenant-1",
        type: "badge",
        status: "issued",
        name: "Test Badge",
        description: "A test badge",
        issuedAt: new Date("2024-01-01"),
        createdAt: new Date("2024-01-01"),
        updatedAt: new Date("2024-01-01"),
        issuer: { id: "sys", name: "System", type: "system" },
        verification: { type: "hash", hash: "abc123" },
        metadata: {
            category: "simulation_mastery",
            customData: { ruleId: "rule-1" },
        },
        ...overrides,
    };
}

function makePrisma() {
    const cred = makeCredential();
    return {
        learningEvent: {
            create: vi.fn().mockResolvedValue({ id: "event-1" }),
            findMany: vi.fn().mockResolvedValue([
                { id: "event-1", payload: cred },
            ]),
        },
        $queryRaw: vi.fn().mockResolvedValue([
            { id: "event-1", payload: JSON.stringify(makeCredential()) }
        ]),
        assessmentAttempt: {
            findMany: vi.fn().mockResolvedValue([]),
        },
        learnerMastery: {
            findMany: vi.fn().mockResolvedValue([]),
        },
    };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------
describe("CredentialService", () => {
    let service: CredentialService;
    let prisma: ReturnType<typeof makePrisma>;

    beforeEach(() => {
        prisma = makePrisma();
        service = new CredentialService(prisma as any);
    });

    describe("create", () => {
        it("persists credential as a LearningEvent", async () => {
            const cred = makeCredential();
            const result = await service.create(cred);
            expect(prisma.learningEvent.create).toHaveBeenCalledOnce();
            const callArg = prisma.learningEvent.create.mock.calls[0][0];
            expect(callArg.data.eventType).toBe("CREDENTIAL_ISSUED");
            expect(callArg.data.userId).toBe("user-1");
            expect(result).toEqual(cred);
        });
    });

    describe("findByUser", () => {
        it("returns credentials for user", async () => {
            const results = await service.findByUser("user-1");
            expect(prisma.learningEvent.findMany).toHaveBeenCalledOnce();
            expect(results).toHaveLength(1);
            expect(results[0].id).toBe("cred-1");
        });

        it("filters by type", async () => {
            const results = await service.findByUser("user-1", { type: "badge" } as any);
            expect(results).toHaveLength(1);
        });

        it("filters out mismatched type", async () => {
            const results = await service.findByUser("user-1", { type: "certificate" } as any);
            expect(results).toHaveLength(0);
        });

        it("filters by status", async () => {
            const results = await service.findByUser("user-1", { status: "issued" } as any);
            expect(results).toHaveLength(1);
        });

        it("filters by category", async () => {
            const results = await service.findByUser("user-1", { category: "simulation_mastery" } as any);
            expect(results).toHaveLength(1);
        });

        it("filters by tenantId", async () => {
            const results = await service.findByUser("user-1", { tenantId: "tenant-1" } as any);
            expect(results).toHaveLength(1);
        });

        it("returns empty array when no events found", async () => {
            prisma.learningEvent.findMany.mockResolvedValue([]);
            const results = await service.findByUser("user-1");
            expect(results).toHaveLength(0);
        });
    });

    describe("hasCredential", () => {
        it("returns true when user has matching ruleId credential", async () => {
            const has = await service.hasCredential("user-1", "rule-1");
            expect(has).toBe(true);
        });

        it("returns false when no matching ruleId", async () => {
            const has = await service.hasCredential("user-1", "rule-999");
            expect(has).toBe(false);
        });
    });

    describe("findById", () => {
        it("returns credential from raw query result", async () => {
            const result = await service.findById("cred-1");
            expect(prisma.$queryRaw).toHaveBeenCalledOnce();
            expect(result).not.toBeNull();
            expect(result?.id).toBe("cred-1");
        });

        it("returns null when not found", async () => {
            prisma.$queryRaw.mockResolvedValue([]);
            const result = await service.findById("missing");
            expect(result).toBeNull();
        });
    });

    describe("update", () => {
        it("returns null when credential not found", async () => {
            prisma.$queryRaw.mockResolvedValue([]);
            const result = await service.update("missing", { status: "revoked" as any });
            expect(result).toBeNull();
        });

        it("inserts CREDENTIAL_UPDATED event and returns merged credential", async () => {
            // findById will call $queryRaw, then mergeCredentialUpdates calls learningEvent.findMany
            prisma.learningEvent.findMany.mockResolvedValue([]);
            const result = await service.update("cred-1", { status: "revoked" as any });
            expect(prisma.learningEvent.create).toHaveBeenCalledOnce();
            const callArg = prisma.learningEvent.create.mock.calls[0][0];
            expect(callArg.data.eventType).toBe("CREDENTIAL_UPDATED");
            expect(result?.status).toBe("revoked");
        });
    });

    describe("getProgress", () => {
        it("returns null-like progress with no attempts", async () => {
            prisma.assessmentAttempt.findMany.mockResolvedValue([]);
            const result = await service.getProgress("user-1", "tenant-1");
            expect(result).not.toBeNull();
            expect(result?.simulationsCompleted).toBe(0);
            expect(result?.averageScore).toBe(0);
        });

        it("calculates averageScore from attempts", async () => {
            prisma.assessmentAttempt.findMany.mockResolvedValue([
                {
                    scorePercent: 80,
                    submittedAt: new Date("2024-01-01"),
                    assessment: { title: "Physics Test", module: { domain: "PHYSICS" } },
                },
                {
                    scorePercent: 100,
                    submittedAt: new Date("2024-01-02"),
                    assessment: { title: "Physics Test 2", module: { domain: "PHYSICS" } },
                },
            ]);
            const result = await service.getProgress("user-1", "tenant-1");
            expect(result?.simulationsCompleted).toBe(2);
            expect(result?.averageScore).toBe(90);
            expect(result?.perfectScoresCount).toBe(1);
        });
    });

    describe("evidence-backed credentialing", () => {
        beforeEach(() => {
            prisma.learnerMastery.findMany.mockResolvedValue([
                { conceptId: "claim-1", masteryProbability: 0.91, evidenceCount: 4 },
            ]);
            prisma.assessmentAttempt.findMany.mockResolvedValue([
                { id: "attempt-1", scorePercent: 88, status: "GRADED" },
            ]);
            prisma.learningEvent.findMany.mockResolvedValue([]);
        });

        it("requires mastered claims and valid assessment evidence for eligibility", async () => {
            const result = await service.evaluateEvidenceEligibility({
                tenantId: "tenant-1",
                userId: "user-1",
                moduleId: "module-1",
            });

            expect(result.eligible).toBe(true);
            expect(result.evidence.masteredClaims).toHaveLength(1);
            expect(result.evidence.assessmentAttempts).toHaveLength(1);
        });

        it("blocks eligibility when a viva requirement is unresolved", async () => {
            prisma.learningEvent.findMany.mockResolvedValue([
                {
                    eventType: "MICRO_VIVA_FAILED",
                    payload: { moduleId: "module-1" },
                },
            ]);

            const result = await service.evaluateEvidenceEligibility({
                tenantId: "tenant-1",
                userId: "user-1",
                moduleId: "module-1",
            });

            expect(result.eligible).toBe(false);
            expect(result.reasons).toContain("A micro-viva requirement is unresolved");
        });

        it("issues credentials with mastery evidence provenance", async () => {
            const credential = await service.issueFromEvidence({
                type: "certificate",
                userId: "user-1",
                tenantId: "tenant-1",
                moduleId: "module-1",
                name: "Motion Mastery",
                description: "Issued from demonstrated claim mastery",
                metadata: { category: "simulation_mastery" },
                certificate: {
                    courseId: "module-1",
                    completionDate: new Date("2026-05-06"),
                },
            });

            expect(credential.status).toBe("issued");
            expect(credential.metadata.customData?.issuedFrom).toBe("mastery_evidence");
            expect(credential.metadata.customData?.evidence).toMatchObject({
                moduleId: "module-1",
            });
            expect(prisma.learningEvent.create).toHaveBeenCalled();
        });

        it("verifies, revokes, and reissues credential records", async () => {
            const verified = await service.verifyCredential("cred-1");
            expect(verified.valid).toBe(true);

            const revoked = await service.revokeCredential("cred-1", "Academic integrity review");
            expect(revoked?.status).toBe("revoked");

            prisma.$queryRaw.mockResolvedValue([
                {
                    id: "event-1",
                    payload: JSON.stringify(makeCredential({
                        metadata: {
                            category: "simulation_mastery",
                            customData: {
                                evidence: { moduleId: "module-1" },
                            },
                        },
                    })),
                },
            ]);

            const reissued = await service.reissueCredential("cred-1");
            expect(reissued?.status).toBe("issued");
            expect(reissued?.metadata.customData?.reissuedFrom).toBe("cred-1");
        });
    });
});
