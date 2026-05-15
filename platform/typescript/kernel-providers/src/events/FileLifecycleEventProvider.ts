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

export interface FileLifecycleEventProviderOptions {
  readonly outputDirectory: string;
  readonly fileName?: string;
}

interface StoredLifecycleEvents {
  readonly schemaVersion: "1.0.0";
  readonly events: readonly KernelLifecycleEvent[];
}

export class FileLifecycleEventProvider implements LifecycleEventProvider {
  readonly providerId = "file-lifecycle-events";
  readonly version = "1.0.0";
  readonly capabilities = ["lifecycle-events", "bootstrap-mode", "file-backed"];

  private readonly outputDirectory: string;
  private readonly fileName: string;

  constructor(options: FileLifecycleEventProviderOptions) {
    this.outputDirectory = options.outputDirectory;
    this.fileName = options.fileName ?? "lifecycle-events.json";
  }

  private get eventsPath(): string {
    return path.join(this.outputDirectory, this.fileName);
  }

  private async readStoredEvents(): Promise<StoredLifecycleEvents> {
    try {
      const content = await fs.readFile(this.eventsPath, "utf-8");
      const parsed = JSON.parse(content) as Partial<StoredLifecycleEvents>;
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
    } catch (error) {
      if (isFileNotFound(error)) {
        return { schemaVersion: "1.0.0", events: [] };
      }
      throw error;
    }
  }

  private async writeStoredEvents(events: StoredLifecycleEvents): Promise<void> {
    await fs.mkdir(this.outputDirectory, { recursive: true });
    const tempPath = `${this.eventsPath}.${process.pid}.${Date.now()}.tmp`;
    await fs.writeFile(tempPath, `${JSON.stringify(events, null, 2)}\n`, "utf-8");
    await fs.rename(tempPath, this.eventsPath);
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

    try {
      const storedEvents = await this.readStoredEvents();
      await this.writeStoredEvents({
        schemaVersion: "1.0.0",
        events: [...storedEvents.events, event],
      });
      return { success: true, ref: this.eventsPath };
    } catch (error) {
      return fail(String(error).replace(/^Error: /, ""), options.required);
    }
  }

  async listEvents(
    query: LifecycleProviderQuery
  ): Promise<readonly KernelLifecycleEvent[]> {
    const storedEvents = await this.readStoredEvents();
    return storedEvents.events.filter((event: KernelLifecycleEvent) => {
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
  }
}

function fail(message: string, required: boolean): LifecycleProviderResult {
  return {
    success: false,
    error: required ? message : `optional lifecycle event write skipped: ${message}`,
  };
}

function isFileNotFound(error: unknown): boolean {
  return (
    typeof error === "object" &&
    error !== null &&
    "code" in error &&
    (error as { readonly code?: unknown }).code === "ENOENT"
  );
}
