/**
 * FileLifecycleEventProvider - bootstrap lifecycle event persistence.
 *
 * @doc.type class
 * @doc.purpose File-backed lifecycle event provider for Kernel bootstrap mode
 * @doc.layer kernel-providers
 * @doc.pattern Provider
 */

import * as fs from "node:fs/promises";
import * as path from "node:path";
import type {
  KernelLifecycleEvent,
  LifecycleEventProvider,
  LifecycleProviderQuery,
  LifecycleProviderResult,
  LifecycleProviderWriteOptions,
} from "@ghatana/kernel-product-contracts";
import { validateKernelLifecycleEvent } from "@ghatana/kernel-product-contracts";
import {
  atomicWrite,
  cleanupRetainedFiles,
  encodePathSegment,
  fail,
  isFileNotFound,
  parseCursor,
  safeJsonRead,
  succeed,
} from "../file-provider/FileProviderUtils.js";

export interface FileLifecycleEventProviderOptions {
  readonly outputDirectory: string;
  readonly fileName?: string;
  readonly scope?: FileLifecycleEventProviderScope;
  readonly eventCountWarningThreshold?: number;
  readonly logger?: {
    readonly warn: (message: string, metadata?: Record<string, unknown>) => void;
  };
}

export interface FileLifecycleEventProviderScope {
  readonly tenantId: string;
  readonly workspaceId: string;
  readonly projectId: string;
}

interface StoredLifecycleEvents {
  readonly schemaVersion: "1.0.0";
  readonly events: readonly KernelLifecycleEvent[];
}

export class FileLifecycleEventProvider implements LifecycleEventProvider {
  readonly providerId = "file-lifecycle-events";
  readonly version = "1.0.0";
  readonly backingStore = "file" as const;
  readonly capabilities = ["lifecycle-events", "bootstrap-mode", "file-backed"];

  private readonly outputDirectory: string;
  private readonly fileName: string;
  private readonly scope: FileLifecycleEventProviderScope | undefined;
  private readonly eventCountWarningThreshold: number;
  private readonly logger:
    | {
        readonly warn: (message: string, metadata?: Record<string, unknown>) => void;
      }
    | undefined;
  private appendQueue: Promise<void> = Promise.resolve();

  constructor(options: FileLifecycleEventProviderOptions) {
    this.outputDirectory = path.resolve(options.outputDirectory);
    this.fileName = options.fileName ?? "lifecycle-events.json";
    this.scope = options.scope;
    this.eventCountWarningThreshold = options.eventCountWarningThreshold ?? 10_000;
    this.logger = options.logger;
  }

  private get eventsDirectory(): string {
    if (this.scope === undefined) {
      return this.outputDirectory;
    }
    return path.join(
      this.outputDirectory,
      encodePathSegment(this.scope.tenantId),
      encodePathSegment(this.scope.workspaceId),
      encodePathSegment(this.scope.projectId),
      "events"
    );
  }

  private get eventsPath(): string {
    return path.join(this.eventsDirectory, this.fileName);
  }

  private eventsRef(correlationId?: string): string {
    const basePath = path.relative(this.outputDirectory, this.eventsPath);
    if (correlationId === undefined) {
      return basePath;
    }
    return `${basePath}?correlationId=${correlationId}&providerId=${this.providerId}`;
  }

  private async readStoredEvents(): Promise<StoredLifecycleEvents> {
    const parsed = await safeJsonRead<Partial<StoredLifecycleEvents>>(this.eventsPath, {
      schemaVersion: "1.0.0",
      events: [],
    });
    if (parsed.schemaVersion !== "1.0.0" || !Array.isArray(parsed.events)) {
      throw new Error("lifecycle events file has invalid shape");
    }
    const invalidEvent = parsed.events.find(
      (event: KernelLifecycleEvent) => !validateKernelLifecycleEvent(event).valid
    );
    if (invalidEvent !== undefined) {
      throw new Error(
        `lifecycle events file contains invalid event ${invalidEvent.metadata.eventId}`
      );
    }
    return {
      schemaVersion: "1.0.0",
      events: parsed.events,
    };
  }

  private async writeStoredEvents(events: StoredLifecycleEvents): Promise<void> {
    await atomicWrite(this.eventsPath, `${JSON.stringify(events, null, 2)}\n`);
  }

  async appendEvent(
    event: KernelLifecycleEvent,
    options: LifecycleProviderWriteOptions
  ): Promise<LifecycleProviderResult> {
    const validation = validateKernelLifecycleEvent(event);
    if (!validation.valid) {
      return fail(
        `Invalid lifecycle event: ${validation.errors.join("; ")}`,
        options.required
      );
    }
    if (event.metadata.correlationId !== options.correlationId) {
      return fail(
        `Lifecycle event correlationId ${event.metadata.correlationId} does not match write correlationId ${options.correlationId}`,
        options.required
      );
    }

    return this.enqueueAppend(async () => {
      const storedEvents = await this.readStoredEvents();
      if (storedEvents.events.length + 1 > this.eventCountWarningThreshold) {
        this.logger?.warn("File lifecycle event store exceeded warning threshold", {
          providerId: this.providerId,
          eventCount: storedEvents.events.length + 1,
          threshold: this.eventCountWarningThreshold,
          ref: this.eventsRef(options.correlationId),
        });
      }
      await this.writeStoredEvents({
        schemaVersion: "1.0.0",
        events: [...storedEvents.events, event],
      });
      return { success: true, ref: this.eventsRef(options.correlationId) };
    }, options.required);
  }

  async listEvents(
    query: LifecycleProviderQuery
  ): Promise<readonly KernelLifecycleEvent[]> {
    const storedEvents = await this.readStoredEvents();
    const filteredEvents = storedEvents.events.filter((event: KernelLifecycleEvent) => {
      if (event.metadata.productUnitId !== query.productUnitId) {
        return false;
      }
      if (query.runId !== undefined && event.metadata.runId !== query.runId) {
        return false;
      }
      if (
        query.correlationId !== undefined &&
        event.metadata.correlationId !== query.correlationId
      ) {
        return false;
      }
      return true;
    });
    const startIndex = parseCursor(query.cursor);
    const limitedEvents =
      query.limit === undefined
        ? filteredEvents.slice(startIndex)
        : filteredEvents.slice(startIndex, startIndex + Math.max(0, query.limit));
    return limitedEvents;
  }

  async cleanupRetainedEvents(): Promise<LifecycleProviderResult> {
    try {
      await cleanupRetainedFiles(this.eventsDirectory);
      return succeed(this.eventsRef());
    } catch (error) {
      return fail(String(error).replace(/^Error: /, ""), true);
    }
  }

  private async enqueueAppend(
    operation: () => Promise<LifecycleProviderResult>,
    required: boolean
  ): Promise<LifecycleProviderResult> {
    const run = this.appendQueue.then(operation, operation);
    this.appendQueue = run.then(
      () => undefined,
      () => undefined
    );
    try {
      return await run;
    } catch (error) {
      return fail(String(error).replace(/^Error: /, ""), required);
    }
  }
}
