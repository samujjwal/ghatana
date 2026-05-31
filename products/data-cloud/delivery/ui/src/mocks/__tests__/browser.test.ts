import { describe, expect, it } from "vitest";
import { isRecoverableMswStartupError } from "../mswRecovery";

describe("isRecoverableMswStartupError", () => {
  it("treats missing or mis-served mockServiceWorker assets as recoverable", () => {
    const error = new Error(
      "[MSW] Failed to register the Service Worker: unsupported MIME type ('text/html') for /mockServiceWorker.js",
    );

    expect(isRecoverableMswStartupError(error)).toBe(true);
  });

  it("does not swallow unrelated bootstrap errors", () => {
    const error = new Error("Root element not found");

    expect(isRecoverableMswStartupError(error)).toBe(false);
  });
});
