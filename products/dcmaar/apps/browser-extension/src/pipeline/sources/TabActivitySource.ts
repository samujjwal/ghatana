/**
 * @fileoverview Tab Activity Source
 * 
 * Event source that captures tab activity from Chrome/Firefox tabs API.
 * Replaces direct chrome.tabs usage in GuardianController with a
 * composable, testable source component.
 * 
 * @module pipeline/sources/TabActivitySource
 */

import { BaseEventSource } from '@ghatana/dcmaar-browser-extension-core';
import type { TabActivityEvent } from '../types';

/**
 * Configuration for TabActivitySource
 */
export interface TabActivitySourceConfig {
    /** Minimum session duration to emit (ms) */
    minSessionDuration?: number;
    /** URLs to ignore (e.g., chrome://, chrome-extension://) */
    ignoredUrlPatterns?: RegExp[];
    /** Whether to track tab switches */
    trackSwitches?: boolean;
    /** Whether to track navigations */
    trackNavigations?: boolean;
    /** Whether to track tab closes */
    trackCloses?: boolean;
}

const DEFAULT_CONFIG: Required<TabActivitySourceConfig> = {
    minSessionDuration: 1000, // 1 second minimum
    ignoredUrlPatterns: [
        /^chrome:\/\//,
        /^chrome-extension:\/\//,
        /^moz-extension:\/\//,
        /^about:/,
        /^edge:\/\//,
    ],
    trackSwitches: true,
    trackNavigations: true,
    trackCloses: true,
};

/**
 * TabActivitySource
 * 
 * Captures tab activity events and emits them into the pipeline.
 * Handles:
 * - Tab navigation (URL changes)
 * - Tab activation (switching between tabs)
 * - Tab closure (with session duration)
 * 
 * @example
 * ```typescript
 * const source = new TabActivitySource({ minSessionDuration: 2000 });
 * source.onEvent((event) => console.log('Tab activity:', event));
 * await source.start();
 * ```
 */
export class TabActivitySource extends BaseEventSource<TabActivityEvent> {
    readonly name = 'tab-activity';

    private readonly config: Required<TabActivitySourceConfig>;
    private activeTabUrl: Map<number, string> = new Map();
    private activeTabTitle: Map<number, string> = new Map();
    private tabStartTime: Map<number, number> = new Map();
    private currentActiveTabId: number | null = null;
    private lastNavigateTime: Map<number, number> = new Map(); // Track last navigate event per tab to avoid duplicates
    private readonly DUPLICATE_PREVENTION_TIMEOUT = 1000; // 1 second

    // Bound listeners for cleanup - use 'any' to avoid chrome type mismatches
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    private boundOnUpdated?: (tabId: number, changeInfo: any, tab: any) => void;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    private boundOnActivated?: (activeInfo: any) => void;
    private boundOnRemoved?: (tabId: number) => void;

    constructor(config: TabActivitySourceConfig = {}) {
        super();
        this.config = { ...DEFAULT_CONFIG, ...config };
    }

    /**
     * Start capturing tab activity
     */
    async start(): Promise<void> {
        const chromeApi = typeof chrome !== 'undefined' ? chrome : undefined;

        if (!chromeApi?.tabs) {
            console.warn('[TabActivitySource] Chrome tabs API not available');
            this.status = 'error';
            return;
        }

        // Bind listeners
        this.boundOnUpdated = this.handleTabUpdated.bind(this);
        this.boundOnActivated = this.handleTabActivated.bind(this);
        this.boundOnRemoved = this.handleTabRemoved.bind(this);

        // Register listeners
        if (this.config.trackNavigations) {
            chromeApi.tabs.onUpdated.addListener(this.boundOnUpdated);
        }
        if (this.config.trackSwitches) {
            chromeApi.tabs.onActivated.addListener(this.boundOnActivated);
        }
        if (this.config.trackCloses) {
            chromeApi.tabs.onRemoved.addListener(this.boundOnRemoved);
        }

        // Initialize with current active tab
        try {
            const [activeTab] = await chromeApi.tabs.query({ active: true, currentWindow: true });
            if (activeTab?.id && activeTab.url) {
                this.currentActiveTabId = activeTab.id;
                this.activeTabUrl.set(activeTab.id, activeTab.url);
                this.activeTabTitle.set(activeTab.id, activeTab.title || '');
                this.tabStartTime.set(activeTab.id, Date.now());
            }
        } catch (error) {
            console.warn('[TabActivitySource] Failed to get initial active tab:', error);
        }

        this.status = 'started';
        console.debug('[TabActivitySource] Started');
    }

