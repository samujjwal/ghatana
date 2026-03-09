/**
 * @fileoverview Content Script Source
 * 
 * Event source that receives rich page metrics from content scripts.
 * Uses Chrome runtime messaging to receive events from injected scripts.
 * 
 * @module pipeline/sources/ContentScriptSource
 */

import { BaseEventSource } from '@ghatana/dcmaar-browser-extension-core';
import type { ContentScriptEvent, PageMetadata, InteractionData, MediaTrackingData, DOMAnalysisData } from '../types';

/**
 * Message types from content script
 */
export type ContentScriptMessageType =
    | 'PAGE_METADATA'
    | 'PAGE_INTERACTION'
    | 'MEDIA_EVENT'
    | 'DOM_ANALYSIS'
    | 'PAGE_UNLOAD';

/**
 * Message structure from content script
 */
export interface ContentScriptMessage {
    type: ContentScriptMessageType;
    tabId?: number;
    url: string;
    domain: string;
    timestamp: number;
    data: PageMetadata | InteractionData | MediaTrackingData | DOMAnalysisData | Record<string, unknown>;
}

/**
 * Configuration for ContentScriptSource
 */
export interface ContentScriptSourceConfig {
    /** Message types to listen for */
    enabledMessageTypes?: ContentScriptMessageType[];
    /** Whether to validate message origin */
    validateOrigin?: boolean;
}

const DEFAULT_CONFIG: Required<ContentScriptSourceConfig> = {
    enabledMessageTypes: ['PAGE_METADATA', 'PAGE_INTERACTION', 'MEDIA_EVENT', 'DOM_ANALYSIS', 'PAGE_UNLOAD'],
    validateOrigin: true,
};

/**
 * ContentScriptSource
 * 
 * Receives rich page metrics from content scripts running in web pages.
 * Handles:
 * - Page metadata (title, meta tags, OpenGraph)
 * - User interactions (scroll, clicks, forms)
 * - Media events (video play/pause)
 * - DOM analysis (safety scanning)
 * 
 * @example
 * ```typescript
 * const source = new ContentScriptSource();
 * source.onEvent((event) => console.log('Content event:', event));
 * await source.start();
 * ```
 */
export class ContentScriptSource extends BaseEventSource<ContentScriptEvent> {
    readonly name = 'content-script';

    private readonly config: Required<ContentScriptSourceConfig>;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    private boundMessageListener?: (message: any, sender: any, sendResponse: any) => boolean | void;

    constructor(config: ContentScriptSourceConfig = {}) {
        super();
        this.config = { ...DEFAULT_CONFIG, ...config };
    }

    /**
     * Start listening for content script messages
     */
    async start(): Promise<void> {
        const chromeApi = typeof chrome !== 'undefined' ? chrome : undefined;

        if (!chromeApi?.runtime?.onMessage) {
            console.warn('[ContentScriptSource] Chrome runtime API not available');
            this.status = 'error';
            return;
        }

        this.boundMessageListener = this.handleMessage.bind(this);
        chromeApi.runtime.onMessage.addListener(this.boundMessageListener);

        this.status = 'started';
        console.debug('[ContentScriptSource] Started');
    }

    /**
     * Stop listening and cleanup
     */
    async stop(): Promise<void> {
        const chromeApi = typeof chrome !== 'undefined' ? chrome : undefined;

        if (chromeApi?.runtime?.onMessage && this.boundMessageListener) {
            chromeApi.runtime.onMessage.removeListener(this.boundMessageListener);
        }

        this.status = 'stopped';
        console.debug('[ContentScriptSource] Stopped');
    }

    /**
     * Handle incoming message from content script
     */
    private handleMessage(
        message: ContentScriptMessage,
        sender: chrome.runtime.MessageSender,
        sendResponse: (response?: unknown) => void
    ): boolean | void {
        // Validate message structure
        if (!message || typeof message !== 'object' || !message.type) {
            return;
        }

        // Check if this message type is enabled
        if (!this.config.enabledMessageTypes.includes(message.type)) {
            return;
        }

        // Validate sender origin if configured
        if (this.config.validateOrigin && !sender.tab?.id) {
            console.debug('[ContentScriptSource] Ignoring message without tab context');
            return;
        }

        const tabId = sender.tab?.id ?? message.tabId ?? 0;
        const url = message.url || sender.tab?.url || '';
        const domain = message.domain || this.extractDomain(url);

        try {
            const event = this.createEvent(message, tabId, url, domain);
            if (event) {
                this.emit(event);
                sendResponse({ success: true });
            }
        } catch (error) {
            console.error('[ContentScriptSource] Error processing message:', error);
            sendResponse({ success: false, error: String(error) });
        }

        // Return true to indicate async response
        return true;
    }

    /**
     * Create a ContentScriptEvent from a message
     */
    private createEvent(
        message: ContentScriptMessage,
        tabId: number,
        url: string,
        domain: string
    ): ContentScriptEvent | null {
        const now = Date.now();
        const baseEvent = {
            id: `content-${tabId}-${now}`,
            timestamp: message.timestamp || now,
            source: this.name,
            tabId,
            url,
            domain,
        };

        switch (message.type) {
            case 'PAGE_METADATA':
                return {
                    ...baseEvent,
                    type: 'page_view',
                    pageMetadata: message.data as PageMetadata,
                };

            case 'PAGE_INTERACTION':
                return {
                    ...baseEvent,
                    type: 'page_interaction',
                    interactions: message.data as InteractionData,
                };

            case 'MEDIA_EVENT':
                return {
                    ...baseEvent,
                    type: 'media_event',
                    media: message.data as MediaTrackingData,
                };

            case 'DOM_ANALYSIS':
                return {
                    ...baseEvent,
                    type: 'dom_analysis',
                    domAnalysis: message.data as DOMAnalysisData,
                };

            case 'PAGE_UNLOAD':
                // Emit final interaction data on page unload
                return {
                    ...baseEvent,
                    type: 'page_interaction',
                    interactions: message.data as InteractionData,
                };

            default:
                return null;
        }
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
}

/**
 * Helper to send messages from content script to background
 * This should be used in the content script itself
 */
export function sendToBackground(message: ContentScriptMessage): Promise<{ success: boolean; error?: string }> {
    return new Promise((resolve) => {
        const chromeApi = typeof chrome !== 'undefined' ? chrome : undefined;

        if (!chromeApi?.runtime?.sendMessage) {
            resolve({ success: false, error: 'Chrome runtime not available' });
            return;
        }

        chromeApi.runtime.sendMessage(message, (response) => {
            if (chrome.runtime.lastError) {
                resolve({ success: false, error: chrome.runtime.lastError.message });
            } else {
                resolve(response || { success: true });
            }
        });
    });
}
