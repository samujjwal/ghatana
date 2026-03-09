/**
 * @fileoverview EventPipeline Tests
 */

import { describe, it, expect, beforeEach, vi } from "vitest";
import { EventPipeline } from "../../src/pipeline/EventPipeline";
import { BaseEventSource } from "../../src/pipeline/EventSource";
import { BaseEventProcessor } from "../../src/pipeline/EventProcessor";
import { BaseEventSink } from "../../src/pipeline/EventSink";

// Mock Event Types
interface TestEvent {
  id: string;
  value: number;
  timestamp: number;
}

interface ProcessedEvent extends TestEvent {
  processed: boolean;
  multiplied?: number;
}

// Mock Source
class MockEventSource extends BaseEventSource<TestEvent> {
  name = "mock-source";
  private events: TestEvent[] = [];

  constructor(events: TestEvent[] = []) {
    super();
    this.events = events;
  }

  async start(): Promise<void> {
    this.status = "started";
    // Emit all events
    for (const event of this.events) {
      this.emit(event);
    }
  }

  async stop(): Promise<void> {
    this.status = "stopped";
  }

  // Helper to emit events manually
  emitEvent(event: TestEvent): void {
    this.emit(event);
  }
}

// Mock Processor 1: Adds 'processed' flag
class AddProcessedFlagProcessor extends BaseEventProcessor<
  TestEvent,
  ProcessedEvent
> {
  name = "add-flag";

  canProcess(_event: TestEvent): boolean {
    return true;
  }

  async process(event: TestEvent): Promise<ProcessedEvent | null> {
    return await this.trackProcess(async () => ({
      ...event,
      processed: true,
    }));
  }
}

// Mock Processor 2: Multiplies value
class MultiplyValueProcessor extends BaseEventProcessor<
  ProcessedEvent,
  ProcessedEvent
> {
  name = "multiply";
  private multiplier: number;

  constructor(multiplier: number = 2) {
    super();
    this.multiplier = multiplier;
  }

  canProcess(event: ProcessedEvent): boolean {
    return event.processed === true;
  }

  async process(event: ProcessedEvent): Promise<ProcessedEvent | null> {
    return await this.trackProcess(async () => ({
      ...event,
      multiplied: event.value * this.multiplier,
    }));
  }
}

// Mock Processor 3: Filters events
class FilterProcessor extends BaseEventProcessor<
  ProcessedEvent,
  ProcessedEvent
> {
  name = "filter";
  private threshold: number;

  constructor(threshold: number = 5) {
    super();
    this.threshold = threshold;
  }

  canProcess(_event: ProcessedEvent): boolean {
    return true;
  }

  async process(event: ProcessedEvent): Promise<ProcessedEvent | null> {
    if (event.value < this.threshold) {
      this.trackFilter();
      return null; // Filter out
    }
    return await this.trackProcess(async () => event);
  }
}

// Mock Sink
class MockEventSink extends BaseEventSink<ProcessedEvent> {
  name = "mock-sink";
  public receivedEvents: ProcessedEvent[] = [];
  public batchedEvents: ProcessedEvent[][] = [];

  async initialize(): Promise<void> {
    this.ready = true;
  }

  async send(event: ProcessedEvent): Promise<void> {
    await this.trackSend(async () => {
      this.receivedEvents.push(event);
    });
  }

  async sendBatch(events: ProcessedEvent[]): Promise<void> {
    await this.trackBatchSend(events.length, async () => {
      this.batchedEvents.push(events);
      this.receivedEvents.push(...events);
    });
  }

  async shutdown(): Promise<void> {
    this.ready = false;
  }

  reset(): void {
    this.receivedEvents = [];
    this.batchedEvents = [];
  }
}

