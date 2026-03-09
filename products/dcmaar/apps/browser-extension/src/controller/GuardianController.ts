/**
 * @fileoverview Guardian Extension Controller
 *
 * Main controller for the Guardian performance monitoring extension.
 * Uses the @ghatana/dcmaar-browser-extension-core framework for lifecycle management.
 */

import {
  BaseExtensionController,
  BrowserStorageAdapter,
  BrowserMessageRouter,
  UnifiedBrowserEventCapture,
  type ControllerConfig,
  type ControllerState,
  type BrowserEvent,
  type ExtensionPluginHost,
} from "@ghatana/dcmaar-browser-extension-core";
import browser from "webextension-polyfill";
import { WebsiteBlocker } from "../blocker/WebsiteBlocker";
import {
  createGuardianPipeline,
  type GuardianPipelineInstance,
  type GuardianPipelineConfig,
  type DailyUsage,
  type DomainUsage,
  type PolicyEvaluatedEvent,
} from "../pipeline";
import { CommandSyncSource, type GuardianCommand } from "../pipeline/sources/CommandSyncSource";
import { CommandExecutionSink } from "../pipeline/sinks/CommandExecutionSink";
import { TelemetrySink } from "../pipeline/sinks/TelemetrySink";

/**
 * Website access status
 */
type AccessStatus = 'allowed' | 'blocked' | 'temporarily_blocked';

/**
 * Web usage data collected by the extension
 */
interface WebUsageData {
  timestamp: number;
  url: string;
  domain: string;
  title: string;
  sessionDuration: number; // milliseconds
  entryTime: number;
  status?: AccessStatus; // 'allowed', 'blocked', 'temporarily_blocked'
  blockedReason?: string; // Reason for blocking if applicable
}

/**
 * Web usage statistics
 */
interface WebUsageStats {
  domain: string;
  urls: string[];
  visitCount: number;
  blockedCount: number; // Number of times blocked
  temporarilyBlockedCount: number; // Number of times temporarily blocked
  totalTimeSpent: number; // milliseconds
  lastVisited: number;
  averageSessionDuration: number;
  status: AccessStatus; // Current/predominant status
}

/**
 * Guardian configuration
 */
export interface GuardianConfig extends ControllerConfig {
  version: string;
  metricsEnabled: boolean;
  eventsEnabled: boolean;
  collectionInterval: number;
  retentionDays: number;
  // Backend sync configuration
  apiBaseUrl?: string;
  deviceId?: string;
  childId?: string;
  syncEnabled?: boolean;
}

/**
 * Guardian state
 */
export interface GuardianState extends ControllerState {
  metricsCollecting: boolean;
  eventsCapturing: boolean;
  totalMetricsCollected: number;
  totalEventsCollected: number;
  lastCollectionTime?: number;
}

interface UnblockRequest {
  id: string;
  url: string;
  policyId?: string;
  blockReason?: string;
  childReason?: string;
  timeRemainingMinutes?: number;
  source?: string;
  requestedAt: number;
}

/**
 * Default Guardian configuration
 */
const DEFAULT_CONFIG: GuardianConfig = {
  version: "1.0.0",
  metricsEnabled: true,
  eventsEnabled: true,
  collectionInterval: 30000, // 30 seconds
  retentionDays: 7,
  // Default backend sync config - connects to local development server
  apiBaseUrl: "http://localhost:3001",
  deviceId: "",
  childId: "",
  syncEnabled: false,
};

/**
 * Guardian Extension Controller
 *
 * Orchestrates metrics collection, event capture, and data storage
 * for the Guardian performance monitoring extension.
 */
export class GuardianController extends BaseExtensionController<
  GuardianConfig,
  GuardianState
