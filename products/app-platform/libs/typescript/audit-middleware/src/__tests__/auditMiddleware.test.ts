import Fastify from "fastify";
import { auditMiddleware } from "../../src/middleware/auditMiddleware";
import type {
  AuditStoreClient,
  AuditPayload,
  AuditReceipt,
} from "../../src/types";

// ── Stub AuditStoreClient ──────────────────────────────────────────────────────

function makeReceiptStub(seq = 0): AuditReceipt {
  return {
    auditId: `audit-${seq}`,
    sequenceNumber: seq,
    previousHash: "0".repeat(64),
    currentHash: "a".repeat(64),
    timestamp: new Date().toISOString(),
  };
}

class StubAuditClient implements AuditStoreClient {
  readonly captured: AuditPayload[] = [];

  async log(payload: AuditPayload): Promise<AuditReceipt> {
    this.captured.push(payload);
    return makeReceiptStub(this.captured.length - 1);
  }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

async function buildApp(client: AuditStoreClient) {
  const app = Fastify({ logger: false });
  await app.register(auditMiddleware, {
    auditClient: client,
    fireAndForget: false, // synchronous for tests
  });

  app.post("/api/orders", async (_req, reply) => {
    reply.code(201).send({ id: "order-1" });
  });

  app.get("/api/orders", async (_req, reply) => {
    reply.send([]);
  });

  app.delete("/api/orders/:id", async (_req, reply) => {
    reply.code(204).send();
  });

  await app.ready();
  return app;
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("@ghatana/audit-middleware", () => {
  let client: StubAuditClient;

  beforeEach(() => {
    client = new StubAuditClient();
  });

  it("audits POST requests", async () => {
    const app = await buildApp(client);
    await app.inject({ method: "POST", url: "/api/orders", payload: {} });

    expect(client.captured).toHaveLength(1);
    expect(client.captured[0].action).toMatch(/^POST:/);
    expect(client.captured[0].outcome).toBe("SUCCESS");
  });

  it("does NOT audit GET requests", async () => {
    const app = await buildApp(client);
    await app.inject({ method: "GET", url: "/api/orders" });

    expect(client.captured).toHaveLength(0);
  });

  it("audits DELETE requests with FAILURE outcome for 4xx", async () => {
    const app = await buildApp(client);
    // 204 is a success
    await app.inject({ method: "DELETE", url: "/api/orders/99" });

    expect(client.captured).toHaveLength(1);
    expect(client.captured[0].outcome).toBe("SUCCESS");
  });

  it("records traceId from request id", async () => {
    const app = await buildApp(client);
    await app.inject({ method: "POST", url: "/api/orders", payload: {} });

    expect(client.captured[0].traceId).toBeDefined();
  });

  it("sets timestampGregorian to ISO string", async () => {
    const app = await buildApp(client);
    await app.inject({ method: "POST", url: "/api/orders", payload: {} });

    const ts = client.captured[0].timestampGregorian;
    expect(ts).toBeDefined();
    expect(new Date(ts!).toISOString()).toBe(ts);
  });

  it("sets timestampBs to empty string (K-15 not yet wired in Sprint 1)", async () => {
    const app = await buildApp(client);
    await app.inject({ method: "POST", url: "/api/orders", payload: {} });

    expect(client.captured[0].timestampBs).toBe("");
  });
});
