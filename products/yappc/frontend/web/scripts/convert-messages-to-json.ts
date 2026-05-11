#!/usr/bin/env tsx
/**
 * Convert existing messages.ts to i18next JSON format
 * This script reads the messages.ts file and generates JSON locale files
 */

import { readFileSync, writeFileSync, mkdirSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// Read the messages.ts file
const messagesPath = join(__dirname, '../src/i18n/messages.ts');
const messagesContent = readFileSync(messagesPath, 'utf-8');

// Extract the enMessages object
const enMessagesMatch = messagesContent.match(/export const enMessages = \{([\s\S]*?)\} as const;/);
if (!enMessagesMatch) {
  throw new Error('Could not find enMessages in messages.ts');
}

const enMessagesString = enMessagesMatch[1];

// Convert the object literal to a proper JSON object
// We need to handle the string literals and remove comments
const lines = enMessagesString.split('\n');
const messages: Record<string, string> = {};

for (const line of lines) {
  const trimmed = line.trim();
  // Skip empty lines and comments
  if (!trimmed || trimmed.startsWith('//') || trimmed.startsWith('/*')) {
    continue;
  }
  
  // Match key-value pairs like 'key': 'value',
  const match = trimmed.match(/^'([^']+)':\s*'([^']*)',?$/);
  if (match) {
    const [, key, value] = match;
    messages[key] = value;
  }
}

// Ensure output directory exists
const outputDir = join(__dirname, '../public/locales/en');
mkdirSync(outputDir, { recursive: true });

// Write the JSON file
const outputPath = join(outputDir, 'common.json');
writeFileSync(outputPath, JSON.stringify(messages, null, 2), 'utf-8');

console.log(`✅ Generated ${outputPath} with ${Object.keys(messages).length} messages`);