> {
  private storage = new BrowserStorageAdapter();
  private router = new BrowserMessageRouter();
  private events = new UnifiedBrowserEventCapture();
  private blocker = new WebsiteBlocker();
  private pluginHost?: ExtensionPluginHost;
  private pipeline?: GuardianPipelineInstance;
  private commandSyncSource?: CommandSyncSource;
  private commandExecutionSink?: CommandExecutionSink;
  private telemetrySink?: TelemetrySink;

  constructor(pluginHost?: ExtensionPluginHost) {
    super(
      {
        initialized: false,
        metricsCollecting: false,
        eventsCapturing: false,
        totalMetricsCollected: 0,
        totalEventsCollected: 0,
      },
      DEFAULT_CONFIG
    );
    this.pluginHost = pluginHost;
  }

  /**
   * Initialize Guardian extension
   */
  protected async doInitialize(): Promise<void> {
    this.log("Initializing Guardian...");

    // Initialize website blocker
    await this.blocker.initialize();

    // Initialize Guardian event pipeline
    const pipelineConfig: GuardianPipelineConfig = {
      name: "guardian",
      continueOnError: true,
      enableContentScript: true,
      enableRealTimeSync: false,
      retentionDays: this.config.retentionDays,
    };
    this.pipeline = createGuardianPipeline(this.blocker, pipelineConfig);
    await this.pipeline.start();

    const apiBaseUrl = (this.config as GuardianConfig & { apiBaseUrl?: string }).apiBaseUrl;
    const deviceId = (this.config as GuardianConfig & { deviceId?: string }).deviceId;

    if (apiBaseUrl && deviceId) {
      const getAuthToken = (): string | null => {
        try {
          const ls = (globalThis as any).localStorage as
            | { getItem: (key: string) => string | null }
            | undefined;
          if (!ls) return null;
          return ls.getItem("guardian_token") || ls.getItem("token");
        } catch {
          return null;
        }
      };

      this.commandExecutionSink = new CommandExecutionSink({
        apiBaseUrl,
        deviceId,
        getAuthToken,
        onPolicyUpdate: async (command: GuardianCommand) => {
          this.log("Received policy_update command", {
            commandId: command.command_id,
            action: command.action,
          });

          try {
            const params = command.params as { policies?: unknown };
            const policies = params?.policies;

            if (Array.isArray(policies)) {
              await this.blocker.savePolicies(policies as any[]);
            } else {
              await this.blocker.syncPoliciesFromBackend(apiBaseUrl, deviceId);
            }
          } catch (error) {
            this.logError("Failed to apply policy_update command", error);
            throw error;
          }
        },
        onImmediateAction: async (command: GuardianCommand) => {
          this.log("Received immediate_action command", {
            commandId: command.command_id,
            action: command.action,
          });

          const action = command.action;

          try {
            if (action === "lock_device") {
              const blockedUrlBase = browser.runtime.getURL(
                "src/pages/blocked.html",
              );
              const blockedUrl = `${blockedUrlBase}?reason=${encodeURIComponent(
                "Device locked by parent",
              )}`;

              const tabs = await browser.tabs.query({
                active: true,
                currentWindow: true,
              });

              if (tabs.length > 0 && tabs[0].id) {
                await browser.tabs.update(tabs[0].id, { url: blockedUrl });
              } else {
                await browser.tabs.create({ url: blockedUrl, active: true });
              }
            } else if (action === "unlock_device") {
              if (browser.notifications && browser.notifications.create) {
                try {
                  await browser.notifications.create({
                    type: "basic",
                    iconUrl: browser.runtime.getURL("icons/icon-128.png"),
                    title: "Device Unlocked",
                    message: "Browsing has been unlocked by your parent.",
                  } as any);
                } catch {
                  // Swallow notification errors to avoid failing command execution
                }
              }
            } else {
              this.log("Unsupported immediate_action command", {
                commandId: command.command_id,
                action,
              });
            }
          } catch (error) {
            this.logError("Failed to apply immediate_action command", error);
            throw error;
          }
        },
        onSessionRequest: async (command: GuardianCommand) => {
          this.log("Received session_request command", {
            commandId: command.command_id,
            action: command.action,
          });

          try {
            const params = command.params as {
              domain?: string;
              duration_minutes?: number;
              reason?: string;
            } | undefined;

            // Handle temporary_unblock action
            if (command.action === 'temporary_unblock' && params?.domain) {
              const durationMinutes = params.duration_minutes || 30; // Default 30 minutes
              await this.blocker.addTempAllow(
                params.domain,
                durationMinutes,
                command.issued_by?.actor_type || 'parent',
                params.reason
              );

              this.log("Applied temporary unblock", {
                domain: params.domain,
                durationMinutes,
              });

              // Notify user via notification if available
              if (browser.notifications && browser.notifications.create) {
                try {
                  await browser.notifications.create({
                    type: "basic",
                    iconUrl: browser.runtime.getURL("icons/icon-128.png"),
                    title: "Access Granted",
                    message: `${params.domain} is now accessible for ${durationMinutes} minutes.`,
                  } as any);
                } catch {
                  // Swallow notification errors
                }
              }
            }

            // Store the request record for history
            const existing =
              (await this.storage.get<unknown[]>(
                "guardian:session_requests",
              )) || [];

            const record = {
              id: command.command_id,
              action: command.action,
              params: command.params,
              issued_by: command.issued_by,
              created_at: command.created_at,
              target: command.target,
            };

            const updated = [...existing, record];
            await this.storage.set("guardian:session_requests", updated);
          } catch (error) {
            this.logError("Failed to handle session_request command", error);
            throw error;
          }
        },
      });

      this.telemetrySink = new TelemetrySink({
        apiBaseUrl,
        deviceId,
        childId: (this.config as GuardianConfig & { childId?: string }).childId,
        getAuthToken,
      });

      await this.commandExecutionSink.initialize();
      await this.telemetrySink.initialize();

      this.commandSyncSource = new CommandSyncSource({
        apiBaseUrl,
        deviceId,
        getAuthToken,
      });

      this.commandSyncSource.onEvent(async (event) => {
        if (!this.commandExecutionSink) {
          return;
        }

        const results = await this.commandExecutionSink.executeCommands(
          event.snapshot.commands.items,
        );

        if (this.telemetrySink) {
          for (const result of results) {
            await this.telemetrySink.sendCommandEvent(
              result.command_id,
              result.status === "processed" ? "completed" : "failed",
              result.error_reason,
            );
          }
        }
      });

      await this.commandSyncSource.start();
    }

    // Setup message handlers
    this.setupMessageHandlers();

    // Metrics collection is now handled by the event pipeline
    this.updateState({ metricsCollecting: true });

    // Start event capture if enabled
    if (this.config.eventsEnabled) {
      await this.startEventCapture();
    }

    // Cleanup old data
    await this.cleanupOldData();

    this.log("Guardian initialized successfully");
  }

  /**
   * Shutdown Guardian extension
   */
  protected async doShutdown(): Promise<void> {
    this.log("Shutting down Guardian...");

    // Metrics collection is handled by content scripts and will naturally stop
    this.updateState({ metricsCollecting: false });

    // Stop event capture
    if (this.state.eventsCapturing) {
      this.events.stop();
      this.updateState({ eventsCapturing: false });
    }

    // Stop event pipeline
    if (this.pipeline) {
      await this.pipeline.stop();
      this.pipeline = undefined;
    }

    if (this.commandSyncSource) {
      await this.commandSyncSource.stop();
      this.commandSyncSource = undefined;
    }

    if (this.commandExecutionSink) {
      await this.commandExecutionSink.shutdown();
      this.commandExecutionSink = undefined;
    }

    if (this.telemetrySink) {
      await this.telemetrySink.shutdown();
      this.telemetrySink = undefined;
    }

    this.log("Guardian shutdown complete");
  }

  // Legacy tab monitoring and direct usage storage have been removed.

  /**
   * Load configuration from storage
   */
  protected async loadConfiguration(): Promise<GuardianConfig | null> {
    const stored = await this.storage.get<GuardianConfig>("guardian-config");
    if (stored) {
      this.log("Loaded configuration from storage", stored);
      return stored;
    }
    return null;
  }

  /**
   * Save configuration to storage
   */
  protected async saveConfiguration(config: GuardianConfig): Promise<void> {
    await this.storage.set("guardian-config", config);
    this.log("Configuration saved to storage");
  }

  /**
   * Apply configuration changes
   */
  protected async applyConfigChanges(
    newConfig: GuardianConfig,
    oldConfig: GuardianConfig
  ): Promise<void> {
    // Note: Metrics collection is now handled by content scripts
    // Configuration changes for metrics are applied on next page load

    // Restart event capture if changed
    if (newConfig.eventsEnabled !== oldConfig.eventsEnabled) {
      if (this.state.eventsCapturing) {
        this.events.stop();
      }
      if (newConfig.eventsEnabled) {
        await this.startEventCapture();
      }
    }
  }

  /**
   * Setup message handlers for cross-context communication
   */
  private setupMessageHandlers(): void {
    // Simple PING handler for debugging connectivity
    this.router.onMessageType("PING", async () => {
      console.debug('[GuardianController] PING received');
      return { success: true, data: { pong: true, timestamp: Date.now() } };
    });

    // Get current events
    this.router.onMessageType("GET_EVENTS", async () => {
      try {
        const events = await this.storage.get<BrowserEvent[]>(
          "guardian-events"
        );
        return {
          success: true,
          data: events || [],
        };
      } catch (error) {
        return {
          success: false,
          error:
            error instanceof Error ? error.message : "Failed to get events",
        };
      }
    });

    // Get controller state
    this.router.onMessageType("GET_STATE", async () => {
      return {
        success: true,
        data: this.getState(),
      };
    });

    // Get configuration
    this.router.onMessageType("GET_CONFIG", async () => {
      return {
        success: true,
        data: this.getConfig(),
      };
    });

    // Update configuration
    this.router.onMessageType("UPDATE_CONFIG", async (message) => {
      try {
        await this.updateConfig(message.payload as Partial<GuardianConfig>);
        return {
          success: true,
          data: this.getConfig(),
        };
      } catch (error) {
        return {
          success: false,
          error:
            error instanceof Error ? error.message : "Failed to update config",
        };
      }
    });

    // Get analytics summary
    this.router.onMessageType("GET_ANALYTICS", async () => {
      try {
        console.debug('[GuardianController] GET_ANALYTICS request received');
        const analytics = await this.getAnalyticsSummary();
        console.debug('[GuardianController] GET_ANALYTICS response', {
          hasDomains: !!analytics.domains,
          domainsLength: analytics.domains?.length,
          hasPages: !!analytics.pages,
          pagesLength: analytics.pages?.length,
          hasBlockedEvents: !!analytics.blockedEvents,
          blockedEventsLength: analytics.blockedEvents?.length,
          hasContentSummary: !!analytics.contentSummary,
          contentSummaryLength: analytics.contentSummary?.length,
        });
        return {
          success: true,
          data: analytics,
        };
      } catch (error) {
        console.error('[GuardianController] GET_ANALYTICS error', error);
        console.error('[GuardianController] Error stack:', (error as any)?.stack);
        return {
          success: false,
          error:
            error instanceof Error ? error.message : "Failed to get analytics",
        };
      }
    });

    // Clear all data
    this.router.onMessageType("CLEAR_DATA", async () => {
      try {
        // Clear legacy event data
        await this.storage.remove("guardian-events");

        // Clear pipeline storage (daily aggregates + raw events)
        if (this.pipeline) {
          await this.pipeline.storageSink.clearAll();
        }

        this.updateState({
          totalMetricsCollected: 0,
          totalEventsCollected: 0,
        });
        return {
          success: true,
        };
      } catch (error) {
        return {
          success: false,
          error:
            error instanceof Error ? error.message : "Failed to clear data",
        };
      }
    });

    // Configure backend sync settings
    this.router.onMessageType("CONFIGURE_BACKEND_SYNC", async (message) => {
      try {
        const payload = message.payload as {
          apiBaseUrl?: string;
          deviceId?: string;
          childId?: string;
          syncEnabled?: boolean;
        };

        // Update config
        const newConfig: Partial<GuardianConfig> = {};
        if (payload.apiBaseUrl !== undefined) newConfig.apiBaseUrl = payload.apiBaseUrl;
        if (payload.deviceId !== undefined) newConfig.deviceId = payload.deviceId;
        if (payload.childId !== undefined) newConfig.childId = payload.childId;
        if (payload.syncEnabled !== undefined) newConfig.syncEnabled = payload.syncEnabled;

        await this.updateConfig(newConfig);

        // Re-initialize telemetry sink if backend is now configured
        if (payload.syncEnabled && payload.apiBaseUrl && payload.deviceId) {
          await this.initializeBackendSync(payload.apiBaseUrl, payload.deviceId, payload.childId);
        }

        return {
          success: true,
          data: {
            apiBaseUrl: this.config.apiBaseUrl,
            deviceId: this.config.deviceId,
            childId: this.config.childId,
            syncEnabled: this.config.syncEnabled,
          },
        };
      } catch (error) {
        return {
          success: false,
          error: error instanceof Error ? error.message : "Failed to configure backend sync",
        };
      }
    });

    // Manual sync trigger - send current analytics to backend
    this.router.onMessageType("SYNC_TO_BACKEND", async () => {
      try {
        const result = await this.syncToBackend();
        return {
          success: true,
          data: result,
        };
      } catch (error) {
        return {
          success: false,
          error: error instanceof Error ? error.message : "Failed to sync to backend",
        };
      }
    });

    // Get backend sync status
    this.router.onMessageType("GET_BACKEND_SYNC_STATUS", async () => {
      return {
        success: true,
        data: {
          configured: !!(this.config.apiBaseUrl && this.config.deviceId),
          enabled: this.config.syncEnabled ?? false,
          apiBaseUrl: this.config.apiBaseUrl,
          deviceId: this.config.deviceId,
          childId: this.config.childId,
          telemetrySinkActive: !!this.telemetrySink,
          commandSyncActive: !!this.commandSyncSource,
        },
      };
    });

    // Get blocking policies (READ-ONLY - policies managed from parent dashboard)
    this.router.onMessageType("GET_POLICIES", async () => {
      try {
        const policies = this.blocker.getPolicies();
        return {
          success: true,
          data: policies,
        };
      } catch (error) {
        return {
          success: false,
          error:
            error instanceof Error ? error.message : "Failed to get policies",
        };
      }
    });

    // Get mapping from category → domains for UI explanations
    this.router.onMessageType("GET_CATEGORY_DOMAINS", async () => {
      try {
        const categories = this.blocker.getCategoryDomains();
        return {
          success: true,
          data: categories,
        };
      } catch (error) {
        return {
          success: false,
          error:
            error instanceof Error
              ? error.message
              : "Failed to get category domains",
        };
      }
    });

    // Get block events (READ-ONLY)
    this.router.onMessageType("GET_BLOCK_EVENTS", async (message) => {
      try {
        const limit = (message.payload as { limit?: number })?.limit || 100;
        const events = await this.blocker.getBlockEvents(limit);
        return {
          success: true,
          data: events,
        };
      } catch (error) {
        return {
          success: false,
          error:
            error instanceof Error ? error.message : "Failed to get block events",
        };
      }
    });

    // Handle access request from blocked page (sends to backend)
    this.router.onMessageType("REQUEST_ACCESS", async (message) => {
      try {
        const anyMessage = message as unknown as { payload?: { url?: string; reason?: string; timestamp?: number } };
        const payload = anyMessage?.payload || {};

        if (!payload.url) {
          return {
            success: false,
            error: "URL is required to request access",
          };
        }

        // Extract domain from URL
        let domain = '';
        try {
          const urlObj = new URL(payload.url);
          domain = urlObj.hostname.replace(/^www\./, '');
        } catch {
          domain = payload.url;
        }

        // Get device and child IDs from config
        const guardianConfig = this.config as GuardianConfig & { deviceId?: string; childId?: string; apiBaseUrl?: string };
        const deviceId = guardianConfig.deviceId;
        const childId = guardianConfig.childId;
        const apiBaseUrl = guardianConfig.apiBaseUrl || 'http://localhost:3001';

        if (!childId) {
          // Store locally if no child ID configured
          const existing = (await this.storage.get<UnblockRequest[]>("guardian:unblock_requests")) || [];
          const request: UnblockRequest = {
            id: `req-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
            url: payload.url,
            childReason: payload.reason,
            source: "blocked_page",
            requestedAt: payload.timestamp || Date.now(),
          };
          await this.storage.set("guardian:unblock_requests", [...existing, request]);

          return {
            success: true,
            data: { requestId: request.id, stored: 'local' },
          };
        }

        // Send request to backend
        const authToken = await this.storage.get<string>("guardian:auth_token");

        const response = await fetch(`${apiBaseUrl}/api/children/${childId}/requests`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...(authToken ? { 'Authorization': `Bearer ${authToken}` } : {}),
          },
          body: JSON.stringify({
            type: 'unblock',
            device_id: deviceId,
            resource: { domain },
            reason: payload.reason,
          }),
        });

        if (!response.ok) {
          throw new Error(`Backend returned ${response.status}`);
        }

        const result = await response.json();

        // Emit telemetry event
        if (this.telemetrySink) {
          await this.telemetrySink.sendEvent({
            kind: 'system',
            subtype: 'access_request_submitted',
            context: { domain, url: payload.url },
            payload: { reason: payload.reason, request_id: result.data?.id },
          });
        }

        return {
          success: true,
          data: { requestId: result.data?.id, stored: 'backend' },
        };
      } catch (error) {
        this.logError("Failed to submit access request", error);
        return {
          success: false,
          error: error instanceof Error ? error.message : "Failed to submit access request",
        };
      }
    });

    // Record unblock / more-time request from blocked page or other UIs
    this.router.onMessageType("REQUEST_UNBLOCK", async (message) => {
      try {
        const anyMessage = message as any;
        const payload = (anyMessage && anyMessage.payload ? anyMessage.payload : anyMessage) as {
          url?: string;
          policyId?: string;
          blockReason?: string;
          childReason?: string;
          timeRemainingMinutes?: number;
          source?: string;
          timestamp?: number;
        };

        if (!payload.url) {
          return {
            success: false,
            error: "URL is required to request unblock",
          };
        }

        const now = payload.timestamp || Date.now();
        const existing =
          (await this.storage.get<UnblockRequest[]>("guardian:unblock_requests")) || [];

        const request: UnblockRequest = {
          id: `req-${now}-${Math.random().toString(36).substr(2, 9)}`,
          url: payload.url,
          policyId: payload.policyId,
          blockReason: payload.blockReason,
          childReason: payload.childReason,
          timeRemainingMinutes: payload.timeRemainingMinutes,
          source: payload.source || "unknown",
          requestedAt: now,
        };

        const updated = [...existing, request];
        await this.storage.set("guardian:unblock_requests", updated);

        return {
          success: true,
          data: request,
        };
      } catch (error) {
        return {
          success: false,
          error:
            error instanceof Error ? error.message : "Failed to record unblock request",
        };
      }
    });

    // Sync policies
    // - From backend (parent-controlled) when apiUrl/deviceId are provided
    // - Directly from UI when a policies array is provided (local edits)
    this.router.onMessageType("SYNC_POLICIES", async (message) => {
      try {
        const payload = message.payload as
          | { apiUrl?: string; deviceId?: string; policies?: unknown }
          | undefined;

        if (payload && Array.isArray((payload as any).policies)) {
          const policies = (payload as any).policies;
          await this.blocker.savePolicies(policies);
          return {
            success: true,
            data: this.blocker.getPolicies(),
          };
        }

        if (payload?.apiUrl && payload?.deviceId) {
          await this.blocker.syncPoliciesFromBackend(payload.apiUrl, payload.deviceId);
          return {
            success: true,
            data: this.blocker.getPolicies(),
          };
        }

        return {
          success: false,
          error: "Invalid SYNC_POLICIES payload",
        };
      } catch (error) {
        return {
          success: false,
          error:
            error instanceof Error ? error.message : "Failed to sync policies",
        };
      }
    });

    this.router.onMessageType("GET_USAGE_SUMMARY", async () => {
      try {
        // Use getAnalyticsSummary() directly - it reads from storage
        const summary = await this.getAnalyticsSummary();
        return {
          success: true,
          data: summary,
        };
      } catch (error) {
        return {
          success: false,
          error:
            error instanceof Error ? error.message : "Failed to get usage summary",
        };
      }
    });

    this.router.onMessageType("EVALUATE_POLICY", async (message) => {
      try {
        const payload = message.payload as { url?: string; category?: string } | undefined;
        const url = payload?.url;

        if (!url) {
          return {
            success: false,
            error: "URL is required",
          };
        }

        // Use WebsiteBlocker directly to evaluate policy
        const blockResult = await this.blocker.shouldBlock(url);

        // Convert to GuardianPolicyDecision format
        const decision: Record<string, unknown> = {
          decision: blockResult.blocked ? 'block' : 'allow',
          reason: blockResult.reason,
          policyId: blockResult.policyId,
        };

        return {
          success: true,
          data: decision,
        };
      } catch (error) {
        return {
          success: false,
          error:
            error instanceof Error ? error.message : "Failed to evaluate policy",
        };
      }
    });

    // Get per-domain quota status (daily time limits) for quota-based policies
    this.router.onMessageType("GET_QUOTA_STATUS", async (message): Promise<{
      success: boolean;
      data?: {
        hasQuota: boolean;
        limitMinutes?: number;
        usedMinutes?: number;
        remainingMinutes?: number;
        policyId?: string;
      };
      error?: string;
    }> => {
      try {
        const payload = message.payload as { url?: string; domain?: string } | undefined;
        const url = payload?.url;
        let domain = payload?.domain;

        if (!domain && url) {
          try {
            const urlObj = new URL(url);
            domain = urlObj.hostname.replace(/^www\./, "");
          } catch {
            // ignore URL parse errors
          }
        }

        if (!domain || !this.pipeline) {
          return {
            success: true,
            data: { hasQuota: false },
          };
        }

        const today = new Date();
        const daily = await this.pipeline.storageSink.getDailyUsage(today);

        const usedMs = daily?.domains?.[domain]?.time ?? 0;
        const usedMinutes = Math.floor(usedMs / 60000);

        // Determine applicable quota policy (smallest dailyLimitMinutes)
        const policies = this.blocker.getPolicies();
        const usageCategory = daily?.domains?.[domain]?.category;

        let quotaPolicyId: string | undefined;
        let limitMinutes: number | undefined;

        for (const policy of policies) {
          if (!policy.enabled) continue;

          const hasDailyLimit =
            typeof policy.dailyLimitMinutes === "number" &&
            policy.dailyLimitMinutes > 0;

          // Quota-based policies are those with a daily limit and no time windows
          if (!hasDailyLimit || (policy.timeWindows && policy.timeWindows.length > 0)) {
            continue;
          }

          const matchesDomain = policy.blockedDomains.some((pattern) => {
            if (pattern === domain) return true;
            if (pattern.startsWith("*.")) {
              const baseDomain = pattern.substring(2);
              return domain === baseDomain || domain.endsWith("." + baseDomain);
            }
            return domain.endsWith("." + pattern);
          });

          const matchesCategory = usageCategory
            ? policy.blockedCategories.includes(usageCategory as any)
            : false;

          if (!matchesDomain && !matchesCategory) continue;

          const limit = policy.dailyLimitMinutes as number;
          if (!limitMinutes || limit < limitMinutes) {
            limitMinutes = limit;
            quotaPolicyId = policy.id;
          }
        }

        if (!limitMinutes || !quotaPolicyId) {
          return {
            success: true,
            data: { hasQuota: false },
          };
        }

        const remainingMinutes = Math.max(0, limitMinutes - usedMinutes);

        return {
          success: true,
          data: {
            hasQuota: true,
            limitMinutes,
            usedMinutes,
            remainingMinutes,
            policyId: quotaPolicyId,
          },
        };
      } catch (error) {
        return {
          success: false,
          error:
            error instanceof Error ? error.message : "Failed to get quota status",
        };
      }
    });

    // Legacy PAGE_USAGE_TRACKED handler removed; usage now flows via the event pipeline.

    // =========================================================================
    // Minimal UX Message Handlers
    // =========================================================================

    // Get today's usage for a specific domain (for Popup)
    this.router.onMessageType("GET_DOMAIN_USAGE_TODAY", async (message) => {
      try {
        const payload = message.payload as { domain?: string } | undefined;
        const domain = payload?.domain;

        if (!domain) {
          return {
            success: false,
            error: "Domain is required",
          };
        }

        const today = new Date();
        const dailyUsage = this.pipeline
          ? await this.pipeline.storageSink.getDailyUsage(today)
          : null;

        const domainData = dailyUsage?.domains?.[domain] as DomainUsage | undefined;

        return {
          success: true,
          data: {
            domain,
            timeMinutes: domainData ? Math.round(domainData.time / 60000) : 0,
            visits: domainData?.visits ?? 0,
          },
        };
      } catch (error) {
        return {
          success: false,
          error: error instanceof Error ? error.message : "Failed to get domain usage",
        };
      }
    });

    // Get blocked events (for Dashboard BlockedPagesLog)
    this.router.onMessageType("GET_BLOCKED_EVENTS", async (message) => {
      try {
        const payload = message.payload as {
          range?: "24h" | "7d" | "all";
          domain?: string;
          limit?: number;
        } | undefined;

        const range = payload?.range ?? "7d";
        const filterDomain = payload?.domain;
        const limit = payload?.limit ?? 100;

        // Get block events from WebsiteBlocker
        const allBlockEvents = await this.blocker.getBlockEvents(500);

        // Filter by time range
        const now = Date.now();
        const cutoffs: Record<string, number> = {
          "24h": now - 24 * 60 * 60 * 1000,
          "7d": now - 7 * 24 * 60 * 60 * 1000,
          all: 0,
        };
        const cutoff = cutoffs[range] ?? cutoffs["7d"];

        let filtered = allBlockEvents.filter((e: any) => e.timestamp >= cutoff);

        // Filter by domain if specified
        if (filterDomain) {
          filtered = filtered.filter((e: any) => e.domain === filterDomain);
        }

        // Apply limit and map to BlockedEvent shape
        const events = filtered.slice(0, limit).map((e: any) => ({
          id: e.id || `block-${e.timestamp}-${Math.random().toString(36).substr(2, 9)}`,
          timestamp: e.timestamp,
          domain: e.domain || "unknown",
          url: e.url || "",
          title: e.title,
          reason: e.reason || "Blocked by policy",
          policyId: e.policyId,
          category: e.category,
        }));

        return {
          success: true,
          data: events,
        };
      } catch (error) {
        return {
          success: false,
          error: error instanceof Error ? error.message : "Failed to get blocked events",
        };
      }
    });

    // Get last blocked event for a specific URL (for Popup)
    this.router.onMessageType("GET_LAST_BLOCKED_EVENT", async (message) => {
      try {
        const payload = message.payload as { url?: string } | undefined;
        const url = payload?.url;

        if (!url) {
          return {
            success: true,
            data: null,
          };
        }

        // Extract domain from URL
        let domain: string;
        try {
          const urlObj = new URL(url);
          domain = urlObj.hostname.replace(/^www\./, "");
        } catch {
          return {
            success: true,
            data: null,
          };
        }

        // Get recent block events for this domain
        const blockEvents = await this.blocker.getBlockEvents(50);
        const matching = blockEvents
          .filter((e: any) => e.domain === domain || e.url === url)
          .sort((a: any, b: any) => b.timestamp - a.timestamp);

        if (matching.length === 0) {
          return {
            success: true,
            data: null,
          };
        }

        const last = matching[0] as any;
        return {
          success: true,
          data: {
            timestamp: last.timestamp,
            reason: last.reason || "Blocked by policy",
            policyId: last.policyId,
          },
        };
      } catch (error) {
        return {
          success: false,
          error: error instanceof Error ? error.message : "Failed to get last blocked event",
        };
      }
    });

    // Update domain policy (block/allow/default/temp-allow)
    this.router.onMessageType("UPDATE_DOMAIN_POLICY", async (message) => {
      try {
        const payload = message.payload as {
          domain?: string;
          action?: "block" | "allow" | "default" | "temp-allow";
          durationMinutes?: number;
        } | undefined;

        const domain = payload?.domain;
        const action = payload?.action;

        if (!domain || !action) {
          return {
            success: false,
            error: "Domain and action are required",
          };
        }

        // Get current policies
        const policies = this.blocker.getPolicies();

        // Find or create a "Quick Actions" policy for browser-level overrides
        let quickPolicy = policies.find((p) => p.id === "quick-actions");
        const nowTs = Date.now();

        if (!quickPolicy) {
          quickPolicy = {
            id: "quick-actions",
            name: "Quick Actions",
            enabled: true,
            blockedDomains: [],
            blockedCategories: [],
            allowedDomains: [],
            timeWindows: [],
            createdAt: nowTs,
            updatedAt: nowTs,
          };
          policies.push(quickPolicy);
        }

        // Update timestamp
        quickPolicy.updatedAt = nowTs;

        // Apply the action
        switch (action) {
          case "block":
            // Add to blocked, remove from allowed
            if (!quickPolicy.blockedDomains.includes(domain)) {
              quickPolicy.blockedDomains.push(domain);
            }
            quickPolicy.allowedDomains = quickPolicy.allowedDomains.filter(
              (d) => d !== domain
            );
            break;

          case "allow":
            // Add to allowed, remove from blocked
            if (!quickPolicy.allowedDomains.includes(domain)) {
              quickPolicy.allowedDomains.push(domain);
            }
            quickPolicy.blockedDomains = quickPolicy.blockedDomains.filter(
              (d) => d !== domain
            );
            break;

          case "default":
            // Remove from both lists
            quickPolicy.blockedDomains = quickPolicy.blockedDomains.filter(
              (d) => d !== domain
            );
            quickPolicy.allowedDomains = quickPolicy.allowedDomains.filter(
              (d) => d !== domain
            );
            break;

          case "temp-allow": {
            // Add temporary allow with time window
            const durationMinutes = payload?.durationMinutes ?? 30;
            const now = new Date();
            const endTime = new Date(now.getTime() + durationMinutes * 60 * 1000);

            // Add to allowed domains
            if (!quickPolicy.allowedDomains.includes(domain)) {
              quickPolicy.allowedDomains.push(domain);
            }
            quickPolicy.blockedDomains = quickPolicy.blockedDomains.filter(
              (d) => d !== domain
            );

            // Store temp-allow expiry for later cleanup
            const tempAllows =
              (await this.storage.get<Record<string, number>>(
                "guardian:temp_allows"
              )) || {};
            tempAllows[domain] = endTime.getTime();
            await this.storage.set("guardian:temp_allows", tempAllows);
            break;
          }
        }

        // Save updated policies
        await this.blocker.savePolicies(policies);

        return {
          success: true,
          data: {
            domain,
            action,
            policies: this.blocker.getPolicies(),
          },
        };
      } catch (error) {
        return {
          success: false,
          error: error instanceof Error ? error.message : "Failed to update domain policy",
        };
      }
    });

    // Get domain policies list (for Settings)
    this.router.onMessageType("GET_DOMAIN_POLICIES", async () => {
      try {
        const policies = this.blocker.getPolicies();

        // Extract domain-level policies from all policies
        const domainPolicies: Array<{
          domain: string;
          status: "blocked" | "allowed" | "default";
        }> = [];

        const seenDomains = new Set<string>();

        for (const policy of policies) {
          if (!policy.enabled) continue;

          // Blocked domains
          for (const domain of policy.blockedDomains) {
            if (!seenDomains.has(domain)) {
              seenDomains.add(domain);
              domainPolicies.push({ domain, status: "blocked" });
            }
          }

          // Allowed domains (exceptions)
          for (const domain of policy.allowedDomains) {
            if (!seenDomains.has(domain)) {
              seenDomains.add(domain);
              domainPolicies.push({ domain, status: "allowed" });
            }
          }
        }

        return {
          success: true,
          data: domainPolicies,
        };
      } catch (error) {
        return {
          success: false,
          error: error instanceof Error ? error.message : "Failed to get domain policies",
        };
      }
    });

    // Get minimal settings (for Settings page)
    this.router.onMessageType("GET_MINIMAL_SETTINGS", async () => {
      try {
        const stored = await this.storage.get<{
          monitoringEnabled?: boolean;
          dataRetentionDays?: number;
          alertsEnabled?: boolean;
        }>("guardian:minimal_settings");

        const domainPoliciesResult = await this.router.sendToBackground({
          type: "GET_DOMAIN_POLICIES",
          payload: {},
        });

        return {
          success: true,
          data: {
            monitoringEnabled: stored?.monitoringEnabled ?? this.config.metricsEnabled,
            dataRetentionDays: stored?.dataRetentionDays ?? this.config.retentionDays,
            alertsEnabled: stored?.alertsEnabled ?? true,
            domainPolicies: domainPoliciesResult?.data ?? [],
          },
        };
      } catch (error) {
        return {
          success: false,
          error: error instanceof Error ? error.message : "Failed to get minimal settings",
        };
      }
    });

    // Save minimal settings
    this.router.onMessageType("SAVE_MINIMAL_SETTINGS", async (message) => {
      try {
        const payload = message.payload as {
          monitoringEnabled?: boolean;
          dataRetentionDays?: number;
          alertsEnabled?: boolean;
          domainPolicies?: Array<{ domain: string; status: "blocked" | "allowed" | "default" }>;
        } | undefined;

        if (!payload) {
          return {
            success: false,
            error: "Settings payload is required",
          };
        }

        // Save core settings
        await this.storage.set("guardian:minimal_settings", {
          monitoringEnabled: payload.monitoringEnabled,
          dataRetentionDays: payload.dataRetentionDays,
          alertsEnabled: payload.alertsEnabled,
        });

        // Apply monitoring state
        if (payload.monitoringEnabled !== undefined) {
          this.updateState({ metricsCollecting: payload.monitoringEnabled });
        }

        // Apply retention days to config
        if (payload.dataRetentionDays !== undefined) {
          await this.updateConfig({ retentionDays: payload.dataRetentionDays });
        }

        // Apply domain policies if provided
        if (payload.domainPolicies) {
          const policies = this.blocker.getPolicies();
          let quickPolicy = policies.find((p) => p.id === "quick-actions");
          const nowTs = Date.now();

          if (!quickPolicy) {
            quickPolicy = {
              id: "quick-actions",
              name: "Quick Actions",
              enabled: true,
              blockedDomains: [],
              blockedCategories: [],
              allowedDomains: [],
              timeWindows: [],
              createdAt: nowTs,
              updatedAt: nowTs,
            };
            policies.push(quickPolicy);
          }

          // Update timestamp and reset lists
          quickPolicy.updatedAt = nowTs;
          quickPolicy.blockedDomains = [];
          quickPolicy.allowedDomains = [];

          for (const dp of payload.domainPolicies) {
            if (dp.status === "blocked") {
              quickPolicy.blockedDomains.push(dp.domain);
            } else if (dp.status === "allowed") {
              quickPolicy.allowedDomains.push(dp.domain);
            }
            // "default" means not in either list
          }

          await this.blocker.savePolicies(policies);
        }

        return {
          success: true,
        };
      } catch (error) {
        return {
          success: false,
          error: error instanceof Error ? error.message : "Failed to save minimal settings",
        };
      }
    });
  }

  /**
   * Start metrics collection
   */
  /**
   * Start event capture
   */
  private async startEventCapture(): Promise<void> {
    this.events.onEvent(async (event) => {
      try {
        // Store captured event
        const existing =
          (await this.storage.get<BrowserEvent[]>("guardian-events")) || [];
        const updated = [...existing, event];

        // Keep only recent events
        const cutoff =
          Date.now() - this.config.retentionDays * 24 * 60 * 60 * 1000;
        const filtered = updated.filter((e) => e.timestamp > cutoff);

        await this.storage.set("guardian-events", filtered);

        // Update state
        this.updateState({
          totalEventsCollected: this.state.totalEventsCollected + 1,
        });
      } catch (error) {
        this.logError("Failed to store event", error);
      }
    });

    this.events.captureAll();
    this.updateState({ eventsCapturing: true });
    this.log("Event capture started");
  }

  /**
   * Cleanup old data beyond retention period
   */
  private async cleanupOldData(): Promise<void> {
    try {
      const cutoff =
        Date.now() - this.config.retentionDays * 24 * 60 * 60 * 1000;

      // Cleanup events captured via UnifiedBrowserEventCapture
      const events =
        (await this.storage.get<BrowserEvent[]>("guardian-events")) || [];
      const filteredEvents = events.filter((e) => e.timestamp > cutoff);
      if (filteredEvents.length !== events.length) {
        await this.storage.set("guardian-events", filteredEvents);
        this.log("Cleaned up old events", {
          removed: events.length - filteredEvents.length,
        });
      }
    } catch (error) {
      this.logError("Failed to cleanup old data", error);
    }
  }

  /**
   * Get analytics summary - web usage patterns and trends
   */
  private async getAnalyticsSummary() {
    try {
      const now = Date.now();
      const oneDayAgo = now - 24 * 60 * 60 * 1000;
      const oneWeekAgo = now - 7 * 24 * 60 * 60 * 1000;

      console.debug('[getAnalyticsSummary] Starting...');

      // Events are still captured via UnifiedBrowserEventCapture
      const events =
        (await this.storage.get<BrowserEvent[]>("guardian-events")) || [];

      console.debug('[getAnalyticsSummary] Events retrieved:', events.length);

      // Usage is now derived from the event pipeline's LocalStorageSink
      let dailyUsage: DailyUsage[] = [];
      let policyEvents: PolicyEvaluatedEvent[] = [];

      if (this.pipeline) {
        try {
          const endDate = new Date(now);
          const startDate = new Date(oneWeekAgo);
          dailyUsage = await this.pipeline.storageSink.getUsageRange(
            startDate,
            endDate,
          );
          policyEvents = await this.pipeline.storageSink.getRawEvents();
          console.debug('[getAnalyticsSummary] Pipeline data retrieved:', {
            dailyUsageLength: dailyUsage.length,
            policyEventsLength: policyEvents.length,
          });
        } catch (error) {
          console.error('[getAnalyticsSummary] Error retrieving pipeline data:', error);
          // Continue with empty arrays if pipeline data retrieval fails
        }
      } else {
        console.warn('[getAnalyticsSummary] Pipeline not initialized');
      }

    // Aggregate domain statistics from daily usage
    const domainStats: Record<string, WebUsageStats> = {};

    for (const day of dailyUsage) {
      for (const [domain, rawUsage] of Object.entries(day.domains)) {
        const usage = rawUsage as DomainUsage;

        if (!domainStats[domain]) {
          domainStats[domain] = {
            domain,
            urls: [],
            visitCount: 0,
            blockedCount: 0,
            temporarilyBlockedCount: 0,
            totalTimeSpent: 0,
            lastVisited: 0,
            averageSessionDuration: 0,
            status: "allowed",
          };
        }

        const stats = domainStats[domain];
        stats.visitCount += usage.visits;
        stats.totalTimeSpent += usage.time;
        if (usage.lastVisit) {
          stats.lastVisited = Math.max(stats.lastVisited, usage.lastVisit);
        }
      }
    }

    // Enrich domain statistics with blocking information from policy-evaluated events
    for (const event of policyEvents) {
      const anyEvent = event as any;
      const domain: string | undefined = anyEvent.domain;
      if (!domain) continue;

      if (!domainStats[domain]) {
        domainStats[domain] = {
          domain,
          urls: [],
          visitCount: 0,
          blockedCount: 0,
          temporarilyBlockedCount: 0,
          totalTimeSpent: 0,
          lastVisited: 0,
          averageSessionDuration: 0,
          status: "allowed",
        };
      }

      const stats = domainStats[domain];

      if (event.policyDecision === "block") {
        stats.blockedCount += 1;
        stats.status = "blocked";
      } else if (event.policyDecision === "warn") {
        stats.temporarilyBlockedCount += 1;
        if (stats.status !== "blocked") {
          stats.status = "temporarily_blocked";
        }
      }

      if (typeof anyEvent.duration === "number") {
        stats.totalTimeSpent += anyEvent.duration as number;
      }
      if (typeof anyEvent.timestamp === "number") {
        stats.lastVisited = Math.max(
          stats.lastVisited,
          anyEvent.timestamp as number,
        );
      }
    }

    // Calculate averages
    for (const stats of Object.values(domainStats)) {
      stats.averageSessionDuration =
        stats.visitCount > 0
          ? stats.totalTimeSpent / stats.visitCount
          : 0;
    }

    // Usage by time period from daily aggregates
    const todayKey = new Date(now).toISOString().split("T")[0];
    const todayUsage = dailyUsage.find((d) => d.date === todayKey);

    const last7dVisits = dailyUsage.reduce((sum, day) => {
      const dayVisits = Object.values(day.domains).reduce((inner, raw) => {
        const u = raw as DomainUsage;
        return inner + u.visits;
      }, 0);
      return sum + dayVisits;
    }, 0);

    const last7dTime = dailyUsage.reduce(
      (sum, day) => sum + day.totalTime,
      0,
    );

    const webUsage = {
      last24h: todayUsage
        ? Object.values(todayUsage.domains).reduce((sum, raw) => {
          const u = raw as DomainUsage;
          return sum + u.visits;
        }, 0)
        : 0,
      last7d: last7dVisits,
      allTime: last7dVisits,
    };

    const timeSpent = {
      last24h: todayUsage ? todayUsage.totalTime : 0,
      last7d: last7dTime,
      allTime: last7dTime,
    };

    // Event statistics from captured browser events
    const eventCounts = events.reduce((acc, e) => {
      acc[e.type] = (acc[e.type] || 0) + 1;
      return acc;
    }, {} as Record<string, number>);

    const recentEvents = events.filter((e) => e.timestamp > oneDayAgo);
    const weeklyEvents = events.filter((e) => e.timestamp > oneWeekAgo);

    const eventsByPeriod = {
      last24h: recentEvents.length,
      last7d: weeklyEvents.length,
      allTime: events.length,
    };

    // Top visited domains
    const topDomains = Object.values(domainStats)
      .sort((a, b) => b.visitCount - a.visitCount)
      .slice(0, 10);

    // Recent usage view built from policy-evaluated events
    const recentUsage = policyEvents
      .filter((e) => (e as any).url)
      .slice(-50)
      .map((e) => {
        const anyEvent = e as any;
        const duration: number =
          typeof anyEvent.duration === "number" ? anyEvent.duration : 0;
        const timestamp: number = anyEvent.timestamp ?? now;
        const entryTime = timestamp - duration;
        const record: WebUsageData = {
          timestamp,
          url: anyEvent.url || "",
          domain: anyEvent.domain || "unknown",
          title: anyEvent.title || anyEvent.domain || "Unknown",
          sessionDuration: duration,
          entryTime,
          status: "allowed",
        };
        return record;
      })
      .sort((a, b) => b.timestamp - a.timestamp);

    const totalUsageRecords = webUsage.allTime;

    // Transform topDomains to DomainUsageSummary format for Dashboard
    const domains = topDomains.map((d) => ({
      domain: d.domain,
      timeLast7DaysMinutes: Math.round(d.totalTimeSpent / 60000),
      visitsLast7Days: d.visitCount,
      blockedAttempts: d.blockedCount,
      contentRisk: 'none' as const,
      status: d.status,
      lastVisited: d.lastVisited,
    }));

    // Transform recentUsage to PageUsageSummary format
    const pages = recentUsage.slice(0, 50).map((u) => ({
      domain: u.domain,
      url: u.url,
      title: u.title,
      timeLast7DaysMinutes: Math.round(u.sessionDuration / 60000),
      lastVisited: u.timestamp,
      blockedAttempts: 0,
      hasChatsOrPosts: false,
      contentRisk: 'none' as const,
    }));

    // Transform policy events to BlockedEvent format
    const blockedEvents = policyEvents
      .filter((e) => e.policyDecision === 'block')
      .slice(-100)
      .map((e, idx) => {
        const anyEvent = e as any;
        return {
          id: `blocked-${idx}-${anyEvent.timestamp ?? now}`,
          timestamp: anyEvent.timestamp ?? now,
          domain: anyEvent.domain || 'unknown',
          url: anyEvent.url || '',
          title: anyEvent.title || anyEvent.domain || 'Unknown',
          reason: e.blockReason || 'Policy blocked',
          policyId: e.matchedPolicyId,
          category: anyEvent.category,
        };
      });

    // Generate content summary by domain (placeholder - no real content analysis yet)
    const contentSummary = topDomains.slice(0, 10).map((d) => ({
      domain: d.domain,
      messagesAnalyzed: 0,
      flaggedByType: {} as Record<string, number>,
      risk: 'none' as const,
      lastFlaggedTime: undefined,
    }));

    return {
      // New format expected by Dashboard
      domains,
      pages,
      blockedEvents,
      contentSummary,
      // Legacy fields for backward compatibility
      webUsage,
      timeSpent,
      topDomains,
      domainStats,
      totalUsageRecords,
      totalEvents: events.length,
      eventsByPeriod,
      eventCounts,
      lastUpdated: now,
      recentUsage,
      state: this.getState(),
    };
    } catch (error) {
      console.error('[getAnalyticsSummary] Fatal error:', error);
      console.error('[getAnalyticsSummary] Error stack:', (error as any)?.stack);
      throw error;
    }
  }

  /**
   * DEPRECATED: Plugin-based recording is no longer used.
   * Web usage is now tracked via setupTabMonitoring()
   * 
   * @deprecated Use saveTabUsage() instead
   */
  // private async recordUsageWithPlugin(usage: WebUsageData): Promise<void> {
  //   // Method deprecated - web usage now tracked via tab monitoring
  // }

  /**
   * DEPRECATED: Plugin-based summary collection is no longer used.
   * Use getAnalyticsSummary() instead.
   * 
   * @deprecated Use getAnalyticsSummary() directly
   */
  // private async getUsageSummaryViaPlugin(): Promise<Record<string, unknown> | null> {
  //   // Method deprecated - use direct storage access instead
  // }

  /**
   * DEPRECATED: Plugin-based policy evaluation is no longer used.
   * Use WebsiteBlocker.shouldBlock() instead.
   * 
   * @deprecated Use blocker.shouldBlock() directly
   */
  // private async evaluatePolicyWithPlugin(
  //   url: string,
  //   category?: string
  // ): Promise<GuardianPolicyDecision | null> {
  //   // Method deprecated - use blocker directly instead
  // }

  /**
   * Initialize backend sync components (TelemetrySink, CommandSyncSource)
   */
  private async initializeBackendSync(
    apiBaseUrl: string,
    deviceId: string,
    childId?: string
  ): Promise<void> {
    this.log("Initializing backend sync", { apiBaseUrl, deviceId, childId });

    const getAuthToken = (): string | null => {
      try {
        const ls = (globalThis as any).localStorage as
          | { getItem: (key: string) => string | null }
          | undefined;
        if (!ls) return null;
        return ls.getItem("guardian_token") || ls.getItem("token");
      } catch {
        return null;
      }
    };

    // Initialize TelemetrySink if not already initialized
    if (!this.telemetrySink) {
      this.telemetrySink = new TelemetrySink({
        apiBaseUrl,
        deviceId,
        childId,
        getAuthToken,
      });
      await this.telemetrySink.initialize();
      this.log("TelemetrySink initialized");
    }

    // Initialize CommandSyncSource if not already initialized
    if (!this.commandSyncSource) {
      this.commandSyncSource = new CommandSyncSource({
        apiBaseUrl,
        deviceId,
        getAuthToken,
      });

      this.commandSyncSource.onEvent(async (event) => {
        if (!this.commandExecutionSink) {
          return;
        }
        const results = await this.commandExecutionSink.executeCommands(
          event.snapshot.commands.items
        );
        // Send command results async - don't block or fail on errors
        this.sendCommandResultsAsync(results);
      });

      await this.commandSyncSource.start();
      this.log("CommandSyncSource started");
    }

    // Initialize CommandExecutionSink if not already initialized
    if (!this.commandExecutionSink) {
      this.commandExecutionSink = new CommandExecutionSink({
        apiBaseUrl,
        deviceId,
        getAuthToken,
        onPolicyUpdate: async () => {
          // Fire and forget - don't let policy sync failures affect command execution
          this.safeSyncPoliciesAsync(apiBaseUrl, deviceId);
        },
        onImmediateAction: async (command) => {
          // Handle immediate actions (lock, alarm, etc.)
          this.log("Immediate action received", { action: command.action });
        },
        onSessionRequest: async (command) => {
          // Handle session requests (extend time, temporary unblock)
          this.log("Session request received", { action: command.action });
        },
        onDataRequest: async (command) => {
          // Backend is requesting data sync - trigger async sync
          this.log("Data request received from backend", { 
            action: command.action, 
            params: command.params 
          });
          this.handleDataRequestAsync(command);
        },
        onSystemCommand: async (command) => {
          // Handle system commands (force_sync, etc.)
          this.log("System command received", { action: command.action });
          if (command.action === 'force_sync') {
            this.safeSyncPoliciesAsync(apiBaseUrl, deviceId);
          }
        },
      });
      await this.commandExecutionSink.initialize();
      this.log("CommandExecutionSink initialized");
    }
  }

  /**
   * Send command results to backend asynchronously (fire-and-forget)
   * Errors are logged but never thrown to prevent disrupting extension
   */
  private sendCommandResultsAsync(results: Array<{ command_id: string; status: string; error_message?: string }>): void {
    // Execute async without awaiting - fire and forget
    (async () => {
      if (!this.telemetrySink) return;
      
      for (const result of results) {
        try {
          await this.telemetrySink.sendCommandEvent(
            result.command_id,
            result.status as 'processed' | 'failed' | 'expired' | 'unsupported',
            result.error_message
          );
        } catch (error) {
          // Log but never throw - backend communication must not disrupt extension
          this.log("Failed to send command result to backend (non-fatal)", { 
            commandId: result.command_id, 
            error: error instanceof Error ? error.message : String(error)
          });
        }
      }
    })().catch((error) => {
      this.log("Async command result send failed (non-fatal)", { error: String(error) });
    });
  }

  /**
   * Sync policies from backend asynchronously (fire-and-forget)
   * Errors are logged but never thrown to prevent disrupting extension
   */
  private safeSyncPoliciesAsync(apiBaseUrl: string, deviceId: string): void {
    (async () => {
      try {
        await this.blocker.syncPoliciesFromBackend(apiBaseUrl, deviceId);
        this.log("Policies synced from backend");
      } catch (error) {
        // Log but never throw - backend communication must not disrupt extension
        this.log("Failed to sync policies from backend (non-fatal)", {
          error: error instanceof Error ? error.message : String(error)
        });
      }
    })().catch((error) => {
      this.log("Async policy sync failed (non-fatal)", { error: String(error) });
    });
  }

  /**
   * Handle data request commands from backend asynchronously (fire-and-forget)
   * Backend can request data sync for recovery, audit, or reconciliation purposes
   */
  private handleDataRequestAsync(command: { action: string; params?: Record<string, unknown> }): void {
    (async () => {
      try {
        const action = command.action;
        const params = command.params || {};

        if (action === 'request_data_sync') {
          // Backend wants us to sync our local data
          const sinceTimestamp = params.since_timestamp as string | undefined;
          const dataTypes = params.data_types as string[] | undefined;
          
          this.log("Processing backend data sync request", { sinceTimestamp, dataTypes });
          
          // Trigger a full sync
          const result = await this.syncToBackendWithOptions({
            sinceTimestamp,
            dataTypes,
            reason: params.reason as string || 'backend_request',
          });
          
          this.log("Backend data sync request completed", { 
            eventsSent: result.eventsSent, 
            success: result.success 
          });
          
        } else if (action === 'request_full_snapshot') {
          // Backend wants a complete analytics snapshot
          this.log("Processing backend full snapshot request");
          
          const result = await this.syncToBackendWithOptions({
            fullSnapshot: true,
            includeHistory: params.include_history as boolean || false,
            reason: params.reason as string || 'backend_snapshot_request',
          });
          
          this.log("Backend full snapshot request completed", {
            eventsSent: result.eventsSent,
            success: result.success
          });
        }
      } catch (error) {
        // Log but never throw - backend requests must not disrupt extension
        this.log("Failed to handle backend data request (non-fatal)", {
          action: command.action,
          error: error instanceof Error ? error.message : String(error)
        });
      }
    })().catch((error) => {
      this.log("Async data request handling failed (non-fatal)", { error: String(error) });
    });
  }

  /**
   * Sync current analytics data to backend
   */
  private async syncToBackend(): Promise<{
    success: boolean;
    eventsSent: number;
    errors: string[];
  }> {
    return this.syncToBackendWithOptions({});
  }

  /**
   * Sync analytics data to backend with options
   * Used for both manual sync and backend-requested sync
   */
  private async syncToBackendWithOptions(options: {
    sinceTimestamp?: string;
    dataTypes?: string[];
    fullSnapshot?: boolean;
    includeHistory?: boolean;
    reason?: string;
  }): Promise<{
    success: boolean;
    eventsSent: number;
    errors: string[];
  }> {
    const errors: string[] = [];
    let eventsSent = 0;

    if (!this.telemetrySink) {
      return {
        success: false,
        eventsSent: 0,
        errors: ["TelemetrySink not initialized. Configure backend sync first."],
      };
    }

    try {
      // Get current analytics
      const analytics = await this.getAnalyticsSummary();
      const reason = options.reason || 'manual_sync';
      const dataTypes = options.dataTypes || ['usage', 'blocks', 'pages'];

      // Send sync start event (helps backend track data request responses)
      try {
        await this.telemetrySink.sendEvent({
          kind: 'system',
          subtype: 'sync_started',
          context: {
            reason,
            requested_data_types: dataTypes,
            since_timestamp: options.sinceTimestamp,
            full_snapshot: options.fullSnapshot || false,
          },
        });
        eventsSent++;
      } catch (error) {
        // Non-fatal - continue with sync
        this.log("Failed to send sync_started event (non-fatal)", { error: String(error) });
      }

      // Send usage events for each domain
      if (dataTypes.includes('usage') || dataTypes.includes('domains')) {
        for (const domain of analytics.domains || []) {
          try {
            await this.telemetrySink.sendEvent({
              kind: 'usage',
              subtype: 'domain_usage_summary',
              context: {
                domain: domain.domain,
                time_spent_minutes: domain.timeLast7DaysMinutes,
                visits: domain.visitsLast7Days,
                blocked_attempts: domain.blockedAttempts,
                content_risk: domain.contentRisk,
                status: domain.status,
                sync_reason: reason,
              },
            });
            eventsSent++;
          } catch (error) {
            errors.push(`Failed to send usage for ${domain.domain}: ${error}`);
          }
        }
      }

      // Send page visit events
      if (dataTypes.includes('pages') || options.fullSnapshot) {
        for (const page of analytics.pages || []) {
          try {
            await this.telemetrySink.sendEvent({
              kind: 'usage',
              subtype: 'page_visit',
              context: {
                url: page.url,
                domain: page.domain,
                title: page.title,
                time_spent_seconds: page.timeSpentSeconds,
                category: page.category,
                last_visit: page.lastVisit,
                sync_reason: reason,
              },
            });
            eventsSent++;
          } catch (error) {
            errors.push(`Failed to send page visit for ${page.url}: ${error}`);
          }
        }
      }

      // Send blocked events
      if (dataTypes.includes('blocks') || dataTypes.includes('blockedEvents')) {
        for (const blocked of analytics.blockedEvents || []) {
          try {
            await this.telemetrySink.sendEvent({
              kind: 'block',
              subtype: 'page_blocked',
              context: {
                domain: blocked.domain,
                url: blocked.url,
                reason: blocked.reason,
                policy_id: blocked.policyId,
                category: blocked.category,
                sync_reason: reason,
              },
            });
            eventsSent++;
          } catch (error) {
            errors.push(`Failed to send blocked event: ${error}`);
          }
        }
      }

      // Send content summary if full snapshot requested
      if (options.fullSnapshot && analytics.contentSummary) {
        for (const content of analytics.contentSummary) {
          try {
            await this.telemetrySink.sendEvent({
              kind: 'usage',
              subtype: 'content_category_summary',
              context: {
                category: content.category,
                time_minutes: content.timeMinutes,
                percentage: content.percentage,
                trend: content.trend,
                sync_reason: reason,
              },
            });
            eventsSent++;
          } catch (error) {
            errors.push(`Failed to send content summary for ${content.category}: ${error}`);
          }
        }
      }

      // Send sync completion event
      try {
        await this.telemetrySink.sendEvent({
          kind: 'system',
          subtype: 'sync_completed',
          context: {
            reason,
            events_sent: eventsSent,
            errors_count: errors.length,
            full_snapshot: options.fullSnapshot || false,
          },
        });
        eventsSent++;
      } catch (error) {
        // Non-fatal
        this.log("Failed to send sync_completed event (non-fatal)", { error: String(error) });
      }

      // Flush the telemetry buffer - wrap in try-catch to ensure we don't throw
      try {
        await this.telemetrySink.flush();
      } catch (flushError) {
        errors.push(`Flush failed: ${flushError}`);
        this.log("Telemetry flush failed (non-fatal)", { error: String(flushError) });
      }

      this.log("Synced to backend", { eventsSent, errors: errors.length, reason });

      return {
        success: errors.length === 0,
        eventsSent,
        errors,
      };
    } catch (error) {
      const errorMsg = error instanceof Error ? error.message : String(error);
      errors.push(`Sync failed: ${errorMsg}`);
      this.log("Backend sync failed", { error: errorMsg });
      return {
        success: false,
        eventsSent,
        errors,
      };
    }
  }
}
