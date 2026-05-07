import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { complianceRoutes } from "../routes.js";

describe("complianceRoutes", () => {
  const service = {
    requestUserExport: vi.fn(),
    getExportStatus: vi.fn(),
    downloadExport: vi.fn(),
    requestUserDeletion: vi.fn(),
    cancelDeletionRequest: vi.fn(),
    getDeletionStatus: vi.fn(),
    createDeletionVerification: vi.fn(),
    verifyAndProcessDeletion: vi.fn(),
    getPrivacyDataAccessSummary: vi.fn(),
    revokeConsent: vi.fn(),
    deleteTelemetryForUser: vi.fn(),
    processDeletionNow: vi.fn(),
  };

  const prisma = {
    user: {
      findFirst: vi.fn(),
      findUnique: vi.fn(),
    },
  };

  let app: ReturnType<typeof Fastify>;

  beforeEach(async () => {
    vi.clearAllMocks();

    prisma.user.findFirst.mockResolvedValue({
      id: "user-1",
      tenantId: "tenant-1",
      email: "user@example.com",
    });
    prisma.user.findUnique.mockResolvedValue({
      id: "user-1",
      email: "user@example.com",
    });

    service.requestUserExport.mockResolvedValue({ id: "req-1", status: "pending" });
    service.getExportStatus.mockResolvedValue({ id: "req-1", status: "completed" });
    service.downloadExport.mockResolvedValue({ downloadUrl: "/download/req-1", expiresAt: "2026-05-07T00:00:00.000Z" });
    service.requestUserDeletion.mockResolvedValue({ id: "del-1", status: "requested" });
    service.cancelDeletionRequest.mockResolvedValue({ id: "del-1", status: "cancelled" });
    service.getDeletionStatus.mockResolvedValue({ id: "del-1", status: "requested" });
    service.getPrivacyDataAccessSummary.mockResolvedValue({
      userId: "user-1",
      tenantId: "tenant-1",
      exportRequests: [],
      deletionRequests: [],
      consent: [],
    });
    service.revokeConsent.mockResolvedValue({
      userId: "user-1",
      tenantId: "tenant-1",
      consentType: "ai_tutor",
      granted: false,
      revokedAt: new Date("2026-05-06T00:00:00.000Z"),
    });
    service.deleteTelemetryForUser.mockResolvedValue({
      requestId: "telemetry-1",
      evidenceType: "telemetry_deletion",
      records: [{ dataType: "learning_events", action: "anonymized", count: 3 }],
    });
    service.processDeletionNow.mockResolvedValue({
      requestId: "delete-1",
      evidenceType: "deletion",
      records: [{ dataType: "user_profile", action: "deleted", count: 1 }],
    });
    service.verifyAndProcessDeletion.mockResolvedValue({
      success: true,
      message: "Scheduled",
    });

    app = Fastify();
    app.decorate("prisma", prisma as never);
    await app.register(complianceRoutes, { service: service as never });

    // Inject auth context so getTenantId/getUserId resolve without a real JWT stack.
    // The role is read from the x-user-role header (if present) so individual
    // tests can override it; admin is used as the safe default.
    app.addHook("preHandler", async (request) => {
      const roleHeader = request.headers["x-user-role"];
      const role = typeof roleHeader === "string" ? roleHeader : "admin";
      (
        request as typeof request & {
          user?: { id: string; sub: string; tenantId: string; role: string };
        }
      ).user = { id: "user-1", sub: "user-1", tenantId: "tenant-1", role };
    });
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
  });

  it("rejects malformed export payloads", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/export",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
      payload: {
        userId: "",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(service.requestUserExport).not.toHaveBeenCalled();
  });

  it("allows self export requests", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/export",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
      payload: {},
    });

    expect(response.statusCode).toBe(200);
    expect(service.requestUserExport).toHaveBeenCalledWith({
      userId: "user-1",
      tenantId: "tenant-1",
      requestedBy: "user-1",
    });
  });

  it("returns the product privacy center summary", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/privacy-center",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
    });

    expect(response.statusCode).toBe(200);
    expect(service.getPrivacyDataAccessSummary).toHaveBeenCalledWith({
      userId: "user-1",
      tenantId: "tenant-1",
    });
  });

  it("returns export status and download evidence", async () => {
    const status = await app.inject({
      method: "GET",
      url: "/export/req-1",
      headers: { "x-tenant-id": "tenant-1", "x-user-id": "user-1" },
    });
    const download = await app.inject({
      method: "GET",
      url: "/export/req-1/download",
      headers: { "x-tenant-id": "tenant-1", "x-user-id": "user-1" },
    });

    expect(status.statusCode).toBe(200);
    expect(download.statusCode).toBe(200);
    expect(service.downloadExport).toHaveBeenCalledWith({
      requestId: "req-1",
      tenantId: "tenant-1",
    });
  });

  it("creates, reads, and cancels product deletion requests", async () => {
    const create = await app.inject({
      method: "POST",
      url: "/deletion/request",
      headers: { "x-tenant-id": "tenant-1", "x-user-id": "user-1" },
      payload: { reason: "privacy_center" },
    });
    const status = await app.inject({
      method: "GET",
      url: "/deletion/del-1",
      headers: { "x-tenant-id": "tenant-1", "x-user-id": "user-1" },
    });
    const cancel = await app.inject({
      method: "DELETE",
      url: "/deletion/del-1",
      headers: { "x-tenant-id": "tenant-1", "x-user-id": "user-1" },
    });

    expect(create.statusCode).toBe(202);
    expect(status.statusCode).toBe(200);
    expect(cancel.statusCode).toBe(200);
    expect(service.requestUserDeletion).toHaveBeenCalledWith({
      userId: "user-1",
      tenantId: "tenant-1",
      requestedBy: "user-1",
      reason: "privacy_center",
    });
  });

  it("revokes consent and returns telemetry deletion evidence", async () => {
    const consent = await app.inject({
      method: "POST",
      url: "/consent/revoke",
      headers: { "x-tenant-id": "tenant-1", "x-user-id": "user-1" },
      payload: { consentType: "ai_tutor" },
    });
    const telemetry = await app.inject({
      method: "POST",
      url: "/telemetry/delete",
      headers: { "x-tenant-id": "tenant-1", "x-user-id": "user-1" },
      payload: { anonymize: true },
    });

    expect(consent.statusCode).toBe(200);
    expect(telemetry.statusCode).toBe(200);
    expect(service.revokeConsent).toHaveBeenCalledWith(expect.objectContaining({
      userId: "user-1",
      tenantId: "tenant-1",
      consentType: "ai_tutor",
    }));
    expect(service.deleteTelemetryForUser).toHaveBeenCalledWith({
      userId: "user-1",
      tenantId: "tenant-1",
      anonymize: true,
    });
  });

  it("restricts immediate deletion processing to admins", async () => {
    const forbidden = await app.inject({
      method: "POST",
      url: "/deletion/process-now",
      headers: { "x-tenant-id": "tenant-1", "x-user-id": "user-1", "x-user-role": "student" },
      payload: { userId: "user-2" },
    });
    const allowed = await app.inject({
      method: "POST",
      url: "/deletion/process-now",
      headers: { "x-tenant-id": "tenant-1", "x-user-id": "user-1", "x-user-role": "admin" },
      payload: { userId: "user-2" },
    });

    expect(forbidden.statusCode).toBe(403);
    expect(allowed.statusCode).toBe(200);
    expect(service.processDeletionNow).toHaveBeenCalledWith({
      userId: "user-2",
      tenantId: "tenant-1",
    });
  });

  it("rejects deletion verification without token", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/deletion/verify",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
      payload: {
        token: "",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(service.verifyAndProcessDeletion).not.toHaveBeenCalled();
  });

  it("forwards valid deletion verification tokens", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/deletion/verify",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
      payload: {
        token: "valid-token",
      },
    });

    expect(response.statusCode).toBe(200);
    expect(service.verifyAndProcessDeletion).toHaveBeenCalledWith({
      userId: "user-1",
      tenantId: "tenant-1",
      token: "valid-token",
    });
  });
});
