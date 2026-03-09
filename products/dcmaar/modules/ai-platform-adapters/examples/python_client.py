#!/usr/bin/env python3

"""
Example Python client for DCMaar Agent

This demonstrates how to:
- Connect to the agent via WebSocket
- Subscribe to real-time metrics and events
- Manage plugins via REST API

Requirements:
    pip install websockets aiohttp

Run with: python examples/python_client.py
"""

import asyncio
import json
import sys
from typing import Dict, List, Optional, Any
from datetime import datetime

try:
    import websockets
    import aiohttp
except ImportError:
    print("❌ Missing dependencies. Install with:")
    print("   pip install websockets aiohttp")
    sys.exit(1)

AGENT_HTTP_URL = "http://localhost:8080"
AGENT_WS_URL = "ws://localhost:8080"


# =============================================================================
# WebSocket Client for Real-Time Streaming
# =============================================================================

class MetricsStreamClient:
    """WebSocket client for streaming metrics and events"""

    def __init__(self, url: str, topics: Optional[List[str]] = None):
        topics_param = f"?topics={','.join(topics)}" if topics else ""
        self.url = f"{url}/ws/metrics{topics_param}"
        self.ws = None
        self.running = False

    async def connect(self):
        """Connect to the WebSocket server"""
        print(f"📡 Connecting to {self.url}...")

        try:
            self.ws = await websockets.connect(self.url)
            print("✅ WebSocket connected")
            self.running = True
        except Exception as e:
            print(f"❌ Failed to connect: {e}")
            raise

    async def listen(self):
        """Listen for messages from the server"""
        try:
            async for message in self.ws:
                try:
                    data = json.loads(message)
                    await self.handle_message(data)
                except json.JSONDecodeError as e:
                    print(f"❌ Error parsing message: {e}")
        except websockets.exceptions.ConnectionClosed:
            print("🔌 Connection closed")
            self.running = False

    async def handle_message(self, message: Dict[str, Any]):
        """Handle incoming messages"""
        msg_type = message.get("type")

        if msg_type == "ping":
            # Respond to ping with pong
            await self.send({"type": "pong", "timestamp": int(datetime.now().timestamp() * 1000)})

        elif msg_type == "metrics":
            print(f"📊 Received metrics:")
            print(json.dumps(message["data"], indent=2))

        elif msg_type == "event":
            print(f"🔔 Event [{message['name']}]:")
            print(json.dumps(message["data"], indent=2))

        elif msg_type == "error":
            print(f"❌ Server error: {message['message']}")

        else:
            print(f"📨 Unknown message type: {msg_type}")

    async def send(self, message: Dict[str, Any]):
        """Send a message to the server"""
        if self.ws and not self.ws.closed:
            await self.ws.send(json.dumps(message))

    async def subscribe(self, topic: str):
        """Subscribe to a topic"""
        print(f"➕ Subscribing to topic: {topic}")
        await self.send({"type": "subscribe", "topic": topic})

    async def unsubscribe(self, topic: str):
        """Unsubscribe from a topic"""
        print(f"➖ Unsubscribing from topic: {topic}")
        await self.send({"type": "unsubscribe", "topic": topic})

    async def close(self):
        """Close the WebSocket connection"""
        if self.ws:
            await self.ws.close()
            self.running = False


# =============================================================================
# REST API Client for Plugin Management
# =============================================================================

class PluginManagerClient:
    """HTTP client for managing plugins"""

    def __init__(self, base_url: str):
        self.base_url = base_url
        self.session = None

    async def __aenter__(self):
        self.session = aiohttp.ClientSession()
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        if self.session:
            await self.session.close()

    async def list_plugins(
        self, status: Optional[str] = None, limit: int = 100, offset: int = 0
    ) -> List[Dict[str, Any]]:
        """List all plugins"""
        params = {"limit": limit, "offset": offset}
        if status:
            params["status"] = status

        async with self.session.get(
            f"{self.base_url}/api/v1/plugins", params=params
        ) as response:
            if response.status == 200:
                return await response.json()
            else:
                error_text = await response.text()
                raise Exception(f"Failed to list plugins: {error_text}")

    async def get_plugin(self, plugin_id: str) -> Dict[str, Any]:
        """Get plugin details"""
        async with self.session.get(
            f"{self.base_url}/api/v1/plugins/{plugin_id}"
        ) as response:
            if response.status == 200:
                return await response.json()
            elif response.status == 404:
                raise Exception(f"Plugin {plugin_id} not found")
            else:
                error_text = await response.text()
                raise Exception(f"Failed to get plugin: {error_text}")

    async def install_plugin(
        self, source: str, config: Optional[Dict[str, Any]] = None
    ) -> Dict[str, Any]:
        """Install a new plugin"""
        payload = {"source": source}
        if config:
            payload["config"] = config

        async with self.session.post(
            f"{self.base_url}/api/v1/plugins", json=payload
        ) as response:
            if response.status == 200:
                return await response.json()
            else:
                error_text = await response.text()
                raise Exception(f"Failed to install plugin: {error_text}")

    async def uninstall_plugin(self, plugin_id: str):
        """Uninstall a plugin"""
        async with self.session.delete(
            f"{self.base_url}/api/v1/plugins/{plugin_id}"
        ) as response:
            if response.status == 204:
                print(f"✅ Plugin {plugin_id} uninstalled")
            else:
                error_text = await response.text()
                raise Exception(f"Failed to uninstall plugin: {error_text}")

    async def start_plugin(self, plugin_id: str) -> Dict[str, Any]:
        """Start a plugin"""
        async with self.session.post(
            f"{self.base_url}/api/v1/plugins/{plugin_id}/start"
        ) as response:
            if response.status == 200:
                return await response.json()
            else:
                error_text = await response.text()
                raise Exception(f"Failed to start plugin: {error_text}")

    async def stop_plugin(self, plugin_id: str) -> Dict[str, Any]:
        """Stop a plugin"""
        async with self.session.post(
            f"{self.base_url}/api/v1/plugins/{plugin_id}/stop"
        ) as response:
            if response.status == 200:
                return await response.json()
            else:
                error_text = await response.text()
                raise Exception(f"Failed to stop plugin: {error_text}")

    async def update_plugin(
        self, plugin_id: str, config: Dict[str, Any]
    ) -> Dict[str, Any]:
        """Update plugin configuration"""
        async with self.session.put(
            f"{self.base_url}/api/v1/plugins/{plugin_id}", json={"config": config}
        ) as response:
            if response.status == 200:
                return await response.json()
            else:
                error_text = await response.text()
                raise Exception(f"Failed to update plugin: {error_text}")


