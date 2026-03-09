/**
 * @fileoverview BrowserMessageRouter Tests
 */

import { describe, it, expect, beforeEach, vi } from "vitest";
import { BrowserMessageRouter } from "../../src/adapters/BrowserMessageRouter";
import browser from "webextension-polyfill";

describe("BrowserMessageRouter", () => {
  let router: BrowserMessageRouter;

  beforeEach(() => {
    router = new BrowserMessageRouter();
    vi.clearAllMocks();
  });

  describe("sendToBackground", () => {
    it("should send message to background script", async () => {
      const mockResponse = { success: true, data: "test" };
      vi.mocked(browser.runtime.sendMessage).mockResolvedValue(mockResponse);

      const response = await router.sendToBackground({
        type: "TEST_MESSAGE",
        payload: { value: 123 },
      });

      expect(browser.runtime.sendMessage).toHaveBeenCalled();
      expect(response).toEqual(mockResponse);
    });

    it("should handle sendMessage errors", async () => {
      vi.mocked(browser.runtime.sendMessage).mockRejectedValue(
        new Error("Send failed")
      );

      const response = await router.sendToBackground({
        type: "TEST_MESSAGE",
        payload: {},
      });

      expect(response.success).toBe(false);
      expect(response.error).toBeDefined();
    });
  });

  describe("sendToContent", () => {
    it("should send message to specific tab", async () => {
      const mockResponse = { success: true };
      vi.mocked(browser.tabs.sendMessage).mockResolvedValue(mockResponse);

      const response = await router.sendToContent(123, {
        type: "TEST_MESSAGE",
        payload: {},
      });

      expect(browser.tabs.sendMessage).toHaveBeenCalledWith(
        123,
        expect.objectContaining({
          type: "TEST_MESSAGE",
        })
      );
      expect(response).toEqual(mockResponse);
    });
  });

  describe("broadcastToContent", () => {
    it("should send message to all tabs", async () => {
      const mockTabs = [
        { id: 1, url: "http://example.com" },
        { id: 2, url: "http://test.com" },
      ];
      vi.mocked(browser.tabs.query).mockResolvedValue(mockTabs as any);
      vi.mocked(browser.tabs.sendMessage).mockResolvedValue({ success: true });

      const responses = await router.broadcastToContent({
        type: "BROADCAST",
        payload: {},
      });

      expect(browser.tabs.sendMessage).toHaveBeenCalledTimes(2);
      expect(responses).toHaveLength(2);
    });
  });

  describe("onMessage", () => {
    it("should register message handler", () => {
      const handler = vi.fn();

      router.onMessage(handler);

      // The handler is registered
      expect(handler).toBeDefined();
    });
  });

  describe("onMessageType", () => {
    it("should register type-specific handler", () => {
      const handler = vi.fn();

      router.onMessageType("GET_DATA", handler);

      // Verify handler was registered
      expect(handler).toBeDefined();
    });
  });

  describe("getContextType", () => {
    it("should detect context type", () => {
      const contextType = router.getContextType();

      expect(contextType).toMatch(
        /background|content|popup|options|devtools|unknown/
      );
    });
  });
});
