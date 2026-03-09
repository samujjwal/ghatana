/**
 * @fileoverview Guardian Background Script
 *
 * Entry point for the Guardian extension background service worker.
 */

import browser from "webextension-polyfill";
import { GuardianController } from "../controller/GuardianController";
import {
  ExtensionPluginHost,
} from "@ghatana/dcmaar-browser-extension-core";
import guardianPluginManifest from "../config/guardian-plugin-manifest";
import { registerGuardianPlugins } from "../plugins/guardianPlugins";

// Filter out expected-but-noisy warnings from shared capture utilities
// about missing browser APIs in MV3/service-worker environments.
const originalWarn = console.warn.bind(console);
const SUPPRESSED_WARN_PREFIXES = [
  "[UnifiedBrowserEventCapture] webRequest API not available.",
  "[UnifiedBrowserEventCapture] webRequest API not available. WebRequest events will not be captured.",
  "[UnifiedBrowserEventCapture] History API not available. Skipping history capture.",
];

console.warn = (...args: unknown[]) => {
  const first = typeof args[0] === "string" ? args[0] : "";

  // Suppress known noisy capture warnings
  if (SUPPRESSED_WARN_PREFIXES.some((p) => first.startsWith(p))) {
    return;
  }

  // Suppress the specific non-fatal DNR duplicate ID warning coming from
  // WebsiteBlocker.applyDynamicRules.
  if (first.startsWith("WebsiteBlocker: failed to update dynamic rules")) {
    const rest = args
      .slice(1)
      .map((a) =>
        a instanceof Error ? a.message : typeof a === "string" ? a : String(a ?? "")
      )
      .join(" ");
    if (rest.includes("does not have a unique ID")) {
      // Downgrade to debug so it is only visible when explicitly inspecting
      // verbose logs.
      console.debug(
        "[Guardian] Suppressed non-fatal DNR duplicate ID warning from WebsiteBlocker",
        rest
      );
      return;
    }
  }

  originalWarn(...(args as Parameters<typeof console.warn>));
};

// Ensure browser object is available
if (!browser) {
  console.error("[Guardian] webextension-polyfill not loaded");
  throw new Error("webextension-polyfill not available");
}

// Initialize plugin host for Guardian using shared plugin abstractions
const pluginHost = new ExtensionPluginHost();
registerGuardianPlugins(pluginHost);

// Create and initialize the Guardian controller
const controller = new GuardianController(pluginHost);

// Log manifest for debugging
console.log("[Guardian] Plugin manifest loaded:", {
  appId: guardianPluginManifest?.appId,
  hasPlugins: !!guardianPluginManifest?.plugins,
  pluginCount: guardianPluginManifest?.plugins?.length ?? 0,
});

// Track initialization state to prevent duplicate initialization
let isInitialized = false;

/**
 * Initialize the extension with proper error handling
 */
const initializeExtension = async () => {
  if (isInitialized) {
    console.debug("[Guardian] Extension already initialized, skipping");
    return;
  }

  try {
    console.log("[Guardian] Initializing extension");
    isInitialized = true;

    await controller.initialize();

    if (guardianPluginManifest?.plugins) {
      await pluginHost
        .initializeFromManifest(guardianPluginManifest)
        .catch((error) => {
          console.error("[Guardian] Plugin host initialization failed:", error);
        });
    } else {
      console.warn("[Guardian] Guardian plugin manifest is not properly initialized");
    }
  } catch (error) {
    console.error("[Guardian] Extension initialization failed:", error);
    isInitialized = false; // Allow retry on error
  }
};

// Initialize on install or startup
browser.runtime.onInstalled.addListener(async () => {
  console.log("[Guardian] Extension installed");
  await initializeExtension();
});

browser.runtime.onStartup.addListener(async () => {
  console.log("[Guardian] Extension started");
  await initializeExtension();
});

// Initialize immediately on first load
initializeExtension().catch((error) => {
  console.error("[Guardian] Initialization failed:", error);
});


// Cleanup on suspend (important for service workers)
if ("onSuspend" in browser.runtime) {
  (browser.runtime as any).onSuspend.addListener(async () => {
    console.log("[Guardian] Service worker suspending");
    await controller.shutdown();
    await pluginHost.shutdown();
  });
}

// Keep service worker alive with periodic alarms
try {
  if (browser.alarms) {
    browser.alarms.create("guardian-keepalive", { periodInMinutes: 1 });

    browser.alarms.onAlarm.addListener((alarm) => {
      if (alarm.name === "guardian-keepalive") {
        // Just accessing the controller keeps the service worker alive
        const state = controller.getState();
        if (!state.initialized) {
          controller.initialize().catch(console.error);
        }
      }
    });
  } else {
    console.warn("[Guardian] browser.alarms not available");
  }
} catch (error) {
  console.error("[Guardian] Failed to setup alarms:", error);
}

// Export for debugging
(globalThis as any).guardianController = controller;
(globalThis as any).guardianPluginHost = pluginHost;
