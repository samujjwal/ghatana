/**
 * @fileoverview Tests for BaseExtensionController
 *
 * Tests lifecycle management, state tracking, configuration handling,
 * and hook execution for the base controller class.
 */

import { describe, it, expect, beforeEach, vi } from "vitest";
import {
  BaseExtensionController,
  type ControllerConfig,
  type ControllerState,
  type ControllerLifecycleHooks,
} from "../../src/controller/BaseExtensionController";

// Test implementation of BaseExtensionController
interface TestConfig extends ControllerConfig {
  apiKey?: string;
  enabled?: boolean;
  timeout?: number;
}

interface TestState extends ControllerState {
  connected: boolean;
  dataCollecting: boolean;
}

class TestController extends BaseExtensionController<TestConfig, TestState> {
  public initializeCalled = false;
  public shutdownCalled = false;
  public configLoadCalled = false;
  public configSaveCalled = false;

  constructor(
    initialConfig?: TestConfig,
    hooks?: ControllerLifecycleHooks<TestConfig, TestState>
  ) {
    super(
      {
        initialized: false,
        connected: false,
        dataCollecting: false,
      },
      initialConfig,
      hooks
    );
  }

  protected async doInitialize(): Promise<void> {
    this.initializeCalled = true;
    this.updateState({
      connected: true,
      dataCollecting: this.config.enabled ?? false,
    });
  }

  protected async doShutdown(): Promise<void> {
    this.shutdownCalled = true;
    this.updateState({
      connected: false,
      dataCollecting: false,
    });
  }

  protected async loadConfiguration(): Promise<TestConfig | null> {
    this.configLoadCalled = true;
    return null; // Use initial config
  }

  protected async saveConfiguration(_config: TestConfig): Promise<void> {
    this.configSaveCalled = true;
  }
}