describe("EventPipeline", () => {
  let pipeline: EventPipeline;
  let source: MockEventSource;
  let sink: MockEventSink;

  beforeEach(() => {
    pipeline = new EventPipeline({ name: "test-pipeline" });
    source = new MockEventSource();
    sink = new MockEventSink();
  });

  describe("Component Registration", () => {
    it("should register sources, processors, and sinks", () => {
      const processor = new AddProcessedFlagProcessor();

      pipeline.registerSource(source);
      pipeline.registerProcessor(processor);
      pipeline.registerSink(sink);

      const counts = pipeline.getComponentCounts();
      expect(counts.sources).toBe(1);
      expect(counts.processors).toBe(1);
      expect(counts.sinks).toBe(1);
    });

    it("should not allow registration while pipeline is running", async () => {
      pipeline.registerSource(source);
      pipeline.registerSink(sink);

      await pipeline.start();

      expect(() => pipeline.registerSource(new MockEventSource())).toThrow();
      expect(() =>
        pipeline.registerProcessor(new AddProcessedFlagProcessor())
      ).toThrow();
      expect(() => pipeline.registerSink(new MockEventSink())).toThrow();

      await pipeline.stop();
    });
  });

  describe("Lifecycle", () => {
    it("should start and stop pipeline", async () => {
      pipeline.registerSource(source);
      pipeline.registerSink(sink);

      expect(pipeline.getIsRunning()).toBe(false);

      await pipeline.start();
      expect(pipeline.getIsRunning()).toBe(true);

      await pipeline.stop();
      expect(pipeline.getIsRunning()).toBe(false);
    });

    it("should initialize sinks on start", async () => {
      pipeline.registerSource(source);
      pipeline.registerSink(sink);

      expect(sink.isReady()).toBe(false);

      await pipeline.start();
      expect(sink.isReady()).toBe(true);

      await pipeline.stop();
    });

    it("should start sources on pipeline start", async () => {
      pipeline.registerSource(source);
      pipeline.registerSink(sink);

      expect(source.getStatus()).toBe("stopped");

      await pipeline.start();
      expect(source.getStatus()).toBe("started");

      await pipeline.stop();
      expect(source.getStatus()).toBe("stopped");
    });
  });

  describe("Event Flow", () => {
    it("should pass events from source to sink", async () => {
      const events: TestEvent[] = [
        { id: "1", value: 10, timestamp: Date.now() },
        { id: "2", value: 20, timestamp: Date.now() },
      ];

      source = new MockEventSource(events);
      pipeline.registerSource(source);
      pipeline.registerSink(sink);

      await pipeline.start();

      // Wait for async event processing
      await new Promise((resolve) => setTimeout(resolve, 100));

      expect(sink.receivedEvents.length).toBe(2);
      expect(sink.receivedEvents[0].id).toBe("1");
      expect(sink.receivedEvents[1].id).toBe("2");

      await pipeline.stop();
    });

    it("should process events through processor chain", async () => {
      const events: TestEvent[] = [
        { id: "1", value: 10, timestamp: Date.now() },
      ];

      source = new MockEventSource(events);
      const processor1 = new AddProcessedFlagProcessor();
      const processor2 = new MultiplyValueProcessor(3);

      pipeline.registerSource(source);
      pipeline.registerProcessor(processor1);
      pipeline.registerProcessor(processor2);
      pipeline.registerSink(sink);

      await pipeline.start();
      await new Promise((resolve) => setTimeout(resolve, 100));

      expect(sink.receivedEvents.length).toBe(1);
      expect(sink.receivedEvents[0].processed).toBe(true);
      expect(sink.receivedEvents[0].multiplied).toBe(30); // 10 * 3

      await pipeline.stop();
    });

    it("should filter events when processor returns null", async () => {
      const events: TestEvent[] = [
        { id: "1", value: 3, timestamp: Date.now() }, // Will be filtered
        { id: "2", value: 10, timestamp: Date.now() }, // Will pass
      ];

      source = new MockEventSource(events);
      const processor = new FilterProcessor(5); // Filter events with value < 5

      pipeline.registerSource(source);
      pipeline.registerProcessor(processor);
      pipeline.registerSink(sink);

      await pipeline.start();
      await new Promise((resolve) => setTimeout(resolve, 100));

      expect(sink.receivedEvents.length).toBe(1);
      expect(sink.receivedEvents[0].id).toBe("2");

      await pipeline.stop();
    });

    it("should send events to multiple sinks", async () => {
      const events: TestEvent[] = [
        { id: "1", value: 10, timestamp: Date.now() },
      ];

      source = new MockEventSource(events);
      const sink1 = new MockEventSink();
      const sink2 = new MockEventSink();

      pipeline.registerSource(source);
      pipeline.registerSink(sink1);
      pipeline.registerSink(sink2);

      await pipeline.start();
      await new Promise((resolve) => setTimeout(resolve, 100));

      expect(sink1.receivedEvents.length).toBe(1);
      expect(sink2.receivedEvents.length).toBe(1);

      await pipeline.stop();
    });
  });

  describe("Statistics", () => {
    it("should track pipeline statistics", async () => {
      const events: TestEvent[] = [
        { id: "1", value: 3, timestamp: Date.now() }, // Filtered
        { id: "2", value: 10, timestamp: Date.now() }, // Processed
        { id: "3", value: 15, timestamp: Date.now() }, // Processed
      ];

      source = new MockEventSource(events);
      const filter = new FilterProcessor(5);
      const processor = new AddProcessedFlagProcessor();

      pipeline.registerSource(source);
      pipeline.registerProcessor(filter);
      pipeline.registerProcessor(processor);
      pipeline.registerSink(sink);

      await pipeline.start();
      await new Promise((resolve) => setTimeout(resolve, 100));

      const stats = pipeline.getStats();
      expect(stats.eventsReceived).toBe(3);
      expect(stats.eventsFiltered).toBe(1);
      expect(stats.eventsProcessed).toBe(2);
      expect(stats.eventsSent).toBe(2);
      expect(stats.errors).toBe(0);

      await pipeline.stop();
    });

    it("should reset statistics", async () => {
      const events: TestEvent[] = [
        { id: "1", value: 10, timestamp: Date.now() },
      ];

      source = new MockEventSource(events);
      pipeline.registerSource(source);
      pipeline.registerSink(sink);

      await pipeline.start();
      await new Promise((resolve) => setTimeout(resolve, 100));

      expect(pipeline.getStats().eventsReceived).toBeGreaterThan(0);

      pipeline.resetStats();

      const stats = pipeline.getStats();
      expect(stats.eventsReceived).toBe(0);
      expect(stats.eventsProcessed).toBe(0);
      expect(stats.eventsSent).toBe(0);

      await pipeline.stop();
    });
  });

  describe("Error Handling", () => {
    it("should handle errors with continueOnError=true", async () => {
      const errorProcessor = new AddProcessedFlagProcessor();
      errorProcessor.process = vi
        .fn()
        .mockRejectedValue(new Error("Processing error"));

      const events: TestEvent[] = [
        { id: "1", value: 10, timestamp: Date.now() },
      ];

      source = new MockEventSource(events);
      pipeline = new EventPipeline({ name: "test", continueOnError: true });
      pipeline.registerSource(source);
      pipeline.registerProcessor(errorProcessor);
      pipeline.registerSink(sink);

      await pipeline.start();
      await new Promise((resolve) => setTimeout(resolve, 100));

      const stats = pipeline.getStats();
      expect(stats.errors).toBeGreaterThan(0);
      expect(sink.receivedEvents.length).toBe(0); // Event not sent due to error

      await pipeline.stop();
    });

    it("should call error handler on errors", async () => {
      const onError = vi.fn();
      const errorProcessor = new AddProcessedFlagProcessor();
      errorProcessor.process = vi
        .fn()
        .mockRejectedValue(new Error("Test error"));

      const events: TestEvent[] = [
        { id: "1", value: 10, timestamp: Date.now() },
      ];

      source = new MockEventSource(events);
      pipeline = new EventPipeline({
        name: "test",
        continueOnError: true,
        onError,
      });
      pipeline.registerSource(source);
      pipeline.registerProcessor(errorProcessor);
      pipeline.registerSink(sink);

      await pipeline.start();
      await new Promise((resolve) => setTimeout(resolve, 100));

      expect(onError).toHaveBeenCalled();

      await pipeline.stop();
    });
  });

  describe("Configuration", () => {
    it("should return pipeline configuration", () => {
      const config = pipeline.getConfig();
      expect(config.name).toBe("test-pipeline");
      expect(config.continueOnError).toBe(true);
      expect(config.maxConcurrency).toBe(100);
    });
  });
});