# =============================================================================
# Example Applications
# =============================================================================

async def example_websocket_streaming():
    """Example 1: WebSocket metrics streaming"""
    print("📊 Example 1: WebSocket Metrics Streaming")
    print("=" * 50)
    print()

    client = MetricsStreamClient(AGENT_WS_URL, topics=["cpu", "memory"])

    try:
        await client.connect()

        # Start listening in background
        listen_task = asyncio.create_task(client.listen())

        # Subscribe to additional topics after 2 seconds
        await asyncio.sleep(2)
        await client.subscribe("disk")

        # Keep running for 5 seconds
        await asyncio.sleep(3)

        # Cancel listening task
        listen_task.cancel()
        try:
            await listen_task
        except asyncio.CancelledError:
            pass

    except Exception as e:
        print(f"❌ Error: {e}")
    finally:
        await client.close()


async def example_plugin_management():
    """Example 2: Plugin management via REST API"""
    print("\n🔌 Example 2: Plugin Management via REST API")
    print("=" * 50)
    print()

    async with PluginManagerClient(AGENT_HTTP_URL) as manager:
        try:
            # List all plugins
            print("📋 Listing all plugins...")
            plugins = await manager.list_plugins()
            print(f"   Found {len(plugins)} plugin(s)\n")

            # Install a new plugin
            print("📥 Installing new plugin...")
            new_plugin = await manager.install_plugin(
                "https://plugins.example.com/monitor.wasm",
                config={"interval": 60, "metrics": ["cpu", "memory"]},
            )
            print(f"   ✅ Installed: {new_plugin['name']} ({new_plugin['id']})\n")

            plugin_id = new_plugin["id"]

            # Start the plugin
            print(f"▶️  Starting plugin {plugin_id}...")
            started = await manager.start_plugin(plugin_id)
            print(f"   ✅ Status: {started['status']}\n")

            # Get plugin details
            print(f"🔍 Getting plugin details...")
            details = await manager.get_plugin(plugin_id)
            print(f"   Plugin details:")
            print(json.dumps(details, indent=2))
            print()

            # Update configuration
            print(f"⚙️  Updating plugin configuration...")
            updated = await manager.update_plugin(
                plugin_id, {"interval": 30, "metrics": ["cpu", "memory", "disk"]}
            )
            print(f"   ✅ Updated config:")
            print(json.dumps(updated["config"], indent=2))
            print()

            # Stop the plugin
            print(f"⏸️  Stopping plugin {plugin_id}...")
            stopped = await manager.stop_plugin(plugin_id)
            print(f"   ✅ Status: {stopped['status']}\n")

            # Uninstall
            print(f"🗑️  Uninstalling plugin {plugin_id}...")
            await manager.uninstall_plugin(plugin_id)
            print()

            # List running plugins
            print("📋 Listing running plugins...")
            running = await manager.list_plugins(status="running")
            print(f"   Found {len(running)} running plugin(s)\n")

        except Exception as e:
            print(f"❌ Error: {e}")


async def example_combined_workflow():
    """Example 3: Combined workflow with WebSocket and REST API"""
    print("\n🔄 Example 3: Combined Workflow")
    print("=" * 50)
    print()

    # Connect to events stream
    events_client = MetricsStreamClient(AGENT_WS_URL.replace("metrics", "events"))

    try:
        await events_client.connect()

        # Start listening for events
        listen_task = asyncio.create_task(events_client.listen())

        print("👂 Listening for plugin events...\n")

        async with PluginManagerClient(AGENT_HTTP_URL) as manager:
            # Install and start a plugin
            plugin = await manager.install_plugin("test-plugin.wasm")
            print(f"📥 Installed plugin: {plugin['id']}")

            await asyncio.sleep(1)

            await manager.start_plugin(plugin["id"])
            print(f"▶️  Started plugin: {plugin['id']}")

            # Wait for events
            await asyncio.sleep(2)

            # Cleanup
            await manager.stop_plugin(plugin["id"])
            await manager.uninstall_plugin(plugin["id"])

        # Cancel listening
        listen_task.cancel()
        try:
            await listen_task
        except asyncio.CancelledError:
            pass

    except Exception as e:
        print(f"❌ Error: {e}")
    finally:
        await events_client.close()


async def main():
    """Run all examples"""
    print("🚀 DCMaar Agent - Python Client Example\n")

    try:
        # Run examples sequentially
        await example_websocket_streaming()
        await example_plugin_management()
        await example_combined_workflow()

        print("\n✨ All examples completed!\n")

    except KeyboardInterrupt:
        print("\n\n👋 Interrupted by user")
    except Exception as e:
        print(f"\n❌ Fatal error: {e}")
        import traceback
        traceback.print_exc()


if __name__ == "__main__":
    asyncio.run(main())