describe("BaseExtensionController", () => {
  let controller: TestController;

  beforeEach(() => {
    controller = new TestController({
      version: "1.0.0",
      apiKey: "test-key",
      enabled: true,
    });
  });

  describe("Initialization", () => {
    it("should initialize successfully", async () => {
      expect(controller.isInitialized()).toBe(false);

      await controller.initialize();

      expect(controller.isInitialized()).toBe(true);
      expect(controller.initializeCalled).toBe(true);
    });

    it("should call loadConfiguration during initialization", async () => {
      await controller.initialize();

      expect(controller.configLoadCalled).toBe(true);
    });

    it("should update state during initialization", async () => {
      await controller.initialize();

      const state = controller.getState();
      expect(state.initialized).toBe(true);
      expect(state.connected).toBe(true);
      expect(state.dataCollecting).toBe(true);
    });

    it("should handle multiple initialize calls safely", async () => {
      await controller.initialize();
      const firstState = controller.getState();

      await controller.initialize();
      const secondState = controller.getState();

      expect(firstState).toEqual(secondState);
      expect(controller.initializeCalled).toBe(true); // Called only once
    });

    it("should call lifecycle hooks in order", async () => {
      const hookCalls: string[] = [];

      const controllerWithHooks = new TestController(
        { version: "1.0.0" },
        {
          onBeforeInit: () => {
            hookCalls.push("onBeforeInit");
          },
          onAfterInit: () => {
            hookCalls.push("onAfterInit");
          },
        }
      );

      await controllerWithHooks.initialize();

      expect(hookCalls).toEqual(["onBeforeInit", "onAfterInit"]);
    });
  });

  describe("Shutdown", () => {
    it("should shutdown successfully", async () => {
      await controller.initialize();
      expect(controller.isInitialized()).toBe(true);

      await controller.shutdown();

      expect(controller.isInitialized()).toBe(false);
      expect(controller.shutdownCalled).toBe(true);
    });

    it("should update state during shutdown", async () => {
      await controller.initialize();

      await controller.shutdown();

      const state = controller.getState();
      expect(state.initialized).toBe(false);
      expect(state.connected).toBe(false);
      expect(state.dataCollecting).toBe(false);
    });

    it("should handle shutdown when not initialized", async () => {
      expect(controller.isInitialized()).toBe(false);

      await controller.shutdown();

      expect(controller.shutdownCalled).toBe(false); // Not called if not initialized
    });

    it("should handle multiple shutdown calls safely", async () => {
      await controller.initialize();

      await controller.shutdown();
      const firstState = controller.getState();

      await controller.shutdown();
      const secondState = controller.getState();

      expect(firstState).toEqual(secondState);
    });

    it("should call lifecycle hooks in order", async () => {
      const hookCalls: string[] = [];

      const controllerWithHooks = new TestController(
        { version: "1.0.0" },
        {
          onBeforeShutdown: () => {
            hookCalls.push("onBeforeShutdown");
          },
          onAfterShutdown: () => {
            hookCalls.push("onAfterShutdown");
          },
        }
      );

      await controllerWithHooks.initialize();
      await controllerWithHooks.shutdown();

      expect(hookCalls).toEqual(["onBeforeShutdown", "onAfterShutdown"]);
    });
  });

  describe("Configuration Management", () => {
    it("should get current configuration", () => {
      const config = controller.getConfig();

      expect(config.version).toBe("1.0.0");
      expect(config.apiKey).toBe("test-key");
      expect(config.enabled).toBe(true);
    });

    it("should update configuration", async () => {
      await controller.updateConfig({
        timeout: 5000,
      });

      const config = controller.getConfig();
      expect(config.timeout).toBe(5000);
      expect(config.apiKey).toBe("test-key"); // Original values preserved
    });

    it("should call saveConfiguration when updating config", async () => {
      await controller.updateConfig({ timeout: 3000 });

      expect(controller.configSaveCalled).toBe(true);
    });

    it("should call onConfigUpdate hook", async () => {
      const hookFn = vi.fn();

      const controllerWithHook = new TestController(
        { version: "1.0.0", enabled: true },
        {
          onConfigUpdate: hookFn,
        }
      );

      await controllerWithHook.updateConfig({ enabled: false });

      expect(hookFn).toHaveBeenCalled();
    });

    it("should not persist config if persist=false", async () => {
      await controller.updateConfig({ timeout: 2000 }, false);

      expect(controller.configSaveCalled).toBe(false);
    });
  });

  describe("State Management", () => {
    it("should get current state", () => {
      const state = controller.getState();

      expect(state.initialized).toBe(false);
      expect(state.connected).toBe(false);
      expect(state.dataCollecting).toBe(false);
    });

    it("should return readonly copy of state", () => {
      const state1 = controller.getState();
      const state2 = controller.getState();

      expect(state1).toEqual(state2);
      expect(state1).not.toBe(state2); // Different instances
    });

    it("should call onStateChange hook when state updates", async () => {
      const hookFn = vi.fn();

      const controllerWithHook = new TestController(
        { version: "1.0.0" },
        {
          onStateChange: hookFn,
        }
      );

      await controllerWithHook.initialize();

      expect(hookFn).toHaveBeenCalled();
      const [newState, oldState] = hookFn.mock.calls[0];
      expect(newState.initialized).toBe(true);
      expect(oldState.initialized).toBe(false);
    });
  });

  describe("Lifecycle Integration", () => {
    it("should support full lifecycle: init -> config update -> shutdown", async () => {
      // Initialize
      await controller.initialize();
      expect(controller.isInitialized()).toBe(true);
      expect(controller.getState().connected).toBe(true);

      // Update config
      await controller.updateConfig({ timeout: 10000 });
      expect(controller.getConfig().timeout).toBe(10000);

      // Shutdown
      await controller.shutdown();
      expect(controller.isInitialized()).toBe(false);
      expect(controller.getState().connected).toBe(false);
    });

    it("should track all state changes throughout lifecycle", async () => {
      const stateChanges: Array<{
        initialized: boolean;
        connected: boolean;
      }> = [];

      const controllerWithHook = new TestController(
        { version: "1.0.0" },
        {
          onStateChange: (state) => {
            stateChanges.push({
              initialized: state.initialized,
              connected: state.connected,
            });
          },
        }
      );

      await controllerWithHook.initialize();
      await controllerWithHook.shutdown();

      // Should have captured state changes
      expect(stateChanges.length).toBeGreaterThan(0);
      expect(stateChanges[stateChanges.length - 1].initialized).toBe(false);
    });
  });

  describe("Error Handling", () => {
    it("should propagate initialization errors", async () => {
      class FailingController extends BaseExtensionController {
        protected async doInitialize(): Promise<void> {
          throw new Error("Initialization failed");
        }

        protected async doShutdown(): Promise<void> {
          return Promise.resolve();
        }
      }

      const failingController = new FailingController({ initialized: false });

      await expect(failingController.initialize()).rejects.toThrow(
        "Initialization failed"
      );
      expect(failingController.isInitialized()).toBe(false);
    });

    it("should propagate shutdown errors", async () => {
      class FailingController extends BaseExtensionController {
        protected async doInitialize(): Promise<void> {
          return Promise.resolve();
        }

        protected async doShutdown(): Promise<void> {
          throw new Error("Shutdown failed");
        }
      }

      const failingController = new FailingController({ initialized: false });

      await failingController.initialize();

      await expect(failingController.shutdown()).rejects.toThrow(
        "Shutdown failed"
      );
    });
  });
});
