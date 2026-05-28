#!/usr/bin/env node

/**
 * PHR IA Coverage Documentation Generator
 *
 * Generates a human-readable markdown document from the PHR use-case baseline.
 * This document provides a clear overview of IA coverage across personas, routes,
 * and implementation status.
 *
 * Usage:
 *   node scripts/generate-phr-ia-coverage-doc.mjs
 */

import { readFileSync, writeFileSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const BASELINE_FILE = join(__dirname, '../products/phr/config/phr-usecase-baseline.json');
const OUTPUT_FILE = join(__dirname, '../products/phr/docs/IA_COVERAGE.md');

function main() {
  console.log('📄 Generating PHR IA coverage documentation...\n');

  const baseline = JSON.parse(readFileSync(BASELINE_FILE, 'utf-8'));
  const usecases = baseline.usecases;

  // Group by persona
  const byPersona = {};
  usecases.forEach(uc => {
    if (!byPersona[uc.persona]) {
      byPersona[uc.persona] = [];
    }
    byPersona[uc.persona].push(uc);
  });

  // Group by status
  const byStatus = {};
  usecases.forEach(uc => {
    if (!byStatus[uc.status]) {
      byStatus[uc.status] = [];
    }
    byStatus[uc.status].push(uc);
  });

  // Group by phase
  const byPhase = {};
  usecases.forEach(uc => {
    if (!byPhase[uc.phase]) {
      byPhase[uc.phase] = [];
    }
    byPhase[uc.phase].push(uc);
  });

let markdown = `# PHR IA Coverage Report

**Generated:** ${new Date().toISOString().split('T')[0]}
**Baseline Version:** ${baseline.version}
**Total Use Cases:** ${usecases.length}

## Summary

| Status | Count | Percentage |
|--------|-------|------------|
${Object.entries(byStatus).map(([status, items]) => {
  const count = items.length;
  const pct = ((count / usecases.length) * 100).toFixed(1);
  return `| ${status} | ${count} | ${pct}% |`;
}).join('\n')}

| Phase | Count | Percentage |
|-------|-------|------------|
${Object.entries(byPhase).map(([phase, items]) => {
  const count = items.length;
  const pct = ((count / usecases.length) * 100).toFixed(1);
  return `| ${phase} | ${count} | ${pct}% |`;
}).join('\n')}

## Implementation Status by Persona

${Object.entries(byPersona).map(([persona, items]) => {
  const implemented = items.filter(uc => uc.status === 'implemented').length;
  const total = items.length;
  const pct = ((implemented / total) * 100).toFixed(1);
  return `- **${persona}**: ${implemented}/${total} (${pct}% implemented)`;
}).join('\n')}

## Use Cases by Persona

${Object.entries(byPersona).map(([persona, items]) => {
  return `
### ${persona.charAt(0).toUpperCase() + persona.slice(1)}

| Use Case | Screen | Route | Status | Notes |
|----------|--------|-------|--------|-------|
${items.map(uc => {
  const screen = uc.screen || 'N/A';
  const route = uc.iaRoute || 'N/A';
  const notes = (uc.notes || '').substring(0, 50) + (uc.notes?.length > 50 ? '...' : '');
  return `| ${uc.id} | ${screen} | ${route} | ${uc.status} | ${notes} |`;
}).join('\n')}
`;
}).join('\n')}

## Use Cases by Status

${Object.entries(byStatus).map(([status, items]) => {
  return `
### ${status.charAt(0).toUpperCase() + status.slice(1)} (${items.length})

${items.map(uc => {
  return `- **${uc.id}** (${uc.persona}): ${uc.screen} - ${uc.iaRoute}`;
}).join('\n')}
`;
}).join('\n')}

## Kernel Capability Mapping

| Use Case | Kernel Capability |
|----------|-------------------|
${usecases.filter(uc => uc.kernelCapability).map(uc => {
  return `| ${uc.id} | ${uc.kernelCapability} |`;
}).join('\n')}

## Backend API Coverage

${usecases.filter(uc => uc.backendApis && uc.backendApis.length > 0).map(uc => {
  return `
### ${uc.id}

${uc.backendApis.map(api => `- ${api}`).join('\n')}
`;
}).join('\n')}

## Offline Support

${usecases.filter(uc => uc.offlineSupport).map(uc => {
  return `- **${uc.id}** (${uc.persona}): Offline support enabled`;
}).join('\n')}

---

*This document is auto-generated from \`products/phr/config/phr-usecase-baseline.json\`*
`;

  writeFileSync(OUTPUT_FILE, markdown, 'utf-8');
  console.log(`✅ Generated IA coverage documentation: ${OUTPUT_FILE}`);
  console.log(`📊 Processed ${usecases.length} use cases across ${Object.keys(byPersona).length} personas\n`);
}

main();
