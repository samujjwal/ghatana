#!/usr/bin/env node
/**
 * DMOS Google Ads Connector Validation
 * Validates Google Ads connector OAuth and idempotency
 */

import { readFileSync, existsSync } from 'fs';
import { resolve } from 'path';

const EVIDENCE_PATH = resolve('.kernel/evidence/digital-marketing/google-ads-connector.json');

function validateGoogleAdsConnector() {
  if (!existsSync(EVIDENCE_PATH)) {
    console.error('❌ Google Ads connector evidence not found:', EVIDENCE_PATH);
    process.exit(1);
  }

  const evidence = JSON.parse(readFileSync(EVIDENCE_PATH, 'utf-8'));

  if (!evidence.oauthConfigured) {
    console.error('❌ OAuth not configured for Google Ads connector');
    process.exit(1);
  }

  if (!evidence.tokenRefreshEnabled) {
    console.error('❌ Token refresh not enabled');
    process.exit(1);
  }

  if (!evidence.idempotencyKeysEnabled) {
    console.error('❌ Idempotency keys not enabled');
    process.exit(1);
  }

  console.log('✅ Google Ads connector validation passed');
  console.log('   - OAuth: configured');
  console.log('   - Token refresh: enabled');
  console.log('   - Idempotency: enabled');
}

validateGoogleAdsConnector();
