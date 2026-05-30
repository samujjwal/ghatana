#!/usr/bin/env node

/**
 * G11-T07: Add mobile offline cache hit/miss/stale counters without PHI.
 * Adds telemetry emission to offlineStore.ts for cache operations.
 */

import { readFileSync, writeFileSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const OFFLINE_STORE_FILE = join(__dirname, '..', 'products', 'phr', 'apps', 'mobile', 'src', 'services', 'offlineStore.ts');

function fixFile(filePath) {
  let content = readFileSync(filePath, 'utf-8');

  // Add telemetry function after imports
  const telemetryFunction = `
/**
 * G11-T07: Mobile offline cache telemetry without PHI.
 * Emits cache hit/miss/stale counters without including any PHI.
 */
function emitCacheMetric(
  operation: 'hit' | 'miss' | 'stale' | 'session_mismatch' | 'schema_mismatch' | 'corrupt',
  sessionIdentity: SessionIdentity
): void {
  // In a real implementation, this would call a telemetry service.
  // For now, we log at debug level without PHI.
  console.debug(\`[phr.cache] operation=\${operation}, role=\${sessionIdentity.role}, tenantId=\${sessionIdentity.tenantId}\`);
}
`;

  if (!content.includes('emitCacheMetric')) {
    const importEndIndex = content.indexOf("import type { MobileDashboard } from '../types';");
    const insertPosition = importEndIndex + "import type { MobileDashboard } from '../types';".length;
    content = content.slice(0, insertPosition) + telemetryFunction + content.slice(insertPosition);
  }

  // Add metric emission in loadDashboardOffline for cache hit
  content = content.replace(
    /try \{\s*return parseMobileDashboard\(envelope\.data\);/g,
    'try {\n    emitCacheMetric(\'hit\', sessionIdentity);\n    return parseMobileDashboard(envelope.data);'
  );

  // Add metric emission for miss (no cache)
  content = content.replace(
    /const raw = await phiGet\(DASHBOARD_KEY\);\s*if \(!raw\) return null;/g,
    'const raw = await phiGet(DASHBOARD_KEY);\n  if (!raw) {\n    emitCacheMetric(\'miss\', sessionIdentity);\n    return null;\n  }'
  );

  // Add metric emission for stale cache
  content = content.replace(
    /if \(ageMs > envelope\.ttlMs\) \{\s*\/\/ Cache expired; discard PHI proactively\.\s*await clearDashboardOffline\(\);\s*return null;\s*\}/g,
    'if (ageMs > envelope.ttlMs) {\n    // Cache expired; discard PHI proactively.\n    emitCacheMetric(\'stale\', sessionIdentity);\n    await clearDashboardOffline();\n    return null;\n  }'
  );

  // Add metric emission for session mismatch
  content = content.replace(
    /\/\/ Check session binding - reject cache if session differs\s*if \(!sessionMatches\(envelope, sessionIdentity\)\) \{\s*await clearDashboardOffline\(\);\s*return null;\s*\}/g,
    '// Check session binding - reject cache if session differs\n  if (!sessionMatches(envelope, sessionIdentity)) {\n    emitCacheMetric(\'session_mismatch\', sessionIdentity);\n    await clearDashboardOffline();\n    return null;\n  }'
  );

  // Add metric emission for schema mismatch
  content = content.replace(
    /if \(envelope\.schemaVersion !== SCHEMA_VERSION\) \{\s*\/\/ Schema mismatch; discard so stale structure is not used\.\s*await clearDashboardOffline\(\);\s*return null;\s*\}/g,
    'if (envelope.schemaVersion !== SCHEMA_VERSION) {\n    // Schema mismatch; discard so stale structure is not used.\n    emitCacheMetric(\'schema_mismatch\', sessionIdentity);\n    await clearDashboardOffline();\n    return null;\n  }'
  );

  // Add metric emission for corrupt payload
  content = content.replace(
    /} catch \{\s*\/\/ Corrupt payload; discard\.\s*await clearDashboardOffline\(\);\s*return null;\s*\}/g,
    '} catch {\n    // Corrupt payload; discard.\n    emitCacheMetric(\'corrupt\', sessionIdentity);\n    await clearDashboardOffline();\n    return null;\n  }'
  );

  writeFileSync(filePath, content, 'utf-8');
  console.log('Added cache telemetry to offlineStore.ts');
}

fixFile(OFFLINE_STORE_FILE);
