/**
 * Desktop Application (Electron/Tauri) - Connector Usage
 * 
 * This example shows how to use connectors in a desktop application
 * with both main process and renderer process integration.
 */

import {
  ConnectorManager,
  HttpConnector,
  WebSocketConnector,
  IpcConnector,
  FileSystemConnector,
  MetricsCollector,
  HealthChecker,
  createMemoryHealthCheck,
} from '@ghatana/dcmaar-connectors';

// ========================================
// Main Process (Node.js)
// ========================================

export class DesktopAppMain {
  private connectorManager: ConnectorManager;
  private ipcServer: IpcConnector;
  private metrics: MetricsCollector;
  private healthChecker: HealthChecker;
  private electronApp: any; // Electron.App

  constructor(electronApp: any) {
    this.electronApp = electronApp;
    this.connectorManager = new ConnectorManager();
    this.metrics = new MetricsCollector();
    this.healthChecker = new HealthChecker();
  }

  async initialize() {
    console.log('🖥️  Initializing Desktop App (Main Process)...');

    // Set up IPC server for renderer communication
    this.ipcServer = new IpcConnector({
      id: 'main-ipc',
      channel: 'dcmaar-desktop',
      mode: 'server',
    });

    await this.ipcServer.connect();

    // Handle IPC messages from renderer
    this.ipcServer.onEvent('message', async (event) => {
      const { type, payload } = event.payload;

      switch (type) {
        case 'get_status':
          await this.sendToRenderer('status', await this.getStatus());
          break;
        case 'get_metrics':
          await this.sendToRenderer('metrics', this.metrics.getSnapshot());
          break;
        case 'send_data':
          await this.handleDataFromRenderer(payload);
          break;
      }
    });

    // Set up health checks
    this.healthChecker.registerCheck(createMemoryHealthCheck());
    this.healthChecker.start();

    // Initialize connector manager
    await this.connectorManager.initialize({
      sources: [
        // HTTP API for cloud sync
        {
          id: 'cloud-sync',
          type: 'http',
          url: 'https://api.dcmaar.com/sync',
          pollInterval: 30000,
          headers: {
            'Authorization': `Bearer ${this.getAuthToken()}`,
          },
          processors: [
            async (event) => {
              // Notify renderer of new data
              await this.sendToRenderer('cloud_update', event.payload);
              return event;
            },
          ],
          sinks: ['local-storage'],
        },

        // WebSocket for real-time updates
        {
          id: 'realtime-updates',
          type: 'websocket',
          url: 'wss://realtime.dcmaar.com',
          autoReconnect: true,
          processors: [
            async (event) => {
              // Forward to renderer
              await this.sendToRenderer('realtime_event', event.payload);
              return event;
            },
          ],
          sinks: ['local-storage', 'notification-sink'],
        },

        // File system watcher for local data
        {
          id: 'local-files',
          type: 'filesystem',
          path: this.getUserDataPath(),
          mode: 'watch',
          pattern: '*.json',
          format: 'json',
          processors: [
            async (event) => {
              // Notify renderer of file changes
              await this.sendToRenderer('file_changed', {
                filename: event.metadata?.filename,
                data: event.payload,
              });
              return event;
            },
          ],
          sinks: ['cloud-sync-sink'],
        },

        // IPC from renderer
        {
          id: 'renderer-ipc',
          type: 'ipc',
          channel: 'dcmaar-desktop',
          mode: 'server',
          sinks: ['cloud-sync-sink', 'local-storage'],
        },
      ],

      sinks: [
        // Local storage
        {
          id: 'local-storage',
          type: 'filesystem',
          path: this.getUserDataPath(),
          mode: 'write',
          format: 'json',
          processors: [
            async (event) => {
              this.metrics.incrementCounter('local_writes', 1);
              return event;
            },
          ],
        },

        // Cloud sync
        {
          id: 'cloud-sync-sink',
          type: 'http',
          url: 'https://api.dcmaar.com/data',
          headers: {
            'Authorization': `Bearer ${this.getAuthToken()}`,
            'Content-Type': 'application/json',
          },
          processors: [
            async (event) => {
              this.metrics.incrementCounter('cloud_syncs', 1);
              return event;
            },
          ],
        },

        // System notifications
        {
          id: 'notification-sink',
          type: 'custom',
          processors: [
            async (event) => {
              // Show system notification
              if (event.metadata?.priority === 'high') {
                this.showNotification(event.payload);
              }
              return event;
            },
          ],
        },
      ],
    });

    console.log('✅ Desktop App Main Process initialized');
  }

  private async sendToRenderer(type: string, payload: any) {
    await this.ipcServer.send({ type, payload });
  }

  private async handleDataFromRenderer(data: any) {
    // Process data from renderer
    this.metrics.incrementCounter('renderer_messages', 1);
    
    // Route through connector manager
    // The data will be processed by the appropriate sinks
  }

  private getAuthToken(): string {
    // Get stored auth token
    return process.env.DCMAAR_TOKEN || '';
  }

  private getUserDataPath(): string {
    // Get user data directory
    return this.electronApp.getPath('userData');
  }

