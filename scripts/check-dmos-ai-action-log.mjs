#!/usr/bin/env node
/**
 * DMOS AI Action Log Validation
 * Validates AI actions are logged with full metadata
 */

import { readFileSync, existsSync } from 'fs';
import { resolve } from 'path';

const EVIDENCE_PATH = resolve('.kernel/evidence/digital-marketing/ai-transparency.json');

function validateAIActionLog() {
  if (!existsSync(EVIDENCE_PATH)) {
    console.error('❌ AI transparency evidence not found:', EVIDENCE_PATH);
    process.exit(1);
  }

  const evidence = JSON.parse(readFileSync(EVIDENCE_PATH, 'utf-8'));

  if (!evidence.promptLogging) {
    console.error('❌ Prompt logging not enabled');
    process.exit(1);
  }

  if (!evidence.modelLogging) {
    console.error('❌ Model logging not enabled');
    process.exit(1);
  }

  if (!evidence.confidenceLogging) {
    console.error('❌ Confidence logging not enabled');
    process.exit(1);
  }

  if (!evidence.rationaleLogging) {
    console.error('❌ Rationale logging not enabled');
    process.exit(1);
  }

  console.log('✅ AI action log validation passed');
  console.log('   - Prompt logging: enabled');
  console.log('   - Model logging: enabled');
  console.log('   - Confidence logging: enabled');
  console.log('   - Rationale logging: enabled');
}

validateAIActionLog();
