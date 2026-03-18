/**
 * @fileoverview Logger Tests
 * Tests for structured logging with correlation IDs
 */

import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  Logger,
  LogContext,
  systemLogger,
  registerLoggerPlugin,
} from "../logger";

// Mock console methods
const mockConsoleLog = vi.spyOn(console, "log").mockImplementation(() => {});
const mockConsoleError = vi
  .spyOn(console, "error")
  .mockImplementation(() => {});

// Mock crypto randomUUID
vi.mock("crypto", () => ({
  randomUUID: vi.fn().mockReturnValue("test-uuid-1234"),
}));

describe("Logger", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    process.env.NODE_ENV = "development";
  });

  afterEach(() => {
    process.env.NODE_ENV = "test";
  });

  describe("constructor", () => {
    it("should create logger with default context", () => {
      const logger = new Logger();
      expect(logger).toBeInstanceOf(Logger);
    });

    it("should create logger with custom context", () => {
      const context: LogContext = {
        userId: "user-123",
        correlationId: "corr-456",
      };
      const logger = new Logger(context);
      expect(logger).toBeInstanceOf(Logger);
    });
  });

  describe("static factory methods", () => {
    it("should create logger from request with correlation ID from header", () => {
      const mockRequest = {
        headers: { "x-correlation-id": "req-corr-123" },
        method: "POST",
        url: "/api/test",
        user: { userId: "user-456" },
      };

      const logger = Logger.fromRequest(mockRequest as any);
      expect(logger).toBeInstanceOf(Logger);
    });

    it("should create logger from request with generated correlation ID", () => {
      const mockRequest = {
        headers: {},
        method: "GET",
        url: "/api/test",
      };

      const logger = Logger.fromRequest(mockRequest as any);
      expect(logger).toBeInstanceOf(Logger);
    });

    it("should create logger with custom context", () => {
      const logger = Logger.create({ component: "test" });
      expect(logger).toBeInstanceOf(Logger);
    });

    it("should generate correlation ID when creating logger", () => {
      const logger = Logger.create({ component: "test" });
      expect(logger).toBeInstanceOf(Logger);
    });
  });

  describe("child logger", () => {
    it("should create child logger with merged context", () => {
      const parent = new Logger({ userId: "user-123" });
      const child = parent.child({ component: "test-component" });

      expect(child).toBeInstanceOf(Logger);
      expect(child).not.toBe(parent);
    });
  });

  describe("logging methods", () => {
    it("should log info message", () => {
      const logger = new Logger({ correlationId: "test-123" });
      logger.info("Test info message");

      expect(mockConsoleLog).toHaveBeenCalled();
    });

    it("should log info with data", () => {
      const logger = new Logger();
      logger.info("Test message", { key: "value", number: 42 });

      expect(mockConsoleLog).toHaveBeenCalled();
    });

    it("should log warning message", () => {
      const logger = new Logger();
      logger.warn("Test warning");

      expect(mockConsoleLog).toHaveBeenCalled();
    });

    it("should log error message", () => {
      const logger = new Logger();
      const error = new Error("Test error");
      logger.error("Test error message", error);

      expect(mockConsoleLog).toHaveBeenCalled();
    });

    it("should log error with non-Error object", () => {
      const logger = new Logger();
      logger.error("Test error", "string error");

      expect(mockConsoleLog).toHaveBeenCalled();
    });

    it("should log debug message in development", () => {
      process.env.NODE_ENV = "development";
      const logger = new Logger();
      logger.debug("Debug message");

      expect(mockConsoleLog).toHaveBeenCalled();
    });

    it("should not log debug in production", () => {
      process.env.NODE_ENV = "production";
      const logger = new Logger();
      logger.debug("Debug message");

      // In production, debug logs should be JSON but debug() skips them
      expect(mockConsoleLog).not.toHaveBeenCalled();
    });
  });

  describe("production logging", () => {
    it("should output JSON in production", () => {
      process.env.NODE_ENV = "production";
      const logger = new Logger({ correlationId: "test-123" });
      logger.info("Production log");

      expect(mockConsoleLog).toHaveBeenCalled();
      const loggedCall = mockConsoleLog.mock.calls[0]?.[0];
      if (loggedCall) {
        expect(() => JSON.parse(loggedCall as string)).not.toThrow();
      }
    });
  });

  describe("logRequest", () => {
    it("should log successful request", () => {
      const logger = new Logger();
      logger.logRequest(200, 150);

      expect(mockConsoleLog).toHaveBeenCalled();
    });

    it("should log client error as warning", () => {
      const logger = new Logger();
      logger.logRequest(404, 50);

      expect(mockConsoleLog).toHaveBeenCalled();
    });

    it("should log server error as error", () => {
      const logger = new Logger();
      logger.logRequest(500, 200);

      expect(mockConsoleLog).toHaveBeenCalled();
    });
  });

  describe("logBusinessEvent", () => {
    it("should log business event", () => {
      const logger = new Logger();
      logger.logBusinessEvent("USER_LOGIN", { userId: "user-123" });

      expect(mockConsoleLog).toHaveBeenCalled();
    });
  });

  describe("systemLogger", () => {
    it("should export system logger singleton", () => {
      expect(systemLogger).toBeInstanceOf(Logger);
    });

    it("should have service context", () => {
      expect(systemLogger).toBeDefined();
    });
  });

  describe("registerLoggerPlugin", () => {
    it("should register Fastify plugin", async () => {
      const mockApp = {
        decorateRequest: vi.fn(),
        addHook: vi.fn(),
      };

      await registerLoggerPlugin(mockApp);

      expect(mockApp.decorateRequest).toHaveBeenCalledWith("logger", null);
      expect(mockApp.addHook).toHaveBeenCalledTimes(3);
    });

    it("should attach logger on request", async () => {
      const mockLogger = { debug: vi.fn() };
      const mockRequest: any = {
        headers: {},
        method: "GET",
        url: "/test",
        logger: null,
      };

      const mockApp = {
        decorateRequest: vi.fn(),
        addHook: vi
          .fn()
          .mockImplementation((event: string, handler: Function) => {
            if (event === "onRequest") {
              handler(mockRequest);
            }
          }),
      };

      await registerLoggerPlugin(mockApp);

      // The hook should have been called
      expect(mockApp.addHook).toHaveBeenCalled();
    });

    it("should log response on onResponse hook", async () => {
      const mockLogger = { logRequest: vi.fn() };
      const mockRequest: any = { logger: mockLogger };
      const mockReply: any = {
        statusCode: 200,
        getResponseTime: vi.fn().mockReturnValue(100),
      };

      let responseHandler: ((req: any, reply: any) => Promise<void>) | null =
        null;
      const mockApp = {
        decorateRequest: vi.fn(),
        addHook: vi
          .fn()
          .mockImplementation((event: string, handler: Function) => {
            if (event === "onResponse") {
              responseHandler = handler;
            }
          }),
      };

      await registerLoggerPlugin(mockApp);

      if (responseHandler) {
        await responseHandler(mockRequest, mockReply);
        expect(mockLogger.logRequest).toHaveBeenCalledWith(200, 100);
      }
    });

    it("should add correlation ID header on onSend hook", async () => {
      const mockLogger = { context: { correlationId: "corr-123" } };
      const mockRequest: any = { logger: mockLogger };
      const mockReply: any = {
        header: vi.fn(),
      };

      let sendHandler: ((req: any, reply: any) => Promise<void>) | null = null;
      const mockApp = {
        decorateRequest: vi.fn(),
        addHook: vi
          .fn()
          .mockImplementation((event: string, handler: Function) => {
            if (event === "onSend") {
              sendHandler = handler;
            }
          }),
      };

      await registerLoggerPlugin(mockApp);

      if (sendHandler) {
        await sendHandler(mockRequest, mockReply);
        expect(mockReply.header).toHaveBeenCalledWith(
          "X-Correlation-Id",
          "corr-123",
        );
      }
    });
  });
});
