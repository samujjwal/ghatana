/**
 * Browser Extension - Connector Usage
 * 
 * This example shows how to use connectors in a browser extension
 * across background script, content scripts, and popup.
 */

import {
  ConnectorManager,
  HttpConnector,
  WebSocketConnector,
  MetricsCollector,
  HealthChecker,
  createMemoryHealthCheck,
} from '@ghatana/dcmaar-connectors';

// ========================================
// Background Script (Service Worker)
// ========================================

export class ExtensionBackground {
  private connectorManager: ConnectorManager;
  private metrics: MetricsCollector;
  private healthChecker: HealthChecker;
  private messageHandlers: Map<string, Function> = new Map();

  async initialize() {
    console.log('🔌 Initializing Extension Background...');

    this.metrics = new MetricsCollector();
    this.healthChecker = new HealthChecker();
    this.connectorManager = new ConnectorManager();

    // Set up health checks
    this.healthChecker.registerCheck(createMemoryHealthCheck());
    this.healthChecker.start();

    // Initialize connectors
    await this.connectorManager.initialize({
      sources: [
        // API polling for user data
        {
          id: 'user-api',
          type: 'http',
          url: 'https://api.dcmaar.com/user/events',
          pollInterval: 60000, // 1 minute
          headers: {
            'Authorization': `Bearer ${await this.getAuthToken()}`,
          },
          processors: [
            async (event) => {
              // Store in extension storage
              await this.storeData('user_events', event.payload);
              
              // Notify all tabs
              await this.broadcastToTabs('user_event', event.payload);
              
              return event;
            },
          ],
          sinks: ['analytics-sink'],
        },

        // WebSocket for real-time notifications
        {
          id: 'notifications',
          type: 'websocket',
          url: 'wss://notifications.dcmaar.com',
          autoReconnect: true,
          processors: [
            async (event) => {
              // Show browser notification
              await this.showNotification(event.payload);
              
              // Update badge
              await this.updateBadge(event.payload);
              
              return event;
            },
          ],
          sinks: ['storage-sink'],
        },

        // Messages from content scripts
        {
          id: 'content-messages',
          type: 'custom',
          processors: [
            async (event) => {
              this.metrics.incrementCounter('content_messages', 1, {
                type: event.type,
              });
              return event;
            },
          ],
          sinks: ['api-sink', 'storage-sink'],
        },
      ],

      sinks: [
        // Send to API
        {
          id: 'api-sink',
          type: 'http',
          url: 'https://api.dcmaar.com/events',
          headers: {
            'Authorization': `Bearer ${await this.getAuthToken()}`,
            'Content-Type': 'application/json',
          },
          processors: [
            async (event) => {
              this.metrics.incrementCounter('api_calls', 1);
              return event;
            },
          ],
        },

        // Store in extension storage
        {
          id: 'storage-sink',
          type: 'custom',
          processors: [
            async (event) => {
              await this.storeData(`event_${event.id}`, event.payload);
              this.metrics.incrementCounter('storage_writes', 1);
              return event;
            },
          ],
        },

        // Analytics
        {
          id: 'analytics-sink',
          type: 'http',
          url: 'https://analytics.dcmaar.com/track',
          processors: [
            async (event) => {
              // Add extension metadata
              return {
                ...event,
                payload: {
                  ...event.payload,
                  extensionId: chrome.runtime.id,
                  version: chrome.runtime.getManifest().version,
                },
              };
            },
          ],
        },
      ],
    });

    // Set up message listeners
    this.setupMessageListeners();

    console.log('✅ Extension Background initialized');
  }

