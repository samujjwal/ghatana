import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import type { KernelLifecycleEvent } from "@ghatana/kernel-product-contracts";
import { FileLifecycleEventProvider } from "../FileLifecycleEventProvider";

describe("FileLifecycleEventProvider", () => {
  let tempDir: string;
  let provider: FileLifecycleEventProvider;

  beforeEach(async () => {
    tempDir = await fs.mkdtemp(path.join(os.tmpdir(), "ghatana-events-"));
    provider = new FileLifecycleEventProvider({ outputDirectory: tempDir });
  });

  afterEach(async () => {
    await fs.rm(tempDir, { recursive: true, force: true });
  });

  it("appends valid lifecycle events atomically", async () => {
    const event = buildEvent("event-1", "run-1", "corr-1");

    const result = await provider.appendEvent(event, {
      required: true,
      correlationId: "corr-1",
    });

    expect(result).toEqual({
      success: true,
      ref: path.join(tempDir, "lifecycle-events.json"),
    });
    await expect(readEventsFile(tempDir)).resolves.toMatchObject({
      schemaVersion: "1.0.0",
      events: [event],
    });
  });

  it("lists events by product unit, run, and correlation", async () => {
    const first = buildEvent("event-1", "run-1", "corr-1");
    const second = buildEvent("event-2", "run-2", "corr-2");
    const third = buildEvent("event-3", "run-1", "corr-3", "finance");
    await provider.appendEvent(first, { required: true, correlationId: "corr-1" });
    await provider.appendEvent(second, { required: true, correlationId: "corr-2" });
    await provider.appendEvent(third, { required: true, correlationId: "corr-3" });

    await expect(
      provider.listEvents({
        productUnitId: "digital-marketing",
        runId: "run-1",
        correlationId: "corr-1",
      })
    ).resolves.toEqual([first]);
    await expect(
      provider.listEvents({
        productUnitId: "digital-marketing",
        correlationId: "corr-3",
      })
    ).resolves.toEqual([]);
  });

  it("rejects invalid events without writing", async () => {
    const invalidEvent = {
      ...buildEvent("event-1", "run-1", "corr-1"),
      metadata: {
        ...buildEvent("event-1", "run-1", "corr-1").metadata,
        eventId: "",
      },
    } as KernelLifecycleEvent;

    const result = await provider.appendEvent(invalidEvent, {
      required: true,
      correlationId: "corr-1",
    });

    expect(result.success).toBe(false);
    expect(result.error).toContain("Invalid lifecycle event");
    await expect(
      fs.access(path.join(tempDir, "lifecycle-events.json"))
    ).rejects.toMatchObject({ code: "ENOENT" });
  });

  it("rejects correlation mismatch", async () => {
    const result = await provider.appendEvent(buildEvent("event-1", "run-1", "corr-1"), {
      required: true,
      correlationId: "corr-2",
    });

    expect(result).toEqual({
      success: false,
      error:
        "Lifecycle event correlationId corr-1 does not match write correlationId corr-2",
    });
  });

  it("fails closed when the existing event file is malformed", async () => {
    await fs.writeFile(path.join(tempDir, "lifecycle-events.json"), "{ nope", "utf-8");

    const result = await provider.appendEvent(buildEvent("event-1", "run-1", "corr-1"), {
      required: true,
      correlationId: "corr-1",
    });

    expect(result.success).toBe(false);
    expect(result.error).toContain("JSON");
  });

  it("fails closed when the existing event file has an invalid shape", async () => {
    await fs.writeFile(
      path.join(tempDir, "lifecycle-events.json"),
      JSON.stringify({ schemaVersion: "1.0.0", events: {} }),
      "utf-8"
    );

    const result = await provider.appendEvent(buildEvent("event-1", "run-1", "corr-1"), {
      required: true,
      correlationId: "corr-1",
    });

    expect(result.success).toBe(false);
    expect(result.error).toBe("lifecycle events file has invalid shape");
  });

  it("fails closed when the existing event file contains an invalid event", async () => {
    await fs.writeFile(
      path.join(tempDir, "lifecycle-events.json"),
      JSON.stringify({
        schemaVersion: "1.0.0",
        events: [
          {
            ...buildEvent("event-1", "run-1", "corr-1"),
            metadata: {
              ...buildEvent("event-1", "run-1", "corr-1").metadata,
              eventId: "",
            },
          },
        ],
      }),
      "utf-8"
    );

    const result = await provider.appendEvent(buildEvent("event-2", "run-1", "corr-1"), {
      required: true,
      correlationId: "corr-1",
    });

    expect(result.success).toBe(false);
    expect(result.error).toBe("lifecycle events file contains invalid event ");
  });

  it("returns an explicit optional failure message for optional writes", async () => {
    const result = await provider.appendEvent(buildEvent("event-1", "run-1", "corr-1"), {
      required: false,
      correlationId: "corr-2",
    });

    expect(result.success).toBe(false);
    expect(result.error).toContain("optional lifecycle event write skipped");
  });
});

function buildEvent(
  eventId: string,
  runId: string,
  correlationId: string,
  productUnitId = "digital-marketing"
): KernelLifecycleEvent {
  return {
    metadata: {
      eventId,
      schemaVersion: "1.0.0",
      eventType: "lifecycle.manifest.written",
      productUnitId,
      runId,
      phase: "build",
      timestamp: "2026-05-14T00:00:00.000Z",
      source: "file-provider-test",
      correlationId,
    },
    payload: {
      manifestType: "lifecycle-events",
      path: ".kernel/out/lifecycle-events.json",
      required: true,
      status: "written",
    },
  };
}

async function readEventsFile(outputDirectory: string): Promise<unknown> {
  const content = await fs.readFile(
    path.join(outputDirectory, "lifecycle-events.json"),
    "utf-8"
  );
  return JSON.parse(content);
}
