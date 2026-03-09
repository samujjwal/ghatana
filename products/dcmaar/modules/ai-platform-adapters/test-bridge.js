#!/usr/bin/env node

/**
 * Quick integration test for the napi-rs bridge
 * Tests TypeScript → Rust data flow
 */

import { createRequire } from 'module';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';

const require = createRequire(import.meta.url);
const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// Try to load the native bridge
let bridge;
try {
  const nativeModule = require('./crates/agent-napi/index.node');
  bridge = new nativeModule.AgentBridge();
  console.log('✅ Native bridge loaded successfully');
} catch (err) {
  console.error('❌ Failed to load native bridge:', err.message);
  process.exit(1);
}

// Test 1: Get version
console.log('\n📝 Test 1: Get version');
try {
  const version = bridge.getVersion();
  console.log(`✅ Bridge version: ${version}`);
} catch (err) {
  console.error('❌ Failed:', err.message);
  process.exit(1);
}

// Test 2: Health check
console.log('\n📝 Test 2: Health check');
try {
  const healthy = await bridge.healthCheck();
  console.log(`✅ Health check: ${healthy ? 'healthy' : 'unhealthy'}`);
} catch (err) {
  console.error('❌ Failed:', err.message);
  process.exit(1);
}

// Test 3: Submit a single event
console.log('\n📝 Test 3: Submit single event');
try {
  const event = {
    id: 'test-event-1',
    type: 'metric.cpu',
    timestamp: Date.now(),
    payload: { value: 85.5, unit: 'percent' },
    metadata: { source: 'test-script', hostname: 'localhost' }
  };

  await bridge.submitEvent(JSON.stringify(event));
  console.log('✅ Event submitted successfully');
} catch (err) {
  console.error('❌ Failed:', err.message);
  process.exit(1);
}

// Test 4: Submit a batch of events
console.log('\n📝 Test 4: Submit batch of events');
try {
  const batch = [
    {
      id: 'test-event-2',
      type: 'metric.memory',
      timestamp: Date.now(),
      payload: { value: 2048, unit: 'MB' },
      metadata: { source: 'test-script' }
    },
    {
      id: 'test-event-3',
      type: 'metric.disk',
      timestamp: Date.now(),
      payload: { value: 75.2, unit: 'percent' },
      metadata: { source: 'test-script' }
    },
    {
      id: 'test-event-4',
      type: 'log.info',
      timestamp: Date.now(),
      payload: { message: 'Test log message', level: 'info' },
      metadata: { source: 'test-script' }
    }
  ];

  const count = await bridge.submitBatch(JSON.stringify(batch));
  console.log(`✅ Batch submitted: ${count} events`);
} catch (err) {
  console.error('❌ Failed:', err.message);
  process.exit(1);
}

// Test 5: Get statistics
console.log('\n📝 Test 5: Get statistics');
try {
  const statsJson = bridge.getStats();
  const stats = JSON.parse(statsJson);

  console.log('✅ Bridge statistics:');
  console.log(`   - Batches processed: ${stats.batches_processed}`);
  console.log(`   - Events processed: ${stats.events_processed}`);
  console.log(`   - Using real client: ${stats.using_real_client}`);
  console.log(`   - Uptime: ${stats.uptime_ms}ms`);
  console.log(`   - Started at: ${stats.started_at}`);
  if (stats.last_error) {
    console.log(`   - Last error: ${stats.last_error}`);
  } else {
    console.log('   - No errors');
  }
} catch (err) {
  console.error('❌ Failed:', err.message);
  process.exit(1);
}

console.log('\n✅ All tests passed!\n');
console.log('📊 Summary:');
console.log('   - Bridge loads correctly');
console.log('   - Version API works');
console.log('   - Health check works');
console.log('   - Single event submission works');
console.log('   - Batch event submission works');
console.log('   - Statistics API works');
console.log('\n🎉 napi-rs bridge is fully functional!\n');