  private showNotification(data: any) {
    // Show system notification using Electron
    // const { Notification } = require('electron');
    // new Notification({
    //   title: data.title,
    //   body: data.message,
    // }).show();
    console.log('📬 Notification:', data);
  }

  async getStatus() {
    return {
      connectors: this.connectorManager.getStatus(),
      health: this.healthChecker.getHealth(),
      metrics: this.metrics.getSnapshot(),
    };
  }

  async shutdown() {
    console.log('🛑 Shutting down Desktop App Main Process...');
    
    await this.connectorManager.shutdown();
    await this.ipcServer.disconnect();
    this.healthChecker.stop();
    
    console.log('✅ Desktop App Main Process shut down');
  }
}

// ========================================
// Renderer Process (Browser/UI)
// ========================================

export class DesktopAppRenderer {
  private ipcClient: IpcConnector;
  private eventHandlers: Map<string, Set<Function>> = new Map();

  async initialize() {
    console.log('🎨 Initializing Desktop App (Renderer Process)...');

    // Connect to main process via IPC
    this.ipcClient = new IpcConnector({
      id: 'renderer-ipc',
      channel: 'dcmaar-desktop',
      mode: 'client',
    });

    await this.ipcClient.connect();

    // Handle messages from main process
    this.ipcClient.onEvent('message', (event) => {
      const { type, payload } = event.payload;
      this.emit(type, payload);
    });

    console.log('✅ Desktop App Renderer Process initialized');
  }

  // API for UI components
  async getStatus() {
    return this.sendRequest('get_status');
  }

  async getMetrics() {
    return this.sendRequest('get_metrics');
  }

  async sendData(data: any) {
    await this.ipcClient.send({
      type: 'send_data',
      payload: data,
    });
  }

  // Event system for UI
  on(event: string, handler: Function) {
    if (!this.eventHandlers.has(event)) {
      this.eventHandlers.set(event, new Set());
    }
    this.eventHandlers.get(event)!.add(handler);
  }

  off(event: string, handler: Function) {
    this.eventHandlers.get(event)?.delete(handler);
  }

  private emit(event: string, data: any) {
    this.eventHandlers.get(event)?.forEach(handler => handler(data));
  }

  private async sendRequest(type: string): Promise<any> {
    return new Promise((resolve) => {
      const responseHandler = (data: any) => {
        this.off(type, responseHandler);
        resolve(data);
      };

      this.on(type, responseHandler);
      this.ipcClient.send({ type });
    });
  }

  async shutdown() {
    await this.ipcClient.disconnect();
  }
}

// ========================================
// React Component Example
// ========================================

/*
import React, { useEffect, useState } from 'react';
import { DesktopAppRenderer } from './desktop-electron';

const appRenderer = new DesktopAppRenderer();

export function Dashboard() {
  const [status, setStatus] = useState(null);
  const [metrics, setMetrics] = useState([]);

  useEffect(() => {
    appRenderer.initialize();

    // Listen for real-time updates
    appRenderer.on('realtime_event', (data) => {
      console.log('Real-time event:', data);
      // Update UI
    });

    appRenderer.on('cloud_update', (data) => {
      console.log('Cloud update:', data);
      // Update UI
    });

    appRenderer.on('file_changed', (data) => {
      console.log('File changed:', data);
      // Update UI
    });

    // Fetch initial data
    loadData();

    // Periodic refresh
    const interval = setInterval(loadData, 5000);

    return () => {
      clearInterval(interval);
      appRenderer.shutdown();
    };
  }, []);

  const loadData = async () => {
    const [statusData, metricsData] = await Promise.all([
      appRenderer.getStatus(),
      appRenderer.getMetrics(),
    ]);
    setStatus(statusData);
    setMetrics(metricsData);
  };

  const handleSendData = async () => {
    await appRenderer.sendData({
      action: 'user_action',
      timestamp: Date.now(),
    });
  };

  return (
    <div className="dashboard">
      <h1>DCMAAR Desktop</h1>
      
      <section>
        <h2>Status</h2>
        <pre>{JSON.stringify(status, null, 2)}</pre>
      </section>

      <section>
        <h2>Metrics</h2>
        <ul>
          {metrics.map(m => (
            <li key={m.name}>{m.name}: {m.value}</li>
          ))}
        </ul>
      </section>

      <button onClick={handleSendData}>Send Data</button>
    </div>
  );
}
*/

// ========================================
// Electron Main Entry Point
// ========================================

/*
// main.ts
import { app, BrowserWindow } from 'electron';
import { DesktopAppMain } from './desktop-electron';

let mainWindow: BrowserWindow | null = null;
let desktopApp: DesktopAppMain | null = null;

async function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    webPreferences: {
      nodeIntegration: true,
      contextIsolation: false,
    },
  });

  mainWindow.loadFile('index.html');

  // Initialize desktop app
  desktopApp = new DesktopAppMain(app);
  await desktopApp.initialize();
}

app.whenReady().then(createWindow);

app.on('window-all-closed', async () => {
  if (desktopApp) {
    await desktopApp.shutdown();
  }
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) {
    createWindow();
  }
});
*/
