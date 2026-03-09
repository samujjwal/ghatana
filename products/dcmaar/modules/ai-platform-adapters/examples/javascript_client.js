#!/usr/bin/env node

/**
 * Example JavaScript/Node.js client for DCMaar Agent
 *
 * This demonstrates how to:
 * - Connect to the agent via WebSocket
 * - Subscribe to real-time metrics
 * - Manage plugins via REST API
 *
 * Requirements:
 *   npm install ws axios
 *
 * Run with: node examples/javascript_client.js
 */

const WebSocket = require('ws');
const axios = require('axios');

const AGENT_HTTP_URL = 'http://localhost:8080';
const AGENT_WS_URL = 'ws://localhost:8080';

// =============================================================================
// WebSocket Client for Real-Time Metrics
// =============================================================================

class MetricsStreamClient {
  constructor(url, topics = []) {
    this.url = `${url}/ws/metrics${topics.length ? `?topics=${topics.join(',')}` : ''}`;
    this.ws = null;
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 5;
  }

  connect() {
    return new Promise((resolve, reject) => {
      console.log(`📡 Connecting to ${this.url}...`);

      this.ws = new WebSocket(this.url);

      this.ws.on('open', () => {
        console.log('✅ WebSocket connected');
        this.reconnectAttempts = 0;
        resolve();
      });

      this.ws.on('message', (data) => {
        try {
          const message = JSON.parse(data);
          this.handleMessage(message);
        } catch (err) {
          console.error('❌ Error parsing message:', err);
        }
      });

      this.ws.on('error', (error) => {
        console.error('❌ WebSocket error:', error.message);
        reject(error);
      });

      this.ws.on('close', () => {
        console.log('🔌 WebSocket disconnected');
        this.attemptReconnect();
      });
    });
  }

  handleMessage(message) {
    switch (message.type) {
      case 'ping':
        // Send pong response
        this.send({ type: 'pong', timestamp: Date.now() });
        break;

      case 'metrics':
        console.log('📊 Received metrics:', JSON.stringify(message.data, null, 2));
        break;

      case 'event':
        console.log(`🔔 Event [${message.name}]:`, JSON.stringify(message.data, null, 2));
        break;

      case 'error':
        console.error('❌ Server error:', message.message);
        break;

      default:
        console.log('📨 Unknown message type:', message.type);
    }
  }

  send(message) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(message));
    }
  }

  subscribe(topic) {
    console.log(`➕ Subscribing to topic: ${topic}`);
    this.send({ type: 'subscribe', topic });
  }

  unsubscribe(topic) {
    console.log(`➖ Unsubscribing from topic: ${topic}`);
    this.send({ type: 'unsubscribe', topic });
  }

  attemptReconnect() {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error('❌ Max reconnection attempts reached');
      return;
    }

    this.reconnectAttempts++;
    const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000);

    console.log(`🔄 Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts})...`);

    setTimeout(() => {
      this.connect().catch(console.error);
    }, delay);
  }

  close() {
    if (this.ws) {
      this.ws.close();
    }
  }
}

// =============================================================================
// REST API Client for Plugin Management
// =============================================================================

class PluginManagerClient {
  constructor(baseUrl) {
    this.baseUrl = baseUrl;
  }

  async listPlugins(status = null, limit = 100, offset = 0) {
    try {
      const params = new URLSearchParams({ limit, offset });
      if (status) params.append('status', status);

      const response = await axios.get(`${this.baseUrl}/api/v1/plugins?${params}`);
      return response.data;
    } catch (error) {
      console.error('❌ Error listing plugins:', error.response?.data || error.message);
      throw error;
    }
  }

  async getPlugin(id) {
    try {
      const response = await axios.get(`${this.baseUrl}/api/v1/plugins/${id}`);
      return response.data;
    } catch (error) {
      console.error(`❌ Error getting plugin ${id}:`, error.response?.data || error.message);
      throw error;
    }
  }

  async installPlugin(source, config = {}) {
    try {
      const response = await axios.post(`${this.baseUrl}/api/v1/plugins`, {
        source,
        config
      });
      return response.data;
    } catch (error) {
      console.error('❌ Error installing plugin:', error.response?.data || error.message);
      throw error;
    }
  }

  async uninstallPlugin(id) {
    try {
      await axios.delete(`${this.baseUrl}/api/v1/plugins/${id}`);
      console.log(`✅ Plugin ${id} uninstalled`);
    } catch (error) {
      console.error(`❌ Error uninstalling plugin ${id}:`, error.response?.data || error.message);
      throw error;
    }
  }

  async startPlugin(id) {
    try {
      const response = await axios.post(`${this.baseUrl}/api/v1/plugins/${id}/start`);
      return response.data;
    } catch (error) {
      console.error(`❌ Error starting plugin ${id}:`, error.response?.data || error.message);
      throw error;
    }
  }

  async stopPlugin(id) {
    try {
      const response = await axios.post(`${this.baseUrl}/api/v1/plugins/${id}/stop`);
      return response.data;
    } catch (error) {
      console.error(`❌ Error stopping plugin ${id}:`, error.response?.data || error.message);
      throw error;
    }
  }

