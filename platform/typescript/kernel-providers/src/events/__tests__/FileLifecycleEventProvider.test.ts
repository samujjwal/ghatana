import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { vi } from "vitest";
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
      ref: "lifecycle-events.json",
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

  it("stores scoped events under tenant workspace project partitions", async () => {
    provider = new FileLifecycleEventProvider({
      outputDirectory: tempDir,
      scope: {
        tenantId: "tenant-1",
        workspaceId: "workspace-1",
        projectId: "project-1",
      },
    });

    const event = buildEvent("event-1", "run-1", "corr-1");
    const result = await provider.appendEvent(event, {
      required: true,
      correlationId: "corr-1",
    });

    expect(result).toEqual({
      success: true,
      ref: path.join("tenant-1", "workspace-1", "project-1", "events", "lifecycle-events.json"),
    });
    await expect(
      readEventsFile(path.join(tempDir, "tenant-1", "workspace-1", "project-1", "events"))
    ).resolves.toMatchObject({
      events: [event],
    });
  });

  it("paginates listed events by cursor and limit", async () => {
    const first = buildEvent("event-1", "run-1", "corr-1");
    const second = buildEvent("event-2", "run-1", "corr-2");
    const third = buildEvent("event-3", "run-1", "corr-3");
    await Promise.all([
      provider.appendEvent(first, { required: true, correlationId: "corr-1" }),
      provider.appendEvent(second, { required: true, correlationId: "corr-2" }),
      provider.appendEvent(third, { required: true, correlationId: "corr-3" }),
    ]);

    await expect(
      provider.listEvents({
        productUnitId: "digital-marketing",
        limit: 1,
        cursor: "1",
      })
    ).resolves.toEqual([second]);
  });

  it("serializes concurrent appends without clobbering events", async () => {
    const writes = Array.from({ length: 8 }, (_, index) =>
      provider.appendEvent(buildEvent(`event-${index}`, "run-1", `corr-${index}`), {
        required: true,
        correlationId: `corr-${index}`,
      })
    );

    await expect(Promise.all(writes)).resolves.toHaveLength(8);
    const stored = await readEventsFile(tempDir);
    expect((stored as { readonly events: readonly unknown[] }).events).toHaveLength(8);
  });

  it("warns when event count exceeds the configured threshold", async () => {
    const warn = vi.fn();
    provider = new FileLifecycleEventProvider({
      outputDirectory: tempDir,
      eventCountWarningThreshold: 1,
      logger: { warn },
    });

    await provider.appendEvent(buildEvent("event-1", "run-1", "corr-1"), {
      required: true,
      correlationId: "corr-1",
    });
    await provider.appendEvent(buildEvent("event-2", "run-1", "corr-2"), {
      required: true,
      correlationId: "corr-2",
    });

    expect(warn).toHaveBeenCalledWith(
      "File lifecycle event store exceeded warning threshold",
      expect.objectContaining({
        eventCount: 2,
        threshold: 1,
        ref: "lifecycle-events.json",
      })
    );
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
