import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import type {
  ApprovalDecision,
  ApprovalRequest,
} from "@ghatana/kernel-product-contracts";
import { FileApprovalProvider } from "../FileApprovalProvider";

describe("FileApprovalProvider", () => {
  let tempDir: string;
  let provider: FileApprovalProvider;

  beforeEach(async () => {
    tempDir = await fs.mkdtemp(path.join(os.tmpdir(), "ghatana-approvals-"));
    provider = new FileApprovalProvider({
      outputDirectory: tempDir,
      defaultEnvironment: "staging",
    });
  });

  afterEach(async () => {
    await fs.rm(tempDir, { recursive: true, force: true });
  });

  it("stores approval requests and writes a pending approval gate", async () => {
    const request = buildRequest("approval-1", ["alice", "bob"]);

    const result = await provider.requestLifecycleApproval(request, {
      required: true,
      correlationId: "corr-1",
    });

    expect(result).toEqual({
      success: true,
      ref: path.join(tempDir, "approvals.json"),
    });
    await expect(readJson(path.join(tempDir, "approvals.json"))).resolves.toMatchObject({
      requests: [{ approvalId: "approval-1" }],
      decisions: [],
    });
    await expect(
      readJson(path.join(tempDir, "approval-gates", "approval-1.json"))
    ).resolves.toMatchObject({
      gate: {
        productId: "digital-marketing",
        environment: "staging",
        gateName: "approval-1",
        status: "pending",
        requiredApprovals: 2,
      },
    });
    await expect(provider.listPendingApprovals()).resolves.toEqual([request]);
  });

  it("records approvals until the gate is approved", async () => {
    const request = buildRequest("approval-1", ["alice", "bob"]);
    await provider.requestLifecycleApproval(request, {
      required: true,
      correlationId: "corr-1",
    });

    await provider.decideLifecycleApproval(buildDecision("approval-1", "alice", true), {
      required: true,
      correlationId: "corr-1",
    });
    await expect(provider.getApprovalStatus("approval-1")).resolves.toMatchObject({
      status: "pending",
      decision: { approvedBy: "alice" },
    });

    await provider.decideLifecycleApproval(buildDecision("approval-1", "bob", true), {
      required: true,
      correlationId: "corr-1",
    });

    await expect(provider.getApprovalStatus("approval-1")).resolves.toMatchObject({
      status: "approved",
      decision: { approvedBy: "bob" },
    });
    await expect(provider.listPendingApprovals()).resolves.toEqual([]);
    await expect(
      readJson(path.join(tempDir, "approval-gates", "approval-1.json"))
    ).resolves.toMatchObject({
      gate: {
        status: "approved",
        approvals: [
          { approver: "alice", approved: true },
          { approver: "bob", approved: true },
        ],
      },
    });
  });

  it("records rejection decisions and marks the gate rejected", async () => {
    await provider.requestLifecycleApproval(buildRequest("approval-1", ["alice"]), {
      required: true,
      correlationId: "corr-1",
    });

    const result = await provider.decideLifecycleApproval(
      buildDecision("approval-1", "alice", false),
      { required: true, correlationId: "corr-1" }
    );

    expect(result.success).toBe(true);
    await expect(provider.getApprovalStatus("approval-1")).resolves.toMatchObject({
      status: "rejected",
      decision: { approved: false },
    });
  });

  it("supports the existing ApprovalProvider throw-on-failure API", async () => {
    const request = buildRequest("approval-1", ["alice"]);

    await expect(provider.requestApproval(request)).resolves.toBeUndefined();
    await expect(
      provider.recordDecision(buildDecision("approval-1", "alice", true))
    ).resolves.toBeUndefined();
    await expect(provider.requestApproval(request)).resolves.toBeUndefined();
  });

  it("throws through the existing decision API on failed decisions", async () => {
    await expect(
      provider.recordDecision(buildDecision("approval-missing", "alice", true))
    ).rejects.toThrow("approval request not found: approval-missing");
  });

  it("rejects malformed requests before writing", async () => {
    const result = await provider.requestLifecycleApproval(
      {
        approvalId: "",
        productUnitId: "",
        requestedBy: "",
        reason: "",
        requiredApprovers: [" "],
        expiresAt: "not-a-date",
      },
      { required: true, correlationId: "corr-1" }
    );

    expect(result.success).toBe(false);
    expect(result.error).toContain("requires approvalId");
    expect(result.error).toContain("requires productUnitId");
    expect(result.error).toContain("requires requestedBy");
    expect(result.error).toContain("requires reason");
    expect(result.error).toContain("approvers must be non-empty");
    expect(result.error).toContain("requires ISO expiresAt");
    await expect(fs.access(path.join(tempDir, "approvals.json"))).rejects.toMatchObject({
      code: "ENOENT",
    });
  });

  it("rejects requests without approvers using the release gate validation", async () => {
    const result = await provider.requestLifecycleApproval(
      buildRequest("approval-1", []),
      { required: true, correlationId: "corr-1" }
    );

    expect(result.success).toBe(false);
    expect(result.error).toBe("approval request requires approvers");
  });

  it("returns optional failure when request correlation id is missing", async () => {
    const result = await provider.requestLifecycleApproval(
      buildRequest("approval-1", ["alice"]),
      { required: false, correlationId: " " }
    );

    expect(result).toEqual({
      success: false,
      error: "optional approval write skipped: approval request write requires correlationId",
    });
  });

  it("rejects duplicate requests", async () => {
    await provider.requestLifecycleApproval(buildRequest("approval-1", ["alice"]), {
      required: true,
      correlationId: "corr-1",
    });

    const result = await provider.requestLifecycleApproval(
      buildRequest("approval-1", ["alice"]),
      { required: true, correlationId: "corr-1" }
    );

    expect(result).toEqual({
      success: false,
      error: "approval request already exists: approval-1",
    });
  });

  it("rejects malformed decisions before writing", async () => {
    const result = await provider.decideLifecycleApproval(
      {
        approvalId: "",
        approved: true,
        approvedBy: "",
        reason: "",
        decidedAt: "not-a-date",
      },
      { required: true, correlationId: "corr-1" }
    );

    expect(result.success).toBe(false);
    expect(result.error).toContain("requires approvalId");
    expect(result.error).toContain("requires approvedBy");
    expect(result.error).toContain("requires reason");
    expect(result.error).toContain("requires ISO decidedAt");
  });

  it("rejects decisions for unknown requests and missing correlation ids", async () => {
    const unknownResult = await provider.decideLifecycleApproval(
      buildDecision("approval-missing", "alice", true),
      { required: true, correlationId: "corr-1" }
    );
    const missingCorrelationResult = await provider.decideLifecycleApproval(
      buildDecision("approval-missing", "alice", true),
      { required: false, correlationId: " " }
    );

    expect(unknownResult).toEqual({
      success: false,
      error: "approval request not found: approval-missing",
    });
    expect(missingCorrelationResult).toEqual({
      success: false,
      error: "optional approval write skipped: approval decision write requires correlationId",
    });
  });

  it("prevents duplicate decisions by the same principal by default", async () => {
    await provider.requestLifecycleApproval(buildRequest("approval-1", ["alice"]), {
      required: true,
      correlationId: "corr-1",
    });
    await provider.decideLifecycleApproval(buildDecision("approval-1", "alice", true), {
      required: true,
      correlationId: "corr-1",
    });

    const result = await provider.decideLifecycleApproval(
      buildDecision("approval-1", "alice", true),
      { required: true, correlationId: "corr-1" }
    );

    expect(result).toEqual({
      success: false,
      error: "approval decision already recorded for approval-1 by alice",
    });
  });

  it("allows duplicate decisions when explicitly configured", async () => {
    provider = new FileApprovalProvider({
      outputDirectory: tempDir,
      allowDuplicateDecisions: true,
    });
    await provider.requestLifecycleApproval(buildRequest("approval-1", ["alice"]), {
      required: true,
      correlationId: "corr-1",
    });
    await provider.decideLifecycleApproval(buildDecision("approval-1", "alice", true), {
      required: true,
      correlationId: "corr-1",
    });

    const result = await provider.decideLifecycleApproval(
      buildDecision("approval-1", "alice", true),
      { required: true, correlationId: "corr-1" }
    );

    expect(result.success).toBe(true);
  });

  it("fails closed when stored approvals are malformed", async () => {
    await fs.writeFile(
      path.join(tempDir, "approvals.json"),
      JSON.stringify({ schemaVersion: "1.0.0", requests: {}, decisions: [] }),
      "utf-8"
    );

    const result = await provider.requestLifecycleApproval(
      buildRequest("approval-1", ["alice"]),
      { required: true, correlationId: "corr-1" }
    );

    expect(result).toEqual({
      success: false,
      error: "approvals file has invalid shape",
    });
  });

  it("fails closed when stored approvals are malformed during decision writes", async () => {
    await fs.writeFile(
      path.join(tempDir, "approvals.json"),
      JSON.stringify({ schemaVersion: "1.0.0", requests: [], decisions: {} }),
      "utf-8"
    );

    const result = await provider.decideLifecycleApproval(
      buildDecision("approval-1", "alice", true),
      { required: true, correlationId: "corr-1" }
    );

    expect(result).toEqual({
      success: false,
      error: "approvals file has invalid shape",
    });
  });

  it("returns rejected status for unknown approvals without stored requests", async () => {
    await expect(provider.getApprovalStatus("missing")).resolves.toEqual({
      status: "rejected",
      decision: null,
    });
  });
});

function buildRequest(
  approvalId: string,
  requiredApprovers: readonly string[]
): ApprovalRequest {
  return {
    approvalId,
    productUnitId: "digital-marketing",
    requestedBy: "release-manager",
    reason: "Promote release",
    requiredApprovers,
    expiresAt: "2026-05-14T00:00:00.000Z",
  };
}

function buildDecision(
  approvalId: string,
  approvedBy: string,
  approved: boolean
): ApprovalDecision {
  return {
    approvalId,
    approved,
    approvedBy,
    reason: approved ? "Looks good" : "Blocked",
    decidedAt: "2026-05-14T00:00:00.000Z",
  };
}

async function readJson(filePath: string): Promise<unknown> {
  return JSON.parse(await fs.readFile(filePath, "utf-8"));
}