  private setupMessageListeners() {
    // Listen to messages from content scripts and popup
    chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
      this.handleMessage(message, sender)
        .then(sendResponse)
        .catch(error => {
          console.error('Message handler error:', error);
          sendResponse({ error: error.message });
        });
      return true; // Async response
    });

    // Listen to tab updates
    chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
      if (changeInfo.status === 'complete') {
        this.handleTabUpdate(tabId, tab);
      }
    });

    // Listen to web requests (if needed)
    chrome.webRequest?.onBeforeRequest.addListener(
      (details) => this.handleWebRequest(details),
      { urls: ['<all_urls>'] },
      ['requestBody']
    );
  }

  private async handleMessage(message: any, sender: chrome.runtime.MessageSender) {
    const { type, payload } = message;

    switch (type) {
      case 'get_status':
        return await this.getStatus();
      
      case 'get_metrics':
        return this.metrics.getSnapshot();
      
      case 'track_event':
        await this.trackEvent(payload);
        return { success: true };
      
      case 'get_data':
        return await this.getData(payload.key);
      
      default:
        console.warn('Unknown message type:', type);
        return { error: 'Unknown message type' };
    }
  }

  private async handleTabUpdate(tabId: number, tab: chrome.tabs.Tab) {
    // Track page views
    await this.trackEvent({
      type: 'page_view',
      url: tab.url,
      title: tab.title,
      timestamp: Date.now(),
    });
  }

  private async handleWebRequest(details: chrome.webRequest.WebRequestBodyDetails) {
    // Intercept and analyze web requests if needed
    this.metrics.incrementCounter('web_requests', 1, {
      method: details.method,
    });
  }

  private async trackEvent(event: any) {
    // Send through connector manager
    await this.connectorManager.addSource({
      id: `temp-${Date.now()}`,
      type: 'custom',
      sinks: ['api-sink', 'analytics-sink'],
    });
  }

  private async getAuthToken(): Promise<string> {
    // Get stored auth token
    const result = await chrome.storage.local.get('auth_token');
    return result.auth_token || '';
  }

  private async storeData(key: string, data: any) {
    await chrome.storage.local.set({ [key]: data });
  }

  private async getData(key: string) {
    const result = await chrome.storage.local.get(key);
    return result[key];
  }

  private async showNotification(data: any) {
    await chrome.notifications.create({
      type: 'basic',
      iconUrl: 'icon.png',
      title: data.title || 'DCMAAR',
      message: data.message || '',
    });
  }

  private async updateBadge(data: any) {
    const count = data.count || 0;
    await chrome.action.setBadgeText({ text: count > 0 ? String(count) : '' });
    await chrome.action.setBadgeBackgroundColor({ color: '#FF0000' });
  }

  private async broadcastToTabs(type: string, payload: any) {
    const tabs = await chrome.tabs.query({});
    for (const tab of tabs) {
      if (tab.id) {
        chrome.tabs.sendMessage(tab.id, { type, payload }).catch(() => {
          // Tab might not have content script
        });
      }
    }
  }

  async getStatus() {
    return {
      connectors: this.connectorManager.getStatus(),
      health: this.healthChecker.getHealth(),
      metrics: this.metrics.getSnapshot(),
    };
  }

  async shutdown() {
    await this.connectorManager.shutdown();
    this.healthChecker.stop();
  }
}

// ========================================
// Content Script
// ========================================

export class ExtensionContent {
  private pageData: any[] = [];

  async initialize() {
    console.log('📄 Initializing Extension Content Script...');

    // Observe page changes
    this.observePage();

    // Listen for messages from background
    chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
      this.handleMessage(message);
      sendResponse({ received: true });
      return true;
    });

    // Send initial page data
    await this.sendPageData();

    console.log('✅ Extension Content Script initialized');
  }

  private observePage() {
    // Observe DOM changes
    const observer = new MutationObserver((mutations) => {
      this.handleMutations(mutations);
    });

    observer.observe(document.body, {
      childList: true,
      subtree: true,
      attributes: true,
    });

    // Track user interactions
    document.addEventListener('click', (e) => this.handleClick(e));
    document.addEventListener('submit', (e) => this.handleSubmit(e));
  }

  private async handleMutations(mutations: MutationRecord[]) {
    // Analyze page changes
    const data = {
      type: 'dom_mutation',
      count: mutations.length,
      timestamp: Date.now(),
    };

    await this.sendToBackground('track_event', data);
  }

  private async handleClick(event: MouseEvent) {
    const target = event.target as HTMLElement;
    
    const data = {
      type: 'click',
      element: target.tagName,
      text: target.textContent?.substring(0, 100),
      timestamp: Date.now(),
    };

    await this.sendToBackground('track_event', data);
  }

  private async handleSubmit(event: Event) {
    const form = event.target as HTMLFormElement;
    
    const data = {
      type: 'form_submit',
      action: form.action,
      method: form.method,
      timestamp: Date.now(),
    };

    await this.sendToBackground('track_event', data);
  }

  private async sendPageData() {
    const data = {
      type: 'page_data',
      url: window.location.href,
      title: document.title,
      timestamp: Date.now(),
    };

    await this.sendToBackground('track_event', data);
  }

  private async sendToBackground(type: string, payload: any) {
    return chrome.runtime.sendMessage({ type, payload });
  }

  private handleMessage(message: any) {
    const { type, payload } = message;

    switch (type) {
      case 'user_event':
        console.log('User event received:', payload);
        // Update UI or show notification
        break;
      
      default:
        console.log('Unknown message:', type);
    }
  }
}