  async updatePlugin(id, config) {
    try {
      const response = await axios.put(`${this.baseUrl}/api/v1/plugins/${id}`, { config });
      return response.data;
    } catch (error) {
      console.error(`❌ Error updating plugin ${id}:`, error.response?.data || error.message);
      throw error;
    }
  }
}

// =============================================================================
// Main Example Application
// =============================================================================

async function runExamples() {
  console.log('🚀 DCMaar Agent - JavaScript Client Example\n');

  // -------------------------------------------------------------------------
  // Example 1: WebSocket Metrics Streaming
  // -------------------------------------------------------------------------
  console.log('📊 Example 1: WebSocket Metrics Streaming');
  console.log('=========================================\n');

  const metricsClient = new MetricsStreamClient(AGENT_WS_URL, ['cpu', 'memory']);

  try {
    await metricsClient.connect();

    // Subscribe to additional topics
    setTimeout(() => {
      metricsClient.subscribe('disk');
    }, 2000);

    // Keep connection open for demo
    await new Promise(resolve => setTimeout(resolve, 5000));

  } catch (error) {
    console.error('Failed to connect to WebSocket:', error);
  } finally {
    metricsClient.close();
  }

  // -------------------------------------------------------------------------
  // Example 2: Plugin Management via REST API
  // -------------------------------------------------------------------------
  console.log('\n🔌 Example 2: Plugin Management via REST API');
  console.log('=============================================\n');

  const pluginManager = new PluginManagerClient(AGENT_HTTP_URL);

  try {
    // List all plugins
    console.log('📋 Listing all plugins...');
    const plugins = await pluginManager.listPlugins();
    console.log(`   Found ${plugins.length} plugin(s)\n`);

    // Install a new plugin
    console.log('📥 Installing new plugin...');
    const newPlugin = await pluginManager.installPlugin(
      'https://plugins.example.com/monitor.wasm',
      { interval: 60, metrics: ['cpu', 'memory'] }
    );
    console.log(`   ✅ Installed: ${newPlugin.name} (${newPlugin.id})\n`);

    // Start the plugin
    console.log(`▶️  Starting plugin ${newPlugin.id}...`);
    const startedPlugin = await pluginManager.startPlugin(newPlugin.id);
    console.log(`   ✅ Status: ${startedPlugin.status}\n`);

    // Get plugin details
    console.log(`🔍 Getting plugin details...`);
    const pluginDetails = await pluginManager.getPlugin(newPlugin.id);
    console.log('   Plugin details:', JSON.stringify(pluginDetails, null, 2));
    console.log();

    // Update plugin configuration
    console.log(`⚙️  Updating plugin configuration...`);
    const updatedPlugin = await pluginManager.updatePlugin(newPlugin.id, {
      interval: 30,
      metrics: ['cpu', 'memory', 'disk']
    });
    console.log(`   ✅ Updated config:`, JSON.stringify(updatedPlugin.config, null, 2));
    console.log();

    // Stop the plugin
    console.log(`⏸️  Stopping plugin ${newPlugin.id}...`);
    const stoppedPlugin = await pluginManager.stopPlugin(newPlugin.id);
    console.log(`   ✅ Status: ${stoppedPlugin.status}\n`);

    // Uninstall the plugin
    console.log(`🗑️  Uninstalling plugin ${newPlugin.id}...`);
    await pluginManager.uninstallPlugin(newPlugin.id);
    console.log();

    // List plugins by status
    console.log('📋 Listing running plugins...');
    const runningPlugins = await pluginManager.listPlugins('running');
    console.log(`   Found ${runningPlugins.length} running plugin(s)\n`);

  } catch (error) {
    console.error('Plugin management error:', error.message);
  }

  // -------------------------------------------------------------------------
  // Example 3: Combined Workflow
  // -------------------------------------------------------------------------
  console.log('\n🔄 Example 3: Combined Workflow');
  console.log('================================\n');

  try {
    // Connect to events stream
    const eventsClient = new MetricsStreamClient(AGENT_WS_URL.replace('/metrics', '/events'));
    await eventsClient.connect();

    console.log('👂 Listening for plugin events...\n');

    // Install and start a plugin (events should be broadcasted)
    const plugin = await pluginManager.installPlugin('test-plugin.wasm');
    console.log(`📥 Installed plugin: ${plugin.id}`);

    await new Promise(resolve => setTimeout(resolve, 1000));

    await pluginManager.startPlugin(plugin.id);
    console.log(`▶️  Started plugin: ${plugin.id}`);

    // Wait for events
    await new Promise(resolve => setTimeout(resolve, 2000));

    // Cleanup
    await pluginManager.stopPlugin(plugin.id);
    await pluginManager.uninstallPlugin(plugin.id);
    eventsClient.close();

  } catch (error) {
    console.error('Combined workflow error:', error.message);
  }

  console.log('\n✨ All examples completed!\n');
}

// Run the examples
if (require.main === module) {
  runExamples().catch(console.error);
}

module.exports = { MetricsStreamClient, PluginManagerClient };
