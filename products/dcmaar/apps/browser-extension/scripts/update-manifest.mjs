#!/usr/bin/env node

/**
 * Post-build script to inject content_scripts into manifest.json
 * 
 * The CRX plugin doesn't automatically add content_scripts that are defined in
 * manifest.config.ts, so we need to inject them after the build.
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.dirname(__dirname); // Go up one level from scripts/

const browsers = ['chrome', 'firefox', 'edge'];
const buildDir = path.join(projectRoot, 'dist');

for (const browser of browsers) {
  const manifestPath = path.join(buildDir, browser, 'manifest.json');
  
  if (!fs.existsSync(manifestPath)) {
    console.warn(`⚠️  Manifest not found: ${manifestPath}`);
    continue;
  }

  const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));

  // Get the compiled content script path from the vite manifest
  const vitaManifestPath = path.join(buildDir, browser, '.vite', 'manifest.json');
  const vitaManifest = JSON.parse(fs.readFileSync(vitaManifestPath, 'utf8'));
  const contentScriptEntry = vitaManifest['src/content/index.ts'];
  
  if (!contentScriptEntry) {
    console.warn(`⚠️  Content script not found in vite manifest for ${browser}`);
    continue;
  }

  // Add content_scripts to manifest
  manifest.content_scripts = [
    {
      matches: ['<all_urls>'],
      js: [contentScriptEntry.file],
      run_at: 'document_start',
      all_frames: false,
    },
  ];

  // Write back the updated manifest
  fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2), 'utf8');
  console.log(`✅ Updated ${browser}/manifest.json with content_scripts: ${contentScriptEntry.file}`);
}

console.log('✅ Post-build manifest updates complete');
