/**
 * XAPIIngestor Tests
 *
 * @doc.type test
 * @doc.purpose Validate xAPI statement ingestion: parsing, normalization, validation, and mapping
 * @doc.layer plugin
 * @doc.pattern UnitTest
 *
 * Requirement IDs: TPUT-FR-063 (xAPI ingestion)
 */
import { describe, it, expect, beforeEach } from "vitest";
import {
  XAPIIngestor,
  createXAPIIngestor,
  type XAPIStatement,
} from "../plugins/XAPIIngestor";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeActor(
  type: "mbox" | "openid" | "account" | "sha1" = "mbox",
): XAPIStatement["actor"] {
  switch (type) {
    case "mbox":
      return { mbox: "mailto:learner@example.com", name: "Test Learner" };
    case "openid":
      return { openid: "https://example.com/users/learner-42" };
    case "account":
      return {
        account: { homePage: "https://canvas.example.com", name: "learner-42" },
      };
    case "sha1":
      return { mbox_sha1sum: "abc123def456" };
  }
}

function makeStatement(overrides: Partial<XAPIStatement> = {}): XAPIStatement {
  return {
    id: "stmt-001",
    actor: makeActor("mbox"),
    verb: {
      id: "http://adlnet.gov/expapi/verbs/answered",
      display: { "en-US": "answered" },
    },
    object: {
      objectType: "Activity",
      id: "https://tutorputor.com/activities/claim-physics-1",
      definition: {
        name: { "en-US": "Newton's Second Law claim" },
        type: "http://adlnet.gov/expapi/activities/question",
      },
    },
    result: {
      success: true,
      score: { scaled: 1.0, raw: 3, min: -6, max: 3 },
      response: "The net force equals mass times acceleration",
      extensions: {
        "http://tutorputor.com/xapi/extensions/confidence": 0.85,
      },
    },
    context: {
      registration: "reg-001",
      contextActivities: {
        parent: [
          {
            id: "https://tutorputor.com/claims/newton-2nd",
            objectType: "Activity",
          },
        ],
      },
    },
    timestamp: "2024-01-15T10:30:00Z",
    ...overrides,
  };
}