    /**
     * Stop capturing and cleanup
     */
    async stop(): Promise<void> {
        const chromeApi = typeof chrome !== 'undefined' ? chrome : undefined;

        if (chromeApi?.tabs) {
            if (this.boundOnUpdated) {
                chromeApi.tabs.onUpdated.removeListener(this.boundOnUpdated);
            }
            if (this.boundOnActivated) {
                chromeApi.tabs.onActivated.removeListener(this.boundOnActivated);
            }
            if (this.boundOnRemoved) {
                chromeApi.tabs.onRemoved.removeListener(this.boundOnRemoved);
            }
        }

        // Emit final events for all tracked tabs
        for (const tabId of this.activeTabUrl.keys()) {
            this.emitTabEvent(tabId, 'close');
        }

        // Clear state
        this.activeTabUrl.clear();
        this.activeTabTitle.clear();
        this.tabStartTime.clear();
        this.lastNavigateTime.clear();
        this.currentActiveTabId = null;

        this.status = 'stopped';
        console.debug('[TabActivitySource] Stopped');
    }

    /**
     * Handle tab navigation/update
     */
    private handleTabUpdated(
        tabId: number,
        changeInfo: { status?: string; url?: string },
        tab: { url?: string; title?: string }
    ): void {
        // Only process when page load is complete
        if (changeInfo.status !== 'complete' || !tab.url || !tab.title) {
            return;
        }

        // Skip ignored URLs
        if (this.shouldIgnoreUrl(tab.url)) {
            return;
        }

        const previousUrl = this.activeTabUrl.get(tabId);
        const now = Date.now();
        const lastNavigate = this.lastNavigateTime.get(tabId) || 0;

        // If URL changed, emit event for previous page
        if (previousUrl && previousUrl !== tab.url) {
            this.emitTabEvent(tabId, 'navigate');
        }

        // Update tracking state
        this.activeTabUrl.set(tabId, tab.url);
        this.activeTabTitle.set(tabId, tab.title);
        this.tabStartTime.set(tabId, now);

        // Only emit navigation event if enough time has passed since last navigate event
        // This prevents duplicate events for the same page load
        if (now - lastNavigate >= this.DUPLICATE_PREVENTION_TIMEOUT) {
            this.emitTabEvent(tabId, 'navigate');
            this.lastNavigateTime.set(tabId, now);
        }
    }

    /**
     * Handle tab activation (switch)
     */
    private handleTabActivated(activeInfo: { tabId: number; windowId: number }): void {
        const tabId = activeInfo.tabId;
        const previousTabId = this.currentActiveTabId;

        // Emit event for previous tab (if tracked)
        if (previousTabId !== null && previousTabId !== tabId) {
            this.emitTabEvent(previousTabId, 'activate');
        }

        this.currentActiveTabId = tabId;

        // Get tab info for the newly activated tab
        const chromeApi = typeof chrome !== 'undefined' ? chrome : undefined;
        chromeApi?.tabs.get(tabId, (tab) => {
            if (chrome.runtime.lastError) {
                console.debug('[TabActivitySource] Error getting tab info:', chrome.runtime.lastError.message);
                return;
            }

            if (tab.url && !this.shouldIgnoreUrl(tab.url)) {
                this.activeTabUrl.set(tabId, tab.url);
                this.activeTabTitle.set(tabId, tab.title || '');
                this.tabStartTime.set(tabId, Date.now());

                this.emitTabEvent(tabId, 'activate');
            }
        });
    }

    /**
     * Handle tab removal
     */
    private handleTabRemoved(tabId: number): void {
        if (this.activeTabUrl.has(tabId)) {
            this.emitTabEvent(tabId, 'close');

            // Cleanup
            this.activeTabUrl.delete(tabId);
            this.activeTabTitle.delete(tabId);
            this.tabStartTime.delete(tabId);

            if (this.currentActiveTabId === tabId) {
                this.currentActiveTabId = null;
            }
        }
    }

    /**
     * Emit a tab activity event
     */
    private emitTabEvent(tabId: number, action: TabActivityEvent['action']): void {
        const url = this.activeTabUrl.get(tabId);
        const title = this.activeTabTitle.get(tabId);
        const startTime = this.tabStartTime.get(tabId);

        if (!url) {
            return;
        }

        const now = Date.now();
        const duration = startTime ? now - startTime : undefined;

        // Skip if duration is below minimum (for close/navigate events)
        if (action !== 'activate' && duration !== undefined && duration < this.config.minSessionDuration) {
            return;
        }

        const event: TabActivityEvent = {
            id: `tab-${tabId}-${now}`,
            type: 'tab_activity',
            timestamp: now,
            source: this.name,
            tabId,
            url,
            domain: this.extractDomain(url),
            title: title || '',
            duration,
            action,
        };

        this.emit(event);
    }

    /**
     * Check if URL should be ignored
     */
    private shouldIgnoreUrl(url: string): boolean {
        return this.config.ignoredUrlPatterns.some((pattern) => pattern.test(url));
    }

    /**
     * Extract domain from URL
     */
    private extractDomain(url: string): string {
        try {
            const urlObj = new URL(url);
            return urlObj.hostname.replace(/^www\./, '');
        } catch {
            return 'unknown';
        }
    }

    /**
     * Get current tracking state (for debugging)
     */
    getTrackingState(): {
        trackedTabs: number;
        currentActiveTabId: number | null;
    } {
        return {
            trackedTabs: this.activeTabUrl.size,
            currentActiveTabId: this.currentActiveTabId,
        };
    }
}