// ========================================
// Popup UI
// ========================================

export class ExtensionPopup {
  private status: any = null;
  private metrics: any[] = [];

  async initialize() {
    console.log('🎨 Initializing Extension Popup...');

    await this.loadData();
    this.render();

    // Refresh periodically
    setInterval(() => this.loadData(), 5000);

    console.log('✅ Extension Popup initialized');
  }

  private async loadData() {
    try {
      const [status, metrics] = await Promise.all([
        this.sendToBackground('get_status'),
        this.sendToBackground('get_metrics'),
      ]);

      this.status = status;
      this.metrics = metrics;
      this.render();
    } catch (error) {
      console.error('Failed to load data:', error);
    }
  }

  private async sendToBackground(type: string, payload?: any) {
    return chrome.runtime.sendMessage({ type, payload });
  }

  private render() {
    const container = document.getElementById('app');
    if (!container) return;

    container.innerHTML = `
      <div class="popup">
        <h1>DCMAAR Extension</h1>
        
        <section class="status">
          <h2>Status</h2>
          <div class="status-indicator ${this.status?.health?.status || 'unknown'}">
            ${this.status?.health?.status || 'Unknown'}
          </div>
        </section>

        <section class="connectors">
          <h2>Connectors</h2>
          <ul>
            ${this.status?.connectors?.sources?.map((s: any) => `
              <li>
                <span class="name">${s.id}</span>
                <span class="status ${s.status}">${s.status}</span>
              </li>
            `).join('') || ''}
          </ul>
        </section>

        <section class="metrics">
          <h2>Metrics</h2>
          <ul>
            ${this.metrics?.slice(0, 10).map((m: any) => `
              <li>
                <span class="name">${m.name}</span>
                <span class="value">${m.value}</span>
              </li>
            `).join('') || ''}
          </ul>
        </section>

        <button id="refresh">Refresh</button>
      </div>
    `;

    // Add event listeners
    document.getElementById('refresh')?.addEventListener('click', () => {
      this.loadData();
    });
  }
}

// ========================================
// Manifest V3 Configuration
// ========================================

/*
// manifest.json
{
  "manifest_version": 3,
  "name": "DCMAAR Extension",
  "version": "1.0.0",
  "description": "DCMAAR Browser Extension",
  
  "permissions": [
    "storage",
    "notifications",
    "tabs",
    "webRequest"
  ],
  
  "host_permissions": [
    "https://api.dcmaar.com/*",
    "https://analytics.dcmaar.com/*"
  ],
  
  "background": {
    "service_worker": "background.js",
    "type": "module"
  },
  
  "content_scripts": [
    {
      "matches": ["<all_urls>"],
      "js": ["content.js"],
      "run_at": "document_end"
    }
  ],
  
  "action": {
    "default_popup": "popup.html",
    "default_icon": {
      "16": "icon16.png",
      "48": "icon48.png",
      "128": "icon128.png"
    }
  },
  
  "web_accessible_resources": [
    {
      "resources": ["icon.png"],
      "matches": ["<all_urls>"]
    }
  ]
}
*/

// ========================================
// Entry Points
// ========================================

// background.ts
/*
import { ExtensionBackground } from './extension-browser';

const background = new ExtensionBackground();
background.initialize().catch(console.error);
*/

// content.ts
/*
import { ExtensionContent } from './extension-browser';

const content = new ExtensionContent();
content.initialize().catch(console.error);
*/

// popup.ts
/*
import { ExtensionPopup } from './extension-browser';

document.addEventListener('DOMContentLoaded', () => {
  const popup = new ExtensionPopup();
  popup.initialize().catch(console.error);
});
*/