function makeRawEvent(statement: XAPIStatement, receivedAt = new Date()) {
  return {
    source: "xapi-lrs",
    format: "xapi" as const,
    payload: statement,
    receivedAt,
  };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("XAPIIngestor – TPUT-FR-063", () => {
  let ingestor: XAPIIngestor;

  beforeEach(() => {
    ingestor = createXAPIIngestor();
  });

  // =========================================================================
  // Plugin identity
  // =========================================================================
  describe("Plugin identity", () => {
    it("should identify itself as xapi-ingestor", () => {
      expect(ingestor.metadata.id).toBe("xapi-ingestor");
      expect(ingestor.metadata.type).toBe("ingestor");
      expect(ingestor.metadata.tags).toContain("xapi");
    });

    it("should accept events with xapi format", () => {
      const event = makeRawEvent(makeStatement());
      expect(ingestor.supports(event)).toBe(true);
    });

    it("should reject events with non-xapi format", () => {
      const event = { ...makeRawEvent(makeStatement()), format: "csv" } as any;
      expect(ingestor.supports(event)).toBe(false);
    });
  });

  // =========================================================================
  // Statement validation – TPUT-FR-063: required fields
  // =========================================================================
  describe("Statement validation", () => {
    it("should return null when actor is missing", async () => {
      const stmt = makeStatement();
      // @ts-expect-error intentional invalid input for boundary test
      delete (stmt as any).actor;
      const result = await ingestor.ingest(makeRawEvent(stmt));
      expect(result).toBeNull();
    });

    it("should return null when verb is missing", async () => {
      const stmt = makeStatement();
      // @ts-expect-error intentional invalid input
      delete (stmt as any).verb;
      const result = await ingestor.ingest(makeRawEvent(stmt));
      expect(result).toBeNull();
    });

    it("should return null when object id is missing", async () => {
      const stmt = makeStatement();
      // @ts-expect-error intentional invalid input
      delete (stmt as any).object;
      const result = await ingestor.ingest(makeRawEvent(stmt));
      expect(result).toBeNull();
    });

    it("should return null when actor has no identifier", async () => {
      const stmt = makeStatement({ actor: { name: "No Identifier" } });
      const result = await ingestor.ingest(makeRawEvent(stmt));
      expect(result).toBeNull();
    });

    it("should reject invalid verb IRI in strict mode", async () => {
      const strictIngestor = createXAPIIngestor({ strictValidation: true });
      const stmt = makeStatement({
        verb: { id: "not-a-valid-iri", display: {} },
      });
      const result = await strictIngestor.ingest(makeRawEvent(stmt));
      expect(result).toBeNull();
    });

    it("should accept statement with valid verb IRI in strict mode", async () => {
      const strictIngestor = createXAPIIngestor({ strictValidation: true });
      const stmt = makeStatement(); // has valid http:// IRI
      const result = await strictIngestor.ingest(makeRawEvent(stmt));
      expect(result).not.toBeNull();
    });

    it("should accept partial statement without result in permissive mode", async () => {
      const stmt = makeStatement({ result: undefined });
      const result = await ingestor.ingest(makeRawEvent(stmt));
      expect(result).not.toBeNull();
    });
  });

  // =========================================================================
  // Actor ID extraction
  // =========================================================================
  describe("Actor ID extraction", () => {
    it("should extract actor from mbox (email)", async () => {
      const stmt = makeStatement({ actor: makeActor("mbox") });
      const event = await ingestor.ingest(makeRawEvent(stmt));
      expect(event?.learnerId).toBe("learner@example.com");
    });

    it("should extract actor from openid", async () => {
      const stmt = makeStatement({ actor: makeActor("openid") });
      const event = await ingestor.ingest(makeRawEvent(stmt));
      expect(event?.learnerId).toBe("https://example.com/users/learner-42");
    });

    it("should extract actor from account (homepage::name)", async () => {
      const stmt = makeStatement({ actor: makeActor("account") });
      const event = await ingestor.ingest(makeRawEvent(stmt));
      expect(event?.learnerId).toBe("https://canvas.example.com::learner-42");
    });

    it("should extract actor from mbox_sha1sum with sha1: prefix", async () => {
      const stmt = makeStatement({ actor: makeActor("sha1") });
      const event = await ingestor.ingest(makeRawEvent(stmt));
      expect(event?.learnerId).toBe("sha1:abc123def456");
    });
  });

  // =========================================================================
  // Evidence event normalization
  // =========================================================================
  describe("Evidence event normalization", () => {
    it("should set the learningUnitId from object.id", async () => {
      const stmt = makeStatement();
      const event = await ingestor.ingest(makeRawEvent(stmt));
      expect(event?.learningUnitId).toBe(
        "https://tutorputor.com/activities/claim-physics-1",
      );
    });

    it("should extract claimId from context parent activities", async () => {
      const stmt = makeStatement();
      const event = await ingestor.ingest(makeRawEvent(stmt));
      expect(event?.claimId).toBe("https://tutorputor.com/claims/newton-2nd");
    });

    it("should default claimId to 'unknown' when context parent is absent", async () => {
      const stmt = makeStatement({
        context: { registration: "reg-x" },
      });
      const event = await ingestor.ingest(makeRawEvent(stmt));
      expect(event?.claimId).toBe("unknown");
    });

    it("should extract verb name from IRI as event type", async () => {
      const stmt = makeStatement({
        verb: { id: "http://adlnet.gov/expapi/verbs/completed" },
      });
      const event = await ingestor.ingest(makeRawEvent(stmt));
      expect(event?.type).toBe("completed");
    });

    it("should map result.success to payload.correct", async () => {
      const stmt = makeStatement({ result: { success: true } });
      const event = await ingestor.ingest(makeRawEvent(stmt));
      expect(event?.payload.correct).toBe(true);
    });

    it("should map result.score.scaled to payload.score", async () => {
      const stmt = makeStatement({
        result: { score: { scaled: 0.75, raw: 3, min: 0, max: 4 } },
      });
      const event = await ingestor.ingest(makeRawEvent(stmt));
      expect(event?.payload.score).toBe(0.75);
    });

    it("should extract confidence from TutorPutor extension", async () => {
      const stmt = makeStatement({
        result: {
          success: true,
          extensions: {
            "http://tutorputor.com/xapi/extensions/confidence": 0.9,
          },
        },
      });
      const event = await ingestor.ingest(makeRawEvent(stmt));
      expect(event?.payload.confidence).toBe(0.9);
    });

    it("should not include confidence in payload when extension is absent", async () => {
      const stmt = makeStatement({
        result: { success: false, response: "wrong answer" },
      });
      const event = await ingestor.ingest(makeRawEvent(stmt));
      expect(event?.payload.confidence).toBeUndefined();
    });

    it("should use statement timestamp when provided", async () => {
      const ts = "2024-06-01T12:00:00Z";
      const stmt = makeStatement({ timestamp: ts });
      const event = await ingestor.ingest(makeRawEvent(stmt));
      expect(event?.timestamp).toEqual(new Date(ts));
    });

    it("should fall back to receivedAt when timestamp is absent", async () => {
      const stmt = makeStatement({ timestamp: undefined });
      const receivedAt = new Date("2024-07-01T09:00:00Z");
      const event = await ingestor.ingest(makeRawEvent(stmt, receivedAt));
      expect(event?.timestamp).toEqual(receivedAt);
    });
  });

  // =========================================================================
  // CMI5 / custom profile verbs
  // =========================================================================
  describe("CMI5 and custom verb support", () => {
    const cmi5Verbs = [
      "http://adlnet.gov/expapi/verbs/passed",
      "http://adlnet.gov/expapi/verbs/failed",
      "http://adlnet.gov/expapi/verbs/completed",
      "http://adlnet.gov/expapi/verbs/initialized",
      "http://adlnet.gov/expapi/verbs/terminated",
    ];

    for (const verbId of cmi5Verbs) {
      it(`should normalize CMI5 verb '${verbId.split("/").pop()}'`, async () => {
        const stmt = makeStatement({ verb: { id: verbId } });
        const event = await ingestor.ingest(makeRawEvent(stmt));
        expect(event).not.toBeNull();
        const expected = verbId.split("/").pop();
        expect(event?.type).toBe(expected);
      });
    }

    it("should handle custom TutorPutor profile verb", async () => {
      const stmt = makeStatement({
        verb: {
          id: "https://tutorputor.com/verbs/predicted",
          display: { "en-US": "predicted" },
        },
      });
      const event = await ingestor.ingest(makeRawEvent(stmt));
      expect(event?.type).toBe("predicted");
    });
  });

  // =========================================================================
  // Edge cases and boundary inputs
  // =========================================================================
  describe("Edge cases and boundary inputs", () => {
    it("should generate a unique ID when statement.id is absent", async () => {
      const stmt = makeStatement({ id: undefined });
      const event = await ingestor.ingest(makeRawEvent(stmt));
      expect(event?.id).toBeTruthy();
      expect(typeof event?.id).toBe("string");
    });

    it("should handle result with undefined success gracefully", async () => {
      const stmt = makeStatement({ result: {} });
      const event = await ingestor.ingest(makeRawEvent(stmt));
      expect(event).not.toBeNull();
      expect(event?.payload.correct).toBeUndefined();
    });

    it("should handle statement with no result at all", async () => {
      const stmt = makeStatement({ result: undefined });
      const event = await ingestor.ingest(makeRawEvent(stmt));
      expect(event).not.toBeNull();
    });

    it("should handle empty verb display map", async () => {
      const stmt = makeStatement({
        verb: { id: "http://adlnet.gov/expapi/verbs/answered", display: {} },
      });
      const event = await ingestor.ingest(makeRawEvent(stmt));
      expect(event).not.toBeNull();
    });

    it("should handle verb IRI with trailing path segment only", async () => {
      const stmt = makeStatement({
        verb: { id: "answered" },
      });
      const event = await ingestor.ingest(makeRawEvent(stmt));
      // Non-IRI verb: should produce event typed as 'unknown'
      expect(event?.type).toBe("unknown");
    });

    it("should handle response text in payload", async () => {
      const stmt = makeStatement({
        result: { response: "acceleration = force / mass" },
      });
      const event = await ingestor.ingest(makeRawEvent(stmt));
      expect(event?.payload.response).toBe("acceleration = force / mass");
    });

    it("two consecutive ingestions produce events with distinct ids", async () => {
      const stmt1 = makeStatement({ id: undefined });
      const stmt2 = makeStatement({ id: undefined });
      const [ev1, ev2] = await Promise.all([
        ingestor.ingest(makeRawEvent(stmt1)),
        ingestor.ingest(makeRawEvent(stmt2)),
      ]);
      expect(ev1?.id).not.toBe(ev2?.id);
    });
  });
});
